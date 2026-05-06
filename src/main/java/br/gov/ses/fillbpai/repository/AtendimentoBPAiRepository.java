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
     * Pré-carrega um mapa de folhas já atribuídas, agrupadas por
     * (especialidade | médico). A folha é vínculo permanente do par
     * especialidade+médico — independente do mês — e deve ser reutilizada
     * em todas as importações seguintes.
     *
     * Chave: "especialidade|medicoId"
     * Valor: número da folha já atribuída
     */
    public Map<String, String> buscarMapaFolhaPorEspecialidadeMedico() {

        List<AtendimentoBPAi> atendimentos = entityManager.createQuery(
                "SELECT a FROM AtendimentoBPAi a JOIN FETCH a.medico " +
                "WHERE a.folha IS NOT NULL",
                AtendimentoBPAi.class
        ).getResultList();

        Map<String, String> mapa = new HashMap<>();

        for (AtendimentoBPAi a : atendimentos) {
            String esp    = a.getEspecialidadeMedico() != null ? a.getEspecialidadeMedico() : "";
            Long medicoId = a.getMedico().getId();
            String folha  = a.getFolha();

            if (folha == null || folha.isBlank()) continue;

            String chave = esp + "|" + medicoId;
            mapa.putIfAbsent(chave, folha);
        }

        return mapa;
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
