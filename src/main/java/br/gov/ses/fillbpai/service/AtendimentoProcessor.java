package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.CboUtils;
import br.gov.ses.fillbpai.util.CidUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CepUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import br.gov.ses.fillbpai.util.EspecialidadeUtils;
import br.gov.ses.fillbpai.util.EtniaUtils;
import br.gov.ses.fillbpai.util.LogradouroUtils;
import br.gov.ses.fillbpai.util.RacaUtils;
import br.gov.ses.fillbpai.util.SimNaoUtils;
import br.gov.ses.fillbpai.util.TelefoneUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe responsável por processar e validar os dados importados do Excel.
 *
 * Pipeline de processamento (executado na ordem):
 * 1. Separações — extrai código/nome de campos combinados
 * 2. Definição SIGTAP — mapeia tipo de serviço para código SIGTAP
 * 3. Normalizações — limpa CPF, CEP, limita tamanho de campos
 * 4. Validações — verifica campos obrigatórios e formato do CNS
 * 5. Conversões — transforma strings de data/hora em LocalDate/LocalTime
 *
 * Esta classe NÃO acessa banco de dados.
 * Apenas prepara o DTO para extração de entidades na camada de serviço.
 *
 * @return Lista de avisos (warnings) gerados durante o processamento.
 *         Avisos NÃO impedem a importação — apenas informam situações atípicas.
 *         Erros bloqueantes são lançados como IllegalArgumentException.
 */
public class AtendimentoProcessor {

	/**
	 * Processa e valida um DTO de importação.
	 *
	 * @param dto dados brutos extraídos de uma linha da planilha Excel
	 * @return lista de avisos (warnings) — vazia se nenhum aviso gerado
	 * @throws IllegalArgumentException se algum campo obrigatório estiver ausente ou inválido
	 */
	public List<String> processar(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		// ===============================
		// 1. Separações
		// Campos combinados "CÓDIGO - NOME" são divididos
		// em campos individuais para normalização; prefixo
		// "Médico"/"Médica" na especialidade é removido
		// ===============================

		avisos.addAll(separarEstabelecimento(dto));
		separarEspecialidadeEMedico(dto);
		dto.setEspecialidadeMedico(EspecialidadeUtils.padronizar(dto.getEspecialidadeMedico()));

		// ===============================
		// 2. Definir SIGTAP
		// Mapeia o tipo de serviço (TELECONSULTA, TELEINTERCONSULTA)
		// para o código SIGTAP correspondente
		// ===============================

		dto.setSigtap(definirSigtap(dto.getTipoServico(), dto.getEspecialidadeMedico()));

		// Nutricionista/psicólogo com tipo vazio: aceito (procedimento fixo),
		// mas indicado no log — mesmo aviso do "Analisar Planilha"
		// (TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO)
		if (isNullOrEmpty(dto.getTipoServico())) {
			avisos.add("Tipo de serviço não informado — para " + dto.getEspecialidadeMedico()
					+ " foi usado o procedimento fixo 0301010315");
		}

		// ===============================
		// 3. Normalizações
		// Remove formatação de CPF/CEP e limita tamanho
		// de campos de endereço conforme layout BPA-I
		// ===============================

		normalizarCpf(dto);
		normalizarCep(dto);
		normalizarSexo(dto);
		limitarCamposBanco(dto);

		// ===============================
		// 4. Validações
		// Verifica campos obrigatórios (paciente, CPF, data)
		// e valida formato do CNS (aceita formato legado com aviso)
		// ===============================

		validarCamposObrigatorios(dto);
		validarCep(dto);
		validarCpf(dto);
		validarCpfMedico(dto);
		validarCbo(dto);
		validarEspecialidade(dto);
		validarRaca(dto);
		avisos.addAll(validarCns(dto));
		avisos.addAll(verificarEtniaDoIndigena(dto));
		avisos.addAll(normalizarSituacaoRua(dto));
		avisos.addAll(normalizarPacienteSemCpf(dto));
		avisos.addAll(normalizarCid(dto));
		avisos.addAll(normalizarTelefone(dto));
		avisos.addAll(normalizarLogradouro(dto));

		// ===============================
		// 5. Conversões
		// Transforma strings de data/hora vindas do Excel
		// em tipos Java (LocalDate/LocalTime)
		// ===============================

		converterDatas(dto, avisos);

		return avisos;
	}

