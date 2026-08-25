package br.gov.ses.fillbpai.util;

import java.text.Normalizer;

/**
 * Normalização de texto para comparação insensível a acentuação e caixa —
 * usado tanto para nomes de coluna de cabeçalho quanto para outros campos
 * de texto comparados por igualdade aproximada.
 */
public class TextoUtils {

	private TextoUtils() {
	}

	/**
	 * Remove acentos, converte para maiúsculas e retira espaços nas pontas.
	 * Retorna string vazia para entrada nula ou em branco.
	 */
	public static String normalizar(String texto) {

		if (texto == null || texto.trim().isEmpty()) {
			return "";
		}

		return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
				.toUpperCase();
	}
}
