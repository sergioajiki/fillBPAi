package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CepUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import br.gov.ses.fillbpai.util.EspecialidadeUtils;
import br.gov.ses.fillbpai.util.EtniaUtils;
import br.gov.ses.fillbpai.util.RacaUtils;
import br.gov.ses.fillbpai.util.SimNaoUtils;

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
		dto.setEspecialidadeMedico(EspecialidadeUtils.normalizar(dto.getEspecialidadeMedico()));

		// ===============================
		// 2. Definir SIGTAP
		// Mapeia o tipo de serviço (TELECONSULTA, TELEINTERCONSULTA)
		// para o código SIGTAP correspondente
		// ===============================

		dto.setSigtap(definirSigtap(dto.getTipoServico()));

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
		validarRaca(dto);
		avisos.addAll(validarCns(dto));
		avisos.addAll(verificarEtniaDoIndigena(dto));
		avisos.addAll(normalizarSituacaoRua(dto));
		avisos.addAll(normalizarPacienteSemCpf(dto));

		// ===============================
		// 5. Conversões
		// Transforma strings de data/hora vindas do Excel
		// em tipos Java (LocalDate/LocalTime)
		// ===============================

		converterDatas(dto);

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
	 * @throws IllegalArgumentException se o tipo de serviço não for reconhecido
	 */
	private String definirSigtap(String tipoServico) {

		if (tipoServico == null) {
			return null;
		}

		switch (tipoServico.trim().toUpperCase()) {

			case "TELECONSULTA":
				return "03.01.01.030-7";

			case "TELEINTERCONSULTA":
				return "08.04.01.006-4";

			default:
				throw new IllegalArgumentException(
						"Tipo de serviço inválido para SIGTAP: " + tipoServico
				);
		}
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

		if (isNullOrEmpty(valorOriginal)) {
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

		if (!isNullOrEmpty(cpf) && !CpfUtils.isValido(cpf)) {
			throw new IllegalArgumentException(
					"CPF com tamanho inválido (" + cpf.length() + " dígitos, esperado 11): " + cpf);
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
			throw new IllegalArgumentException(
					"Raça do paciente \"" + raca.trim() + "\" não reconhecida - verifique a grafia "
							+ "na planilha (aceito: Branca, Preta, Parda, Amarela, Indígena).");
		}
	}

	/**
	 * Verifica a etnia do paciente — regra de negócio só considera a coluna
	 * Etnia quando a raça é Indígena (código 5 do BPA-I); para as demais
	 * raças o conteúdo da coluna é ignorado, mesmo que preenchido com algo
	 * que não bata com a tabela oficial (a coluna normalmente nem se aplica
	 * fora desse caso).
	 * <p>
	 * Com raça Indígena: etnia em branco → aviso pedindo pra preencher;
	 * etnia preenchida mas não reconhecida por {@link EtniaUtils#resolver}
	 * → aviso de etnia não encontrada; etnia reconhecida → sem aviso.
	 *
	 * @return lista com o aviso aplicável, ou lista vazia
	 */
	private List<String> verificarEtniaDoIndigena(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		if (!RacaUtils.isIndigena(dto.getRacaPaciente())) {
			return avisos;
		}

		String etnia = dto.getEtniaPaciente();

		if (isNullOrEmpty(etnia)) {
			avisos.add("Raca informada como Indigena - e necessario preencher a etnia do paciente");
		} else if (EtniaUtils.resolver(etnia) == null) {
			avisos.add("Etnia \"" + etnia.trim() + "\" nao encontrada na tabela oficial - verifique o texto informado");
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

		if (isNullOrEmpty(dto.getCpfPaciente())) {
			throw new IllegalArgumentException("CPF do paciente não informado.");
		}

		if (isNullOrEmpty(dto.getDataAgendamentoString())) {
			throw new IllegalArgumentException("Data de agendamento não informada.");
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
	 * - horaAtendimentoString → horaAtendimento (LocalTime)
	 * - dataNascimentoString  → dataNascimento (LocalDate)
	 *
	 * Formatos suportados por DateUtils: dd/MM/yyyy, yyyy-MM-dd, dd-MM-yyyy
	 * Formatos suportados por TimeUtils: HH:mm, H:mm, HH:mm:ss, HHmm
	 */
	private void converterDatas(LinhaImportacaoDTO dto) {

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

		if (!isNullOrEmpty(dto.getHoraAtendimentoString())) {
			try {
				LocalTime hora =
						TimeUtils.parse(dto.getHoraAtendimentoString());
				dto.setHoraAtendimento(hora);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Hora de atendimento inválida: "
								+ dto.getHoraAtendimentoString()
				);
			}
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

		dto.setEndereco(
				StringUtils.limitarTamanho(dto.getEndereco(), 30));

		dto.setBairro(
				StringUtils.limitarTamanho(dto.getBairro(), 30));

		dto.setComplemento(
				StringUtils.limitarTamanho(dto.getComplemento(), 10));

		dto.setNumero(
				StringUtils.limitarTamanho(dto.getNumero(), 5));
	}

	// ===============================
	// UTIL
	// ===============================

	private boolean isNullOrEmpty(String value) {
		return value == null || value.trim().isEmpty();
	}
}
