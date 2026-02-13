package br.gov.ses.fillbpai.app;

import br.gov.ses.fillbpai.controller.MainController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Classe principal da aplicação JavaFX.
 *
 * Responsável por:
 * - Inicializar JPA
 * - Criar layout raiz
 * - Instanciar MainController
 * - Iniciar aplicação
 */
public class MainApp extends Application {

    private EntityManagerFactory emf;
    private EntityManager entityManager;

    @Override
    public void start(Stage primaryStage) {

        // 🔹 Inicializa JPA
        emf = Persistence.createEntityManagerFactory("bpaPU");
        entityManager = emf.createEntityManager();

        // 🔹 Cria layout raiz
        BorderPane root = new BorderPane();

        // 🔹 Instancia controller principal
        MainController controller =
                new MainController(entityManager, root);

        // 🔹 Cria cena usando o MESMO root
        Scene scene = new Scene(root, 1500, 700);

        primaryStage.setTitle("Importador BPAi");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {

        // 🔹 Fecha recursos JPA ao encerrar aplicação
        if (entityManager != null) {
            entityManager.close();
        }

        if (emf != null) {
            emf.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
