package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.Estabelecimento;
import br.gov.ses.fillbpai.util.TextoUtils;
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

	/**
	 * Busca estabelecimento pelo nome, ignorando acentuação e caixa.
	 * <p>
	 * Usado quando a célula "Estabelecimento" da planilha não trouxe um código
	 * reconhecido (formato "código - nome") — tenta reaproveitar um
	 * estabelecimento já cadastrado com esse nome, em vez de descartar o
	 * vínculo. A tabela é pequena (unidades de saúde do Núcleo), então a
	 * comparação em memória é suficiente.
	 */
	public Optional<Estabelecimento> buscarPorNome(String nome) {

		if (nome == null || nome.isBlank()) {
			return Optional.empty();
		}

		String nomeNormalizado = TextoUtils.normalizar(nome);

		return entityManager
				.createQuery("SELECT e FROM Estabelecimento e", Estabelecimento.class)
				.getResultStream()
				.filter(e -> TextoUtils.normalizar(e.getNome()).equals(nomeNormalizado))
				.findFirst();
	}
}
