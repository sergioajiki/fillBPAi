package br.gov.ses.fillbpai.util;

/**
 * Sexo do paciente para o BPA-I (seq 11, {@code prd-sexo}, 1 posição: F, M ou
 * I — "I" desde a versão 04.11 do BPA).
 * <p>
 * Antes, valor fora de F/M/I/Feminino/Masculino/Indeterminado era gravado como
 * veio: "Fem"/"Mulher" passavam do tamanho da coluna e cancelavam a importação,
 * "H"/"1"/"X" iam para o BPA-I, e sexo vazio saía como F (16/10/2026).
 */
public class SexoUtils {

	private SexoUtils() {
	}

	/**
	 * Código F/M/I a partir do texto da planilha (sem acento, caixa ou ponto
	 * final): F, Feminino, Fem, Mulher → F; M, Masculino, Masc, Homem → M;
	 * I, Indeterminado → I. Vazio ou outro valor ("H", "1", "X") → {@code null}.
	 */
	public static String normalizar(String valor) {

		if (valor == null || valor.isBlank()) {
			return null;
		}

		return switch (TextoUtils.normalizar(valor).trim().replaceAll("\\.+$", "")) {
			case "F", "FEMININO", "FEM", "MULHER" -> "F";
			case "M", "MASCULINO", "MASC", "HOMEM" -> "M";
			case "I", "INDETERMINADO" -> "I";
			default -> null;
		};
	}
}
