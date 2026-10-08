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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
 * 3. FALLBACK: Consulta APIs de CEP pela internet — ViaCEP; se falhar,
 * BrasilAPI e depois OpenCEP (reservas)
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

	// Regex para detectar resposta de erro do ViaCEP (CEP não encontrado). Hoje
	// o ViaCEP responde "erro": "true" (com aspas); antes era true sem aspas —
	// aceita os dois.
	private static final Pattern PATTERN_ERRO =
			Pattern.compile("\"erro\"\\s*:\\s*\"?true\"?");

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
	 * 3. APIs de CEP (ViaCEP → BrasilAPI → OpenCEP) — apenas se não encontrado no cache
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
				return new IbgeResultado(codigoPorCep, avisoMunicipioPeloCep(nomeMunicipio, cep, codigoPorCep));
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
	 * Aviso para quando o município não está na tabela de MS e o IBGE veio do
	 * CEP: mostra o município que o CEP indica, para o usuário conferir se é o
	 * mesmo que quis informar na planilha (o CEP pode estar errado).
	 */
	public static String avisoMunicipioPeloCep(String nomeMunicipio, String cep, String codigoIbge) {
		return "Município \"" + nomeMunicipio + "\" não encontrado na tabela de MS; pelo CEP " + cep
				+ " o município encontrado foi " + descreverMunicipio(codigoIbge)
				+ " — confira se é o mesmo informado na planilha";
	}

	/** "Cuiabá/MT (IBGE 5103403)", ou só o código se ele não estiver na tabela do IBGE. */
	public static String descreverMunicipio(String codigoIbge) {
		String nome = nomeMunicipio(codigoIbge);
		return nome != null
				? nome + " (IBGE " + codigoIbge + ")"
				: "o de código IBGE " + codigoIbge + " (nome não encontrado na tabela do IBGE)";
	}

	/** Mapa: código IBGE (7 dígitos) → "Nome/UF", de todos os municípios do Brasil. Lazy loading. */
	private static Map<String, String> nomesPorCodigo = null;

	/**
	 * Nome oficial e UF do município pelo código IBGE (7 dígitos), ex.:
	 * {@code "5103403"} → {@code "Cuiabá/MT"}. Tabela {@code dados/municipios_brasil.csv}
	 * (5.571 municípios, lista oficial do IBGE — servicodados.ibge.gov.br,
	 * obtida em 08/10/2026). Usada só para exibir o município encontrado pelo
	 * CEP; a busca do IBGE pelo nome continua só na tabela de MS.
	 *
	 * @return "Nome/UF" ou {@code null} se o código não existir
	 */
	public static synchronized String nomeMunicipio(String codigoIbge) {

		if (codigoIbge == null) {
			return null;
		}

		if (nomesPorCodigo == null) {
			nomesPorCodigo = new HashMap<>();
			try (InputStream is = IbgeUtils.class.getResourceAsStream("/dados/municipios_brasil.csv")) {
				if (is == null) {
					log.error("Arquivo municipios_brasil.csv não encontrado no classpath");
				} else {
					BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
					String linha;
					while ((linha = reader.readLine()) != null) {
						String[] partes = linha.split(";");
						if (partes.length >= 3 && !linha.startsWith("codigo")) {
							nomesPorCodigo.put(partes[0].trim(), partes[1].trim() + "/" + partes[2].trim());
						}
					}
				}
			} catch (Exception e) {
				log.error("Erro ao carregar municipios_brasil.csv: {}", e.getMessage());
			}
		}

		return nomesPorCodigo.get(codigoIbge.trim());
	}

	/**
	 * Busca o código IBGE pelo CEP: cache (CEPs já resolvidos, inclusive do
	 * banco) e, se não houver, as APIs de CEP na ordem de {@link #APIS_CEP} —
	 * ViaCEP; se falhar ou não tiver o CEP, BrasilAPI; depois OpenCEP.
	 * Resultados são cacheados em memória.
	 *
	 * @param cep CEP normalizado (8 dígitos numéricos)
	 * @return código IBGE (7 dígitos) ou null se não encontrado/erro
	 */
	static String buscarPorCep(String cep) {

		// Verifica cache primeiro
		if (cacheViaCep.containsKey(cep)) {
			return cacheViaCep.get(cep);
		}

		boolean algumaFalhou = false;

		// ViaCEP primeiro; BrasilAPI e OpenCEP só se a anterior falhar ou não
		// tiver o CEP (serviços gratuitos, sem garantia de disponibilidade)
		for (ApiCep api : APIS_CEP) {

			Consulta consulta = consultar(api, cep);

			if (consulta.codigoIbge() != null) {
				cacheViaCep.put(cep, consulta.codigoIbge());
				return consulta.codigoIbge();
			}

			if (consulta.falhou()) {
				algumaFalhou = true;
			}
		}

		// Cache negativo só se TODAS responderam "não encontrado". Se alguma
		// falhou (sem internet, fora do ar), a próxima importação tenta de novo.
		if (!algumaFalhou) {
			log.debug("CEP {} não encontrado em nenhuma API de CEP", cep);
			cacheViaCep.put(cep, null);
		}

		return null;
	}

	/**
	 * Consulta uma API de CEP. Resultado: código IBGE encontrado; ou "não
	 * encontrado" (a API respondeu que o CEP não existe); ou falha (rede,
	 * fora do ar, resposta inesperada).
	 */
	private static Consulta consultar(ApiCep api, String cep) {

		try {

			RespostaHttp resposta = buscadorHttp.apply(String.format(api.urlModelo(), cep));

			if (resposta.status() == 404) {
				return new Consulta(null, false); // CEP não existe nesta API
			}

			if (resposta.status() != 200) {
				log.warn("{} retornou status {} para CEP {}", api.nome(), resposta.status(), cep);
				return new Consulta(null, true);
			}

			Matcher matcher = api.padraoIbge().matcher(resposta.corpo());

			if (matcher.find()) {
				return new Consulta(matcher.group(1), false);
			}

			if (PATTERN_ERRO.matcher(resposta.corpo()).find()) {
				return new Consulta(null, false); // ViaCEP: 200 com "erro": "true"
			}

			log.warn("Campo IBGE não encontrado na resposta de {} para CEP {}", api.nome(), cep);
			return new Consulta(null, true);

		} catch (Exception e) {
			// Timeout, sem internet — tenta a próxima API
			log.warn("Erro ao consultar {} para CEP {}: {}", api.nome(), cep, e.getMessage());
			return new Consulta(null, true);
		}
	}

	/** Resposta HTTP simplificada (status + corpo) — permite simular as APIs nos testes. */
	public record RespostaHttp(int status, String corpo) {
	}

	/** Uma API de CEP: URL ({@code %s} = CEP) e onde está o código IBGE na resposta. */
	private record ApiCep(String nome, String urlModelo, Pattern padraoIbge) {
	}

	private record Consulta(String codigoIbge, boolean falhou) {
	}

	/**
	 * Ordem de consulta. As três devolvem o IBGE de 7 dígitos (conferido em
	 * 08/10/2026 com o CEP 79002-000 → 5002704). CEP inexistente: ViaCEP
	 * responde 200 com {@code "erro": "true"}; BrasilAPI e OpenCEP, 404.
	 */
	private static final List<ApiCep> APIS_CEP = List.of(
			new ApiCep("ViaCEP", "https://viacep.com.br/ws/%s/json/",
					Pattern.compile("\"ibge\"\\s*:\\s*\"(\\d{7})\"")),
			new ApiCep("BrasilAPI", "https://brasilapi.com.br/api/cep/v1/%s",
					Pattern.compile("\"ibge\"\\s*:\\s*\\{[^}]*\"city\"\\s*:\\s*\"(\\d{7})\"")),
			new ApiCep("OpenCEP", "https://opencep.com/v1/%s",
					Pattern.compile("\"ibge\"\\s*:\\s*\"(\\d{7})\"")));

	/** Requisição HTTP real (GET, tempo-limite de 5 s). Trocado nos testes. */
	private static final Function<String, RespostaHttp> BUSCADOR_HTTP_REAL = url -> {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(5))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return new RespostaHttp(response.statusCode(), response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	};

	private static Function<String, RespostaHttp> buscadorHttp = BUSCADOR_HTTP_REAL;

	/** Só para testes: simula as APIs de CEP sem rede; {@code null} volta ao HTTP real. */
	public static void usarBuscadorHttpParaTeste(Function<String, RespostaHttp> buscador) {
		buscadorHttp = buscador != null ? buscador : BUSCADOR_HTTP_REAL;
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
	public static String buscarPorNome(String nomeMunicipio) {

		carregarCsvSeNecessario();

		// Tolera sigla da UF no fim ("Campo Grande - MS", "/MS", "(MS)") e espaços
		// repetidos — antes essas variações não batiam com a tabela e o IBGE
		// dependia do CEP (e da internet, para CEP novo)
		String nomeNormalizado = SIGLA_UF_NO_FIM.matcher(normalizar(nomeMunicipio)).replaceFirst("")
				.replaceAll("\\s+", " ").trim();

		return mapaMunicipios.get(nomeNormalizado);
	}

	/** " - MS", " – MS", "/MS", " (MS)" no fim do nome, depois de {@link #normalizar} (maiúsculas). */
	private static final Pattern SIGLA_UF_NO_FIM = Pattern.compile("\\s*(?:[-–—/]\\s*[A-Z]{2}|\\(\\s*[A-Z]{2}\\s*\\))\\s*$");

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
