package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para resolução do CNS (Cartão Nacional de Saúde) do profissional
 * a partir do nome, utilizando cache local (medicos_cns.csv).
 *
 * A busca por nome é normalizada: uppercase, sem acentos, trim.
 * Exemplo: "José da Silva" e "JOSE DA SILVA" são considerados iguais.
 *
 * Profissionais não encontrados são reportados como avisos no log de importação.
 */
public class CnsProfissionalUtils {

	private static final Logger log = LoggerFactory.getLogger(CnsProfissionalUtils.class);

	/** Caminho do arquivo CSV cache local (para gravação) */
	private static final String CAMINHO_CSV_FONTE = "src/main/resources/dados/medicos_cns.csv";

	/** Mapa: nome normalizado → CNS do profissional. Lazy loading. */
	private static Map<String, String> mapaCns = null;

	/** Mapa: nome normalizado → nome original (para exibição). Lazy loading. */
	private static Map<String, String> mapaNomeOriginal = null;

	/**
	 * Busca o CNS do profissional pelo nome.
	 *
	 * @param nome nome do profissional (com ou sem acentos, qualquer casing)
	 * @return CnsResultado com o CNS encontrado (ou null) e aviso opcional
	 */
	public static CnsResultado buscar(String nome) {

		if (nome == null || nome.isBlank()) {
			return new CnsResultado(null, null);
		}

		carregarCsvSeNecessario();

		String nomeNormalizado = normalizar(nome);

		String cns = mapaCns.get(nomeNormalizado);

		if (cns != null) {
			log.debug("CNS encontrado para profissional '{}': {}", nome, cns);
			return new CnsResultado(cns, null);
		}

		// Nome não encontrado no cache — aviso para o log de importação
		String aviso = "CNS do profissional não encontrado para: " + nome
				+ ". Verifique o nome na planilha ou adicione o profissional ao medicos_cns.csv.";

		log.debug(aviso);

		return new CnsResultado(null, aviso);
	}

	/**
	 * Salva um novo profissional no arquivo CSV cache e atualiza o cache em memória.
	 *
	 * @param nome nome do profissional
	 * @param cns  CNS do profissional
	 * @throws RuntimeException se houver erro na gravação
	 */
	public static synchronized void salvar(String nome, String cns) {

		carregarCsvSeNecessario();

		String nomeNorm = normalizar(nome);

		// Verifica se já existe com o mesmo CNS
		String cnsExistente = mapaCns.get(nomeNorm);

		if (cnsExistente != null && cnsExistente.equals(cns)) {
			log.info("Profissional já cadastrado: Nome='{}', CNS={}", nome, cns);
			return;
		}

		// Atualiza cache em memória
		mapaCns.put(nomeNorm, cns);
		mapaNomeOriginal.put(nomeNorm, nome);

		// Persiste no CSV
		salvarNoCsv(nome, cns);

		log.info("Profissional cadastrado: Nome='{}', CNS={}", nome, cns);
	}

	/**
	 * Retorna todos os profissionais registrados no cache local.
	 * Cada entrada é um array: [nome, cns].
	 *
	 * @return lista de arrays [nome, cns]
	 */
	public static List<String[]> obterTodosRegistrados() {

		carregarCsvSeNecessario();

		List<String[]> lista = new ArrayList<>();

		for (Map.Entry<String, String> entry : mapaCns.entrySet()) {

			String nomeNorm = entry.getKey();
			String cns = entry.getValue();
			String nomeOriginal = mapaNomeOriginal.getOrDefault(nomeNorm, nomeNorm);

			lista.add(new String[] { nomeOriginal, cns });
		}

		return lista;
	}

	/**
	 * Limpa o cache em memória, forçando recarga na próxima busca.
	 * Útil para testes e recarregamento manual.
	 */
	public static synchronized void limparCache() {
		mapaCns = null;
		mapaNomeOriginal = null;
	}

	// ======================================================
	// MÉTODOS INTERNOS
	// ======================================================

	/**
	 * Carrega o arquivo CSV de profissionais do classpath e arquivo externo (lazy loading).
	 * Formato: nome;cns (separador ponto-e-vírgula)
	 * Encoding: UTF-8
	 */
	private static synchronized void carregarCsvSeNecessario() {

		if (mapaCns != null) {
			return;
		}

		mapaCns = new HashMap<>();
		mapaNomeOriginal = new HashMap<>();

		// Carrega do classpath (recurso embutido)
		carregarDeClasspath();

		// Carrega do arquivo externo (sobrescreve se houver duplicatas)
		carregarDeArquivoExterno();

		log.info("Cache CNS profissional carregado: {} entradas", mapaCns.size());
	}

