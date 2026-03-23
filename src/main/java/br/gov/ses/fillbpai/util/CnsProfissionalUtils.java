package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para resolução do CNS (Cartão Nacional de Saúde) do profissional
 * a partir do nome, utilizando cache local (CSV) e arquivo DATASUS como fonte primária.
 *
 * Estratégia de busca:
 * 1. Cache local: arquivo medicos_cns.csv (nome;cns) — rápido, persistente
 * 2. Arquivo DATASUS: tbDadosProfissionalSus*.csv — 7M+ registros, streaming
 *
 * A busca por nome é normalizada: uppercase, sem acentos, trim.
 * Exemplo: "José da Silva" e "JOSE DA SILVA" são considerados iguais.
 *
 * Divergências (nome duplicado com CNS diferentes, nome não encontrado)
 * são reportadas como avisos no log de importação.
 */
public class CnsProfissionalUtils {

	private static final Logger log = LoggerFactory.getLogger(CnsProfissionalUtils.class);

	/** Caminho do arquivo CSV cache local (para gravação) */
	private static final String CAMINHO_CSV_FONTE = "src/main/resources/dados/medicos_cns.csv";

	/** Mapa: nome normalizado → CNS do profissional. Lazy loading. */
	private static Map<String, String> mapaCns = null;

	/** Mapa: nome normalizado → nome original (para exibição). Lazy loading. */
	private static Map<String, String> mapaNomeOriginal = null;

	/** Caminho do arquivo DATASUS para busca secundária */
	private static String caminhoDatasus = null;

	/**
	 * Define o caminho do arquivo DATASUS para buscas de CNS.
	 *
	 * @param caminho caminho completo do arquivo CSV do DATASUS
	 */
	public static void setCaminhoDatasus(String caminho) {
		caminhoDatasus = caminho;
	}

	/**
	 * Retorna o caminho do arquivo DATASUS configurado.
	 *
	 * @return caminho do arquivo ou null se não configurado
	 */
	public static String getCaminhoDatasus() {
		return caminhoDatasus;
	}

	/**
	 * Tenta auto-detectar o arquivo DATASUS na pasta Documents do usuário.
	 * Procura por arquivos com padrão "tbDadosProfissionalSus*.csv".
	 *
	 * @return true se encontrou e configurou o caminho, false caso contrário
	 */
	public static boolean autoDetectarDatasus() {

		if (caminhoDatasus != null) {
			return true;
		}

		File documentos = new File(System.getProperty("user.home"), "Documents");

		if (!documentos.exists() || !documentos.isDirectory()) {
			return false;
		}

		File[] arquivos = documentos.listFiles(
				(dir, name) -> name.startsWith("tbDadosProfissionalSus") && name.endsWith(".csv"));

		if (arquivos != null && arquivos.length > 0) {
			caminhoDatasus = arquivos[0].getAbsolutePath();
			log.info("Arquivo DATASUS auto-detectado: {}", caminhoDatasus);
			return true;
		}

		return false;
	}

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
				+ ". Utilize o Pré-Cadastro CNS ou verifique o nome na planilha.";

		log.debug(aviso);

