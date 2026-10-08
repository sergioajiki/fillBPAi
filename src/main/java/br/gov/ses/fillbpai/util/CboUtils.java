package br.gov.ses.fillbpai.util;

/**
 * CBO (Classificação Brasileira de Ocupações) do profissional — campo
 * {@code prd-cbo} do BPA-I (seq 5, posições 031–036, 6 caracteres).
 * <p>
 * Na planilha o CBO costuma vir com máscara ({@code 2251-25},
 * {@code 225.125}). Gravado como veio, ele tinha 7 caracteres e deslocava
 * todos os campos seguintes do registro posicional. Por isso o valor é
 * reduzido aos dígitos na importação e na geração, e só 6 dígitos são
 * aceitos.
 */
public final class CboUtils {

	private CboUtils() {
	}

	/** Só os dígitos do CBO ({@code "2251-25"} → {@code "225125"}); nulo vira vazio. */
	public static String normalizar(String cbo) {
		return cbo == null ? "" : cbo.replaceAll("\\D", "");
	}

	/** CBO válido: exatamente 6 dígitos depois de {@link #normalizar}. */
	public static boolean isValido(String cbo) {
		return normalizar(cbo).length() == 6;
	}
}