	/**
	 * Valida e normaliza o CNS do paciente.
	 *
	 * CNS com 15 dígitos → padrão atual, aceito sem aviso.
	 * CNS com mais de 15 dígitos → formato legado (antigo), aceito com aviso.
	 * CNS ausente ou com menos de 15 dígitos → erro bloqueante.
	 *
	 * @return lista de avisos gerados pela validação do CNS
	 */
	private List<String> validarCns(LinhaImportacaoDTO dto) {

		CnsUtils.CnsResultado resultado =
				CnsUtils.processar(dto.getCnsPaciente());

		// Atualiza o DTO com o CNS normalizado (somente dígitos)
		dto.setCnsPaciente(resultado.getCns());

		return resultado.getAvisos();
	}

	/**
	 * Mapeia o tipo de serviço para o código SIGTAP correspondente.
	 *
	 * Mapeamento:
	 * - TELECONSULTA       → 03.01.01.030-7
	 * - TELEINTERCONSULTA   → 08.04.01.006-4
	 *
	 * Tipo vazio é erro, exceto para as especialidades de procedimento fixo
	 * (NUTRICIONISTA/PSICÓLOGO — {@link EspecialidadeUtils#usaProcedimentoFixo}),
	 * que mantêm o comportamento anterior (SIGTAP nulo) por decisão de
	 * 08/10/2026. Antes, tipo vazio passava para todas: o BPA-I saía com
	 * procedimento 0000000000 e a reimportação duplicava o atendimento.
	 *
	 * @throws IllegalArgumentException se o tipo de serviço estiver vazio ou não for reconhecido
	 */
	private String definirSigtap(String tipoServico, String especialidade) {

		if (isNullOrEmpty(tipoServico)) {

			if (EspecialidadeUtils.usaProcedimentoFixo(especialidade)) {
				return null;
			}

			throw new IllegalArgumentException(
					"Tipo de serviço não informado (aceitos: " + TIPOS_SERVICO_ACEITOS + ").");
		}

		String sigtap = codigoSigtap(tipoServico);

		if (sigtap == null) {
			throw new IllegalArgumentException(
					"Tipo de serviço inválido para SIGTAP: " + tipoServico
							+ " (aceitos: " + TIPOS_SERVICO_ACEITOS + ")");
		}

		return sigtap;
	}

	/** Tipos de serviço aceitos, para as mensagens (decisão de 08/10/2026: só estes, por enquanto). */
	static final String TIPOS_SERVICO_ACEITOS = "Teleconsulta, Teleinterconsulta";

	/**
	 * Código SIGTAP do tipo de serviço (sem diferença de caixa e sem espaços
	 * nas pontas), ou {@code null} se o tipo não for reconhecido. Usado também
	 * pelo {@code ValidacaoPlanilhaService}, para a análise aplicar a mesma regra.
	 */
	static String codigoSigtap(String tipoServico) {

		if (tipoServico == null) {
			return null;
		}

		return switch (tipoServico.trim().toUpperCase()) {
			case "TELECONSULTA" -> "03.01.01.030-7";
			case "TELEINTERCONSULTA" -> "08.04.01.006-4";
			default -> null;
		};
	}

	/**
	 * Separa o campo combinado "CÓDIGO - NOME" do estabelecimento
	 * em dois campos distintos: codEstabelecimento e estabelecimento (nome).
	 *
	 * Exemplo: "12345 - HOSPITAL CENTRAL" → código="12345", nome="HOSPITAL CENTRAL"
	 * <p>
	 * Quando a célula não traz um separador reconhecido, o nome é preservado
	 * (célula inteira) mas o código fica indefinido — {@code AtendimentoImportacaoService}
	 * tenta então vincular por nome a um estabelecimento já cadastrado; se não
	 * encontrar, o atendimento fica sem estabelecimento. Gera aviso nesse caso.
	 *
	 * @return lista com o aviso aplicável, ou lista vazia
	 */
	private List<String> separarEstabelecimento(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		String valorOriginal = dto.getEstabelecimento();

		// Vazio, só o separador ou código zero: não informado — atendimento
		// fica sem estabelecimento, com aviso (mesma regra da análise,
		// ESTABELECIMENTO_AUSENTE). Antes passava em silêncio, e "-"/"0" eram
		// tratados como nome/código.
		if (StringUtils.isEstabelecimentoNaoInformado(valorOriginal)) {
			dto.setCodEstabelecimento(null);
			dto.setEstabelecimento(null);
			avisos.add("Estabelecimento não informado — o atendimento ficou sem estabelecimento");
			return avisos;
		}

		String[] partes =
				StringUtils.separarCodigoENome(valorOriginal);

		if (partes[0] == null) {
			avisos.add("Estabelecimento \"" + valorOriginal.trim() + "\" nao traz codigo reconhecido "
					+ "(formato esperado: \"codigo - nome\") - sera vinculado por nome, se ja houver "
					+ "um estabelecimento cadastrado com esse nome; caso contrario, o atendimento "
					+ "ficara sem estabelecimento");
		}

		dto.setCodEstabelecimento(partes[0]);
		dto.setEstabelecimento(partes[1]);

		return avisos;
	}

