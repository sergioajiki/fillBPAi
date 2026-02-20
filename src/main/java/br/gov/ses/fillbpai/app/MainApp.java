package br.gov.ses.fillbpai.app;

import br.gov.ses.fillbpai.config.DatabaseInitializer;
import br.gov.ses.fillbpai.controller.MainController;
import jakarta.persistence.EntityManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Classe principal da aplicação JavaFX.
 *
 * Responsável por:
 * - Inicializar infraestrutura do banco
 * - Criar layout principal
 * - Encerrar recursos corretamente
 */
public class MainApp extends Application {

    private DatabaseInitializer databaseInitializer;
    private EntityManager entityManager;

    @Override
    public void start(Stage primaryStage) {

        try {

            // 🔹 Inicializa infraestrutura
            databaseInitializer = new DatabaseInitializer();
            databaseInitializer.iniciar();

            // 🔹 Obtém EntityManager ativo
            entityManager = databaseInitializer.getEntityManager();

        } catch (Exception e) {

            System.err.println("Erro ao iniciar aplicação:");
            e.printStackTrace();
            System.exit(1);
            return;
        }

        // 🔹 Layout raiz
        BorderPane root = new BorderPane();

        // 🔹 Controller principal
        new MainController(entityManager, root);

        // 🔹 Cena
        Scene scene = new Scene(root, 1500, 700);

        primaryStage.setTitle("Importador BPAi");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {

        if (databaseInitializer != null) {
            databaseInitializer.finalizar();
        }

        System.out.println("Aplicação finalizada com sucesso.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}