package br.gov.ses.fillbpai.service;

/**
 * Representa um erro de validação encontrado em uma linha da planilha Excel.
 * <p>
 * Erros de validação são bloqueantes: linhas com qualquer erro não são
 * importadas. O relatório gerado lista todos os erros para correção manual
 * antes de uma nova tentativa de importação.
 *
 * @param linha    número da linha na planilha (1-based, considerando cabeçalho)
 * @param tipoErro tipo do erro (CNS_INVALIDO, CEP_AUSENTE, CPF_AUSENTE)
 * @param detalhe  mensagem descritiva do erro
 */
public record ErroValidacao(int linha, String tipoErro, String detalhe) {

	/** CNS do paciente com menos de 15 dígitos */
	public static final String CNS_INVALIDO = "CNS_INVALIDO";

	/** CEP do endereço ausente ou vazio */
	public static final String CEP_AUSENTE = "CEP_AUSENTE";

	/** CPF do paciente ausente ou vazio */
	public static final String CPF_AUSENTE = "CPF_AUSENTE";
}
