package br.gov.ses.fillbpai.model;

import jakarta.persistence.*;

/**
 * Entidade que representa um estabelecimento de saúde.
 * Chave natural: código (único).
 * Dados atualizados pela última importação.
 */
@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo", length = 10, unique = true, nullable = false)
	private String codigo;

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

	public String getCodigo() {
		return codigo;
	}

	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}
}
