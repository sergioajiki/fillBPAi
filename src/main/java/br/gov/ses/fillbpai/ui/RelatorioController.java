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
 * 🔹 Não consulta banco
 * 🔹 Não contém regra de negócio
 * 🔹 Apenas monta a tela e exibe dados recebidos
 */
public class RelatorioController {

    /**
     * Lista contendo apenas os registros
     * importados na execução atual.
     */
    private final List<AtendimentoBPAi> registrosImportados;

    /**
     * Construtor recebe apenas os registros
     * da execução atual.
     *
     * Isso garante que:
     * ✔ Não exibimos o banco inteiro
     * ✔ A tela é independente do repositório
     */
    public RelatorioController(List<AtendimentoBPAi> registrosImportados) {
        this.registrosImportados = registrosImportados;
    }

    /**
     * Método principal que cria e exibe a janela
     * do relatório.
     */
    public void exibirRelatorio() {

        Stage stage = new Stage();
        stage.setTitle("Relatório de Importação BPAi");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        TableView<AtendimentoBPAiDTO> table = new TableView<>();

        // Criação dinâmica das colunas
        adicionarColunas(table);

        // Carrega apenas dados recebidos
        carregarDados(table);

        // Label com total importado nesta execução
        Label totalLabel = new Label(
                "Total importado nesta execução: "
                        + (registrosImportados != null
                        ? registrosImportados.size()
                        : 0)
        );

        VBox bottom = new VBox(10, totalLabel);
        bottom.setPadding(new Insets(10));

        root.setCenter(table);
        root.setBottom(bottom);

        stage.setScene(new Scene(root, 1500, 600));
        stage.show();
    }

    /**
     * Cria todas as colunas da tabela.
     *
     * Cada coluna mapeia para uma propriedade do DTO.
     */
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

    /**
     * Método genérico para criar uma coluna.
     *
     * Utiliza reflection para buscar dinamicamente
     * o getter correspondente no DTO.
     *
     * Isso evita repetição de código.
     */
    private TableColumn<AtendimentoBPAiDTO, String> criarColuna(
            String titulo,
            String propriedade
    ) {

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

    /**
     * Usa reflection para acessar dinamicamente
     * o getter da propriedade informada.
     *
     * Exemplo:
     * propriedade = "paciente"
     * chama -> getPaciente()
     */
    private String getValor(AtendimentoBPAiDTO dto, String propriedade) {

        try {
            Object valor = dto.getClass()
                    .getMethod("get" +
                            propriedade.substring(0, 1).toUpperCase() +
                            propriedade.substring(1))
                    .invoke(dto);

            return valor != null ? valor.toString() : "";

        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Carrega dados apenas da lista recebida
     * na importação.
     *
     * NÃO consulta banco.
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

            /*
             * Remonta o estabelecimento para exibição
             * no formato original da planilha:
             *
             * "123456 - HOSPITAL MUNICIPAL"
             */
            String estabelecimentoFormatado =
                    (a.getCodEstabelecimento() != null
                            ? a.getCodEstabelecimento() + " - "
                            : "")
                            + (a.getEstabelecimento() != null
                            ? a.getEstabelecimento()
                            : "");

            listaUI.add(new AtendimentoBPAiDTO(
                    a.getTipoServico(),
                    a.getDataAgendamento() != null
                            ? a.getDataAgendamento().format(dataFormat)
                            : "",
                    a.getHoraAtendimento() != null
                            ? a.getHoraAtendimento().format(horaFormat)
                            : "",
                    estabelecimentoFormatado,
                    a.getEspecialidadeMedico(),
                    a.getCpfMedico(),
                    a.getCboMedico(),
                    a.getMunicipio(),
                    a.getCpfPaciente(),
                    a.getPaciente(),
                    a.getCnsPaciente(),
                    a.getRacaPaciente(),
                    a.getDataNascimento() != null
                            ? a.getDataNascimento().format(dataFormat)
                            : "",
                    a.getCidConsulta(),
                    a.getTelefone(),
                    a.getTipoZona(),
                    a.getEnderecoCompleto()
            ));
        }

        table.setItems(listaUI);
    }
}
