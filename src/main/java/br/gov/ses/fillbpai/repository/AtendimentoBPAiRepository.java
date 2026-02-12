package br.gov.ses.fillbpai.repository;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

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

        EntityTransaction transaction = entityManager.getTransaction();

        try {
            transaction.begin();

            entityManager.persist(atendimento);

            transaction.commit();

        } catch (Exception e) {

            if (transaction.isActive()) {
                transaction.rollback();
            }

            throw e;
        }
    }
}
