package br.gov.ses.fillbpai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bairro (BPA-I seq 34, 30 posições) e complemento (seq 32, 10 posições) do
 * endereço do paciente — campos opcionais, só informativos (16/10/2026).
 * <p>
 * Antes eram só cortados no meio da palavra ("Apartamento 201 Bloco B" →
 * "Apartament"), e valores de preenchimento ("-", "N/A", "0") iam para o
 * BPA-I como se fossem o bairro ou o complemento.
 */
public class EnderecoUtils {

	private EnderecoUtils() {
	}

	public static final int TAMANHO_BAIRRO = 30;
	public static final int TAMANHO_COMPLEMENTO = 10;

	/** Valores de preenchimento: "-", ".", "X", "0", "N/A", "NA", "NÃO INFORMADO", "NÃO SE APLICA", "SEM", "NENHUM". */
	private static final Pattern PREENCHIMENTO =
			Pattern.compile("[-.\\s]+|X|0+|N\\s*/?\\s*A|NAO INFORMADO|NAO SE APLICA|SEM|NENHUM|NULL");

	/** Abreviações usuais dos Correios para o complemento (palavra sem acento e sem ponto final → abreviação). */
	private static final Map<String, String> ABREVIACOES = Map.ofEntries(
			Map.entry("APARTAMENTO", "AP"), Map.entry("APTO", "AP"), Map.entry("APT", "AP"), Map.entry("AP", "AP"),
			Map.entry("BLOCO", "BL"), Map.entry("BLC", "BL"), Map.entry("BL", "BL"),
			Map.entry("CASA", "CS"), Map.entry("FUNDOS", "FDS"), Map.entry("FUNDO", "FDS"),
			Map.entry("QUADRA", "QD"), Map.entry("QD", "QD"), Map.entry("LOTE", "LT"), Map.entry("LT", "LT"),
			Map.entry("SALA", "SL"), Map.entry("CONJUNTO", "CJ"), Map.entry("EDIFICIO", "ED"), Map.entry("ED", "ED"),
			Map.entry("ANDAR", "AND"));

	/** Complemento normalizado e se precisou ser cortado (merece aviso — decisão de 16/10/2026). */
	public record Complemento(String valor, String semCorte, boolean cortado) {
	}

	/** Célula com valor de preenchimento ou vazia. */
	public static boolean isVazioOuPreenchimento(String valor) {
		return valor == null || valor.isBlank() || PREENCHIMENTO.matcher(TextoUtils.normalizar(valor).trim()).matches();
	}

	/**
	 * Bairro para o BPA-I: vazio/preenchimento → {@code null}; sem a palavra
	 * "Bairro" no começo; cortado em 30 na última palavra inteira (sem aviso).
	 */
	public static String bairro(String celula) {

		if (isVazioOuPreenchimento(celula)) {
			return null;
		}

		String valor = celula.trim().replaceFirst("(?i)^bairro\\b[\\s.:\\-]*", "");

		return valor.isBlank() ? null : StringUtils.limitarNaPalavra(valor, TAMANHO_BAIRRO);
	}

	/**
	 * Complemento para o BPA-I: vazio/preenchimento → {@code null}; palavras
	 * comuns abreviadas (Apartamento → AP, Bloco → BL, Casa → CS…); cortado em
	 * 10 na última palavra inteira — {@code cortado} indica que algo se perdeu.
	 */
	public static Complemento complemento(String celula) {

		if (isVazioOuPreenchimento(celula)) {
			return new Complemento(null, null, false);
		}

		String original = celula.trim().replaceAll("\\s+", " ");

		// Cabe nos 10: fica como foi digitado (só abrevia o que não cabe)
		if (original.length() <= TAMANHO_COMPLEMENTO) {
			return new Complemento(original, original, false);
		}

		List<String> palavras = new ArrayList<>();
		for (String palavra : celula.trim().split("\\s+")) {
			String chave = TextoUtils.normalizar(palavra).replaceAll("\\.+$", "");
			palavras.add(ABREVIACOES.getOrDefault(chave, palavra));
		}

		String abreviado = String.join(" ", palavras);
		String valor = StringUtils.limitarNaPalavra(abreviado, TAMANHO_COMPLEMENTO);

		return new Complemento(valor, abreviado, abreviado.length() > TAMANHO_COMPLEMENTO);
	}

	/** Mensagem do aviso de complemento cortado — análise e log de importação. */
	public static String mensagemComplementoCortado(String celula, Complemento complemento) {
		return "Complemento \"" + celula.trim() + "\" maior que 10 caracteres — o BPA-I aceita até 10; será enviado \""
				+ complemento.valor() + "\" (abreviado: \"" + complemento.semCorte() + "\")";
	}

	/**
	 * Bairro com o mesmo nome do município do paciente (ignorando acento, caixa
	 * e a sigla da UF) — provavelmente a cidade digitada na coluna do bairro.
	 */
	public static boolean isBairroIgualMunicipio(String bairro, String municipio) {

		if (isVazioOuPreenchimento(bairro) || municipio == null || municipio.isBlank()) {
			return false;
		}

		return semUf(bairro).equals(semUf(municipio));
	}

	private static String semUf(String nome) {
		return TextoUtils.normalizar(nome).trim()
				.replaceAll("\\s*(?:[-/]\\s*[A-Z]{2}|\\(\\s*[A-Z]{2}\\s*\\))$", "")
				.replaceAll("\\s+", " ");
	}
}
