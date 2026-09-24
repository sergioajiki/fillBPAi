package br.gov.ses.fillbpai.util;

/**
 * Normaliza a informação de "situação de rua" do paciente para o código
 * S/N usado no BPA-I (seq 39, {@code prd_situacao_rua}).
 * <p>
 * Usado tanto por {@code ValidacaoPlanilhaService} (análise pré-importação)
 * quanto por {@code AtendimentoProcessor} (importação), para que os dois
 * caminhos reconheçam exatamente os mesmos valores.
 */
public class SituacaoRuaUtils {

	private SituacaoRuaUtils() {
	}

	/**
	 * Normaliza o valor lido da planilha para {@code "S"}, {@code "N"} ou
	 * {@code null}. Aceita S/N, Sim/Não, 1/0 (case e acento insensível).
	 * Valor em branco ou não reconhecido retorna {@code null} — o chamador
	 * decide se avisa sobre um valor não reconhecido via {@link #isInvalido}.
	 */
	public static String normalizar(String valor) {

		if (valor == null || valor.trim().isEmpty()) {
			return null;
		}

		String norm = TextoUtils.normalizar(valor);

		return switch (norm) {
			case "S", "SIM", "1" -> "S";
			case "N", "NAO", "0" -> "N";
			default -> null;
		};
	}

	/** true quando o valor não está em branco mas não foi reconhecido como S/N. */
	public static boolean isInvalido(String valor) {
		return valor != null && !valor.trim().isEmpty() && normalizar(valor) == null;
	}
}
