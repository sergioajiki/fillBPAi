package br.gov.ses.fillbpai.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO que transporta os dados brutos de uma linha da planilha Excel.
 * <p>
 * Ciclo de vida:
 * 1. ExcelImportService preenche os campos crus (Strings)
 * 2. AtendimentoProcessor valida, normaliza e converte
 * 3. AtendimentoImportacaoService extrai entidades e persiste
 */
public class LinhaImportacaoDTO {

	// ==============================
	// Dados do atendimento
	// ==============================

	private String tipoServico;
	private String sigtap;
	private LocalDate dataAgendamento;
	private LocalTime horaAtendimento;
	private String cidConsulta;
	private String codIne;

	// ==============================
	// Dados do estabelecimento
	// ==============================

	private String codEstabelecimento;
	private String estabelecimento;

	// ==============================
	// Dados do médico
	// ==============================

	private String especialidadeMedico;
	private String medico;
	private String cpfMedico;
	private String cboMedico;

	// ==============================
	// Dados do paciente
	// ==============================

	private String cpfPaciente;
	private String paciente;
	private String cnsPaciente;
	private String sexoPaciente;
	private String racaPaciente;
	private LocalDate dataNascimento;
	private String telefone;

	// ==============================
	// Dados do endereço
	// ==============================

	private String municipio;
	private String tipoZona;
	private String cep;
	private String codLogradouro;
	private String endereco;
	private String complemento;
	private String numero;
	private String bairro;

	// ==============================
	// Campos auxiliares (strings brutas do Excel)
	// ==============================

	private String dataAgendamentoString;
	private String horaAtendimentoString;
	private String dataNascimentoString;

	// ==============================
	// Getters e Setters
	// ==============================

	public String getTipoServico() {
		return tipoServico;
	}

	public void setTipoServico(String tipoServico) {
		this.tipoServico = tipoServico;
	}

	public String getSigtap() {
		return sigtap;
	}

	public void setSigtap(String sigtap) {
		this.sigtap = sigtap;
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

	public String getCidConsulta() {
		return cidConsulta;
	}

	public void setCidConsulta(String cidConsulta) {
		this.cidConsulta = cidConsulta;
	}

	public String getCodIne() {
		return codIne;
	}

	public void setCodIne(String codIne) {
		this.codIne = codIne;
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

	public String getSexoPaciente() {
		return sexoPaciente;
	}

	public void setSexoPaciente(String sexoPaciente) {
		this.sexoPaciente = sexoPaciente;
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

	public String getTelefone() {
		return telefone;
	}

	public void setTelefone(String telefone) {
		this.telefone = telefone;
	}

	public String getMunicipio() {
		return municipio;
	}

	public void setMunicipio(String municipio) {
		this.municipio = municipio;
	}

	public String getTipoZona() {
		return tipoZona;
	}

	public void setTipoZona(String tipoZona) {
		this.tipoZona = tipoZona;
	}

	public String getCep() {
		return cep;
	}

	public void setCep(String cep) {
		this.cep = cep;
	}

	public String getCodLogradouro() {
		return codLogradouro;
	}

	public void setCodLogradouro(String codLogradouro) {
		this.codLogradouro = codLogradouro;
	}

	public String getEndereco() {
		return endereco;
	}

	public void setEndereco(String endereco) {
		this.endereco = endereco;
	}

	public String getComplemento() {
		return complemento;
	}

	public void setComplemento(String complemento) {
		this.complemento = complemento;
	}

	public String getNumero() {
		return numero;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public String getBairro() {
		return bairro;
	}

	public void setBairro(String bairro) {
		this.bairro = bairro;
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
}
