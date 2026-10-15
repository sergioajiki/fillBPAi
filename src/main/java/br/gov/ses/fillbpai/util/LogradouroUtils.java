package br.gov.ses.fillbpai.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tipo de logradouro do BPA-I (seq 30, {@code prd_lograd_pcnte}) pela tabela
 * oficial de tipos de logradouro do DNE ({@code dados/tipos_logradouro.csv},
 * 393 tipos, código de 3 dígitos — fornecida pelo usuário em 15/10/2026).
 * <p>
 * Dois usos:
 * <ul>
 *   <li>{@link #resolver(String)} — o tipo no <b>começo do endereço</b>
 *       ("Rua das Flores" → 081 + "das Flores"); o prefixo sai do endereço,
 *       porque o tipo vai no campo próprio.</li>
 *   <li>{@link #codigoDaColuna(String)} — a coluna "Cód. Logradouro" da
 *       planilha, que pode trazer o código ({@code 81}, {@code 081}) ou o nome
 *       do tipo ({@code Rua}, {@code Av}).</li>
 * </ul>
 * Comparação sem acento e sem diferença de caixa. Vale o tipo mais longo que
 * casar ("Rua Projetada 5" → 698 RUA PROJETADA), desde que sobre um nome
 * depois dele ("Avenida Central" → 008 AVENIDA + "Central", e não 829 AVENIDA
 * CENTRAL com o nome vazio). Abreviações comuns: R, AV, TV/TRAV/TRV, AL,
 * PC/PCA, EST/ESTR, ROD.
 * <p>
 * Tipo não reconhecido: código {@code null} — o gerador usa o padrão 081
 * (Rua), decisão de negócio de 11/09/2026.
 */
public class LogradouroUtils {

	private static final Logger log = LoggerFactory.getLogger(LogradouroUtils.class);

	/** Abreviações usuais → código (só valem seguidas de espaço ou pontuação). */
	private static final Map<String, String> ABREVIACOES = Map.ofEntries(
			Map.entry("R", "081"), Map.entry("AV", "008"), Map.entry("AVE", "008"),
			Map.entry("TV", "100"), Map.entry("TRAV", "100"), Map.entry("TRV", "100"),
			Map.entry("AL", "004"), Map.entry("PC", "065"), Map.entry("PCA", "065"),
			Map.entry("EST", "031"), Map.entry("ESTR", "031"), Map.entry("ROD", "090"));

	/** Nome normalizado → código, do nome mais longo para o mais curto (carregado sob demanda). */
	private static Map<String, String> tipos;

	/** Códigos da tabela. */
	private static java.util.Set<String> codigos;

	/**
	 * Tipo no começo do endereço e o endereço sem ele.
	 *
	 * @return código {@code null} e endereço só aparado quando nenhum tipo é reconhecido
	 */
	public static LogradouroResultado resolver(String enderecoRaw) {

		if (enderecoRaw == null || enderecoRaw.isBlank()) {
			return new LogradouroResultado(null, enderecoRaw == null ? null : enderecoRaw.trim());
		}

		carregarSeNecessario();

		String original = enderecoRaw.trim();
		String norm = normalizar(original);

		// Nome completo da tabela, do mais longo para o mais curto; precisa sobrar o nome
		for (Map.Entry<String, String> tipo : tipos.entrySet()) {
			String resto = restoDepoisDoPrefixo(original, norm, tipo.getKey());
			if (resto != null && !resto.isEmpty()) {
				return new LogradouroResultado(tipo.getValue(), resto);
			}
		}

		// Abreviação ("R. Dom Aquino", "AV: Afonso Pena")
		for (Map.Entry<String, String> abrev : ABREVIACOES.entrySet()) {
			String resto = restoDepoisDoPrefixo(original, norm, abrev.getKey());
			if (resto != null && !resto.isEmpty()) {
				return new LogradouroResultado(abrev.getValue(), resto);
			}
		}

		return new LogradouroResultado(null, original);
	}

	/**
	 * Código da coluna "Cód. Logradouro": código numérico da tabela (1 a 3
	 * dígitos, completado com zeros) ou nome/abreviação do tipo.
	 *
	 * @return código de 3 dígitos, ou {@code null} se vazio ou não reconhecido
	 */
	public static String codigoDaColuna(String celula) {

		if (celula == null || celula.isBlank()) {
			return null;
		}

		carregarSeNecessario();

		String valor = celula.trim();

		if (valor.matches("\\d{1,3}")) {
			String codigo = "0".repeat(3 - valor.length()) + valor;
			return codigos.contains(codigo) ? codigo : null;
		}

		String norm = normalizar(valor).replaceAll("[.:]+$", "").trim();
		String codigo = tipos.get(norm);

		return codigo != null ? codigo : ABREVIACOES.get(norm);
	}

	/** Coluna preenchida mas não reconhecida — para o aviso da análise e da importação. */
	public static boolean isColunaNaoReconhecida(String celula) {
		return celula != null && !celula.isBlank() && codigoDaColuna(celula) == null;
	}

	/**
	 * Se {@code norm} começa com {@code prefixo} seguido de fim, espaço ou
	 * pontuação, o restante do texto original (sem a pontuação/espaços do
	 * começo); senão {@code null}.
	 */
	private static String restoDepoisDoPrefixo(String original, String norm, String prefixo) {

		if (!norm.startsWith(prefixo)) {
			return null;
		}

		if (norm.length() > prefixo.length() && Character.isLetterOrDigit(norm.charAt(prefixo.length()))) {
			return null; // "RUAZINHA" não é "RUA"
		}

		return original.substring(prefixo.length()).replaceFirst("^[\\s.:,;\\-]+", "").trim();
	}

	/**
	 * Maiúsculas sem acento, caractere a caractere (o tamanho não muda, para o
	 * índice do prefixo valer no texto original).
	 */
	static String normalizar(String texto) {

		StringBuilder sb = new StringBuilder(texto.length());

		for (char c : texto.toCharArray()) {
			String semAcento = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
			sb.append(semAcento.isEmpty() ? c : Character.toUpperCase(semAcento.charAt(0)));
		}

		return sb.toString();
	}

	private static synchronized void carregarSeNecessario() {

		if (tipos != null) {
			return;
		}

		List<String[]> linhas = new ArrayList<>();
		java.util.Set<String> codigosLidos = new java.util.HashSet<>();

		try (InputStream is = LogradouroUtils.class.getResourceAsStream("/dados/tipos_logradouro.csv")) {

			if (is == null) {
				log.error("Arquivo tipos_logradouro.csv não encontrado no classpath");
			} else {
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
				String linha;
				while ((linha = reader.readLine()) != null) {
					if (linha.isBlank() || linha.startsWith("codigo")) {
						continue;
					}
					String[] partes = linha.split(";", 2);
					if (partes.length == 2 && !partes[0].isBlank() && !partes[1].isBlank()) {
						linhas.add(new String[] { normalizar(partes[1].trim()), partes[0].trim() });
						codigosLidos.add(partes[0].trim());
					}
				}
			}

		} catch (Exception e) {
			log.error("Erro ao carregar tipos_logradouro.csv: {}", e.getMessage());
		}

		linhas.sort(Comparator.comparingInt((String[] l) -> l[0].length()).reversed());

		Map<String, String> mapa = new LinkedHashMap<>();
		for (String[] l : linhas) {
			mapa.putIfAbsent(l[0], l[1]);
		}

		codigos = codigosLidos;
		tipos = mapa;
	}

	public static class LogradouroResultado {

		private final String codLogradouro;
		private final String endereco;

		public LogradouroResultado(String codLogradouro, String endereco) {
			this.codLogradouro = codLogradouro;
			this.endereco      = endereco;
		}

		/** Código BPA-I do tipo de logradouro, ou null se não reconhecido. */
		public String getCodLogradouro() {
			return codLogradouro;
		}

		/** Endereço sem o prefixo do tipo de logradouro. */
		public String getEndereco() {
			return endereco;
		}
	}
}
