package br.gov.ses.fillbpai.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;
import br.gov.ses.fillbpai.util.HibernateUtil;

import java.time.LocalDate;
import java.time.LocalTime;

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

        AtendimentoBPAi atendimento = new AtendimentoBPAi();
        atendimento.setTipoServico("Consulta");
        atendimento.setDataAgendamento(LocalDate.now());
        atendimento.setHoraAtendimento(LocalTime.now());
        atendimento.setEstabelecimento("SSD");
        atendimento.setEspecialidadeMedico("Endocrinologia");
        atendimento.setCpfMedico("12345678900");
        atendimento.setCboMedico("225142");
        atendimento.setMunicipio("Campo Grande");
        atendimento.setCpfPaciente("98765432100");
        atendimento.setPaciente("Paciente Teste");
        atendimento.setCnsPaciente("123456789012345");
        atendimento.setRacaPaciente("Branca");
        atendimento.setDataNascimento("1990-01-01");
        atendimento.setCidConsulta("E11");
        atendimento.setTelefone("67999999999");
        atendimento.setTipoZona("Urbana");
        atendimento.setEnderecoCompleto("Rua Teste, 123");

        new AtendimentoBPAiRepository().salvar(atendimento);

        System.out.println("Registro salvo com sucesso!");

    }



    /**
     * Método main tradicional.
     * Responsável por iniciar o JavaFX.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
