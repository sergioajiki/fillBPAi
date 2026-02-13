package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.ui.FileChooserService;
import br.gov.ses.fillbpai.ui.RelatorioController;
import jakarta.persistence.EntityManager;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Controller principal da aplicação.
 *
 * Responsável por:
 * - Acionar FileChooser
 * - Executar importação
 * - Exibir relatório da execução atual
 */
public class MainController {

    private final EntityManager entityManager;
    private final FileChooserService fileChooserService;

    public MainController(EntityManager entityManager) {
        this.entityManager = entityManager;
        this.fileChooserService = new FileChooserService();
    }

    public void importar(Stage stage) {

        String caminho = fileChooserService.selecionarPlanilha(stage);

        if (caminho == null) {
            return;
        }

        AtendimentoImportacaoService service =
                new AtendimentoImportacaoService(entityManager);

        ImportacaoResultado resultado =
                service.importar(caminho);

        mostrarResumo(resultado);

        // ✅ Agora passa SOMENTE os registros importados nesta execução
        if (resultado.getTotalSucesso() > 0) {

            RelatorioController relatorio =
                    new RelatorioController(
                            resultado.getRegistrosImportados()
                    );

            relatorio.exibirRelatorio();

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