		return new CnsResultado(null, aviso);
	}

	/**
	 * Busca em lote no arquivo DATASUS para um conjunto de nomes de profissionais.
	 *
	 * Realiza um único streaming pass pelo arquivo DATASUS (7M+ linhas),
	 * buscando todos os nomes simultaneamente. Resultados encontrados são
	 * cacheados no CSV local para futuras importações.
	 *
	 * @param nomes conjunto de nomes a buscar (não normalizados)
	 * @return lista de avisos gerados durante a busca
	 */
	public static List<String> buscarLoteDatasus(Set<String> nomes) {

		List<String> avisos = new ArrayList<>();

		if (nomes == null || nomes.isEmpty()) {
			return avisos;
		}

		carregarCsvSeNecessario();

		// Filtra apenas nomes que NÃO estão no cache local
		Map<String, String> nomesParaBuscar = new LinkedHashMap<>();

		for (String nome : nomes) {

			if (nome == null || nome.isBlank()) {
				continue;
			}

			String nomeNormalizado = normalizar(nome);

			if (!mapaCns.containsKey(nomeNormalizado)) {
				nomesParaBuscar.put(nomeNormalizado, nome);
			}
		}

		if (nomesParaBuscar.isEmpty()) {
			log.info("Todos os profissionais já estão no cache local");
			return avisos;
		}

		// Tenta auto-detectar o arquivo DATASUS se não configurado
		if (caminhoDatasus == null) {
			autoDetectarDatasus();
		}

		if (caminhoDatasus == null || !new File(caminhoDatasus).exists()) {

			for (String nomeOriginal : nomesParaBuscar.values()) {
				avisos.add("Arquivo DATASUS não configurado. CNS não encontrado para: "
						+ nomeOriginal + ". Utilize o Pré-Cadastro CNS.");
			}

			return avisos;
		}

		log.info("Buscando {} profissional(is) no arquivo DATASUS...", nomesParaBuscar.size());

		// Mapa temporário para detectar duplicatas: nomeNorm → lista de CNS encontrados
		Map<String, List<String>> resultadosDatasus = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						new FileInputStream(caminhoDatasus),
						Charset.forName("ISO-8859-1")))) {

			String linha;
			boolean primeiraLinha = true;
			int linhasProcessadas = 0;

			while ((linha = reader.readLine()) != null) {

				if (primeiraLinha) {
					primeiraLinha = false;
					continue;
				}

				linhasProcessadas++;

				// Log de progresso a cada 1M de linhas
				if (linhasProcessadas % 1_000_000 == 0) {
					log.info("DATASUS: {} milhões de linhas processadas...",
							linhasProcessadas / 1_000_000);
				}

				String[] campos = linha.split(";");

				if (campos.length < 4) {
					continue;
				}

				String nomeDatasus = removerAspas(campos[2]);
				String cnsDatasus = removerAspas(campos[3]);

				if (nomeDatasus.isEmpty() || cnsDatasus.isEmpty()) {
					continue;
				}

				String nomeNormDatasus = normalizar(nomeDatasus);

				if (nomesParaBuscar.containsKey(nomeNormDatasus)) {

					resultadosDatasus
							.computeIfAbsent(nomeNormDatasus, k -> new ArrayList<>())
							.add(cnsDatasus);
				}
			}

			log.info("DATASUS: {} linhas processadas no total", linhasProcessadas);

		} catch (Exception e) {
			log.error("Erro ao buscar no arquivo DATASUS: {}", e.getMessage());
			avisos.add("Erro ao acessar arquivo DATASUS: " + e.getMessage());
			return avisos;
		}

		// Processa resultados: cache + detecção de divergências
		for (Map.Entry<String, String> entry : nomesParaBuscar.entrySet()) {

			String nomeNorm = entry.getKey();
			String nomeOriginal = entry.getValue();

			List<String> cnsEncontrados = resultadosDatasus.get(nomeNorm);

			if (cnsEncontrados == null || cnsEncontrados.isEmpty()) {

				avisos.add("CNS do profissional não encontrado no DATASUS para: "
						+ nomeOriginal + ". Utilize o Pré-Cadastro CNS.");
				continue;
			}

			// Usa o primeiro CNS encontrado
			String cnsPrimeiro = cnsEncontrados.get(0);

			// Detecta CNS duplicados diferentes para o mesmo nome
			long cnsDistintos = cnsEncontrados.stream().distinct().count();

			if (cnsDistintos > 1) {

				List<String> cnsUnicos = cnsEncontrados.stream().distinct().toList();

				avisos.add("Profissional '" + nomeOriginal
						+ "' possui " + cnsDistintos
						+ " CNS diferentes no DATASUS: " + cnsUnicos
						+ ". Utilizando o primeiro: " + cnsPrimeiro
						+ ". Verifique e corrija manualmente se necessário.");

				log.warn("Divergência DATASUS: '{}' possui {} CNS diferentes: {}",
						nomeOriginal, cnsDistintos, cnsUnicos);
			}

			// Salva no cache local
			mapaCns.put(nomeNorm, cnsPrimeiro);
			mapaNomeOriginal.put(nomeNorm, nomeOriginal);

			salvarNoCsv(nomeOriginal, cnsPrimeiro);

			log.info("CNS encontrado no DATASUS para '{}': {}", nomeOriginal, cnsPrimeiro);
		}

		return avisos;
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
	 * Remove aspas duplas de um campo CSV.
	 */
	private static String removerAspas(String campo) {

		if (campo == null) {
			return "";
		}

		return campo.replace("\"", "").trim();
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