	/**
	 * Carrega entradas do CSV embutido no classpath.
	 */
	private static void carregarDeClasspath() {

		try (InputStream is = CnsProfissionalUtils.class
				.getResourceAsStream("/dados/medicos_cns.csv")) {

			if (is == null) {
				log.warn("Arquivo medicos_cns.csv não encontrado no classpath");
				return;
			}

			carregarDeReader(new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8)));

		} catch (Exception e) {
			log.error("Erro ao carregar CSV do classpath: {}", e.getMessage());
		}
	}

	/**
	 * Carrega entradas do arquivo externo (src/main/resources).
	 * Usado para capturar entradas adicionadas pelo pré-cadastro
	 * que ainda não foram recompiladas no classpath.
	 */
	private static void carregarDeArquivoExterno() {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		if (!Files.exists(caminho)) {
			return;
		}

		try (BufferedReader reader = Files.newBufferedReader(
				caminho, StandardCharsets.UTF_8)) {

			carregarDeReader(reader);

		} catch (Exception e) {
			log.error("Erro ao carregar CSV externo: {}", e.getMessage());
		}
	}

	/**
	 * Carrega entradas de um BufferedReader.
	 * Formato aceito: nome;cns (novo) ou cpf;cns;nome (legado — migrado automaticamente)
	 */
	private static void carregarDeReader(BufferedReader reader) {

		try {
			String linha;

			while ((linha = reader.readLine()) != null) {

				// Ignora linhas vazias e cabeçalho
				if (linha.isBlank() || linha.startsWith("nome") || linha.startsWith("cpf")) {
					continue;
				}

				String[] partes = linha.split(";");

				if (partes.length >= 2) {

					String campo1 = partes[0].trim();
					String campo2 = partes[1].trim();

					String nome;
					String cns;

					// Detecta formato: se campo1 é só dígitos, é formato legado (cpf;cns;nome)
					if (campo1.matches("\\d+") && campo1.length() <= 14) {
						// Formato legado: cpf;cns;nome → ignora cpf, usa cns e nome
						cns = campo2;
						nome = partes.length >= 3 ? partes[2].trim() : "";
					} else {
						// Formato novo: nome;cns
						nome = campo1;
						cns = campo2;
					}

					if (!nome.isEmpty() && !cns.isEmpty()) {
						String nomeNorm = normalizar(nome);
						mapaCns.put(nomeNorm, cns);
						mapaNomeOriginal.put(nomeNorm, nome);
					}
				}
			}

		} catch (Exception e) {
			log.error("Erro ao processar linhas do CSV: {}", e.getMessage());
		}
	}

	/**
	 * Persiste uma entrada no arquivo CSV (append).
	 */
	private static void salvarNoCsv(String nome, String cns) {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		try {

			Files.createDirectories(caminho.getParent());

			if (!Files.exists(caminho)) {
				Files.writeString(caminho, "nome;cns\n", StandardCharsets.UTF_8);
			}

			try (BufferedWriter writer = Files.newBufferedWriter(
					caminho, StandardCharsets.UTF_8,
					StandardOpenOption.APPEND)) {

				writer.write(nome + ";" + cns);
				writer.newLine();
			}

		} catch (Exception e) {
			log.error("Erro ao salvar profissional no CSV: {}", e.getMessage());
			throw new RuntimeException("Erro ao salvar no arquivo CSV: " + e.getMessage(), e);
		}
	}

	/**
	 * Normaliza texto para busca: remove acentos, converte para maiúsculo, trim.
	 * Garante que "José da Silva" encontra "JOSE DA SILVA".
	 *
	 * @param texto texto a normalizar
	 * @return texto normalizado (uppercase, sem acentos)
	 */
	static String normalizar(String texto) {

		if (texto == null) {
			return "";
		}

		String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");

		return semAcentos.toUpperCase().trim();
	}

	/**
	 * Resultado da busca de CNS do profissional.
	 * Contém o CNS encontrado (ou null) e um aviso opcional.
	 */
	public static class CnsResultado {

		private final String cns;
		private final String aviso;

		public CnsResultado(String cns, String aviso) {
			this.cns = cns;
			this.aviso = aviso;
		}

		/** CNS do profissional ou null se não encontrado. */
		public String getCns() {
			return cns;
		}

		/** Mensagem de aviso ou null se encontrado sem problemas. */
		public String getAviso() {
			return aviso;
		}
	}
}
