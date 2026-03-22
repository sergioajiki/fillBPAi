package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CepUtils;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Classe responsável por:
 * - Validar dados importados do Excel
 * - Converter Strings em tipos corretos
 * - Normalizar informações (CPF etc.)
 * <p>
 * Esta classe NÃO acessa banco.
 * Apenas prepara o DTO para extração de entidades.
 */

public class AtendimentoProcessor {

	/**
	 * Processa e valida um DTO de importação.
	 */
	public void processar(LinhaImportacaoDTO dto) {

		// ===============================
		// 1️⃣ Separações
		// ===============================

		separarEstabelecimento(dto);
		separarEspecialidadeEMedico(dto);

		// ===============================
		// 2️⃣ Definir SIGTAP
		// ===============================

		dto.setSigtap(definirSigtap(dto.getTipoServico()));

		// ===============================
		// 4️⃣ Normalizações
		// ===============================

		normalizarCpf(dto);
		normalizarCep(dto);
		limitarCamposBanco(dto);

		// ===============================
		// 2️⃣ Validações
		// ===============================

		validarCamposObrigatorios(dto);
		validarCns(dto);


		// ===============================
		// 3️⃣ Conversões
		// ===============================

		converterDatas(dto);
	}

	private void validarCns(LinhaImportacaoDTO dto) {

		String cnsProcessado =
				CnsUtils.processar(dto.getCnsPaciente());

		dto.setCnsPaciente(cnsProcessado);
	}

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
	 * Separa código e nome do estabelecimento.
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
	 * Separa especialidade e nome do médico.
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

	private void normalizarCpf(LinhaImportacaoDTO dto) {

		if (!isNullOrEmpty(dto.getCpfPaciente())) {
			dto.setCpfPaciente(
					dto.getCpfPaciente()
							.replace(".", "")
							.replace("-", "")
							.trim()
			);
		}

		if (!isNullOrEmpty(dto.getCpfMedico())) {
			dto.setCpfMedico(
					dto.getCpfMedico()
							.replace(".", "")
							.replace("-", "")
							.trim()
			);
		}
	}

	/**
	 * Normaliza CEP.
	 * Remove hífen, espaços e caracteres inválidos.
	 *
	 * Ex:
	 * 79.003-020 -> 79003020
	 */
	private void normalizarCep(LinhaImportacaoDTO dto) {

		if (!isNullOrEmpty(dto.getCep())) {

			dto.setCep(
					CepUtils.normalizar(dto.getCep())
			);
		}
	}

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
