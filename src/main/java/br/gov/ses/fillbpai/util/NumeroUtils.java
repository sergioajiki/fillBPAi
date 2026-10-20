package br.gov.ses.fillbpai.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Número do endereço do paciente para o BPA-I (seq 33, {@code prd_num_pcnte},
 * 5 posições ALFA; o layout indica "SN" como padrão).
 * <p>
 * Antes o número era só cortado em 5 caracteres: "SEM NUMERO" virava
 * "SEM N", "123456" virava "12345", "100 apto 201" virava "100 a" e vazio
 * saía em branco (decisões de 16/10/2026).
 */
public class NumeroUtils {

	private NumeroUtils() {
	}

	/** Valor do BPA-I para endereço sem número. */
	public static final String SEM_NUMERO = "SN";

	private static final int TAMANHO = 5;

	/** Formas de "sem número": S/N, S N, S.N., SN, SEM NUMERO, SEM Nº, S/Nº, 0, 00..., e só traço (-, --, –, —; 19/10/2026). */
	private static final Pattern SEM_NUMERO_PADRAO =
			Pattern.compile("S\\s*[/.\\-]?\\s*N\\s*[º°O.]?|SEM\\s*(N|NUM|NUMERO|N\\s*[º°O.])?\\.?|0+|[-–—]+");

	/**
	 * Número de endereço no padrão: dígitos com uma letra opcional no fim
	 * ({@code 100}, {@code 12A}, {@code 12-B}, {@code 12 B}). O resto
	 * ({@code X}, {@code N/A}, {@code LT 5}) é aviso (16/10/2026); traço sozinho é "sem número".
	 */
	private static final Pattern NUMERO_VALIDO = Pattern.compile("\\d{1,5}|\\d{1,3}\\s?-?\\s?[A-Z]");

	/** Prefixos antes do número: Nº, N°, NO., NUM, NUMERO, KM. */
	private static final Pattern NUMERO_INICIAL =
			Pattern.compile("^(?:(?:N\\s*[º°O.]|NUM\\.?|NUMERO|KM)\\s*)?(\\d{1,5})(?!\\d)");

	/**
	 * @param valor       o que vai para o BPA-I (até 5 caracteres; "SN" quando não há número)
	 * @param foraDoPadrao a célula não é um número de endereço nem uma forma de "sem número" — merece aviso
	 */
	public record Resultado(String valor, boolean foraDoPadrao) {
	}

	/** Mensagem do aviso de número fora do padrão — análise e log de importação. */
	public static String mensagemForaDoPadrao(String celula, Resultado resultado) {
		return "Número do endereço \"" + celula.trim() + "\" fora do padrão — informe o número (ex.: 100 ou 12A) ou"
				+ " S/N para sem número; será enviado \"" + resultado.valor() + "\"";
	}

	/**
	 * Número do endereço para o BPA-I:
	 * <ul>
	 *   <li>vazio, forma de "sem número" ou só traço → {@code SN}, sem aviso;</li>
	 *   <li>número no padrão ({@code 100}, {@code 12A}) → como veio, sem aviso;</li>
	 *   <li>outro valor com até 5 caracteres ({@code X}, {@code N/A}) →
	 *       como veio, com aviso;</li>
	 *   <li>mais de 5: o número do começo (também depois de Nº/KM) se tiver até 5
	 *       dígitos ("100 apto 201" → 100, "Km 12,5" → 12); senão {@code SN}
	 *       ("123456", "Lote 10 Quadra 5") — os dois com aviso.</li>
	 * </ul>
	 */
	public static Resultado normalizar(String celula) {

		if (celula == null || celula.isBlank()) {
			return new Resultado(SEM_NUMERO, false);
		}

		String valor = celula.trim().replaceAll("\\s+", " ");
		String norm = TextoUtils.normalizar(valor);

		if (SEM_NUMERO_PADRAO.matcher(norm).matches()) {
			return new Resultado(SEM_NUMERO, false);
		}

		if (valor.length() <= TAMANHO) {
			return new Resultado(valor, !NUMERO_VALIDO.matcher(norm).matches());
		}

		Matcher m = NUMERO_INICIAL.matcher(norm);

		return new Resultado(m.find() ? m.group(1) : SEM_NUMERO, true);
	}
}