	/**
	 * Separa o campo combinado legado "ESPECIALIDADE - NOME DO MÉDICO" em dois
	 * campos distintos. Planilhas no formato atual trazem especialidade (coluna
	 * própria) e nome do médico em colunas diferentes, então os dois valores
	 * nunca chegam idênticos — só dispara quando {@link PlanilhaColumnMapper}
	 * reaproveitou o mesmo índice de coluna para os dois campos (planilha
	 * legado, uma única coluna "Especialidade/Médico" com valor combinado).
	 *
	 * Exemplo: "CARDIOLOGIA - DR. SILVA" → especialidade="CARDIOLOGIA", médico="DR. SILVA"
	 */
	private void separarEspecialidadeEMedico(LinhaImportacaoDTO dto) {

		String especialidade = dto.getEspecialidadeMedico();
		String medico = dto.getMedico();

		if (isNullOrEmpty(especialidade) || !especialidade.equals(medico)) {
			return;
		}

		String[] partes = StringUtils.separarEspecialidadeEMedico(especialidade);

		// Sem o separador o nome do médico viraria a especialidade e o médico
		// ficaria sem nome (caso real: coluna da especialidade sem cabeçalho)
		if (partes[1] == null) {
			throw new IllegalArgumentException(
					"A coluna \"Especialidade/Médico\" (formato antigo) não traz \"ESPECIALIDADE - NOME\": \""
							+ especialidade.trim() + "\" — se a especialidade está em outra coluna, coloque o"
							+ " cabeçalho \"Especialidade\" nela.");
		}

		dto.setEspecialidadeMedico(partes[0]);
		dto.setMedico(partes[1]);
	}

	// ===============================
	// VALIDAÇÕES
	// ===============================

	/**
	 * Valida o tamanho do CEP após normalização.
	 * CEP presente com tamanho diferente de 8 dígitos é erro bloqueante.
	 *
	 * @throws IllegalArgumentException se o CEP estiver presente mas com tamanho incorreto
	 */
	private void validarCep(LinhaImportacaoDTO dto) {

		String cep = dto.getCep();

		if (!isNullOrEmpty(cep) && !CepUtils.isValido(cep)) {
			throw new IllegalArgumentException(
					"CEP com tamanho inválido (" + cep.length() + " dígitos, esperado 8): " + cep);
		}
	}

	/**
	 * Valida o tamanho do CPF após normalização.
	 * CPF presente com tamanho diferente de 11 dígitos é erro bloqueante.
	 *
	 * @throws IllegalArgumentException se o CPF estiver presente mas com tamanho incorreto
	 */
	private void validarCpf(LinhaImportacaoDTO dto) {

		String cpf = dto.getCpfPaciente();

		if (isNullOrEmpty(cpf)) {
			return; // vazio: tratado em validarCamposObrigatorios (aceito só com "Paciente sem CPF" = Sim)
		}

		// CPF preenchido com "Paciente sem CPF" = Sim: contradição (decisão de 08/10/2026)
		if ("S".equals(SimNaoUtils.normalizar(dto.getPacienteSemCpf()))) {
			throw new IllegalArgumentException(
					"CPF preenchido com \"Paciente sem CPF\" = Sim (" + cpf + ") — se o paciente tem CPF,"
							+ " marque Não; se não tem, deixe o CPF vazio.");
		}

		// Menos de 11 dígitos é erro — não se completa com zeros (decisão de
		// 08/10/2026: não dá para garantir que o dígito que falta é um zero à
		// esquerda perdido pelo Excel). A mensagem orienta a corrigir.
		if (!CpfUtils.isValido(cpf)) {
			throw new IllegalArgumentException(
					"CPF com tamanho inválido (" + cpf.length() + " dígitos, esperado 11): " + cpf
							+ (cpf.length() < 11
									? " — se o CPF começa com zero, formate a coluna como texto e digite os 11 dígitos"
									: ""));
		}

		if (CpfUtils.isFalso(cpf)) {
			throw new IllegalArgumentException(
					"CPF do paciente inválido (dígitos repetidos): " + cpf
							+ " — para paciente sem CPF, deixe o CPF vazio e marque \"Paciente sem CPF\" = Sim");
		}

		if (!CpfUtils.isDvValido(cpf)) {
			throw new IllegalArgumentException(
					"CPF do paciente inválido (dígitos verificadores não conferem): " + cpf
							+ " — confira se algum dígito foi digitado errado");
		}
	}

