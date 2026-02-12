package br.gov.ses.fillbpai.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;
import br.gov.ses.fillbpai.util.HibernateUtil;

import org.h2.tools.Server;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Classe principal da aplicação JavaFX.
 *
 * Responsável por:
 * - Inicializar infraestrutura (H2 + Hibernate)
 * - Iniciar a interface JavaFX
 * - Encerrar recursos corretamente ao fechar o sistema
 */
public class MainApp extends Application {

    /**
     * Instância do servidor Web do H2 (Console).
     */
    private Server h2Server;

    /**
     * Inicializa o H2 Console programaticamente.
     * Permite acessar via navegador:
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
     * Método chamado automaticamente pelo JavaFX
     * após a inicialização da aplicação.
     */
    @Override
    public void start(Stage primaryStage) {

        /*
         * 1️⃣ Inicializa infraestrutura antes da interface
         */
        startH2Console();                       // Inicia console do H2
        HibernateUtil.getSessionFactory();      // Inicializa Hibernate

        System.out.println("Hibernate iniciado com sucesso!");

        /*
         * 2️⃣ Teste de persistência (temporário para validação)
         */
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
        atendimento.setDataNascimento("1990-01-01"); // Ainda como String
        atendimento.setCidConsulta("E11");
        atendimento.setTelefone("67999999999");
        atendimento.setTipoZona("Urbana");
        atendimento.setEnderecoCompleto("Rua Teste, 123");

        new AtendimentoBPAiRepository().salvar(atendimento);

        System.out.println("Registro salvo com sucesso!");

        /*
         * 3️⃣ Construção da Interface Gráfica
         */

        Label label = new Label("fillBPAi - Aplicação iniciada com sucesso!");

        StackPane root = new StackPane(label);

        Scene scene = new Scene(root, 600, 400);

        primaryStage.setTitle("fillBPAi");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Método chamado automaticamente ao fechar a aplicação.
     * Responsável por liberar recursos.
     */
    @Override
    public void stop() {

        if (h2Server != null) {
            h2Server.stop();
            System.out.println("H2 Console finalizado.");
        }

        HibernateUtil.shutdown();
        System.out.println("Hibernate finalizado.");
    }

    /**
     * Método main tradicional.
     * Responsável por iniciar o JavaFX.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
