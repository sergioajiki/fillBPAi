package br.gov.ses.fillbpai.service;

/**
 * Representa um problema de validação encontrado em uma linha da planilha Excel.
 * <p>
 * Pode ser bloqueante (ERRO) ou informativo (AVISO):
 * <ul>
 *   <li>ERRO — impede a importação da planilha; deve ser corrigido antes de importar</li>
 *   <li>AVISO — registrado no log de análise mas não impede a importação</li>
 * </ul>
 *
 * @param linha      número da linha na planilha (1-based, considerando cabeçalho)
 * @param severidade severidade do problema (ERRO ou AVISO)
 * @param tipoErro   tipo do problema (CNS_INVALIDO, CEP_AUSENTE, CPF_AUSENTE, CNS_LEGADO)
 * @param detalhe    mensagem descritiva do problema
 */
public record ErroValidacao(int linha, Severidade severidade, String tipoErro, String detalhe) {

	public enum Severidade { ERRO, AVISO }

	/** CNS do paciente com menos de 15 dígitos — ERRO bloqueante */
	public static final String CNS_INVALIDO = "CNS_INVALIDO";

	/** CEP do endereço ausente ou vazio — ERRO bloqueante */
	public static final String CEP_AUSENTE = "CEP_AUSENTE";

	/** CPF do paciente ausente ou vazio — ERRO bloqueante */
	public static final String CPF_AUSENTE = "CPF_AUSENTE";

	/** CNS do paciente com mais de 15 dígitos (formato legado) — AVISO, não bloqueia */
	public static final String CNS_LEGADO = "CNS_LEGADO";

	/** Retorna true se este registro é bloqueante para a importação. */
	public boolean isBloqueante() {
		return severidade == Severidade.ERRO;
	}
}
