package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entidade que representa um atendimento BPA-I.
 * <p>
 * Relacionamentos:
 * - Paciente (N:1) — dados do paciente e endereço
 * - Medico (N:1) — dados do médico
 * - Estabelecimento (N:1) — dados do estabelecimento
 * <p>
 * Campos próprios do atendimento:
 * - Tipo de serviço, SIGTAP, data, hora, CID, folha, INE
 * - Especialidade e CBO (podem variar por atendimento para o mesmo médico)
 * - CNS profissional (preenchido manualmente na UI)
 */
@Entity
@Table(name = "atendimento_bpai")
public class AtendimentoBPAi {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// ==============================
	// Relacionamentos
	// ==============================

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "paciente_id")
	private Paciente paciente;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "medico_id")
	private Medico medico;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "estabelecimento_id")
	private Estabelecimento estabelecimento;

	// ==============================
	// Campos próprios do atendimento
	// ==============================

	@Column(name = "cnes_nts")
	private String cnesNts = "6970451";

	@Column(name = "cod_ine")
	private String codIne;

	@Column(name = "folha")
	private String folha;

	@Column(name = "cns_profissional")
	private String cnsProfissional;

	@Column(name = "tipo_servico", length = 100)
	private String tipoServico;

	@Column(name = "sigtap", length = 20)
	private String sigtap;

	@Column(name = "data_agendamento")
	private LocalDate dataAgendamento;

	@Column(name = "hora_atendimento")
	private LocalTime horaAtendimento;

	@Column(name = "especialidade_medico", length = 150)
	private String especialidadeMedico;

	@Column(name = "cbo_medico", length = 20)
	private String cboMedico;

	@Column(name = "cid_consulta", length = 20)
	private String cidConsulta;

	// ======================
	// Getters e Setters
	// ======================

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Paciente getPaciente() {
		return paciente;
	}

	public void setPaciente(Paciente paciente) {
		this.paciente = paciente;
	}

	public Medico getMedico() {
		return medico;
	}

	public void setMedico(Medico medico) {
		this.medico = medico;
	}

	public Estabelecimento getEstabelecimento() {
		return estabelecimento;
	}

	public void setEstabelecimento(Estabelecimento estabelecimento) {
		this.estabelecimento = estabelecimento;
	}

	public String getCnesNts() {
		return cnesNts;
	}

	public void setCnesNts(String cnesNts) {
		this.cnesNts = cnesNts;
	}

	public String getCodIne() {
		return codIne;
	}

	public void setCodIne(String codIne) {
		this.codIne = codIne;
	}

	public String getFolha() {
		return folha;
	}

	public void setFolha(String folha) {
		this.folha = folha;
	}

	public String getCnsProfissional() {
		return cnsProfissional;
	}

	public void setCnsProfissional(String cnsProfissional) {
		this.cnsProfissional = cnsProfissional;
	}

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

	public String getEspecialidadeMedico() {
		return especialidadeMedico;
	}

	public void setEspecialidadeMedico(String especialidadeMedico) {
		this.especialidadeMedico = especialidadeMedico;
	}

	public String getCboMedico() {
		return cboMedico;
	}

	public void setCboMedico(String cboMedico) {
		this.cboMedico = cboMedico;
	}

	public String getCidConsulta() {
		return cidConsulta;
	}

	public void setCidConsulta(String cidConsulta) {
		this.cidConsulta = cidConsulta;
	}
}
