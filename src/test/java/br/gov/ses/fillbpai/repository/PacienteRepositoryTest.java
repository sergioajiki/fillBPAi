package br.gov.ses.fillbpai.repository;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.model.Endereco;
import br.gov.ses.fillbpai.model.Paciente;

import static org.assertj.core.api.Assertions.assertThat;

/** Usa a persistence unit de teste {@code bpaPU-test} (H2 em memória, isolada do banco real). */
class PacienteRepositoryTest {

	private EntityManagerFactory emf;
	private EntityManager entityManager;
	private PacienteRepository repository;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
		repository = new PacienteRepository(entityManager);
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	@Test
	void salvarEBuscarPorCpfEncontraOPacienteComOEnderecoJunto() {

		Paciente paciente = new Paciente();
		paciente.setCpf("12345678900");
		paciente.setNome("MARIA SILVA");

		Endereco endereco = new Endereco();
		endereco.setPaciente(paciente);
		endereco.setCep("79003020");
		endereco.setMunicipio("CAMPO GRANDE");
		paciente.setEndereco(endereco);

		entityManager.getTransaction().begin();
		repository.salvar(paciente);
		entityManager.getTransaction().commit();
		entityManager.clear();

		Optional<Paciente> encontrado = repository.buscarPorCpf("12345678900");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getNome()).isEqualTo("MARIA SILVA");
		assertThat(encontrado.get().getEndereco()).isNotNull();
		assertThat(encontrado.get().getEndereco().getCep()).isEqualTo("79003020");
	}

	@Test
	void buscarPorCpfComCpfInexistenteDevolveVazio() {
		Optional<Paciente> encontrado = repository.buscarPorCpf("00000000000");

		assertThat(encontrado).isEmpty();
	}
}
