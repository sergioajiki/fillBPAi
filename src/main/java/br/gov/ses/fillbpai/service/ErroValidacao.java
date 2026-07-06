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

	/** CNS do paciente ausente ou com menos de 15 dígitos — AVISO, não bloqueia */
	public static final String CNS_INVALIDO = "CNS_INVALIDO";

	/** CEP do endereço ausente ou vazio — ERRO bloqueante */
	public static final String CEP_AUSENTE = "CEP_AUSENTE";

	/** CEP presente mas com tamanho incorreto (diferente de 8 dígitos após normalização) — ERRO bloqueante */
	public static final String CEP_INVALIDO = "CEP_INVALIDO";

	/** CPF do paciente ausente ou vazio — ERRO bloqueante */
	public static final String CPF_AUSENTE = "CPF_AUSENTE";

	/** CPF presente mas com tamanho incorreto (diferente de 11 dígitos após normalização) — ERRO bloqueante */
	public static final String CPF_INVALIDO = "CPF_INVALIDO";

	/** CNS do paciente com mais de 15 dígitos (formato incomum) — AVISO, não bloqueia */
	public static final String CNS_INCOMUM = "CNS_INCOMUM";

	/** Raça do paciente informada como Indígena — verificação de etnia necessária — AVISO, não bloqueia */
	public static final String RACA_INDIGENA = "RACA_INDIGENA";

	/** Estrutura da planilha inválida: coluna ausente, incorreta ou fora de ordem — ERRO bloqueante */
	public static final String ESTRUTURA_INVALIDA = "ESTRUTURA_INVALIDA";

	/** Retorna true se este registro é bloqueante para a importação. */
	public boolean isBloqueante() {
		return severidade == Severidade.ERRO;
	}
}
