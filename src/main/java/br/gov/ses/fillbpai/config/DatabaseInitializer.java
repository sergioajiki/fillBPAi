package br.gov.ses.fillbpai.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.h2.tools.Server;

/**
 * Responsável por inicializar e encerrar
 * a infraestrutura de banco de dados.
 *
 * Arquitetura adotada:
 *
 * Aplicação (Hibernate)
 *        ↓
 *   H2 FILE + AUTO_SERVER
 *        ↓
 *   Arquivo físico (.mv.db)
 *
 * O AUTO_SERVER permite que o console
 * conecte simultaneamente ao banco.
 */
public class DatabaseInitializer {

    private Server webServer; // Console H2
    private EntityManagerFactory emf;
    private EntityManager entityManager;

    /**
     * Inicializa:
     * - H2 Console
     * - JPA/Hibernate
     */
    public void iniciar() {

        try {

            // 🔹 Inicia apenas o console web do H2
            webServer = Server.createWebServer(
                    "-web",
                    "-webAllowOthers",
                    "-webPort", "8082"
            ).start();

            System.out.println("H2 Console iniciado em: " + webServer.getURL());

        } catch (Exception e) {
            System.err.println("Erro ao iniciar H2 Console:");
            e.printStackTrace();
        }

        try {

            // 🔹 Inicializa JPA/Hibernate
            emf = Persistence.createEntityManagerFactory("bpaPU");
            entityManager = emf.createEntityManager();

            System.out.println("JPA/Hibernate iniciado com sucesso!");

        } catch (Exception e) {

            System.err.println("Erro ao inicializar JPA:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * Encerra todos os recursos corretamente.
     */
    public void finalizar() {

        try {

            if (entityManager != null && entityManager.isOpen()) {
                entityManager.close();
                System.out.println("EntityManager fechado.");
            }

            if (emf != null && emf.isOpen()) {
                emf.close();
                System.out.println("EntityManagerFactory fechada.");
            }

            if (webServer != null) {
                webServer.stop();
                System.out.println("H2 Console finalizado.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Infraestrutura encerrada com sucesso.");
    }
}