package br.gov.ses.fillbpai.dto;

/**
 * DTO usado apenas para exibição na TableView.
 */
public class AtendimentoBPAiDTO {

    // 🔥 ID da entidade (ESSENCIAL para update)
    private Long id;

    // 🔥 CAMPOS FIXOS BPA
    private final String cnesNts = "697045";
    private final String codIne = "0";

    private String folha = "";
    private String cnsProfissional = "";

    private String tipoServico;
    private String sigtap;
    private String dataAgendamento;
    private String horaAtendimento;

    private String codEstabelecimento;
    private String estabelecimento;
    private String especialidade;
    private String medico;

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
            Long id,
            String tipoServico,
            String sigtap,
            String dataAgendamento,
            String horaAtendimento,
            String codEstabelecimento,
            String estabelecimento,
            String especialidade,
            String medico,
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
        this.id = id; // ✅ agora está correto
        this.tipoServico = tipoServico;
        this.sigtap = sigtap;
        this.dataAgendamento = dataAgendamento;
        this.horaAtendimento = horaAtendimento;
        this.codEstabelecimento = codEstabelecimento;
        this.estabelecimento = estabelecimento;
        this.especialidade = especialidade;
        this.medico = medico;
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

    // =============================
    // GETTERS
    // =============================

    public Long getId() { return id; }

    public String getCnesNts() { return cnesNts; }
    public String getCodIne() { return codIne; }
    public String getFolha() { return folha; }
    public String getCnsProfissional() { return cnsProfissional; }

    public String getTipoServico() { return tipoServico; }
    public String getSigtap() { return sigtap; }
    public String getDataAgendamento() { return dataAgendamento; }
    public String getHoraAtendimento() { return horaAtendimento; }
    public String getCodEstabelecimento() { return codEstabelecimento; }
    public String getEstabelecimento() { return estabelecimento; }
    public String getEspecialidade() { return especialidade; }
    public String getMedico() { return medico; }
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

    // =============================
    // SETTERS (somente campos editáveis)
    // =============================

    public void setFolha(String folha) { this.folha = folha; }
    public void setCnsProfissional(String cnsProfissional) {
        this.cnsProfissional = cnsProfissional;
    }
}