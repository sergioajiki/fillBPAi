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

        // Erros inesperados (ex.: ao abrir uma janela) vão para logs\erros_programa.log
        // e aparecem numa mensagem — o programa instalado não tem console (21/10/2026)
        RegistroErros registroErros = new RegistroErros(
                br.gov.ses.fillbpai.config.PastasApp.atual().logs());
        Thread.UncaughtExceptionHandler tratador = (thread, erro) -> tratarErroInesperado(registroErros, erro);
        Thread.setDefaultUncaughtExceptionHandler(tratador);
        Thread.currentThread().setUncaughtExceptionHandler(tratador);

        try {

            // 🔹 Inicializa infraestrutura
            databaseInitializer = new DatabaseInitializer();
            databaseInitializer.iniciar();

            // 🔹 Obtém EntityManager ativo
            entityManager = databaseInitializer.getEntityManager();

        } catch (Exception e) {

            System.err.println("Erro ao iniciar aplicação:");
            e.printStackTrace();
            registroErros.registrar("abertura do banco", e);

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
        // Ícone da janela e da barra de tarefas (21/10/2026) — o mesmo do PonteBPA.exe
        for (int tamanho : new int[] { 16, 32, 48, 256 }) {
            java.io.InputStream icone = MainApp.class.getResourceAsStream("/icones/pontebpa-" + tamanho + ".png");
            if (icone != null) {
                primaryStage.getIcons().add(new javafx.scene.image.Image(icone));
            }
        }
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Erro não tratado em qualquer ponto do programa: grava no registro de
     * erros e avisa o usuário, com o caminho do arquivo para pedir ajuda.
     */
    private static void tratarErroInesperado(RegistroErros registro, Throwable erro) {

        erro.printStackTrace();
        boolean gravou = registro.registrar("erro inesperado", erro);

        Runnable mostrar = () -> {
            javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alerta.setTitle("PonteBPA");
            alerta.setHeaderText("Ocorreu um erro inesperado");
            alerta.setContentText((erro.getMessage() != null ? erro.getMessage() : erro.toString())
                    + (gravou ? "\n\nDetalhes gravados em:\n" + registro.arquivo().toAbsolutePath()
                            + "\nEnvie esse arquivo ao pedir ajuda." : ""));
            alerta.showAndWait();
        };

        if (javafx.application.Platform.isFxApplicationThread()) {
            mostrar.run();
        } else {
            javafx.application.Platform.runLater(mostrar);
        }
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