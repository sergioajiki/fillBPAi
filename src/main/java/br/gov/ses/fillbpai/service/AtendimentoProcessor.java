package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Classe responsável por:
 * - Validar dados importados do Excel
 * - Converter Strings em tipos corretos
 * - Normalizar informações (CPF, telefone etc.)
 *
 * Esta classe NÃO acessa banco.
 * Apenas prepara o objeto para persistência.
 */
public class AtendimentoProcessor {

    /**
     * Processa e valida um atendimento antes da persistência.
     *
     * @throws IllegalArgumentException se algum dado estiver inválido
     */
    public void processar(AtendimentoBPAi atendimento) {

        validarCamposObrigatorios(atendimento);

        converterDatas(atendimento);

        normalizarCpf(atendimento);
    }

    // ===============================
    // VALIDAÇÕES
    // ===============================

    private void validarCamposObrigatorios(AtendimentoBPAi atendimento) {

        if (isNullOrEmpty(atendimento.getPaciente())) {
            throw new IllegalArgumentException("Paciente não informado.");
        }

        if (isNullOrEmpty(atendimento.getCpfPaciente())) {
            throw new IllegalArgumentException("CPF do paciente não informado.");
        }

        if (isNullOrEmpty(atendimento.getDataAgendamentoString())) {
            throw new IllegalArgumentException("Data de agendamento não informada.");
        }
    }

    // ===============================
    // CONVERSÕES
    // ===============================

    private void converterDatas(AtendimentoBPAi atendimento) {

        // Converter Data Agendamento usando DateUtils
        if (!isNullOrEmpty(atendimento.getDataAgendamentoString())) {
            try {
                LocalDate data = DateUtils.parse(
                        atendimento.getDataAgendamentoString()
                );
                atendimento.setDataAgendamento(data);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Data de agendamento inválida: "
                                + atendimento.getDataAgendamentoString()
                );
            }
        }

// Converter Hora Atendimento usando TimeUtils
        if (!isNullOrEmpty(atendimento.getHoraAtendimentoString())) {
            try {
                LocalTime hora = TimeUtils.parse(
                        atendimento.getHoraAtendimentoString()
                );
                atendimento.setHoraAtendimento(hora);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Hora de atendimento inválida: "
                                + atendimento.getHoraAtendimentoString()
                );
            }
        }

        // Converter Data Nascimento usando DateUtils
        if (!isNullOrEmpty(atendimento.getDataNascimentoString())) {
            try {
                LocalDate nascimento = DateUtils.parse(
                        atendimento.getDataNascimentoString()
                );
                atendimento.setDataNascimento(nascimento);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Data de nascimento inválida: "
                                + atendimento.getDataNascimentoString()
                );
            }
        }
    }

    // ===============================
    // NORMALIZAÇÃO
    // ===============================

    private void normalizarCpf(AtendimentoBPAi atendimento) {

        if (!isNullOrEmpty(atendimento.getCpfPaciente())) {
            atendimento.setCpfPaciente(
                    atendimento.getCpfPaciente()
                            .replace(".", "")
                            .replace("-", "")
                            .trim()
            );
        }

        if (!isNullOrEmpty(atendimento.getCpfMedico())) {
            atendimento.setCpfMedico(
                    atendimento.getCpfMedico()
                            .replace(".", "")
                            .replace("-", "")
                            .trim()
            );
        }
    }

    // ===============================
    // UTIL
    // ===============================

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