	/**
	 * Valida o CPF do médico — chave do cadastro de {@code Medico}. Ausente ou
	 * com tamanho diferente de 11 dígitos é erro bloqueante da linha, lançado
	 * aqui (antes de qualquer gravação) para não chegar ao banco: um CPF
	 * fora do tamanho da coluna derrubava a transação da planilha inteira.
	 *
	 * @throws IllegalArgumentException se o CPF do médico estiver ausente ou inválido
	 */
	/**
	 * CBO do médico — vai para o BPA-I ({@code prd-cbo}, 6 posições). Reduzido
	 * aos dígitos ({@code 2251-25} → {@code 225125}): com a máscara ele tinha 7
	 * caracteres e deslocava o registro. Vazio ou diferente de 6 dígitos é erro
	 * bloqueante da linha (decisão de 08/10/2026).
	 *
	 * @throws IllegalArgumentException se o CBO estiver ausente ou inválido
	 */
	private void validarCbo(LinhaImportacaoDTO dto) {

		String cbo = dto.getCboMedico();

		if (isNullOrEmpty(cbo)) {
			throw new IllegalArgumentException(
					"CBO do médico não informado (médico: " + dto.getMedico() + ").");
		}

		if (!CboUtils.isValido(cbo)) {
			throw new IllegalArgumentException(
					"CBO do médico inválido (esperado 6 dígitos): " + cbo.trim()
							+ " (médico: " + dto.getMedico() + ").");
		}

		dto.setCboMedico(CboUtils.normalizar(cbo));
	}

	/**
	 * Especialidade — não vai para o BPA-I, mas organiza a árvore da tela, a
	 * folha (chave especialidade + médico) e a regra de procedimento fixo de
	 * nutricionista/psicólogo. Sem ela o atendimento sumia da árvore. Vazia é
	 * erro bloqueante da linha (decisão de 08/10/2026).
	 *
	 * @throws IllegalArgumentException se a especialidade estiver vazia
	 */
	private void validarEspecialidade(LinhaImportacaoDTO dto) {

		if (isNullOrEmpty(dto.getEspecialidadeMedico())) {
			throw new IllegalArgumentException(
					"Especialidade não informada (médico: " + dto.getMedico() + ").");
		}
	}

	private void validarCpfMedico(LinhaImportacaoDTO dto) {

		String cpf = dto.getCpfMedico();

		if (isNullOrEmpty(cpf)) {
			throw new IllegalArgumentException(
					"CPF do médico não informado (médico: " + dto.getMedico() + ").");
		}

		if (!CpfUtils.isValido(cpf)) {
			throw new IllegalArgumentException(
					"CPF do médico com tamanho inválido (" + cpf.length() + " dígitos, esperado 11): " + cpf
							+ " (médico: " + dto.getMedico() + ").");
		}

		// Dígitos repetidos ou verificadores errados — ERRO (decisão de
		// 08/10/2026). Um CPF digitado errado criava um "segundo médico" no
		// banco, com folha própria no BPA-I.
		if (CpfUtils.isFalso(cpf) || !CpfUtils.isDvValido(cpf)) {
			throw new IllegalArgumentException(
					"CPF do médico inválido (" + (CpfUtils.isFalso(cpf) ? "dígitos repetidos" : "dígitos verificadores não conferem")
							+ "): " + cpf + " (médico: " + dto.getMedico() + ").");
		}
	}

	/**
	 * Valida a raça do paciente — campo obrigatório no layout do BPA-I
	 * (seq 21, {@code prd-raca}). Ausente ou não reconhecida por
	 * {@link RacaUtils#resolverCodigo} (grafia incorreta) é erro bloqueante.
	 *
	 * @throws IllegalArgumentException se a raça estiver ausente ou não for reconhecida
	 */
	private void validarRaca(LinhaImportacaoDTO dto) {

		String raca = dto.getRacaPaciente();

		if (isNullOrEmpty(raca)) {
			throw new IllegalArgumentException("Raça do paciente não informada.");
		}

		if (RacaUtils.resolverCodigo(raca) == null) {
			throw new IllegalArgumentException(RacaUtils.mensagemNaoReconhecida(raca));
		}
	}

