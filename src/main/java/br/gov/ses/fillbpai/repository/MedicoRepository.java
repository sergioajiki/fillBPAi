package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.Medico;
import jakarta.persistence.EntityManager;

import java.util.Optional;

/**
 * Repositório para a entidade Medico.
 * Busca por CPF (chave natural).
 */
public class MedicoRepository {

	private final EntityManager entityManager;

	public MedicoRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Busca médico pelo CPF normalizado.
	 */
	public Optional<Medico> buscarPorCpf(String cpf) {
		return entityManager
				.createQuery(
						"SELECT m FROM Medico m WHERE m.cpf = :cpf",
						Medico.class)
				.setParameter("cpf", cpf)
				.getResultStream()
				.findFirst();
	}

	/**
	 * Persiste um novo médico.
	 */
	public void salvar(Medico medico) {
		entityManager.persist(medico);
	}
}
