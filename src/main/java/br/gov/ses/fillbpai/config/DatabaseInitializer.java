package br.gov.ses.fillbpai.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Abre e fecha o banco de dados (H2 em arquivo, via JPA/Hibernate).
 * <p>
 * Desde 21/10/2026: o banco fica na pasta de dados do programa
 * ({@link PastasApp} — no programa instalado, {@code %LOCALAPPDATA%\PonteBPA\database}),
 * com senha guardada fora do código ({@link SenhaBanco}). O console web do
 * H2 (porta 8082, aberto a outros computadores, com {@code sa} sem senha) e o
 * {@code AUTO_SERVER} (porta para um segundo programa usar o mesmo banco)
 * foram retirados: só o PonteBPA abre o banco. Abrir o programa duas vezes
 * dá o aviso de banco em uso ({@link #isBancoEmUso}).
 */
public class DatabaseInitializer {

    /** Código de erro do H2 para banco já aberto por outro programa. */
    private static final int ERRO_BANCO_EM_USO = 90020;

    private EntityManagerFactory emf;
    private EntityManager entityManager;

    /**
     * Prepara as pastas, obtém a senha e abre o banco. Erros sobem para quem
     * chamou ({@code MainApp}), que mostra a mensagem ao usuário.
     */
    public void iniciar() throws Exception {

        PastasApp pastas = PastasApp.atual();
        pastas.prepararPastas();

        String senha = SenhaBanco.obter(pastas.config(), pastas.database());

        // URL, usuário e senha definidos aqui, sobre o persistence.xml
        Map<String, Object> conexao = new HashMap<>();
        conexao.put("jakarta.persistence.jdbc.url", SenhaBanco.url(pastas.database()));
        conexao.put("jakarta.persistence.jdbc.user", SenhaBanco.USUARIO);
        conexao.put("jakarta.persistence.jdbc.password", senha);

        emf = Persistence.createEntityManagerFactory("bpaPU", conexao);
        entityManager = emf.createEntityManager();

        System.out.println("Banco aberto em " + pastas.database().toAbsolutePath());
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    /**
     * {@code true} se o erro (ou alguma causa dele) é o de banco já aberto por
     * outro programa. O H2 avisa de dois jeitos: o código 90020 ("Database may
     * be already in use") ou, no formato de arquivo atual, "The file is locked"
     * dentro de um erro geral — visto no teste do pacote em 21/10/2026, com o
     * programa aberto duas vezes.
     */
    public static boolean isBancoEmUso(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getErrorCode() == ERRO_BANCO_EM_USO) {
                return true;
            }
            String mensagem = t.getMessage();
            if (mensagem != null && (mensagem.contains(String.valueOf(ERRO_BANCO_EM_USO))
                    || mensagem.contains("The file is locked")
                    || mensagem.contains("already in use"))) {
                return true;
            }
        }
        return false;
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

        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Infraestrutura encerrada com sucesso.");
    }
}