	/**
	 * Verifica a etnia do paciente — o BPA-I só leva a etnia quando a raça é
	 * Indígena (código 05 — layout 2026, seq 22).
	 * <p>
	 * Com raça Indígena a etnia é obrigatória (decisão de 13/10/2026 — o BPA
	 * exige a etnia para raça 05 e descartaria o registro): vazia ou fora da
	 * tabela oficial ({@link EtniaUtils#resolver}) é erro bloqueante da linha.
	 * Com outra raça a coluna não é olhada, sem aviso: a etnia só é relevante
	 * para raça Indígena (o gerador a envia em branco nos demais casos).
	 *
	 * @return lista vazia (mantida pela assinatura dos demais verificadores)
	 * @throws IllegalArgumentException se a raça é Indígena e a etnia está vazia ou não foi encontrada
	 */
	private List<String> verificarEtniaDoIndigena(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();
		String etnia = dto.getEtniaPaciente();

		if (!RacaUtils.isIndigena(dto.getRacaPaciente())) {
			return avisos;
		}

		if (isNullOrEmpty(etnia)) {
			throw new IllegalArgumentException(
					"Raça Indígena exige a etnia do paciente — preencha a coluna de etnia.");
		}

		if (EtniaUtils.resolver(etnia) == null) {
			throw new IllegalArgumentException(
					"Grafia da etnia \"" + etnia.trim() + "\" não reconhecida.");
		}

		return avisos;
	}

	/**
	 * Normaliza a situação de rua do paciente para o código S/N do BPA-I.
	 * <p>
	 * Coluna ausente da planilha (valor {@code null}) ou célula em branco:
	 * sem aviso — {@link br.gov.ses.fillbpai.service.GeradorBPAiService}
	 * usa o padrão "N" nesse caso. Valor presente mas não reconhecido
	 * (não é S/N, Sim/Não nem 1/0): aviso, e o campo fica sem valor
	 * definido (mesmo fallback "N" na geração).
	 *
	 * @return lista com o aviso aplicável, ou lista vazia
	 */
	private List<String> normalizarSituacaoRua(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		String bruto = dto.getSituacaoRua();

		if (SimNaoUtils.isInvalido(bruto)) {
			avisos.add("Situacao de rua \"" + bruto.trim()
					+ "\" nao reconhecida (use Sim/Nao ou S/N) - sera enviado 'N' na remessa");
		}

		dto.setSituacaoRua(SimNaoUtils.normalizar(bruto));

		return avisos;
	}

	/**
	 * Normaliza a informação de "paciente sem CPF/registro civil" (seq 39
	 * do BPA-I, <code>prd_sem_cpf</code>) para o código S/N.
	 * <p>
	 * Coluna ausente da planilha (valor {@code null}) ou célula em branco:
	 * sem aviso — {@link br.gov.ses.fillbpai.service.GeradorBPAiService}
	 * deriva o valor automaticamente a partir da presença do CPF do
	 * paciente nesse caso. Valor presente mas não reconhecido: aviso, e o
	 * campo fica sem valor definido (mesma derivação automática se aplica).
	 *
	 * @return lista com o aviso aplicável, ou lista vazia
	 */
	private List<String> normalizarPacienteSemCpf(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		String bruto = dto.getPacienteSemCpf();

		if (SimNaoUtils.isInvalido(bruto)) {
			avisos.add("Paciente sem CPF \"" + bruto.trim()
					+ "\" nao reconhecido (use Sim/Nao ou S/N) - o valor sera derivado automaticamente"
					+ " a partir do CPF informado");
		}

		dto.setPacienteSemCpf(SimNaoUtils.normalizar(bruto));

		return avisos;
	}

