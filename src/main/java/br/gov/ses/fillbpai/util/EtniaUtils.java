package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve o código oficial (4 caracteres) de uma etnia indígena a partir do
 * nome, usando a tabela em {@code dados/etnias_indigenas.csv} (carregada do
 * classpath, somente leitura — sem cadastro via UI e sem chamada de rede).
 * <p>
 * A tabela tem dois formatos de código, ambos com 4 caracteres: numérico
 * ({@code 0001}–{@code 0334}, com lacunas) e alfanumérico com prefixo "X"
 * ({@code X265}–{@code X411}, também com lacunas) — por isso o código é
 * tratado como texto (ALFA), nunca zero-padded.
 * <p>
 * A faixa {@code 0265}–{@code 0334} é uma tabela suplementar/legada que
 * repete alguns nomes já presentes em {@code 0001}–{@code 0264} com código
 * diferente (ex.: "ARARA DE RONDONIA" aparece como {@code 0014} e como
 * {@code 0270}). O carregamento usa {@code putIfAbsent} — a primeira
 * ocorrência no arquivo vence, e o arquivo lista {@code 0001}–{@code 0264}
 * antes de {@code 0265}–{@code 0334}, então a faixa principal tem
 * precedência sobre a suplementar.
 */
public class EtniaUtils {

	private static final Logger log = LoggerFactory.getLogger(EtniaUtils.class);

	/** Mapa: nome normalizado → código (4 caracteres). Lazy loading. */
	private static Map<String, String> mapaEtnias = null;

	/**
	 * Resolve o código da etnia pelo nome.
	 *
	 * @param nomeEtnia nome da etnia (como veio da planilha, com ou sem acentos, qualquer casing)
	 * @return código de 4 caracteres, ou {@code null} se não encontrado ou nome vazio
	 */
	public static String resolver(String nomeEtnia) {

		if (nomeEtnia == null || nomeEtnia.isBlank()) {
			return null;
		}

		carregarCsvSeNecessario();

		return mapaEtnias.get(normalizar(nomeEtnia));
	}

	/**
	 * Limpa o cache em memória, forçando recarga na próxima busca.
	 * Útil para testes.
	 */
	public static synchronized void limparCache() {
		mapaEtnias = null;
	}

	// ======================================================
	// MÉTODOS INTERNOS
	// ======================================================

	private static synchronized void carregarCsvSeNecessario() {

		if (mapaEtnias != null) {
			return;
		}

		mapaEtnias = new HashMap<>();

		try (InputStream is = EtniaUtils.class.getResourceAsStream("/dados/etnias_indigenas.csv")) {

			if (is == null) {
				log.error("Arquivo etnias_indigenas.csv não encontrado no classpath");
				return;
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

			String linha;

			while ((linha = reader.readLine()) != null) {

				// Ignora linhas vazias e cabeçalho
				if (linha.isBlank() || linha.startsWith("codigo")) {
					continue;
				}

				String[] partes = linha.split(";", 2);

				if (partes.length == 2) {

					String codigo = partes[0].trim();
					String nome = partes[1].trim();

					if (!codigo.isEmpty() && !nome.isEmpty()) {
						// putIfAbsent: primeira ocorrência no arquivo vence (ver javadoc da classe)
						mapaEtnias.putIfAbsent(normalizar(nome), codigo);
					}
				}
			}

			log.info("CSV de etnias indígenas carregado: {} entradas", mapaEtnias.size());

		} catch (Exception e) {
			log.error("Erro ao carregar CSV de etnias indígenas: {}", e.getMessage());
		}
	}

	/**
	 * Normaliza texto para busca: remove acentos, converte para maiúsculo, trim.
	 */
	static String normalizar(String texto) {

		if (texto == null) {
			return "";
		}

		String semAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");

		return semAcentos.toUpperCase().trim();
	}
}
