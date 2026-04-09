package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para resolução de código IBGE de municípios.
 *
 * Estratégia de resolução (em cascata):
 * 1. PRIMÁRIO: Busca por nome do município em arquivo CSV embutido
 *    - Sem rede, resultado instantâneo
 *    - Normalização Unicode para matching (remove acentos, uppercase)
 *    - CSV contém municípios de MS (pode ser expandido)
 *
 * 2. SECUNDÁRIO: Cache de CEPs resolvidos (inclui dados pré-carregados do banco)
 *    - Pré-populado antes da importação com CEP → codigoIbge dos endereços já persistidos
 *    - Sem rede, resultado instantâneo para CEPs já conhecidos
 *
 * 3. FALLBACK: Consulta API ViaCEP pelo CEP do paciente
 *    - Usado apenas quando CEP não está no cache
 *    - Determinístico (CEP → município é 1:1)
 *    - Resultados cacheados em memória para evitar chamadas repetidas
 *
 * Códigos IBGE:
 * - Padrão IBGE: 7 dígitos (2 estado + 4 município + 1 verificador)
 * - BPA-I (prd-ibge): 6 posições (sem dígito verificador)
 * - Esta classe retorna o código completo (7 dígitos).
 *   A truncagem para 6 é feita no GeradorBPAiService.
 */
public class IbgeUtils {

	private static final Logger log = LoggerFactory.getLogger(IbgeUtils.class);

	// Cache: CEP → código IBGE (7 dígitos)
	// Evita chamadas repetidas à API para o mesmo CEP
	private static final Map<String, String> cacheViaCep = new HashMap<>();

	// Mapa: nome normalizado do município → código IBGE (7 dígitos)
	// Carregado do CSV na primeira utilização (lazy loading)
	private static Map<String, String> mapaMunicipios = null;

	// Regex para extrair o campo "ibge" da resposta JSON do ViaCEP
	private static final Pattern PATTERN_IBGE =
			Pattern.compile("\"ibge\"\\s*:\\s*\"(\\d+)\"");

	// Regex para detectar resposta de erro do ViaCEP (CEP não encontrado)
	private static final Pattern PATTERN_ERRO =
			Pattern.compile("\"erro\"\\s*:\\s*true");

	// HttpClient reutilizável com timeout de conexão
	private static final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	/**
	 * Pré-carrega o cache de CEPs com dados já persistidos no banco de dados.
	 * Deve ser chamado antes do loop de importação para evitar chamadas à API
	 * para CEPs que já foram resolvidos em importações anteriores.
	 *
	 * @param cepParaIbge mapa de CEP normalizado → código IBGE (7 dígitos) vindo do banco
	 */
	public static void preCarregarCacheDb(java.util.Map<String, String> cepParaIbge) {
		if (cepParaIbge == null || cepParaIbge.isEmpty()) {
			return;
		}
		// Insere apenas CEPs que ainda não estão no cache (não sobrescreve resultados negativos)
		cepParaIbge.forEach((cep, ibge) -> {
			if (ibge != null && !ibge.isBlank()) {
				cacheViaCep.putIfAbsent(cep, ibge);
			}
		});
		log.info("Cache IBGE pré-carregado do banco: {} CEPs", cepParaIbge.size());
	}

	/**
	 * Resolve o código IBGE em cascata:
	 * 1. CSV (por nome do município) — sem rede, instantâneo
	 * 2. Cache/banco (por CEP) — sem rede, instantâneo para CEPs já conhecidos
	 * 3. API ViaCEP (por CEP) — apenas se não encontrado no cache
	 *
	 * @param cep CEP normalizado (8 dígitos, sem hífen) — pode ser null
	 * @param nomeMunicipio nome do município da planilha — pode ser null
	 * @return resultado com código IBGE (7 dígitos) e/ou aviso
	 */
	public static IbgeResultado resolver(String cep, String nomeMunicipio) {

		// 1. CSV: busca por nome do município (sem rede)
		if (nomeMunicipio != null && !nomeMunicipio.isBlank()) {

			String codigoPorNome = buscarPorNome(nomeMunicipio);

			if (codigoPorNome != null) {
				log.debug("IBGE resolvido via CSV para '{}': {}", nomeMunicipio, codigoPorNome);
				return new IbgeResultado(codigoPorNome, null);
			}
		}

		// 2. Cache/banco + 3. API ViaCEP: ambos via buscarPorCep (cache primeiro, API se necessário)
		if (cep != null && !cep.isBlank() && cep.length() >= 5) {

			String codigoPorCep = buscarPorCep(cep);

			if (codigoPorCep != null) {
				log.debug("IBGE resolvido via cache/API para CEP {}: {}", cep, codigoPorCep);
				return new IbgeResultado(codigoPorCep,
						"Código IBGE resolvido por CEP (não encontrado no CSV por nome): " + nomeMunicipio);
			}
		}

		// Nenhuma estratégia funcionou
		String aviso = "Código IBGE não encontrado para CEP=" + cep
				+ ", município=" + nomeMunicipio
				+ ". Campo prd-ibge será preenchido com brancos.";

		log.warn(aviso);

		return new IbgeResultado(null, aviso);
	}

