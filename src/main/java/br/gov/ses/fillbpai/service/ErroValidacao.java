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
 * @param detalhe    mensagem descritiva do problema (frase completa, usada quando não há {@code valor})
 * @param valor      o que varia de uma ocorrência para outra (ex.: o valor da célula entre aspas, ou o
 *                   nome do paciente), separado da explicação fixa do tipo — permite agrupar
 *                   ocorrências iguais na tela e no log ({@link AgrupamentoValidacao}); {@code null}
 *                   quando não se aplica
 */
public record ErroValidacao(int linha, Severidade severidade, String tipoErro, String detalhe, String valor) {

	public enum Severidade { ERRO, AVISO }

	/** Sem valor separado — o item aparece pelo {@code detalhe} completo. */
	public ErroValidacao(int linha, Severidade severidade, String tipoErro, String detalhe) {
		this(linha, severidade, tipoErro, detalhe, null);
	}

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

	/** Data de agendamento (data do atendimento) não informada — ERRO bloqueante */
	public static final String DATA_AUSENTE = "DATA_AUSENTE";

	/** Data de agendamento em formato não reconhecido por {@code DateUtils} — ERRO bloqueante */
	public static final String DATA_INVALIDA = "DATA_INVALIDA";

	/** Tipo de serviço não informado (exceto nutricionista/psicólogo, que usam procedimento fixo) — ERRO bloqueante */
	public static final String TIPO_SERVICO_AUSENTE = "TIPO_SERVICO_AUSENTE";

	/** Tipo de serviço vazio para nutricionista/psicólogo — aceito (procedimento fixo 0301010315), só indicado. AVISO, não bloqueia. */
	public static final String TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO = "TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO";

	/** Tipo de serviço diferente de Teleconsulta/Teleinterconsulta — ERRO bloqueante */
	public static final String TIPO_SERVICO_INVALIDO = "TIPO_SERVICO_INVALIDO";

	/** CPF do paciente com os 11 dígitos iguais (00000000000, 11111111111...) — CPF de preenchimento, juntaria pacientes diferentes — ERRO bloqueante */
	public static final String CPF_FALSO = "CPF_FALSO";

	/** CPF preenchido com a coluna "Paciente sem CPF" = Sim — informações contraditórias — ERRO bloqueante */
	public static final String CPF_CONFLITO_SEM_CPF = "CPF_CONFLITO_SEM_CPF";

	/** Célula do município vazia na planilha — sem o nome não há a busca do IBGE pela tabela de MS — ERRO bloqueante */
	public static final String MUNICIPIO_AUSENTE = "MUNICIPIO_AUSENTE";

	/** Município fora da tabela de MS e código IBGE não encontrado pelo CEP (banco + APIs) — conferir grafia e CEP — ERRO bloqueante */
	public static final String MUNICIPIO_NAO_ENCONTRADO = "MUNICIPIO_NAO_ENCONTRADO";

	/** CPF do paciente com dígitos verificadores errados (dígito digitado errado) — ERRO bloqueante */
	public static final String CPF_DV_INVALIDO = "CPF_DV_INVALIDO";

	/** CPF do médico com dígitos repetidos ou verificadores errados — criaria um "segundo médico" — ERRO bloqueante */
	public static final String CPF_MEDICO_DV_INVALIDO = "CPF_MEDICO_DV_INVALIDO";

	/** CBO do médico não informado — vai para o BPA-I (prd-cbo) — ERRO bloqueante */
	public static final String CBO_AUSENTE = "CBO_AUSENTE";

	/** CBO do médico sem 6 dígitos depois de tirar a máscara (ex.: "22512", texto) — ERRO bloqueante */
	public static final String CBO_INVALIDO = "CBO_INVALIDO";

	/** Especialidade não informada — sem ela o atendimento some da árvore da tela — ERRO bloqueante */
	public static final String ESPECIALIDADE_AUSENTE = "ESPECIALIDADE_AUSENTE";

	/** CPF do médico ausente ou vazio — identifica o médico na importação — ERRO bloqueante */
	public static final String CPF_MEDICO_AUSENTE = "CPF_MEDICO_AUSENTE";

	/** CPF do médico presente mas com tamanho incorreto (diferente de 11 dígitos após normalização) — ERRO bloqueante */
	public static final String CPF_MEDICO_INVALIDO = "CPF_MEDICO_INVALIDO";

