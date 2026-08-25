package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registro de aliases (nomes de cabeçalho aceitos) para cada campo canônico
 * lido da planilha de atendimentos.
 * <p>
 * Duas camadas, carregadas na mesma ordem de precedência já usada por
 * {@link CnsProfissionalUtils}:
 * <ol>
 *   <li><b>Padrão</b> — classpath ({@code /dados/colunas_aliases.csv}),
 *       versionado no git, comum a todas as instalações.</li>
 *   <li><b>Local</b> — arquivo externo
 *       ({@code src/main/resources/dados/colunas_aliases.csv}), aliases
 *       adicionados por esta instalação. Complementa o padrão e pode ser
 *       removido; o padrão não pode.</li>
 * </ol>
 * Formato do CSV: {@code campoCanonico;alias} (separador ponto-e-vírgula).
 */
public class ColunaAliasUtils {

	private static final Logger log = LoggerFactory.getLogger(ColunaAliasUtils.class);

	/** Caminho do arquivo CSV local (para leitura e gravação). */
	private static final String CAMINHO_CSV_FONTE = "src/main/resources/dados/colunas_aliases.csv";

	public enum Origem { PADRAO, LOCAL }

	public record AliasInfo(String nome, Origem origem) {
	}

	/** Campo canônico → aliases vindos do classpath. Lazy loading. */
	private static Map<String, List<String>> aliasesPadrao = null;

	/** Campo canônico → aliases vindos do arquivo externo desta instalação. Lazy loading. */
	private static Map<String, List<String>> aliasesLocais = null;

	/** Alias normalizado → campo canônico (índice combinado, para busca rápida). */
	private static Map<String, String> campoPorAlias = null;

	/**
	 * Resolve o campo canônico correspondente a um texto de cabeçalho, ou
	 * {@code null} se nenhum alias cadastrado bater com o texto informado.
	 */
	public static String resolverCampo(String textoCabecalho) {

		carregarCsvSeNecessario();

		String normalizado = TextoUtils.normalizar(textoCabecalho);

		if (normalizado.isEmpty()) {
			return null;
		}

		return campoPorAlias.get(normalizado);
	}

	/** Lista, na ordem cadastrada, os campos canônicos conhecidos (padrão + locais). */
	public static List<String> obterCamposCanonicos() {

		carregarCsvSeNecessario();

		LinkedHashSet<String> campos = new LinkedHashSet<>(aliasesPadrao.keySet());
		campos.addAll(aliasesLocais.keySet());

		return new ArrayList<>(campos);
	}

	/** Aliases cadastrados para um campo canônico, com a origem de cada um. */
	public static List<AliasInfo> obterAliases(String campoCanonico) {

		carregarCsvSeNecessario();

		List<AliasInfo> lista = new ArrayList<>();

		for (String alias : aliasesPadrao.getOrDefault(campoCanonico, List.of())) {
			lista.add(new AliasInfo(alias, Origem.PADRAO));
		}
		for (String alias : aliasesLocais.getOrDefault(campoCanonico, List.of())) {
			lista.add(new AliasInfo(alias, Origem.LOCAL));
		}

		return lista;
	}

	/**
	 * Todos os campos e seus aliases cadastrados — usado pela tela de
	 * configuração para listar tudo de uma vez.
	 */
	public static Map<String, List<AliasInfo>> obterTodosRegistrados() {

		carregarCsvSeNecessario();

		Map<String, List<AliasInfo>> todos = new LinkedHashMap<>();

		for (String campo : obterCamposCanonicos()) {
			todos.put(campo, obterAliases(campo));
		}

		return todos;
	}

	/**
	 * Cadastra um novo alias local para um campo canônico e persiste no
	 * arquivo CSV externo desta instalação.
	 *
	 * @throws RuntimeException se houver erro na gravação
	 */
	public static synchronized void salvar(String campoCanonico, String alias) {

		carregarCsvSeNecessario();

		String aliasNorm = TextoUtils.normalizar(alias);

		if (aliasNorm.isEmpty()) {
			return;
		}

		String campoExistente = campoPorAlias.get(aliasNorm);

		if (campoCanonico.equals(campoExistente)) {
			log.info("Alias já cadastrado: campo='{}', alias='{}'", campoCanonico, alias);
			return;
		}

		aliasesLocais.computeIfAbsent(campoCanonico, k -> new ArrayList<>()).add(alias);
		campoPorAlias.put(aliasNorm, campoCanonico);

		salvarNoCsv(campoCanonico, alias);

		log.info("Alias de coluna cadastrado: campo='{}', alias='{}'", campoCanonico, alias);
	}

	/**
	 * Remove um alias <b>local</b>. Aliases padrão (classpath) nunca são
	 * removidos por aqui — para isso existe {@link #restaurarPadrao}.
	 */
	public static synchronized void remover(String campoCanonico, String alias) {

		carregarCsvSeNecessario();

		List<String> locais = aliasesLocais.get(campoCanonico);

		if (locais != null) {
			locais.removeIf(a -> TextoUtils.normalizar(a).equals(TextoUtils.normalizar(alias)));
		}

		reindexarCampoPorAlias();
		reescreverArquivoExterno();

		log.info("Alias de coluna removido: campo='{}', alias='{}'", campoCanonico, alias);
	}

