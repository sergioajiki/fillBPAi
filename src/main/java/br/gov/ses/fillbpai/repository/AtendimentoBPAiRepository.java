package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;

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

}
