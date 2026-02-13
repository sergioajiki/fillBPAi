package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.util.DateUtils;
import br.gov.ses.fillbpai.util.TimeUtils;
import br.gov.ses.fillbpai.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;

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
     */
    public void processar(AtendimentoBPAi atendimento) {

        // ===============================
        // 1️⃣ Separar código e nome estabelecimento
        // ===============================
        String valorOriginal = atendimento.getEstabelecimento();

        if (valorOriginal != null && !valorOriginal.trim().isEmpty()) {

            String[] partes =
                    StringUtils.separarCodigoENome(valorOriginal);

            atendimento.setCodEstabelecimento(partes[0]); // código
            atendimento.setEstabelecimento(partes[1]);    // nome

            System.out.println("Original: " + valorOriginal);
            System.out.println("Código: " + partes[0]);
            System.out.println("Nome: " + partes[1]);

        }

        // ===============================
        // 2️⃣ Validações
        // ===============================
        validarCamposObrigatorios(atendimento);

        // ===============================
        // 3️⃣ Conversões
        // ===============================
        converterDatas(atendimento);

        // ===============================
        // 4️⃣ Normalizações
        // ===============================
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