	/**
	 * Consulta a API ViaCEP para obter o código IBGE a partir do CEP.
	 * Resultados são cacheados em memória.
	 *
	 * API: https://viacep.com.br/ws/{cep}/json/
	 * Resposta contém campo "ibge" com o código do município (7 dígitos).
	 *
	 * @param cep CEP normalizado (8 dígitos numéricos)
	 * @return código IBGE (7 dígitos) ou null se não encontrado/erro
	 */
	static String buscarPorCep(String cep) {

		// Verifica cache primeiro
		if (cacheViaCep.containsKey(cep)) {
			return cacheViaCep.get(cep);
		}

		try {

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://viacep.com.br/ws/" + cep + "/json/"))
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();

			HttpResponse<String> response =
					httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				log.warn("ViaCEP retornou status {} para CEP {}", response.statusCode(), cep);
				return null;
			}

			String body = response.body();

			// Verifica se o CEP não foi encontrado
			if (PATTERN_ERRO.matcher(body).find()) {
				log.debug("CEP {} não encontrado no ViaCEP", cep);
				cacheViaCep.put(cep, null); // cache negativo
				return null;
			}

			// Extrai o código IBGE do JSON
			Matcher matcher = PATTERN_IBGE.matcher(body);

			if (matcher.find()) {
				String codigoIbge = matcher.group(1);
				cacheViaCep.put(cep, codigoIbge);
				return codigoIbge;
			}

			log.warn("Campo 'ibge' não encontrado na resposta ViaCEP para CEP {}", cep);

		} catch (Exception e) {
			// Timeout, sem internet, erro de parse — loga e continua para fallback
			log.warn("Erro ao consultar ViaCEP para CEP {}: {}", cep, e.getMessage());
		}

		return null;
	}

	/**
	 * Busca o código IBGE pelo nome do município no arquivo CSV embutido.
	 *
	 * A busca é case-insensitive e ignora acentos (normalização Unicode).
	 * O CSV é carregado na primeira chamada (lazy loading).
	 *
	 * @param nomeMunicipio nome do município (como veio da planilha)
	 * @return código IBGE (7 dígitos) ou null se não encontrado
	 */
	static String buscarPorNome(String nomeMunicipio) {

		carregarCsvSeNecessario();

		String nomeNormalizado = normalizar(nomeMunicipio);

		return mapaMunicipios.get(nomeNormalizado);
	}

	/**
	 * Carrega o arquivo CSV de municípios do classpath (lazy loading).
	 * Formato: codigo_ibge;nome_municipio;uf
	 * Encoding: UTF-8
	 */
	private static synchronized void carregarCsvSeNecessario() {

		if (mapaMunicipios != null) {
			return;
		}

		mapaMunicipios = new HashMap<>();

		try (InputStream is = IbgeUtils.class.getResourceAsStream("/dados/municipios_ibge.csv")) {

			if (is == null) {
				log.error("Arquivo municipios_ibge.csv não encontrado no classpath");
				return;
			}

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8));

			String linha;

			while ((linha = reader.readLine()) != null) {

				// Ignora linhas vazias e cabeçalho
				if (linha.isBlank() || linha.startsWith("codigo")) {
					continue;
				}

				String[] partes = linha.split(";");

				if (partes.length >= 2) {

					String codigo = partes[0].trim();
					String nome = partes[1].trim();

					// Indexa pelo nome normalizado (sem acentos, uppercase)
					mapaMunicipios.put(normalizar(nome), codigo);
				}
			}

			log.info("CSV de municípios carregado: {} entradas", mapaMunicipios.size());

		} catch (Exception e) {
			log.error("Erro ao carregar CSV de municípios: {}", e.getMessage());
		}
	}

	/**
	 * Normaliza texto para comparação: remove acentos e converte para uppercase.
	 * Utiliza java.text.Normalizer para decomposição Unicode.
	 *
	 * Exemplos:
	 * - "Campo Grande" → "CAMPO GRANDE"
	 * - "Três Lagoas"  → "TRES LAGOAS"
	 * - "São Paulo"    → "SAO PAULO"
	 */
	static String normalizar(String texto) {

		if (texto == null) {
			return "";
		}

		// NFD decompõe acentos em caractere base + combining mark
		String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
				.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

		return semAcentos.trim().toUpperCase();
	}

	/**
	 * Resultado da resolução de código IBGE.
	 * Contém o código encontrado (ou null) e um aviso opcional.
	 */
	public static class IbgeResultado {

		private final String codigoIbge;
		private final String aviso;

		public IbgeResultado(String codigoIbge, String aviso) {
			this.codigoIbge = codigoIbge;
			this.aviso = aviso;
		}

		/** Código IBGE do município (7 dígitos) ou null se não resolvido. */
		public String getCodigoIbge() {
			return codigoIbge;
		}

		/** Mensagem de aviso ou null se resolvido sem problemas. */
		public String getAviso() {
			return aviso;
		}
	}
}