	/** Remove todos os aliases locais de um campo, voltando só ao padrão. */
	public static synchronized void restaurarPadrao(String campoCanonico) {

		carregarCsvSeNecessario();

		aliasesLocais.remove(campoCanonico);

		reindexarCampoPorAlias();
		reescreverArquivoExterno();

		log.info("Aliases locais restaurados ao padrão: campo='{}'", campoCanonico);
	}

	/** Limpa o cache em memória, forçando recarga na próxima chamada. */
	public static synchronized void limparCache() {
		aliasesPadrao = null;
		aliasesLocais = null;
		campoPorAlias = null;
	}

	// ======================================================
	// MÉTODOS INTERNOS
	// ======================================================

	private static synchronized void carregarCsvSeNecessario() {

		if (aliasesPadrao != null) {
			return;
		}

		aliasesPadrao = new LinkedHashMap<>();
		aliasesLocais = new LinkedHashMap<>();
		campoPorAlias = new LinkedHashMap<>();

		carregarDeClasspath();
		carregarDeArquivoExterno();
		reindexarCampoPorAlias();

		log.info("Cache de aliases de coluna carregado: {} campos", aliasesPadrao.size());
	}

	private static void carregarDeClasspath() {

		try (InputStream is = ColunaAliasUtils.class
				.getResourceAsStream("/dados/colunas_aliases.csv")) {

			if (is == null) {
				log.warn("Arquivo colunas_aliases.csv não encontrado no classpath");
				return;
			}

			carregarDeReader(new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8)), aliasesPadrao);

		} catch (Exception e) {
			log.error("Erro ao carregar CSV de aliases do classpath: {}", e.getMessage());
		}
	}

	private static void carregarDeArquivoExterno() {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		if (!Files.exists(caminho)) {
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(caminho, StandardCharsets.UTF_8)) {
			carregarDeReader(reader, aliasesLocais);
		} catch (Exception e) {
			log.error("Erro ao carregar CSV externo de aliases: {}", e.getMessage());
		}
	}

	private static void carregarDeReader(BufferedReader reader, Map<String, List<String>> destino) {

		try {
			String linha;

			while ((linha = reader.readLine()) != null) {

				if (linha.isBlank() || linha.startsWith("campo")) {
					continue;
				}

				String[] partes = linha.split(";", 2);

				if (partes.length == 2) {

					String campo = partes[0].trim();
					String alias = partes[1].trim();

					if (!campo.isEmpty() && !alias.isEmpty()) {
						destino.computeIfAbsent(campo, k -> new ArrayList<>()).add(alias);
					}
				}
			}

		} catch (Exception e) {
			log.error("Erro ao processar linhas do CSV de aliases: {}", e.getMessage());
		}
	}

	private static void reindexarCampoPorAlias() {

		campoPorAlias.clear();

		for (Map.Entry<String, List<String>> entry : aliasesPadrao.entrySet()) {
			for (String alias : entry.getValue()) {
				campoPorAlias.put(TextoUtils.normalizar(alias), entry.getKey());
			}
		}
		for (Map.Entry<String, List<String>> entry : aliasesLocais.entrySet()) {
			for (String alias : entry.getValue()) {
				campoPorAlias.put(TextoUtils.normalizar(alias), entry.getKey());
			}
		}
	}

	private static void salvarNoCsv(String campoCanonico, String alias) {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		try {

			Files.createDirectories(caminho.getParent());

			if (!Files.exists(caminho)) {
				Files.writeString(caminho, "campo;alias\n", StandardCharsets.UTF_8);
			}

			try (BufferedWriter writer = Files.newBufferedWriter(
					caminho, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {

				writer.write(campoCanonico + ";" + alias);
				writer.newLine();
			}

		} catch (Exception e) {
			log.error("Erro ao salvar alias de coluna no CSV: {}", e.getMessage());
			throw new RuntimeException("Erro ao salvar no arquivo CSV: " + e.getMessage(), e);
		}
	}

	/**
	 * Reescreve o arquivo externo inteiro a partir do estado atual em
	 * memória de {@code aliasesLocais} — necessário porque remoção não é
	 * uma operação de append. Nunca toca no CSV do classpath.
	 */
	private static void reescreverArquivoExterno() {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		try {

			Files.createDirectories(caminho.getParent());

			try (BufferedWriter writer = Files.newBufferedWriter(
					caminho, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

				writer.write("campo;alias");
				writer.newLine();

				for (Map.Entry<String, List<String>> entry : aliasesLocais.entrySet()) {
					for (String alias : entry.getValue()) {
						writer.write(entry.getKey() + ";" + alias);
						writer.newLine();
					}
				}
			}

		} catch (Exception e) {
			log.error("Erro ao reescrever CSV externo de aliases: {}", e.getMessage());
			throw new RuntimeException("Erro ao reescrever o arquivo CSV: " + e.getMessage(), e);
		}
	}
}
