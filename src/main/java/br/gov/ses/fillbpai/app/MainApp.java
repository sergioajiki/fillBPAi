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

            // Mensagem na tela: o programa instalado não tem janela de console
            javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alerta.setTitle("PonteBPA");
            if (DatabaseInitializer.isBancoEmUso(e)) {
                alerta.setHeaderText("O PonteBPA já está aberto");
                alerta.setContentText("O banco de dados está em uso por outra janela do PonteBPA. "
                        + "Feche a outra janela e abra o programa de novo.");
            } else {
                alerta.setHeaderText("Não foi possível abrir o banco de dados");
                alerta.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());
            }
            alerta.showAndWait();

            System.exit(1);
            return;
        }

        // 🔹 Layout raiz
        BorderPane root = new BorderPane();

        // 🔹 Controller principal
        new MainController(entityManager, root);

        // 🔹 Cena
        Scene scene = new Scene(root, 1500, 700);

        // Nome exibido do programa (20/10/2026): só o texto visível mudou —
        // pacotes, classes e o projeto Maven continuam como fillbpai
        primaryStage.setTitle("PonteBPA");
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