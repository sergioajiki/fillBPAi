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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para resolução do CNS (Cartão Nacional de Saúde) do profissional
 * a partir do nome, utilizando cache local (medicos_cns.csv).
 * <p>
 * O CNS é a chave canônica de cada médico; os nomes são apelidos (aliases)
 * dela — a mesma planilha pode grafar o nome de formas diferentes (com/sem
 * sobrenome, com/sem acento) e todas devem resolver para o mesmo CNS. O
 * arquivo continua no formato {@code nome;cns}: várias linhas com o mesmo
 * CNS já significam, por definição, apelidos do mesmo médico — não há
 * migração de formato, só de leitura.
 * <p>
 * A busca por nome é normalizada: uppercase, sem acentos, trim.
 * Exemplo: "José da Silva" e "JOSE DA SILVA" são considerados iguais.
 * <p>
 * Profissionais não encontrados são reportados como avisos no log de importação.
 */
public class CnsProfissionalUtils {

	private static final Logger log = LoggerFactory.getLogger(CnsProfissionalUtils.class);

	/** Caminho do arquivo CSV cache local (para gravação) */
	private static final String CAMINHO_CSV_FONTE = "src/main/resources/dados/medicos_cns.csv";

	/** Mapa: CNS → apelidos (nomes originais), em ordem de cadastro. Índice 0 = nome principal. Lazy loading. */
	private static Map<String, List<String>> apelidosPorCns = null;

	/** Mapa: nome normalizado → CNS. Índice reverso para busca rápida. Lazy loading. */
	private static Map<String, String> cnsPorNome = null;

	/**
	 * Busca o CNS do profissional pelo nome (ou qualquer apelido cadastrado).
	 *
	 * @param nome nome do profissional (com ou sem acentos, qualquer casing)
	 * @return CnsResultado com o CNS encontrado (ou null) e aviso opcional
	 */
	public static CnsResultado buscar(String nome) {

		if (nome == null || nome.isBlank()) {
			return new CnsResultado(null, null);
		}

		carregarCsvSeNecessario();

		String cns = cnsPorNome.get(normalizar(nome));

		if (cns != null) {
			log.debug("CNS encontrado para profissional '{}': {}", nome, cns);
			return new CnsResultado(cns, null);
		}

		// Nome não encontrado no cache — aviso para o log de importação
		String aviso = "CNS do profissional não encontrado para: " + nome
				+ ". Verifique o nome na planilha ou cadastre o profissional em Configurações → CNS de Médicos.";

		log.debug(aviso);

		return new CnsResultado(null, aviso);
	}

	/**
	 * Lista todos os médicos cadastrados, agrupados por CNS.
	 * O primeiro apelido de cada médico (ordem de cadastro) é o nome principal.
	 */
	public static List<MedicoInfo> obterTodosMedicos() {

		carregarCsvSeNecessario();

		List<MedicoInfo> lista = new ArrayList<>();

		for (Map.Entry<String, List<String>> entry : apelidosPorCns.entrySet()) {
			lista.add(new MedicoInfo(entry.getKey(), List.copyOf(entry.getValue())));
		}

		return lista;
	}

	/**
	 * Cadastra um médico novo.
	 *
	 * @throws IllegalArgumentException se o CNS já estiver cadastrado
	 */
	public static synchronized void cadastrar(String cns, String primeiroNome) {

		carregarCsvSeNecessario();

		if (apelidosPorCns.containsKey(cns)) {
			throw new IllegalArgumentException(
					"Já existe um médico com este CNS: " + nomePrincipal(cns));
		}

		String nomeNorm = normalizar(primeiroNome);
		String cnsExistente = cnsPorNome.get(nomeNorm);

		if (cnsExistente != null) {
			throw new IllegalArgumentException(
					"Este nome já está associado a outro CNS: " + cnsExistente);
		}

		List<String> apelidos = new ArrayList<>();
		apelidos.add(primeiroNome);
		apelidosPorCns.put(cns, apelidos);
		cnsPorNome.put(nomeNorm, cns);

		reescreverArquivoExterno();

		log.info("Médico cadastrado: CNS={}, Nome='{}'", cns, primeiroNome);
	}

	/**
	 * Adiciona um apelido (variação de nome) a um médico já cadastrado.
	 *
	 * @throws IllegalArgumentException se o CNS não existir ou o nome já pertencer a outro CNS
	 */
	public static synchronized void adicionarApelido(String cns, String novoNome) {

		carregarCsvSeNecessario();

		List<String> apelidos = apelidosPorCns.get(cns);

		if (apelidos == null) {
			throw new IllegalArgumentException("Médico não encontrado para o CNS: " + cns);
		}

		String nomeNorm = normalizar(novoNome);
		String cnsExistente = cnsPorNome.get(nomeNorm);

		if (cnsExistente != null && !cnsExistente.equals(cns)) {
			throw new IllegalArgumentException(
					"Este nome já está associado a outro CNS: " + cnsExistente);
		}

		if (cnsExistente != null) {
			// Já é apelido deste mesmo médico — nada a fazer.
			return;
		}

		apelidos.add(novoNome);
		cnsPorNome.put(nomeNorm, cns);

		reescreverArquivoExterno();

		log.info("Apelido adicionado: CNS={}, Nome='{}'", cns, novoNome);
	}

