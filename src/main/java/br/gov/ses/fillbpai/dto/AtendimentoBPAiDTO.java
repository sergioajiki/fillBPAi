package br.gov.ses.fillbpai.dto;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Endereco;
import br.gov.ses.fillbpai.model.Paciente;

import java.time.format.DateTimeFormatter;

/**
 * DTO para exibição dos dados de atendimento na UI.
 * Navega pelos relacionamentos (Paciente, Medico, Estabelecimento, Endereco)
 * para montar os campos de apresentação.
 */
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

	public String getCodEstabelecimento() {
		return entity.getEstabelecimento() != null ?
				entity.getEstabelecimento().getCodigo() : "";
	}

	public String getEstabelecimento() {
		return entity.getEstabelecimento() != null ?
				entity.getEstabelecimento().getNome() : "";
	}

	public String getCnesNts() { return entity.getCnesNts(); }

	public String getCodIne() { return entity.getCodIne(); }

	public String getFolha() { return entity.getFolha(); }

	public String getMedico() {
		return entity.getMedico() != null ?
				entity.getMedico().getNome() : "";
	}

	public String getEspecialidadeMedico() { return entity.getEspecialidadeMedico(); }

	public String getCpfMedico() {
		return entity.getMedico() != null ?
				entity.getMedico().getCpf() : "";
	}

	public String getCboMedico() { return entity.getCboMedico(); }

	public String getCnsProfissional() { return entity.getCnsProfissional(); }

	public String getPaciente() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getNome() : "";
	}

	public String getCpfPaciente() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getCpf() : "";
	}

	public String getSexoPaciente() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getSexo() : "";
	}

	public String getCnsPaciente() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getCns() : "";
	}

	public String getRacaPaciente() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getRaca() : "";
	}

	public String getDataNascimento() {
		Paciente p = entity.getPaciente();
		if (p != null && p.getDataNascimento() != null) {
			return p.getDataNascimento().format(DATA);
		}
		return "";
	}

	public String getTelefone() {
		return entity.getPaciente() != null ?
				entity.getPaciente().getTelefone() : "";
	}

	public String getMunicipio() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getMunicipio() : "";
	}

	public String getTipoZona() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getTipoZona() : "";
	}

	public String getCep() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getCep() : "";
	}

	public String getCodLogradouro() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getCodLogradouro() : "";
	}

	public String getEndereco() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getEndereco() : "";
	}

	public String getComplemento() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getComplemento() : "";
	}

	public String getNumero() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getNumero() : "";
	}

	public String getBairro() {
		Endereco e = getEnderecoEntity();
		return e != null ? e.getBairro() : "";
	}

	public String getCodigoIbge() {
		Endereco e = getEnderecoEntity();
		return e != null && e.getCodigoIbge() != null ? e.getCodigoIbge() : "";
	}

	public String getCidConsulta() { return entity.getCidConsulta(); }

	/**
	 * Helper para acessar o endereço do paciente.
	 */
	private Endereco getEnderecoEntity() {
		Paciente p = entity.getPaciente();
		return p != null ? p.getEndereco() : null;
	}
}
