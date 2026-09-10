package br.gov.ses.fillbpai.repository;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.model.Estabelecimento;

import static org.assertj.core.api.Assertions.assertThat;

class EstabelecimentoRepositoryTest {

	private EntityManagerFactory emf;
	private EntityManager entityManager;
	private EstabelecimentoRepository repository;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
		repository = new EstabelecimentoRepository(entityManager);
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	@Test
	void salvarEBuscarPorCodigoEncontraOEstabelecimento() {

		Estabelecimento estabelecimento = new Estabelecimento();
		estabelecimento.setCodigo("12345");
		estabelecimento.setNome("HOSPITAL CENTRAL");

		entityManager.getTransaction().begin();
		repository.salvar(estabelecimento);
		entityManager.getTransaction().commit();
		entityManager.clear();

		Optional<Estabelecimento> encontrado = repository.buscarPorCodigo("12345");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getNome()).isEqualTo("HOSPITAL CENTRAL");
	}

	@Test
	void buscarPorCodigoComCodigoInexistenteDevolveVazio() {
		Optional<Estabelecimento> encontrado = repository.buscarPorCodigo("99999");

		assertThat(encontrado).isEmpty();
	}
}
