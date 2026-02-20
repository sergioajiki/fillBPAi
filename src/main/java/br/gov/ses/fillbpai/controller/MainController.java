package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.ui.FileChooserService;
import br.gov.ses.fillbpai.ui.RelatorioController;
import jakarta.persistence.EntityManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller principal da aplicação.
 *
 * ✔ Botão importar
 * ✔ Atualiza tabela
 * ✔ Exibe log detalhado
 */
public class MainController {

    private final EntityManager entityManager;
    private final FileChooserService fileChooserService;
    private final RelatorioController relatorioController;
    private final BorderPane rootLayout;

    public MainController(EntityManager entityManager, BorderPane rootLayout) {

        this.entityManager = entityManager;
        this.fileChooserService = new FileChooserService();

        // ✅ AGORA PASSA O ENTITY MANAGER
        this.relatorioController = new RelatorioController(entityManager);

        this.rootLayout = rootLayout;

        configurarLayout();
    }

    private void configurarLayout() {

        Button btnImportar = new Button("Importar Planilha");

        btnImportar.setOnAction(event -> {
            Stage stage = (Stage) rootLayout.getScene().getWindow();
            importar(stage);
        });

        HBox topBar = new HBox(10, btnImportar);
        topBar.setPadding(new Insets(10));

        rootLayout.setTop(topBar);
        rootLayout.setCenter(relatorioController.criarComponente());
    }

    private void importar(Stage stage) {

        String caminho = fileChooserService.selecionarPlanilha(stage);

        if (caminho == null) {
            return;
        }

        AtendimentoImportacaoService service =
                new AtendimentoImportacaoService(entityManager);

        ImportacaoResultado resultado =
                service.importar(caminho);

        mostrarResumoComLog(resultado);

        if (resultado.getTotalSucesso() > 0) {

            relatorioController.atualizarDados(
                    resultado.getRegistrosImportados()
            );
        }
    }

    private void mostrarResumoComLog(ImportacaoResultado resultado) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Resultado da Importação");
        alert.setHeaderText("Importação Finalizada");

        Label resumo = new Label(
                "Total processados: " + resultado.getTotalProcessados()
                        + "\nSucesso: " + resultado.getTotalSucesso()
                        + "\nErros: " + resultado.getTotalErro()
        );

        TextArea areaLog = new TextArea();
        areaLog.setEditable(false);
        areaLog.setWrapText(true);
        areaLog.setPrefHeight(200);

        if (!resultado.getErros().isEmpty()) {

            StringBuilder sb = new StringBuilder();

            for (String erro : resultado.getErros()) {
                sb.append(erro).append("\n");
            }

            areaLog.setText(sb.toString());
        } else {
            areaLog.setText("Nenhum erro encontrado.");
        }

        VBox layout = new VBox(10, resumo, new Label("Log de Erros:"), areaLog);
        layout.setPadding(new Insets(10));

        alert.getDialogPane().setContent(layout);

        alert.showAndWait();
    }
}