	/** CNS do paciente com mais de 15 dígitos (formato incomum) — AVISO, não bloqueia */
	public static final String CNS_INCOMUM = "CNS_INCOMUM";

	/** Raça do paciente informada como Indígena — verificação de etnia necessária — AVISO, não bloqueia */
	public static final String RACA_INDIGENA = "RACA_INDIGENA";

	/** Raça do paciente ausente ou vazia — campo obrigatório no layout do BPA-I (seq 21) — ERRO bloqueante */
	public static final String RACA_AUSENTE = "RACA_AUSENTE";

	/** Raça do paciente presente mas não reconhecida (grafia incorreta) — ERRO bloqueante */
	public static final String RACA_INVALIDA = "RACA_INVALIDA";

	/** Estrutura da planilha inválida: coluna ausente, incorreta ou fora de ordem — ERRO bloqueante */
	public static final String ESTRUTURA_INVALIDA = "ESTRUTURA_INVALIDA";

	/** Coluna "Especialidade/Médico" combinada — formato legado, separado automaticamente na importação. AVISO, não bloqueia. */
	public static final String FORMATO_LEGADO_ESPECIALIDADE_MEDICO = "FORMATO_LEGADO_ESPECIALIDADE_MEDICO";

	/** Etnia do paciente preenchida mas não encontrada na tabela oficial — AVISO, não bloqueia. */
	public static final String ETNIA_NAO_ENCONTRADA = "ETNIA_NAO_ENCONTRADA";

	/** Coluna opcional (ex.: COD_LOGRADOURO) não encontrada no cabeçalho — AVISO, não bloqueia. */
	public static final String COLUNA_OPCIONAL_AUSENTE = "COLUNA_OPCIONAL_AUSENTE";

	/** Situação de rua preenchida mas com valor não reconhecido (não é S/N, Sim/Não ou 1/0) — AVISO, não bloqueia. */
	public static final String SITUACAO_RUA_INVALIDA = "SITUACAO_RUA_INVALIDA";

	/** "Paciente sem CPF" preenchido mas com valor não reconhecido (não é S/N, Sim/Não ou 1/0) — AVISO, não bloqueia. */
	public static final String PACIENTE_SEM_CPF_INVALIDO = "PACIENTE_SEM_CPF_INVALIDO";

	/** Estabelecimento preenchido mas sem separador "código - nome" reconhecido — código não identificado. AVISO, não bloqueia. */
	public static final String ESTABELECIMENTO_SEM_CODIGO = "ESTABELECIMENTO_SEM_CODIGO";

	/** Estabelecimento não informado (célula vazia, só o separador "-" ou código zero) — atendimento fica sem estabelecimento. AVISO, não bloqueia. */
	public static final String ESTABELECIMENTO_AUSENTE = "ESTABELECIMENTO_AUSENTE";

	/** Estabelecimento informado só pelo código ("1234567", "12345 -") — vinculado se o código já estiver cadastrado (sem apagar o nome); senão o atendimento fica sem estabelecimento. AVISO, não bloqueia. */
	public static final String ESTABELECIMENTO_SEM_NOME = "ESTABELECIMENTO_SEM_NOME";

	/** Município fora da tabela de MS, mas código IBGE encontrado pelo CEP — mostra o município que o CEP indica, para conferir. AVISO, não bloqueia. */
	public static final String MUNICIPIO_PELO_CEP = "MUNICIPIO_PELO_CEP";

	/** Hora de atendimento preenchida mas não reconhecida como horário (ex.: "-", "--:--") — atendimento importado sem hora. AVISO, não bloqueia. */
	public static final String HORA_INVALIDA = "HORA_INVALIDA";

	/** Grafia de médico não encontrada em Configurações → CNS de Médicos (um aviso por grafia, com as linhas) — CNS herdado do mesmo CPF quando conhecido; senão a geração do BPA-I fica bloqueada até informar o CNS. AVISO, não bloqueia. */
	public static final String CNS_PROFISSIONAL_NAO_CADASTRADO = "CNS_PROFISSIONAL_NAO_CADASTRADO";

	/** Retorna true se este registro é bloqueante para a importação. */
	public boolean isBloqueante() {
		return severidade == Severidade.ERRO;
	}

	/** Retorna true se este registro é um problema de estrutura do cabeçalho (coluna ausente ou duplicada). */
	public boolean isEstrutural() {
		return ESTRUTURA_INVALIDA.equals(tipoErro);
	}
}
