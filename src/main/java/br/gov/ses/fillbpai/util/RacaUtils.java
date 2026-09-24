package br.gov.ses.fillbpai.util;

/**
 * Resolve o código de raça/cor do paciente (2 dígitos) usado no BPA-I
 * (seq 21, {@code prd-raca}) a partir do texto livre da planilha.
 * <p>
 * Usado tanto por {@code ValidacaoPlanilhaService}/{@code AtendimentoProcessor}
 * (para detectar raça ausente ou não reconhecida — grafia incorreta) quanto
 * por {@code GeradorBPAiService} (para gerar o código no arquivo) — mesma
 * régua nos dois lugares.
 */
public class RacaUtils {

	private RacaUtils() {
	}

	/**
	 * Resolve o código de 2 dígitos a partir do texto da raça, ou
	 * {@code null} quando em branco ou não reconhecido. Reconhece por
	 * substring — tolera variações como "Branco"/"Branca", "Pardo"/"Parda"
	 * — case e acento insensível. O código "99" (Sem informação) não é
	 * resolvido aqui: está marcado como descontinuado no layout oficial e
	 * nunca deve ser digitado na planilha; ausência de raça é tratada como
	 * erro, não como "99".
	 */
	public static String resolverCodigo(String raca) {

		if (raca == null || raca.trim().isEmpty()) {
			return null;
		}

		String norm = TextoUtils.normalizar(raca);

		if (norm.contains("BRANC")) return "01";
		if (norm.contains("PRET")) return "02";
		if (norm.contains("PARD")) return "03";
		if (norm.contains("AMAREL")) return "04";
		if (norm.contains("IND")) return "05";

		return null;
	}

	/** true quando a raça informada corresponde ao código 05 (Indígena). */
	public static boolean isIndigena(String raca) {
		return "05".equals(resolverCodigo(raca));
	}
}
