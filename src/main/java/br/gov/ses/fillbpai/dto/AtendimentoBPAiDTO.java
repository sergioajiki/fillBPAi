package br.gov.ses.fillbpai.dto;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;

import java.time.format.DateTimeFormatter;

public class AtendimentoBPAiDTO {

    private final AtendimentoBPAi entity;

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm");

    public AtendimentoBPAiDTO(AtendimentoBPAi entity) {
        this.entity = entity;
    }

    public static AtendimentoBPAiDTO fromEntity(AtendimentoBPAi a) {
        return new AtendimentoBPAiDTO(a);
    }

    public Long getId() { return entity.getId(); }

    public String getTipoServico() { return entity.getTipoServico(); }

    public String getSigtap() { return entity.getSigtap(); }

    public String getDataAgendamento() {
        return entity.getDataAgendamento() != null ?
                entity.getDataAgendamento().format(DATA) : "";
    }

    public String getHoraAtendimento() {
        return entity.getHoraAtendimento() != null ?
                entity.getHoraAtendimento().format(HORA) : "";
    }

    public String getCodEstabelecimento() { return entity.getCodEstabelecimento(); }

    public String getEstabelecimento() { return entity.getEstabelecimento(); }

    public String getCnesNts() { return entity.getCnesNts(); }

    public String getCodIne() { return entity.getCodIne(); }

    public String getFolha() { return entity.getFolha(); }

    public String getMedico() { return entity.getMedico(); }

    public String getEspecialidadeMedico() { return entity.getEspecialidadeMedico(); }

    public String getCpfMedico() { return entity.getCpfMedico(); }

    public String getCboMedico() { return entity.getCboMedico(); }

    public String getCnsProfissional() { return entity.getCnsProfissional(); }

    public String getPaciente() { return entity.getPaciente(); }

    public String getCpfPaciente() { return entity.getCpfPaciente(); }

    public String getSexoPaciente() { return entity.getSexoPaciente(); }

    public String getCnsPaciente() { return entity.getCnsPaciente(); }

    public String getRacaPaciente() { return entity.getRacaPaciente(); }

    public String getDataNascimento() {
        return entity.getDataNascimento() != null ?
                entity.getDataNascimento().format(DATA) : "";
    }

    public String getTelefone() { return entity.getTelefone(); }

    public String getMunicipio() { return entity.getMunicipio(); }

    public String getTipoZona() { return entity.getTipoZona(); }

    public String getCep() { return entity.getCep(); }

    public String getCodLogradouro() { return entity.getCodLogradouro(); }

    public String getEndereco() { return entity.getEndereco(); }

    public String getComplemento() { return entity.getComplemento(); }

    public String getNumero() { return entity.getNumero(); }

    public String getBairro() { return entity.getBairro(); }

    public String getCidConsulta() { return entity.getCidConsulta(); }
}