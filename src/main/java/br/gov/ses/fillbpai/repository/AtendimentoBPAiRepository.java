package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Medico;
import br.gov.ses.fillbpai.model.Paciente;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Camada responsável apenas por persistência.
 * Não contém regra de negócio.
 */
public class AtendimentoBPAiRepository {

    private final EntityManager entityManager;

    public AtendimentoBPAiRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void salvar(AtendimentoBPAi atendimento) {
            entityManager.persist(atendimento);
    }

    public List<AtendimentoBPAi> buscarTodos() {
        return entityManager
                .createQuery("FROM AtendimentoBPAi", AtendimentoBPAi.class)
                .getResultList();
    }

    /**
     * Busca atendimento existente pela chave natural de negócio:
     * paciente + médico + data de agendamento + código SIGTAP.
     *
     * Utilizado para evitar duplicatas na reimportação de planilhas.
     *
     * @param paciente        paciente do atendimento
     * @param medico          médico do atendimento
     * @param dataAgendamento data do atendimento
     * @param sigtap          código SIGTAP do procedimento
     * @return Optional com o atendimento existente, ou vazio se não encontrado
     */
    public Optional<AtendimentoBPAi> buscarDuplicata(
            Paciente paciente, Medico medico,
            LocalDate dataAgendamento, String sigtap) {

        List<AtendimentoBPAi> resultados = entityManager
                .createQuery(
                        "FROM AtendimentoBPAi a "
                                + "WHERE a.paciente = :paciente "
                                + "AND a.medico = :medico "
                                + "AND a.dataAgendamento = :data "
                                + "AND a.sigtap = :sigtap",
                        AtendimentoBPAi.class)
                .setParameter("paciente", paciente)
                .setParameter("medico", medico)
                .setParameter("data", dataAgendamento)
                .setParameter("sigtap", sigtap)
                .getResultList();

        return resultados.isEmpty()
                ? Optional.empty()
                : Optional.of(resultados.get(0));
    }

}
