package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.Paciente;
import jakarta.persistence.EntityManager;

import java.util.Optional;

/**
 * Repositório para a entidade Paciente.
 * Busca por CPF (chave natural).
 */
public class PacienteRepository {

	private final EntityManager entityManager;

	public PacienteRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Busca paciente pelo CPF normalizado.
	 */
	public Optional<Paciente> buscarPorCpf(String cpf) {
		return entityManager
				.createQuery(
						"SELECT p FROM Paciente p LEFT JOIN FETCH p.endereco WHERE p.cpf = :cpf",
						Paciente.class)
				.setParameter("cpf", cpf)
				.getResultStream()
				.findFirst();
	}

	/**
	 * Persiste um novo paciente.
	 */
	public void salvar(Paciente paciente) {
		entityManager.persist(paciente);
	}
}
