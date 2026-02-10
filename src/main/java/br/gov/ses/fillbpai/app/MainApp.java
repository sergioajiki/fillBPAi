package br.gov.ses.fillbpai.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import br.gov.ses.fillbpai.util.HibernateUtil;

/**
 * Classe principal da aplicação JavaFX.
 *
 * Responsável por inicializar o JavaFX e abrir a janela principal.
 */
public class MainApp extends Application {

    /**
     * Método chamado automaticamente pelo JavaFX
     * após a inicialização da aplicação.
     */
    @Override
    public void start(Stage primaryStage) {

        // 1. Componente simples para teste visual
        Label label = new Label("fillBPAi - Aplicação iniciada com sucesso!");

        // 2. Layout básico
        StackPane root = new StackPane(label);

        // 3. Cena (conteúdo da janela)
        Scene scene = new Scene(root, 600, 400);

        // 4. Configuração da janela
        primaryStage.setTitle("fillBPAi");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Teste de inicialização do Hibernate
        HibernateUtil.getSessionFactory();
        System.out.println("Hibernate iniciado com sucesso!");

    }

    /**
     * Método main tradicional.
     * Responsável por iniciar o JavaFX.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