	/**
	 * Valida campos obrigatórios para a importação.
	 *
	 * Campos obrigatórios:
	 * - Nome do paciente
	 * - CPF do paciente
	 * - Data de agendamento
	 *
	 * @throws IllegalArgumentException se algum campo obrigatório estiver ausente
	 */
	private void validarCamposObrigatorios(LinhaImportacaoDTO dto) {

		if (isNullOrEmpty(dto.getPaciente())) {
			throw new IllegalArgumentException("Paciente não informado.");
		}

		// Município: sem o nome não há a primeira busca do IBGE (tabela de MS) —
		// ERRO, mesma regra da análise (MUNICIPIO_AUSENTE, decisão de 08/10/2026)
		if (isNullOrEmpty(dto.getMunicipio())) {
			throw new IllegalArgumentException("Município do paciente não informado.");
		}

		// CPF vazio só é aceito com a coluna "Paciente sem CPF" = Sim (decisão
		// de 08/10/2026); o paciente recebe uma chave interna na importação
		if (isNullOrEmpty(dto.getCpfPaciente()) && !"S".equals(SimNaoUtils.normalizar(dto.getPacienteSemCpf()))) {
			throw new IllegalArgumentException(
					"CPF do paciente não informado — para paciente sem CPF, marque a coluna \"Paciente sem CPF\" = Sim.");
		}

		// Paciente sem CPF: o CNS é o documento do paciente no BPA-I (seq 10) —
		// obrigatório com 15 dígitos (decisão de 09/10/2026, CNS_OBRIGATORIO_SEM_CPF)
		if (isNullOrEmpty(dto.getCpfPaciente()) && !CnsUtils.isCnsSemCpfValido(dto.getCnsPaciente())) {
			throw new IllegalArgumentException(
					"Paciente sem CPF precisa do CNS com 15 dígitos e dígito verificador válido ("
							+ CnsUtils.motivoCnsSemCpfInvalido(dto.getCnsPaciente())
							+ (isNullOrEmpty(dto.getCnsPaciente()) ? "" : ": " + dto.getCnsPaciente().trim()) + ").");
		}

		if (isNullOrEmpty(dto.getDataAgendamentoString())) {
			throw new IllegalArgumentException("Data de agendamento não informada.");
		}

		// Sem nascimento a idade sairia 000 no BPA-I (decisão de 14/10/2026)
		if (isNullOrEmpty(dto.getDataNascimentoString())) {
			throw new IllegalArgumentException("Data de nascimento do paciente não informada.");
		}
	}

	// ===============================
	// CONVERSÕES
	// ===============================

	/**
	 * Converte strings de data/hora do Excel em tipos Java.
	 *
	 * Campos convertidos:
	 * - dataAgendamentoString → dataAgendamento (LocalDate)
	 * - horaAtendimentoString → horaAtendimento (LocalTime) — não reconhecida vira aviso, não erro
	 * - dataNascimentoString  → dataNascimento (LocalDate)
	 *
	 * Formatos suportados por DateUtils: dd/MM/yyyy, yyyy-MM-dd, dd-MM-yyyy
	 * Formatos suportados por TimeUtils: HH:mm, H:mm, HH:mm:ss, HHmm
	 */
	private void converterDatas(LinhaImportacaoDTO dto, List<String> avisos) {

		if (!isNullOrEmpty(dto.getDataAgendamentoString())) {
			try {
				LocalDate data =
						DateUtils.parse(dto.getDataAgendamentoString());
				dto.setDataAgendamento(data);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Data de agendamento inválida: "
								+ dto.getDataAgendamentoString()
				);
			}
		}

		// Célula que mostra só dia e mês: o Excel completou o ano (14/10/2026).
		// Atendimento: aviso (a coluna pode estar só formatada sem o ano)
		if (dto.isDataAgendamentoSemAno() && dto.getDataAgendamento() != null) {
			avisos.add("Data do atendimento (" + DateUtils.formatar(dto.getDataAgendamento()).substring(0, 5)
					+ ") nao esta no formato correto (dd/MM/aaaa) (DATA_ATENDIMENTO_SEM_ANO)");
		}

		avisos.addAll(converterHora(dto));

		// Nascimento: erro — o paciente ficaria com idade de meses
		if (dto.isDataNascimentoSemAno()) {
			LocalDate completada = DateUtils.parse(dto.getDataNascimentoString());
			throw new IllegalArgumentException(
					"Data de nascimento (" + DateUtils.formatar(completada).substring(0, 5)
							+ ") não está no formato correto (dd/MM/aaaa).");
		}

