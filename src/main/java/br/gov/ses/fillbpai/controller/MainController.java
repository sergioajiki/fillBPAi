package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.ui.FileChooserService;
import br.gov.ses.fillbpai.ui.PreCadastroController;
import br.gov.ses.fillbpai.ui.RelatorioController;
import jakarta.persistence.EntityManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainController {

    private final EntityManager entityManager;
    private final FileChooserService fileChooserService;
    private final RelatorioController relatorioController;
    private final BorderPane rootLayout;

    public MainController(EntityManager entityManager, BorderPane rootLayout) {

        this.entityManager = entityManager;
        this.fileChooserService = new FileChooserService();
        this.relatorioController = new RelatorioController(entityManager);
        this.rootLayout = rootLayout;

        configurarLayout();

        // Carrega registros já existentes no banco
        relatorioController.carregarDoBanco();
    }

    private void configurarLayout() {

        Button btnImportar = new Button("Importar Planilha");

        btnImportar.setOnAction(event -> {
            Stage stage = (Stage) rootLayout.getScene().getWindow();
            importar(stage);
        });

        Button btnPreCadastro = new Button("Pré-Cadastro CNS");

        btnPreCadastro.setOnAction(event -> {
            Stage stage = (Stage) rootLayout.getScene().getWindow();
            new PreCadastroController().abrirDialog(stage);
        });

        HBox topBar = new HBox(10, btnImportar, btnPreCadastro);
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

        // 🔥 Recarrega sempre do banco (fonte oficial)
        if (resultado.getTotalSucesso() > 0) {
            relatorioController.carregarDoBanco();
        }
    }

    /**
     * Exibe um diálogo com o resumo da importação.
     *
     * O log é dividido em duas seções:
     * - ERROS: linhas que NÃO foram importadas (ex: campos obrigatórios ausentes)
     * - AVISOS: linhas importadas com sucesso, mas com dados atípicos (ex: CNS legado)
     *
     * Isso permite ao usuário identificar rapidamente problemas
     * e tomar ações corretivas quando necessário.
     */
    private void mostrarResumoComLog(ImportacaoResultado resultado) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Resultado da Importação");
        alert.setHeaderText("Importação Finalizada");

        // Resumo numérico: processados, sucesso, erros, avisos
        Label resumo = new Label(
                "Total processados: " + resultado.getTotalProcessados()
                        + "\nSucesso: " + resultado.getTotalSucesso()
                        + "\nErros: " + resultado.getTotalErro()
                        + "\nAvisos: " + resultado.getTotalAvisos()
        );

        TextArea areaLog = new TextArea();
        areaLog.setEditable(false);
        areaLog.setWrapText(true);
        areaLog.setPrefHeight(300);

        StringBuilder sb = new StringBuilder();

        // Seção de erros — linhas que não foram importadas
        if (!resultado.getErros().isEmpty()) {
            sb.append("=== ERROS (linhas não importadas) ===\n");
            for (String erro : resultado.getErros()) {
                sb.append(erro).append("\n");
            }
        }

        // Seção de avisos — linhas importadas com ressalvas
        if (!resultado.getAvisos().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("=== AVISOS (linhas importadas com ressalvas) ===\n");
            for (String aviso : resultado.getAvisos()) {
                sb.append(aviso).append("\n");
            }
        }

        // Se não houver erros nem avisos
        if (sb.length() == 0) {
            sb.append("Importação concluída sem erros ou avisos.");
        }

        areaLog.setText(sb.toString());

        VBox layout = new VBox(10, resumo, new Label("Log de Importação:"), areaLog);
        layout.setPadding(new Insets(10));

        alert.getDialogPane().setContent(layout);
        alert.showAndWait();
    }
}