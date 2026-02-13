package br.gov.ses.fillbpai.dto;

public class AtendimentoBPAiDTO {

    private String tipoServico;
    private String dataAgendamento;
    private String horaAtendimento;
    private String estabelecimento;
    private String especialidadeMedico;
    private String cpfMedico;
    private String cboMedico;
    private String municipio;
    private String cpfPaciente;
    private String paciente;
    private String cnsPaciente;
    private String racaPaciente;
    private String dataNascimento;
    private String cidConsulta;
    private String telefone;
    private String tipoZona;
    private String enderecoCompleto;

    public AtendimentoBPAiDTO(
            String tipoServico,
            String dataAgendamento,
            String horaAtendimento,
            String estabelecimento,
            String especialidadeMedico,
            String cpfMedico,
            String cboMedico,
            String municipio,
            String cpfPaciente,
            String paciente,
            String cnsPaciente,
            String racaPaciente,
            String dataNascimento,
            String cidConsulta,
            String telefone,
            String tipoZona,
            String enderecoCompleto
    ) {
        this.tipoServico = tipoServico;
        this.dataAgendamento = dataAgendamento;
        this.horaAtendimento = horaAtendimento;
        this.estabelecimento = estabelecimento;
        this.especialidadeMedico = especialidadeMedico;
        this.cpfMedico = cpfMedico;
        this.cboMedico = cboMedico;
        this.municipio = municipio;
        this.cpfPaciente = cpfPaciente;
        this.paciente = paciente;
        this.cnsPaciente = cnsPaciente;
        this.racaPaciente = racaPaciente;
        this.dataNascimento = dataNascimento;
        this.cidConsulta = cidConsulta;
        this.telefone = telefone;
        this.tipoZona = tipoZona;
        this.enderecoCompleto = enderecoCompleto;
    }

    // getters apenas (somente leitura para UI)

    public String getTipoServico() { return tipoServico; }
    public String getDataAgendamento() { return dataAgendamento; }
    public String getHoraAtendimento() { return horaAtendimento; }
    public String getEstabelecimento() { return estabelecimento; }
    public String getEspecialidadeMedico() { return especialidadeMedico; }
    public String getCpfMedico() { return cpfMedico; }
    public String getCboMedico() { return cboMedico; }
    public String getMunicipio() { return municipio; }
    public String getCpfPaciente() { return cpfPaciente; }
    public String getPaciente() { return paciente; }
    public String getCnsPaciente() { return cnsPaciente; }
    public String getRacaPaciente() { return racaPaciente; }
    public String getDataNascimento() { return dataNascimento; }
    public String getCidConsulta() { return cidConsulta; }
    public String getTelefone() { return telefone; }
    public String getTipoZona() { return tipoZona; }
    public String getEnderecoCompleto() { return enderecoCompleto; }
}