		if (!isNullOrEmpty(dto.getDataNascimentoString())) {
			try {
				LocalDate nascimento =
						DateUtils.parse(dto.getDataNascimentoString());
				dto.setDataNascimento(nascimento);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Data de nascimento inválida: "
								+ dto.getDataNascimentoString()
				);
			}
		}

		// Nascimento depois do atendimento ou idade acima de 130 anos (limite
		// do layout): a idade no BPA-I sairia errada
		String incoerencia = DateUtils.incoerenciaNascimento(dto.getDataNascimento(), dto.getDataAgendamento());
		if (incoerencia != null) {
			throw new IllegalArgumentException(
					"Data de nascimento " + DateUtils.formatar(dto.getDataNascimento()) + " não confere: " + incoerencia + ".");
		}
	}

	/**
	 * CID da consulta (BPA-I seq 13): grava só o código CID-10 encontrado na
	 * célula ({@code "CID I10"}, {@code "I10 - Hipertensão"} → {@code I10};
	 * {@code "I10.9"} → {@code I109}). Célula sem código ou com mais de um é
	 * erro da linha; vazia é aceita com aviso (decisão de 14/10/2026).
	 *
	 * @return lista com o aviso de CID vazio, ou lista vazia
	 * @throws IllegalArgumentException se a célula não tiver exatamente um código CID-10
	 */
	private List<String> normalizarCid(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();
		String cid = dto.getCidConsulta();

		if (isNullOrEmpty(cid)) {
			dto.setCidConsulta(null);
			avisos.add("CID da consulta nao informado (CID_AUSENTE) - o BPA-I sera gerado com o CID em branco");
			return avisos;
		}

		String codigo = CidUtils.extrair(cid);

		if (codigo == null) {
			throw new IllegalArgumentException(
					"CID da consulta \"" + cid.trim() + "\" inválido — " + CidUtils.motivoInvalido(cid) + ".");
		}

		dto.setCidConsulta(codigo);
		return avisos;
	}

	/**
	 * Converte a hora de atendimento. Diferente das datas, hora não reconhecida
	 * (ex.: "-", "--:--", "SEM HORARIO") NÃO descarta a linha: a hora não é
	 * enviada no BPA-I, então o atendimento é importado sem hora e com aviso
	 * (mesma regra de {@code ValidacaoPlanilhaService}, aviso HORA_INVALIDA).
	 *
	 * @return lista com um aviso quando a hora está preenchida mas não é reconhecida
	 */
	private List<String> converterHora(LinhaImportacaoDTO dto) {

		if (isNullOrEmpty(dto.getHoraAtendimentoString())) {
			return List.of();
		}

		try {
			LocalTime hora = TimeUtils.parse(dto.getHoraAtendimentoString());
			dto.setHoraAtendimento(hora);
			return List.of();
		} catch (IllegalArgumentException e) {
			dto.setHoraAtendimento(null);
			return List.of("Horário de atendimento não reconhecido: \""
					+ dto.getHoraAtendimentoString().trim()
					+ "\" — como a remessa BPA-I não utiliza o horário, o atendimento"
					+ " foi importado com o horário vazio");
		}
	}

	// ===============================
	// NORMALIZAÇÃO
	// ===============================

	/**
	 * Remove formatação dos CPFs (paciente e médico) via CpfUtils.
	 * Remove pontos, traços e demais não-numéricos: 123.456.789-00 → 12345678900
	 */
	private void normalizarCpf(LinhaImportacaoDTO dto) {

		if (!isNullOrEmpty(dto.getCpfPaciente())) {
			dto.setCpfPaciente(CpfUtils.normalizar(dto.getCpfPaciente()));
		}

		if (!isNullOrEmpty(dto.getCpfMedico())) {
			dto.setCpfMedico(CpfUtils.normalizar(dto.getCpfMedico()));
		}
	}

	/**
	 * Normaliza CEP removendo formatação.
	 * Remove hífen, espaços e caracteres inválidos.
	 *
	 * Exemplo: 79.003-020 → 79003020
	 */
	private void normalizarCep(LinhaImportacaoDTO dto) {

		if (!isNullOrEmpty(dto.getCep())) {

			dto.setCep(
					CepUtils.normalizar(dto.getCep())
			);
		}
	}

	/**
	 * Normaliza o sexo do paciente para o código de 1 caractere usado no BPA-I.
	 * Aceita valor abreviado (F/M/I) ou por extenso (Feminino/Masculino/Indeterminado),
	 * em qualquer combinação de maiúsculas/minúsculas.
	 */
	private void normalizarSexo(LinhaImportacaoDTO dto) {

		if (isNullOrEmpty(dto.getSexoPaciente())) {
			return;
		}

		switch (dto.getSexoPaciente().trim().toUpperCase()) {
			case "F":
			case "FEMININO":
				dto.setSexoPaciente("F");
				break;
			case "M":
			case "MASCULINO":
				dto.setSexoPaciente("M");
				break;
			case "I":
			case "INDETERMINADO":
				dto.setSexoPaciente("I");
				break;
			// Valor não reconhecido: mantém como veio
		}
	}

	/**
	 * Limita o tamanho dos campos de endereço conforme restrições do banco
	 * e do layout BPA-I.
	 *
	 * Limites:
	 * - endereco:    30 caracteres (seq 31 do BPA-I)
	 * - bairro:      30 caracteres (seq 34 do BPA-I)
	 * - complemento: 10 caracteres (seq 32 do BPA-I)
	 * - numero:       5 caracteres (seq 33 do BPA-I)
	 */
	private void limitarCamposBanco(LinhaImportacaoDTO dto) {

		// Endereço: o tipo de logradouro sai do começo ANTES do corte em 30
		// (antes o corte vinha primeiro e o "Rua " ocupava 4 posições), e o corte
		// é na última palavra inteira — ver normalizarLogradouro
		dto.setBairro(
				StringUtils.limitarTamanho(dto.getBairro(), 30));

		dto.setComplemento(
				StringUtils.limitarTamanho(dto.getComplemento(), 10));

		dto.setNumero(
				StringUtils.limitarTamanho(dto.getNumero(), 5));

		// Tipo de zona não vai para o BPA-I: só é cortado no tamanho da coluna
		// (50), para nunca derrubar a importação
		dto.setTipoZona(
				StringUtils.limitarTamanho(dto.getTipoZona(), 50));
	}

	/**
	 * Tipo de logradouro (BPA-I seq 30) e endereço (seq 31), pela tabela DNE
	 * ({@link LogradouroUtils}, 15/10/2026):
	 * <ul>
	 *   <li>tipo no começo do endereço → código, e o prefixo sai do endereço;</li>
	 *   <li>senão, a coluna "Cód. Logradouro" (código ou nome do tipo); preenchida
	 *       mas não reconhecida → aviso, e o gerador usa o padrão 081 (Rua);</li>
	 *   <li>endereço cortado em 30 na última palavra inteira.</li>
	 * </ul>
	 * Nunca bloqueia a linha, e o valor gravado sempre cabe nas colunas.
	 *
	 * @return lista com o aviso de logradouro não reconhecido, ou lista vazia
	 */
	private List<String> normalizarLogradouro(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();
		LogradouroUtils.LogradouroResultado prefixo = LogradouroUtils.resolver(dto.getEndereco());
		String coluna = dto.getCodLogradouro();

		if (prefixo.getCodLogradouro() != null) {
			dto.setCodLogradouro(prefixo.getCodLogradouro());
		} else {
			if (LogradouroUtils.isColunaNaoReconhecida(coluna)) {
				avisos.add("Tipo de logradouro \"" + coluna.trim() + "\" nao reconhecido (LOGRADOURO_NAO_RECONHECIDO)"
						+ " - informe o codigo ou o nome do tipo (ex.: 081 ou Rua); se nao for corrigido, sera"
						+ " usado 081 (Rua)");
			}
			dto.setCodLogradouro(LogradouroUtils.codigoDaColuna(coluna));
		}

		dto.setEndereco(StringUtils.limitarNaPalavra(prefixo.getEndereco(), 30));
		return avisos;
	}

	/**
	 * Telefone do paciente (BPA-I seq 35, opcional): grava só DDD + número
	 * ({@link TelefoneUtils#normalizar}). Sem DDD ou inválido: aviso e campo
	 * vazio (decisão de 14/10/2026) — nunca bloqueia a linha.
	 *
	 * @return lista com o aviso aplicável, ou lista vazia
	 */
	private List<String> normalizarTelefone(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();
		String bruto = dto.getTelefone();
		TelefoneUtils.Resultado telefone = TelefoneUtils.normalizar(bruto);

		switch (telefone.situacao()) {
			case SEM_DDD -> avisos.add("Telefone \"" + bruto.trim() + "\" fora do padrao (TELEFONE_SEM_DDD) - informe"
					+ " DDD + numero (ex.: 67 99999-9999); se nao for corrigido, o telefone sera gerado em branco");
			case INVALIDO -> avisos.add("Telefone \"" + bruto.trim() + "\" nao reconhecido (TELEFONE_INVALIDO) - o"
					+ " BPA-I sera gerado com o telefone em branco");
			default -> { }
		}

		dto.setTelefone(telefone.numero());
		return avisos;
	}

	// ===============================
	// UTIL
	// ===============================

	private boolean isNullOrEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}
}