	/**
	 * Remove um apelido de um médico. Recusa remover o único apelido restante
	 * (use {@link #removerMedico(String)} para excluir o médico inteiro).
	 *
	 * @throws IllegalArgumentException se for o único apelido do médico
	 */
	public static synchronized void removerApelido(String cns, String nome) {

		carregarCsvSeNecessario();

		List<String> apelidos = apelidosPorCns.get(cns);

		if (apelidos == null) {
			return;
		}

		if (apelidos.size() <= 1) {
			throw new IllegalArgumentException(
					"Este é o único apelido do médico — remova o médico em vez do apelido.");
		}

		String nomeNormAlvo = normalizar(nome);
		apelidos.removeIf(a -> normalizar(a).equals(nomeNormAlvo));
		cnsPorNome.remove(nomeNormAlvo);

		reescreverArquivoExterno();

		log.info("Apelido removido: CNS={}, Nome='{}'", cns, nome);
	}

	/**
	 * Altera o CNS de um médico já cadastrado (corrige erro de digitação).
	 *
	 * @throws IllegalArgumentException se o CNS antigo não existir ou o novo já pertencer a outro médico
	 */
	public static synchronized void alterarCns(String cnsAntigo, String cnsNovo) {

		carregarCsvSeNecessario();

		List<String> apelidos = apelidosPorCns.get(cnsAntigo);

		if (apelidos == null) {
			throw new IllegalArgumentException("Médico não encontrado para o CNS: " + cnsAntigo);
		}

		if (cnsAntigo.equals(cnsNovo)) {
			return;
		}

		if (apelidosPorCns.containsKey(cnsNovo)) {
			throw new IllegalArgumentException(
					"Este CNS já pertence a outro médico: " + nomePrincipal(cnsNovo)
							+ " — adicione este nome como apelido daquele cadastro em vez de editar este.");
		}

		apelidosPorCns.remove(cnsAntigo);
		apelidosPorCns.put(cnsNovo, apelidos);

		for (String apelido : apelidos) {
			cnsPorNome.put(normalizar(apelido), cnsNovo);
		}

		reescreverArquivoExterno();

		log.info("CNS alterado: {} → {}", cnsAntigo, cnsNovo);
	}

	/** Remove um médico e todos os seus apelidos. */
	public static synchronized void removerMedico(String cns) {

		carregarCsvSeNecessario();

		List<String> apelidos = apelidosPorCns.remove(cns);

		if (apelidos != null) {
			for (String apelido : apelidos) {
				cnsPorNome.remove(normalizar(apelido));
			}
		}

		reescreverArquivoExterno();

		log.info("Médico removido: CNS={}", cns);
	}

	/**
	 * Limpa o cache em memória, forçando recarga na próxima busca.
	 * Útil para testes e recarregamento manual.
	 */
	public static synchronized void limparCache() {
		apelidosPorCns = null;
		cnsPorNome = null;
	}

	// ======================================================
	// MÉTODOS INTERNOS
	// ======================================================

	private static String nomePrincipal(String cns) {
		List<String> apelidos = apelidosPorCns.get(cns);
		return (apelidos == null || apelidos.isEmpty()) ? cns : apelidos.get(0);
	}

	/**
	 * Carrega o arquivo CSV de profissionais do classpath e arquivo externo (lazy loading).
	 * Formato: nome;cns (separador ponto-e-vírgula)
	 * Encoding: UTF-8
	 */
	private static synchronized void carregarCsvSeNecessario() {

		if (apelidosPorCns != null) {
			return;
		}

		apelidosPorCns = new LinkedHashMap<>();
		cnsPorNome = new LinkedHashMap<>();

		// Carrega do classpath (recurso embutido)
		carregarDeClasspath();

		// Carrega do arquivo externo (mesma fonte, em desenvolvimento; complementa em produção)
		carregarDeArquivoExterno();

		log.info("Cache CNS profissional carregado: {} médicos", apelidosPorCns.size());
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
	 * Usado para capturar entradas adicionadas pelo cadastro
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

						// Mesmo apelido já lido (ex.: presente no classpath e no externo) — não duplica.
						if (cnsPorNome.containsKey(nomeNorm)) {
							continue;
						}

						apelidosPorCns.computeIfAbsent(cns, k -> new ArrayList<>()).add(nome);
						cnsPorNome.put(nomeNorm, cns);
					}
				}
			}

		} catch (Exception e) {
			log.error("Erro ao processar linhas do CSV: {}", e.getMessage());
		}
	}

	/**
	 * Reescreve o arquivo externo inteiro a partir do estado atual em
	 * memória — necessário porque atualização e remoção não são operações
	 * de append. Uma linha por apelido, ordenadas por nome para diffs de
	 * git legíveis. Nunca toca no CSV do classpath.
	 */
	private static void reescreverArquivoExterno() {

		Path caminho = Path.of(CAMINHO_CSV_FONTE);

		List<String[]> linhas = new ArrayList<>();

		for (Map.Entry<String, List<String>> entry : apelidosPorCns.entrySet()) {
			for (String apelido : entry.getValue()) {
				linhas.add(new String[] { apelido, entry.getKey() });
			}
		}

		linhas.sort(Comparator.comparing(l -> l[0], String.CASE_INSENSITIVE_ORDER));

		try {

			Files.createDirectories(caminho.getParent());

			try (BufferedWriter writer = Files.newBufferedWriter(
					caminho, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

				writer.write("nome;cns");
				writer.newLine();

				for (String[] linha : linhas) {
					writer.write(linha[0] + ";" + linha[1]);
					writer.newLine();
				}
			}

		} catch (Exception e) {
			log.error("Erro ao reescrever CSV de médicos: {}", e.getMessage());
			throw new RuntimeException("Erro ao reescrever o arquivo CSV: " + e.getMessage(), e);
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

	/** Um médico cadastrado: CNS e seus apelidos (nomes), em ordem de cadastro — índice 0 é o nome principal. */
	public record MedicoInfo(String cns, List<String> apelidos) {

		public String nomePrincipal() {
			return apelidos.isEmpty() ? "" : apelidos.get(0);
		}
	}
}
