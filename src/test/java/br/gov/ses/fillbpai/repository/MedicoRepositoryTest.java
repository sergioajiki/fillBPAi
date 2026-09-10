package br.gov.ses.fillbpai.repository;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.model.Medico;

import static org.assertj.core.api.Assertions.assertThat;

class MedicoRepositoryTest {

	private EntityManagerFactory emf;
	private EntityManager entityManager;
	private MedicoRepository repository;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
		repository = new MedicoRepository(entityManager);
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	@Test
	void salvarEBuscarPorCpfEncontraOMedico() {

		Medico medico = new Medico();
		medico.setCpf("98765432100");
		medico.setNome("RODRIGO SILVA GRILO");

		entityManager.getTransaction().begin();
		repository.salvar(medico);
		entityManager.getTransaction().commit();
		entityManager.clear();

		Optional<Medico> encontrado = repository.buscarPorCpf("98765432100");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getNome()).isEqualTo("RODRIGO SILVA GRILO");
	}

	@Test
	void buscarPorCpfComCpfInexistenteDevolveVazio() {
		Optional<Medico> encontrado = repository.buscarPorCpf("00000000000");

		assertThat(encontrado).isEmpty();
	}
}
