package br.gov.ses.fillbpai.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.util.HibernateUtil;

import jakarta.persistence.EntityManager;
import org.h2.tools.Server;

/**
 * Classe principal da aplicação JavaFX.
 *
 * Responsável por:
 * - Inicializar infraestrutura (H2 + Hibernate)
 * - Executar importação de arquivo Excel
 * - Iniciar interface JavaFX
 * - Encerrar recursos corretamente
 */
public class MainApp extends Application {

    /**
     * Servidor Web do H2 (Console)
     */
    private Server h2Server;

    /**
     * EntityManager compartilhado durante a execução
     */
    private EntityManager entityManager;

    /**
     * Inicia o console web do H2 programaticamente.
     * Acesso via navegador:
     * http://localhost:8082
     */
    private void startH2Console() {
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
    }

    /**
     * Método executado automaticamente após o launch().
     */
    @Override
    public void start(Stage primaryStage) {

        /*
         * 1️⃣ Inicializa infraestrutura
         */
        startH2Console(); // Inicia console H2

        entityManager = HibernateUtil
                .getSessionFactory()
                .createEntityManager();

        System.out.println("Hibernate iniciado com sucesso!");

        /*
         * 2️⃣ Executa importação do arquivo Excel
         *
         * ⚠ Aqui você pode depois substituir por
         * botão na interface gráfica.
         */
        executarImportacao();

        /*
         * 3️⃣ Construção da interface gráfica básica
         */
        Label label = new Label("fillBPAi - Sistema iniciado com sucesso!");

        StackPane root = new StackPane(label);

        Scene scene = new Scene(root, 600, 400);

        primaryStage.setTitle("fillBPAi");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Executa a importação e imprime relatório no console.
     */
    private void executarImportacao() {

        try {

            String caminhoArquivo = "data/bpai.xlsx";

            AtendimentoImportacaoService service =
                    new AtendimentoImportacaoService(entityManager);

            ImportacaoResultado resultado =
                    service.importar(caminhoArquivo);

            /*
             * Relatório final da importação
             */
            System.out.println("=================================");
            System.out.println("RESULTADO DA IMPORTAÇÃO");
            System.out.println("Total processados: " + resultado.getTotalProcessados());
            System.out.println("Sucesso: " + resultado.getTotalSucesso());
            System.out.println("Erros: " + resultado.getTotalErro());

            if (!resultado.getErros().isEmpty()) {
                System.out.println("Detalhes dos erros:");
                resultado.getErros().forEach(System.out::println);
            }

            System.out.println("=================================");

        } catch (Exception e) {
            System.err.println("Erro geral na importação: " + e.getMessage());
        }
    }

    /**
     * Executado automaticamente ao fechar a aplicação.
     * Libera recursos corretamente.
     */
    @Override
    public void stop() {

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

    /**
     * Método main tradicional.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
