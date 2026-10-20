package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Medico;
import br.gov.ses.fillbpai.model.Paciente;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Folha, linha e data da remessa gravadas no atendimento depois da geração (20/10/2026). */
class RegistroRemessaTest {

	private EntityManagerFactory emf;
	private EntityManager entityManager;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	private AtendimentoBPAi persistir(String cpfPaciente, Medico medico, String especialidade, int dia) {
		Paciente paciente = new Paciente();
		paciente.setCpf(cpfPaciente);
		paciente.setNome("PACIENTE " + cpfPaciente);
		entityManager.persist(paciente);

		AtendimentoBPAi a = new AtendimentoBPAi();
		a.setPaciente(paciente);
		a.setMedico(medico);
		a.setEspecialidadeMedico(especialidade);
		a.setDataAgendamento(LocalDate.of(2026, 9, dia));
		a.setSigtap("03.01.01.030-7");
		entityManager.persist(a);
		return a;
	}

	@Test
	void registrarRemessaGravaFolhaLinhaEDataSoNosAtendimentosEnviados() {
		entityManager.getTransaction().begin();
		Medico andre = new Medico();
		andre.setCpf("11122233396");
		andre.setNome("DR. ANDRE COSTA");
		entityManager.persist(andre);
		Medico beatriz = new Medico();
		beatriz.setCpf("98765432100");
		beatriz.setNome("DRA. BEATRIZ LIMA");
		entityManager.persist(beatriz);
		AtendimentoBPAi a1 = persistir("11111111111", andre, "CARDIOLOGIA", 1);
		AtendimentoBPAi b1 = persistir("22222222222", beatriz, "CARDIOLOGIA", 2);
		entityManager.getTransaction().commit();

		GeradorBPAiService gerador = new GeradorBPAiService(entityManager);
		Map<AtendimentoBPAi, GeradorBPAiService.PosicaoFolha> numeracao =
				GeradorBPAiService.numerarFolhas(List.of(a1, b1));
		LocalDateTime quando = LocalDateTime.of(2026, 10, 20, 14, 32);

		// Parcial da Dra. Beatriz: só o atendimento dela é marcado
		gerador.registrarRemessa(List.of(b1), numeracao, quando);

		entityManager.clear();
		AtendimentoBPAi enviado = entityManager.find(AtendimentoBPAi.class, b1.getId());
		AtendimentoBPAi naoEnviado = entityManager.find(AtendimentoBPAi.class, a1.getId());

		assertThat(enviado.getFolha()).isEqualTo("2");
		assertThat(enviado.getLinhaFolha()).isEqualTo(1);
		assertThat(enviado.getRemessaEm()).isEqualTo(quando);
		assertThat(naoEnviado.getRemessaEm()).isNull();

		AtendimentoBPAiDTO naTabela = new AtendimentoBPAiDTO(enviado);
		assertThat(naTabela.getFolha()).isEqualTo("002");
		assertThat(naTabela.getLinhaFolha()).isEqualTo("01");
		assertThat(naTabela.getRemessa()).isEqualTo("20/10/2026 14:32");

		AtendimentoBPAiDTO semRemessa = new AtendimentoBPAiDTO(naoEnviado);
		assertThat(semRemessa.getFolha()).isEqualTo("—");
		assertThat(semRemessa.getLinhaFolha()).isEqualTo("—");
		assertThat(semRemessa.getRemessa()).isEqualTo("não enviada");
	}
}
