package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entidade que representa uma linha da planilha BPAi.
 */
@Entity
@Table(name = "atendimento_bpai")
public class AtendimentoBPAi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_servico")
    private String tipoServico;

    @Column(name = "data_agendamento")
    private LocalDate dataAgendamento;

    @Column(name = "hora_atendimento")
    private LocalTime horaAtendimento;

    private String estabelecimento;

    @Column(name = "especialidade_medico")
    private String especialidadeMedico;

    @Column(name = "cpf_medico")
    private String cpfMedico;

    @Column(name = "cbo_medico")
    private String cboMedico;

    private String municipio;

    @Column(name = "cpf_paciente")
    private String cpfPaciente;

    private String paciente;

    @Column(name = "cns_paciente")
    private String cnsPaciente;

    @Column(name = "raca_paciente")
    private String racaPaciente;

    @Column(name = "data_nascimento")
    private String dataNascimento;

    @Column(name = "cid_consulta")
    private String cidConsulta;

    private String telefone;

    @Column(name = "tipo_zona")
    private String tipoZona;

    @Column(name = "endereco_completo", length = 1000)
    private String enderecoCompleto;

    // Getters e Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipoServico() {
        return tipoServico;
    }

    public void setTipoServico(String tipoServico) {
        this.tipoServico = tipoServico;
    }

    public LocalDate getDataAgendamento() {
        return dataAgendamento;
    }

    public void setDataAgendamento(LocalDate dataAgendamento) {
        this.dataAgendamento = dataAgendamento;
    }

    public LocalTime getHoraAtendimento() {
        return horaAtendimento;
    }

    public void setHoraAtendimento(LocalTime horaAtendimento) {
        this.horaAtendimento = horaAtendimento;
    }

    public String getEstabelecimento() {
        return estabelecimento;
    }

    public void setEstabelecimento(String estabelecimento) {
        this.estabelecimento = estabelecimento;
    }

    public String getEspecialidadeMedico() {
        return especialidadeMedico;
    }

    public void setEspecialidadeMedico(String especialidadeMedico) {
        this.especialidadeMedico = especialidadeMedico;
    }

    public String getCpfMedico() {
        return cpfMedico;
    }

    public void setCpfMedico(String cpfMedico) {
        this.cpfMedico = cpfMedico;
    }

    public String getCboMedico() {
        return cboMedico;
    }

    public void setCboMedico(String cboMedico) {
        this.cboMedico = cboMedico;
    }

    public String getMunicipio() {
        return municipio;
    }

    public void setMunicipio(String municipio) {
        this.municipio = municipio;
    }

    public String getCpfPaciente() {
        return cpfPaciente;
    }

    public void setCpfPaciente(String cpfPaciente) {
        this.cpfPaciente = cpfPaciente;
    }

    public String getPaciente() {
        return paciente;
    }

    public void setPaciente(String paciente) {
        this.paciente = paciente;
    }

    public String getCnsPaciente() {
        return cnsPaciente;
    }

    public void setCnsPaciente(String cnsPaciente) {
        this.cnsPaciente = cnsPaciente;
    }

    public String getRacaPaciente() {
        return racaPaciente;
    }

    public void setRacaPaciente(String racaPaciente) {
        this.racaPaciente = racaPaciente;
    }

    public String getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(String dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getCidConsulta() {
        return cidConsulta;
    }

    public void setCidConsulta(String cidConsulta) {
        this.cidConsulta = cidConsulta;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getTipoZona() {
        return tipoZona;
    }

    public void setTipoZona(String tipoZona) {
        this.tipoZona = tipoZona;
    }

    public String getEnderecoCompleto() {
        return enderecoCompleto;
    }

    public void setEnderecoCompleto(String enderecoCompleto) {
        this.enderecoCompleto = enderecoCompleto;
    }
}

