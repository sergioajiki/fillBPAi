package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.Estabelecimento;
import jakarta.persistence.EntityManager;

import java.util.Optional;

/**
 * Repositório para a entidade Estabelecimento.
 * Busca por código (chave natural).
 */
public class EstabelecimentoRepository {

	private final EntityManager entityManager;

	public EstabelecimentoRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * Busca estabelecimento pelo código.
	 */
	public Optional<Estabelecimento> buscarPorCodigo(String codigo) {
		return entityManager
				.createQuery(
						"SELECT e FROM Estabelecimento e WHERE e.codigo = :codigo",
						Estabelecimento.class)
				.setParameter("codigo", codigo)
				.getResultStream()
				.findFirst();
	}

	/**
	 * Persiste um novo estabelecimento.
	 */
	public void salvar(Estabelecimento estabelecimento) {
		entityManager.persist(estabelecimento);
	}
}
