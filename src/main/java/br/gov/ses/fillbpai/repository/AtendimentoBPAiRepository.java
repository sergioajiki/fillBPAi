package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Medico;
import br.gov.ses.fillbpai.model.Paciente;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * paciente + médico + data de agendamento + código SIGTAP + horário.
     *
     * Utilizado para evitar duplicatas na reimportação de planilhas.
     *
     * @param paciente        paciente do atendimento
     * @param medico          médico do atendimento
     * @param dataAgendamento data do atendimento
     * @param sigtap          código SIGTAP do procedimento
     * @param hora            horário do atendimento ({@code null} casa só com registro sem horário)
     * @return Optional com o atendimento existente, ou vazio se não encontrado
     */
    public Optional<AtendimentoBPAi> buscarDuplicata(
            Paciente paciente, Medico medico,
            LocalDate dataAgendamento, String sigtap, java.time.LocalTime hora) {

        // Horário faz parte da chave: mesmo paciente/médico/data/procedimento
        // com horário diferente é outro atendimento (importado separado, com
        // aviso de suspeita). Sem horário casa só com registro sem horário.
        return buscarDuplicata(paciente, medico, dataAgendamento, sigtap).stream()
                .filter(a -> java.util.Objects.equals(a.getHoraAtendimento(), hora))
                .findFirst();
    }

    /** Todos os atendimentos com a chave sem o horário — ver a versão com horário. */
    private List<AtendimentoBPAi> buscarDuplicata(
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

        return resultados;
    }

}
