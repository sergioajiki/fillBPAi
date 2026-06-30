package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CepUtils;
import br.gov.ses.fillbpai.util.CpfUtils;

import java.text.Normalizer;
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
		// em campos individuais para normalização
		// ===============================

		separarEstabelecimento(dto);
		separarEspecialidadeEMedico(dto);

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
		avisos.addAll(validarCns(dto));
		avisos.addAll(verificarRacaIndigena(dto));

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
	 */
	private void separarEstabelecimento(LinhaImportacaoDTO dto) {

		String valorOriginal = dto.getEstabelecimento();

		if (isNullOrEmpty(valorOriginal)) {
			return;
		}

		String[] partes =
				StringUtils.separarCodigoENome(valorOriginal);

		dto.setCodEstabelecimento(partes[0]);
		dto.setEstabelecimento(partes[1]);
	}

	/**
	 * Separa o campo combinado "ESPECIALIDADE - NOME DO MÉDICO"
	 * em dois campos distintos: especialidadeMedico e medico (nome).
	 *
	 * Exemplo: "CARDIOLOGIA - DR. SILVA" → especialidade="CARDIOLOGIA", médico="DR. SILVA"
	 */
	private void separarEspecialidadeEMedico(LinhaImportacaoDTO dto) {

		String valorOriginal = dto.getEspecialidadeMedico();

		if (isNullOrEmpty(valorOriginal)) {
			return;
		}

		String[] partes =
				StringUtils.separarEspecialidadeEMedico(valorOriginal);

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
	 * Verifica se a raça do paciente é Indígena (com ou sem acento, qualquer capitalização).
	 * Quando detectado, gera aviso para verificação manual da etnia.
	 *
	 * @return lista com aviso, ou lista vazia
	 */
	private List<String> verificarRacaIndigena(LinhaImportacaoDTO dto) {

		List<String> avisos = new ArrayList<>();

		String raca = dto.getRacaPaciente();

		if (isNullOrEmpty(raca)) {
			return avisos;
		}

		String racaNorm = Normalizer
				.normalize(raca.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
				.toUpperCase();

		if ("INDIGENA".equals(racaNorm)) {
			avisos.add("Raca informada como Indigena - e necessario verificar a etnia do paciente");
		}

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
