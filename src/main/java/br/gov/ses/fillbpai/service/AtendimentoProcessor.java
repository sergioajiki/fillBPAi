package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;
import br.gov.ses.fillbpai.util.CnsUtils;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Classe responsável por:
 * - Validar dados importados do Excel
 * - Converter Strings em tipos corretos
 * - Normalizar informações (CPF etc.)
 * <p>
 * Esta classe NÃO acessa banco.
 * Apenas prepara o objeto para persistência.
 */

public class AtendimentoProcessor {

    /**
     * Processa e valida um atendimento antes da persistência.
     */
    public void processar(AtendimentoBPAi atendimento) {

        // ===============================
        // 1️⃣ Separações
        // ===============================

        separarEstabelecimento(atendimento);
        separarEspecialidadeEMedico(atendimento);

        // ===============================
        // 2️⃣ Definir SIGTAP
        // ===============================

        atendimento.setSigtap(definirSigtap(atendimento.getTipoServico()));

        // ===============================
        // 4️⃣ Normalizações
        // ===============================

        normalizarCpf(atendimento);

        // ===============================
        // 2️⃣ Validações
        // ===============================

        validarCamposObrigatorios(atendimento);
        validarCns(atendimento);


        // ===============================
        // 3️⃣ Conversões
        // ===============================

        converterDatas(atendimento);
    }

    private void validarCns(AtendimentoBPAi atendimento) {

        String cnsProcessado =
                CnsUtils.processar(atendimento.getCnsPaciente());

        atendimento.setCnsPaciente(cnsProcessado);
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
    private void separarEstabelecimento(AtendimentoBPAi atendimento) {

        String valorOriginal = atendimento.getEstabelecimento();

        if (isNullOrEmpty(valorOriginal)) {
            return;
        }

        String[] partes =
                StringUtils.separarCodigoENome(valorOriginal);

        atendimento.setCodEstabelecimento(partes[0]);
        atendimento.setEstabelecimento(partes[1]);
    }

    /**
     * Separa especialidade e nome do médico.
     */
    private void separarEspecialidadeEMedico(AtendimentoBPAi atendimento) {

        String valorOriginal = atendimento.getEspecialidadeMedico();

        if (isNullOrEmpty(valorOriginal)) {
            return;
        }

        String[] partes =
                StringUtils.separarEspecialidadeEMedico(valorOriginal);

        atendimento.setEspecialidadeMedico(partes[0]);
        atendimento.setMedico(partes[1]);
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

        if (!isNullOrEmpty(atendimento.getDataAgendamentoString())) {
            try {
                LocalDate data =
                        DateUtils.parse(atendimento.getDataAgendamentoString());
                atendimento.setDataAgendamento(data);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Data de agendamento inválida: "
                                + atendimento.getDataAgendamentoString()
                );
            }
        }

        if (!isNullOrEmpty(atendimento.getHoraAtendimentoString())) {
            try {
                LocalTime hora =
                        TimeUtils.parse(atendimento.getHoraAtendimentoString());
                atendimento.setHoraAtendimento(hora);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Hora de atendimento inválida: "
                                + atendimento.getHoraAtendimentoString()
                );
            }
        }

        if (!isNullOrEmpty(atendimento.getDataNascimentoString())) {
            try {
                LocalDate nascimento =
                        DateUtils.parse(atendimento.getDataNascimentoString());
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
