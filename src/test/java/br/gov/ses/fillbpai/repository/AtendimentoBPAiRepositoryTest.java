package br.gov.ses.fillbpai.repository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Medico;
import br.gov.ses.fillbpai.model.Paciente;

import static org.assertj.core.api.Assertions.assertThat;

class AtendimentoBPAiRepositoryTest {

	private EntityManagerFactory emf;
	private EntityManager entityManager;
	private AtendimentoBPAiRepository repository;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
		repository = new AtendimentoBPAiRepository(entityManager);
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	private Paciente criarPaciente(String cpf) {
		Paciente paciente = new Paciente();
		paciente.setCpf(cpf);
		paciente.setNome("PACIENTE " + cpf);
		entityManager.persist(paciente);
		return paciente;
	}

	private Medico criarMedico(String cpf, String nome) {
		Medico medico = new Medico();
		medico.setCpf(cpf);
		medico.setNome(nome);
		entityManager.persist(medico);
		return medico;
	}

	private AtendimentoBPAi criarAtendimento(Paciente paciente, Medico medico, String especialidade,
			LocalDate data, String sigtap, String folha) {
		AtendimentoBPAi atendimento = new AtendimentoBPAi();
		atendimento.setPaciente(paciente);
		atendimento.setMedico(medico);
		atendimento.setEspecialidadeMedico(especialidade);
		atendimento.setDataAgendamento(data);
		atendimento.setSigtap(sigtap);
		atendimento.setFolha(folha);
		entityManager.persist(atendimento);
		return atendimento;
	}

	@Test
	void salvarEBuscarTodosDevolveOsAtendimentosPersistidos() {

		entityManager.getTransaction().begin();
		Paciente paciente = criarPaciente("12345678909");
		Medico medico = criarMedico("98765432100", "JOAO DA SILVA");
		criarAtendimento(paciente, medico, "CARDIOLOGIA", LocalDate.of(2024, 12, 25), "03.01.01.030-7", null);
		entityManager.getTransaction().commit();
		entityManager.clear();

		assertThat(repository.buscarTodos()).hasSize(1);
	}

	@Test
	void buscarDuplicataEncontraPelaChaveNaturalExata() {

		entityManager.getTransaction().begin();
		Paciente paciente = criarPaciente("12345678909");
		Medico medico = criarMedico("98765432100", "JOAO DA SILVA");
		LocalDate data = LocalDate.of(2024, 12, 25);
		criarAtendimento(paciente, medico, "CARDIOLOGIA", data, "03.01.01.030-7", null);
		entityManager.getTransaction().commit();
		entityManager.clear();

		Paciente pacienteRecarregado = entityManager.find(Paciente.class, paciente.getId());
		Medico medicoRecarregado = entityManager.find(Medico.class, medico.getId());

		Optional<AtendimentoBPAi> encontrado = repository.buscarDuplicata(
				pacienteRecarregado, medicoRecarregado, data, "03.01.01.030-7", null);

		assertThat(encontrado).isPresent();
	}

	@Test
	void buscarDuplicataConsideraOHorario() {

		entityManager.getTransaction().begin();
		Paciente paciente = criarPaciente("12345678909");
		Medico medico = criarMedico("98765432100", "JOAO DA SILVA");
		LocalDate data = LocalDate.of(2024, 12, 25);
		criarAtendimento(paciente, medico, "CARDIOLOGIA", data, "03.01.01.030-7", null)
				.setHoraAtendimento(java.time.LocalTime.of(8, 0));
		entityManager.getTransaction().commit();
		entityManager.clear();

		Paciente p = entityManager.find(Paciente.class, paciente.getId());
		Medico m = entityManager.find(Medico.class, medico.getId());

		assertThat(repository.buscarDuplicata(p, m, data, "03.01.01.030-7", java.time.LocalTime.of(8, 0)))
				.isPresent();
		assertThat(repository.buscarDuplicata(p, m, data, "03.01.01.030-7", java.time.LocalTime.of(9, 0)))
				.isEmpty();
		assertThat(repository.buscarDuplicata(p, m, data, "03.01.01.030-7", null)).isEmpty();
	}

	@Test
	void buscarDuplicataComSigtapDiferenteNaoEncontra() {

		entityManager.getTransaction().begin();
		Paciente paciente = criarPaciente("12345678909");
		Medico medico = criarMedico("98765432100", "JOAO DA SILVA");
		LocalDate data = LocalDate.of(2024, 12, 25);
		criarAtendimento(paciente, medico, "CARDIOLOGIA", data, "03.01.01.030-7", null);
		entityManager.getTransaction().commit();
		entityManager.clear();

		Paciente pacienteRecarregado = entityManager.find(Paciente.class, paciente.getId());
		Medico medicoRecarregado = entityManager.find(Medico.class, medico.getId());

		Optional<AtendimentoBPAi> encontrado = repository.buscarDuplicata(
				pacienteRecarregado, medicoRecarregado, data, "08.04.01.006-4", null);

		assertThat(encontrado).isEmpty();
	}
}
