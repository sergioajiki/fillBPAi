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
 * - CNS profissional (resolvido pelo nome do médico na importação —
 *   {@code medicos_cns.csv} — ou editado na tela)
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
	private String cnesNts = br.gov.ses.fillbpai.config.DadosUnidade.CNES;

	@Column(name = "cod_ine")
	private String codIne;

	/**
	 * Folha ({@code prd-flh}) com que o atendimento foi para a última remessa
	 * BPA-I — gravada na geração (20/10/2026), junto com {@link #linhaFolha} e
	 * {@link #remessaEm}. Nula enquanto o atendimento não foi enviado.
	 */
	@Column(name = "folha")
	private String folha;

	/** Linha dentro da folha ({@code prd-seq}, 1 a 99) na última remessa BPA-I. */
	@Column(name = "linha_folha")
	private Integer linhaFolha;

	/** Data/hora da última remessa BPA-I em que o atendimento foi enviado; nula = não enviado. */
	@Column(name = "remessa_em")
	private java.time.LocalDateTime remessaEm;

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

	/**
	 * "S"/"N"/""/nulo — paciente sem CPF/registro civil (prd_sem_cpf do BPA-I).
	 * Nulo = planilha sem a coluna (o gerador deriva o valor da presença do CPF
	 * do paciente); "" = coluna presente com a célula vazia (vai em branco).
	 */
	@Column(name = "paciente_sem_cpf", length = 1)
	private String pacienteSemCpf;

	/**
	 * Situação de rua no momento <b>deste atendimento</b> (prd_situacao_rua do
	 * BPA-I — a informação do BPA é a do momento do atendimento; 16/10/2026).
	 * "S"/"N" informados (ou "N" quando a planilha não tem a coluna); "" =
	 * coluna presente com a célula vazia (vai em branco). Nulo só em
	 * atendimentos gravados antes desta mudança — o gerador usa então o valor
	 * que estava no paciente.
	 */
	@Column(name = "situacao_rua", length = 1)
	private String situacaoRua;

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

	public Integer getLinhaFolha() {
		return linhaFolha;
	}

	public void setLinhaFolha(Integer linhaFolha) {
		this.linhaFolha = linhaFolha;
	}

	public java.time.LocalDateTime getRemessaEm() {
		return remessaEm;
	}

	public void setRemessaEm(java.time.LocalDateTime remessaEm) {
		this.remessaEm = remessaEm;
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

	public String getPacienteSemCpf() {
		return pacienteSemCpf;
	}

	public void setPacienteSemCpf(String pacienteSemCpf) {
		this.pacienteSemCpf = pacienteSemCpf;
	}

	public String getSituacaoRua() {
		return situacaoRua;
	}

	public void setSituacaoRua(String situacaoRua) {
		this.situacaoRua = situacaoRua;
	}
}
