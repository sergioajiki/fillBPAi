package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Entidade que representa um paciente.
 * Chave natural: CPF (único).
 * Dados atualizados pela última importação.
 */
@Entity
@Table(name = "paciente")
public class Paciente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cpf", length = 14, unique = true, nullable = false)
	private String cpf;

	@Column(name = "nome", length = 200)
	private String nome;

	@Column(name = "cns", length = 20)
	private String cns;

	@Column(name = "sexo", length = 1)
	private String sexo;

	@Column(name = "raca", length = 50)
	private String raca;

	@Column(name = "data_nascimento")
	private LocalDate dataNascimento;

	@Column(name = "telefone", length = 20)
	private String telefone;

	@OneToOne(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)
	private Endereco endereco;

	// ======================
	// Getters e Setters
	// ======================

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCpf() {
		return cpf;
	}

	public void setCpf(String cpf) {
		this.cpf = cpf;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getCns() {
		return cns;
	}

	public void setCns(String cns) {
		this.cns = cns;
	}

	public String getSexo() {
		return sexo;
	}

	public void setSexo(String sexo) {
		this.sexo = sexo;
	}

	public String getRaca() {
		return raca;
	}

	public void setRaca(String raca) {
		this.raca = raca;
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

	public Endereco getEndereco() {
		return endereco;
	}

	public void setEndereco(Endereco endereco) {
		this.endereco = endereco;
	}
}
