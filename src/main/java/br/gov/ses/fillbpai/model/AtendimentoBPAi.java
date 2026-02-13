package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entidade que representa uma linha da planilha BPAi.
 *
 * Estratégia adotada:
 * - Datas reais são armazenadas como LocalDate / LocalTime
 * - Valores vindos do Excel são lidos como String
 * - Campos auxiliares @Transient são usados para conversão antes da persistência
 */
@Entity
@Table(name = "atendimento_bpai")
public class AtendimentoBPAi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_servico", length = 100)
    private String tipoServico;

    @Column(name = "data_agendamento")
    private LocalDate dataAgendamento;

    @Column(name = "hora_atendimento")
    private LocalTime horaAtendimento;

    @Column(name = "cod_estabelecimento", length = 10)
    private String codEstabelecimento;

    @Column(name = "estabelecimento", length = 200)
    private String estabelecimento;

    @Column(name = "especialidade_medico", length = 150)
    private String especialidadeMedico;

    @Column(name = "medico", length = 200)
    private String medico;

    @Column(name = "cpf_medico", length = 14)
    private String cpfMedico;

    @Column(name = "cbo_medico", length = 20)
    private String cboMedico;

    @Column(length = 150)
    private String municipio;

    @Column(name = "cpf_paciente", length = 14)
    private String cpfPaciente;

    @Column(length = 200)
    private String paciente;

    @Column(name = "cns_paciente", length = 20)
    private String cnsPaciente;

    @Column(name = "raca_paciente", length = 50)
    private String racaPaciente;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "cid_consulta", length = 20)
    private String cidConsulta;

    @Column(length = 20)
    private String telefone;

    @Column(name = "tipo_zona", length = 50)
    private String tipoZona;

    @Column(name = "endereco_completo", length = 1000)
    private String enderecoCompleto;

    /*
     * Campos auxiliares para importação do Excel.
     * NÃO são persistidos no banco.
     */

    @Transient
    private String dataAgendamentoString;

    @Transient
    private String horaAtendimentoString;

    @Transient
    private String dataNascimentoString;

    // ======================
    // Getters e Setters
    // ======================

    public Long getId() {
        return id;
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

    public String getCodEstabelecimento() {
        return codEstabelecimento;
    }

    public void setCodEstabelecimento(String codEstabelecimento) {
        this.codEstabelecimento = codEstabelecimento;
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

    public String getMedico() {
        return medico;
    }

    public void setMedico(String medico) {
        this.medico = medico;
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

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
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

    public String getDataAgendamentoString() {
        return dataAgendamentoString;
    }

    public void setDataAgendamentoString(String dataAgendamentoString) {
        this.dataAgendamentoString = dataAgendamentoString;
    }

    public String getHoraAtendimentoString() {
        return horaAtendimentoString;
    }

    public void setHoraAtendimentoString(String horaAtendimentoString) {
        this.horaAtendimentoString = horaAtendimentoString;
    }

    public String getDataNascimentoString() {
        return dataNascimentoString;
    }

    public void setDataNascimentoString(String dataNascimentoString) {
        this.dataNascimentoString = dataNascimentoString;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
