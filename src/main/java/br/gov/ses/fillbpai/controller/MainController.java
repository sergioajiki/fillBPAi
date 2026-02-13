
package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.ui.FileChooserService;
import jakarta.persistence.EntityManager;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

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

        mostrarResultado(resultado);
    }

    private void mostrarResultado(ImportacaoResultado resultado) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Resultado da Importação");
        alert.setHeaderText("Importação Finalizada");

        alert.setContentText(
                "Total: " + resultado.getTotalProcessados()
                        + "\nSucesso: " + resultado.getTotalSucesso()
                        + "\nErros: " + resultado.getTotalErro()
        );

        alert.showAndWait();
    }
}
