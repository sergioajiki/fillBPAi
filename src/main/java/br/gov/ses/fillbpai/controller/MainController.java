package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.ui.FileChooserService;
import br.gov.ses.fillbpai.ui.RelatorioController;
import jakarta.persistence.EntityManager;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Controller principal da aplicação.
 *
 * Responsável por:
 * - Criar botão de importação
 * - Acionar FileChooser
 * - Executar importação
 * - Atualizar tabela na mesma tela
 */
public class MainController {

    private final EntityManager entityManager;
    private final FileChooserService fileChooserService;
    private final RelatorioController relatorioController;
    private final BorderPane rootLayout;

    public MainController(EntityManager entityManager, BorderPane rootLayout) {
        this.entityManager = entityManager;
        this.fileChooserService = new FileChooserService();
        this.relatorioController = new RelatorioController();
        this.rootLayout = rootLayout;

        configurarLayout();
    }

    /**
     * Configura layout inicial da tela:
     * - Botão no topo
     * - Tabela no centro
     */
    private void configurarLayout() {

        // 🔹 Botão Importar
        Button btnImportar = new Button("Importar Planilha");

        btnImportar.setOnAction(event -> {
            Stage stage = (Stage) rootLayout.getScene().getWindow();
            importar(stage);
        });

        HBox topBar = new HBox(btnImportar);
        topBar.setPadding(new Insets(10));
        topBar.setSpacing(10);

        // 🔹 Insere botão no topo
        rootLayout.setTop(topBar);

        // 🔹 Insere tabela no centro
        rootLayout.setCenter(relatorioController.criarComponente());
    }

    /**
     * Executa processo de importação.
     */
    private void importar(Stage stage) {

        String caminho = fileChooserService.selecionarPlanilha(stage);

        if (caminho == null) {
            return;
        }

        AtendimentoImportacaoService service =
                new AtendimentoImportacaoService(entityManager);

        ImportacaoResultado resultado =
                service.importar(caminho);

        mostrarResumo(resultado);

        if (resultado.getTotalSucesso() > 0) {

            // Atualiza tabela
            relatorioController.atualizarDados(
                    resultado.getRegistrosImportados()
            );

        } else {
            mostrarErroImportacao();
        }
    }

    private void mostrarResumo(ImportacaoResultado resultado) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Resultado da Importação");
        alert.setHeaderText("Importação Finalizada");

        alert.setContentText(
                "Total processados: " + resultado.getTotalProcessados()
                        + "\nSucesso: " + resultado.getTotalSucesso()
                        + "\nErros: " + resultado.getTotalErro()
        );

        alert.showAndWait();
    }

    private void mostrarErroImportacao() {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erro na Importação");
        alert.setHeaderText("Não foi possível importar os dados.");
        alert.setContentText("Nenhum registro válido foi importado.");

        alert.showAndWait();
    }
}
