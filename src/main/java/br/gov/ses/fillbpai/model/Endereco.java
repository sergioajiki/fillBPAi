package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

/**
 * Entidade que representa o endereço de um paciente.
 * Relacionamento 1:1 com Paciente.
 * Atualizado pela última importação.
 */
@Entity
@Table(name = "endereco")
public class Endereco {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "paciente_id", unique = true, nullable = false)
	private Paciente paciente;

	@Column(name = "municipio", length = 150)
	private String municipio;

	@Column(name = "tipo_zona", length = 50)
	private String tipoZona;

	@Column(name = "cep", length = 8)
	private String cep;

	@Column(name = "cod_logradouro", length = 3)
	private String codLogradouro;

	@Column(name = "endereco", length = 30)
	private String endereco;

	@Column(name = "complemento", length = 10)
	private String complemento;

	@Column(name = "numero", length = 5)
	private String numero;

	@Column(name = "bairro", length = 30)
	private String bairro;

	/**
	 * Código IBGE do município (7 dígitos).
	 * Resolvido automaticamente durante a importação via:
	 * 1. API ViaCEP (pelo CEP) — primário
	 * 2. CSV embutido (pelo nome do município) — fallback
	 *
	 * O BPA-I usa apenas 6 dígitos (sem verificador).
	 * A truncagem é feita no GeradorBPAiService.
	 */
	@Column(name = "codigo_ibge", length = 7)
	private String codigoIbge;

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

	public String getCodigoIbge() {
		return codigoIbge;
	}

	public void setCodigoIbge(String codigoIbge) {
		this.codigoIbge = codigoIbge;
	}
}
