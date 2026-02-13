package br.gov.ses.fillbpai.config;

import br.gov.ses.fillbpai.util.HibernateUtil;
import jakarta.persistence.EntityManager;
import org.h2.tools.Server;

/**
 * Responsável por inicializar e encerrar
 * infraestrutura de banco de dados.
 */
public class DatabaseInitializer {

    private Server h2Server;
    private EntityManager entityManager;

    public void iniciar() {

        try {
            h2Server = Server.createWebServer(
                    "-web",
                    "-webAllowOthers",
                    "-webPort", "8082"
            ).start();

            System.out.println("H2 Console iniciado em: " + h2Server.getURL());

        } catch (Exception e) {
            System.err.println("Erro ao iniciar H2 Console: " + e.getMessage());
        }

        entityManager = HibernateUtil
                .getSessionFactory()
                .createEntityManager();

        System.out.println("Hibernate iniciado com sucesso!");
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void finalizar() {

        if (entityManager != null && entityManager.isOpen()) {
            entityManager.close();
            System.out.println("EntityManager fechado.");
        }

        if (h2Server != null) {
            h2Server.stop();
            System.out.println("H2 Console finalizado.");
        }

        HibernateUtil.shutdown();
        System.out.println("Hibernate finalizado.");
    }
}

