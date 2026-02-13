package br.gov.ses.fillbpai.app;

import br.gov.ses.fillbpai.config.DatabaseInitializer;
import br.gov.ses.fillbpai.controller.MainController;
import jakarta.persistence.EntityManager;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Classe principal da aplicação.
 *
 * Responsável apenas por:
 * - Inicializar JavaFX
 * - Montar a interface
 * - Delegar responsabilidades
 */
public class MainApp extends Application {

    private DatabaseInitializer databaseInitializer;

    @Override
    public void start(Stage primaryStage) {

        // 1️⃣ Inicializa infraestrutura
        databaseInitializer = new DatabaseInitializer();
        databaseInitializer.iniciar();

        EntityManager entityManager =
                databaseInitializer.getEntityManager();

        // 2️⃣ Cria controller
        MainController controller =
                new MainController(entityManager);

        // 3️⃣ Interface simples com botão
        Button btnImportar = new Button("Importar Planilha BPAi");

        btnImportar.setOnAction(e ->
                controller.importar(primaryStage)
        );

        StackPane root = new StackPane(btnImportar);

        Scene scene = new Scene(root, 600, 400);

        primaryStage.setTitle("fillBPAi");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {

        if (databaseInitializer != null) {
            databaseInitializer.finalizar();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
