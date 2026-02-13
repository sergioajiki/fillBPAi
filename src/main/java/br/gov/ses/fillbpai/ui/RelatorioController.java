package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller responsável por exibir
 * SOMENTE os dados importados nesta execução.
 *
 * Não consulta banco.
 */
public class RelatorioController {

    private final List<AtendimentoBPAi> registrosImportados;

    /**
     * Recebe apenas os registros da execução atual.
     */
    public RelatorioController(List<AtendimentoBPAi> registrosImportados) {
        this.registrosImportados = registrosImportados;
    }

    public void exibirRelatorio() {

        Stage stage = new Stage();
        stage.setTitle("Relatório de Importação BPAi");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TableView<AtendimentoBPAiDTO> table = new TableView<>();

        adicionarColunas(table);
        carregarDados(table);

        Label totalLabel = new Label(
                "Total importado nesta execução: "
                        + registrosImportados.size()
        );

        VBox bottom = new VBox(10, totalLabel);
        bottom.setPadding(new Insets(10));

        root.setCenter(table);
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 1500, 600));
        stage.show();
    }

    private void adicionarColunas(TableView<AtendimentoBPAiDTO> table) {

        table.getColumns().addAll(
                criarColuna("Tipo Serviço", "tipoServico"),
                criarColuna("Data Agendamento", "dataAgendamento"),
                criarColuna("Hora Atendimento", "horaAtendimento"),
                criarColuna("Estabelecimento", "estabelecimento"),
                criarColuna("Especialidade Médico", "especialidadeMedico"),
                criarColuna("CPF Médico", "cpfMedico"),
                criarColuna("CBO Médico", "cboMedico"),
                criarColuna("Município", "municipio"),
                criarColuna("CPF Paciente", "cpfPaciente"),
                criarColuna("Paciente", "paciente"),
                criarColuna("CNS Paciente", "cnsPaciente"),
                criarColuna("Raça Paciente", "racaPaciente"),
                criarColuna("Data Nascimento", "dataNascimento"),
                criarColuna("CID Consulta", "cidConsulta"),
                criarColuna("Telefone", "telefone"),
                criarColuna("Tipo Zona", "tipoZona"),
                criarColuna("Endereço Completo", "enderecoCompleto")
        );
    }

    private TableColumn<AtendimentoBPAiDTO, String> criarColuna(
            String titulo, String propriedade) {

        TableColumn<AtendimentoBPAiDTO, String> coluna =
                new TableColumn<>(titulo);

        coluna.setCellValueFactory(data ->
                new SimpleStringProperty(
                        getValor(data.getValue(), propriedade)
                )
        );

        coluna.setPrefWidth(150);

        return coluna;
    }

    private String getValor(AtendimentoBPAiDTO dto, String propriedade) {

        try {
            return String.valueOf(
                    dto.getClass()
                            .getMethod("get" +
                                    propriedade.substring(0, 1).toUpperCase() +
                                    propriedade.substring(1))
                            .invoke(dto)
            );
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Agora usa APENAS os registros recebidos,
     * não consulta banco.
     */
    private void carregarDados(TableView<AtendimentoBPAiDTO> table) {

        if (registrosImportados == null ||
                registrosImportados.isEmpty()) {
            return;
        }

        DateTimeFormatter dataFormat =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter horaFormat =
                DateTimeFormatter.ofPattern("HH:mm");

        ObservableList<AtendimentoBPAiDTO> listaUI =
                FXCollections.observableArrayList();

        for (AtendimentoBPAi a : registrosImportados) {

            listaUI.add(new AtendimentoBPAiDTO(
                    a.getTipoServico(),
                    a.getDataAgendamento() != null ?
                            a.getDataAgendamento().format(dataFormat) : "",
                    a.getHoraAtendimento() != null ?
                            a.getHoraAtendimento().format(horaFormat) : "",
                    a.getEstabelecimento(),
                    a.getEspecialidadeMedico(),
                    a.getCpfMedico(),
                    a.getCboMedico(),
                    a.getMunicipio(),
                    a.getCpfPaciente(),
                    a.getPaciente(),
                    a.getCnsPaciente(),
                    a.getRacaPaciente(),
                    a.getDataNascimento() != null ?
                            a.getDataNascimento().format(dataFormat) : "",
                    a.getCidConsulta(),
                    a.getTelefone(),
                    a.getTipoZona(),
                    a.getEnderecoCompleto()
            ));
        }

        table.setItems(listaUI);
    }
}
