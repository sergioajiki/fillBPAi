package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

/**
 * Entidade que representa um médico.
 * Chave natural: CPF (único).
 * Dados atualizados pela última importação.
 */
@Entity
@Table(name = "medico")
public class Medico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cpf", length = 14, unique = true, nullable = false)
	private String cpf;

	@Column(name = "nome", length = 200)
	private String nome;

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
}
