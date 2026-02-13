package br.gov.ses.fillbpai.ui;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Serviço responsável exclusivamente por abrir diálogos
 * de seleção de arquivos.
 *
 * Não contém regra de negócio.
 */
public class FileChooserService {

    /**
     * Abre diálogo para selecionar planilha Excel.
     *
     * @param stage Stage da aplicação
     * @return caminho absoluto ou null se cancelado
     */
    public String selecionarPlanilha(Stage stage) {

        FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Selecionar Planilha BPAi");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Arquivos Excel (*.xlsx)", "*.xlsx"
                )
        );

        File arquivo = fileChooser.showOpenDialog(stage);

        if (arquivo != null) {
            return arquivo.getAbsolutePath();
        }

        return null;
    }
}

