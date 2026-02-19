package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Controller responsável pela TABELA DE RESULTADO.
 *
 * ✔ CNES separado do estabelecimento
 * ✔ Especialidade separada do médico
 * ✔ Permite filtro por especialidade
 * ✔ Mantém ordenação por clique
 */
public class RelatorioController {

    private TableView<AtendimentoBPAiDTO> table;
    private ComboBox<String> comboEspecialidade;

    private ObservableList<AtendimentoBPAiDTO> listaBase;
    private FilteredList<AtendimentoBPAiDTO> listaFiltrada;
    private SortedList<AtendimentoBPAiDTO> listaOrdenada;

    public BorderPane criarComponente() {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        table = new TableView<>();

        comboEspecialidade = new ComboBox<>();
        comboEspecialidade.setPromptText("Selecionar Especialidade");
        comboEspecialidade.setPrefWidth(300);

        listaBase = FXCollections.observableArrayList();
        listaFiltrada = new FilteredList<>(listaBase, p -> true);
        listaOrdenada = new SortedList<>(listaFiltrada);

        listaOrdenada.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(listaOrdenada);

        adicionarColunas(table);

        comboEspecialidade.setOnAction(e -> aplicarFiltro());

        HBox topBar = new HBox(10, comboEspecialidade);
        topBar.setPadding(new Insets(10));

        root.setTop(topBar);
        root.setCenter(table);

        return root;
    }

    public void atualizarDados(java.util.List<AtendimentoBPAi> registrosImportados) {

        listaBase.clear();

        if (registrosImportados == null || registrosImportados.isEmpty()) {
            return;
        }

        DateTimeFormatter dataFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter horaFormat = DateTimeFormatter.ofPattern("HH:mm");

        for (AtendimentoBPAi a : registrosImportados) {

            String cnes = a.getCodEstabelecimento() != null
                    ? a.getCodEstabelecimento()
                    : "";

            String estabelecimento = a.getEstabelecimento() != null
                    ? a.getEstabelecimento()
                    : "";

            String especialidade = a.getEspecialidadeMedico() != null
                    ? a.getEspecialidadeMedico()
                    : "";

            String medico = a.getMedico() != null
                    ? a.getMedico()
                    : "";

            listaBase.add(new AtendimentoBPAiDTO(
                    a.getTipoServico(),
                    a.getDataAgendamento() != null
                            ? a.getDataAgendamento().format(dataFormat)
                            : "",
                    a.getHoraAtendimento() != null
                            ? a.getHoraAtendimento().format(horaFormat)
                            : "",
                    cnes,
                    estabelecimento,
                    especialidade,
                    medico,
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

        atualizarComboEspecialidades();
    }

    private void atualizarComboEspecialidades() {

        Set<String> especialidadesUnicas = listaBase.stream()
                .map(AtendimentoBPAiDTO::getEspecialidade)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        comboEspecialidade.getItems().clear();
        comboEspecialidade.getItems().add("TODAS");
        comboEspecialidade.getItems().addAll(especialidadesUnicas);
        comboEspecialidade.getSelectionModel().selectFirst();
    }

    private void aplicarFiltro() {

        String selecionada = comboEspecialidade.getValue();

        if (selecionada == null || selecionada.equals("TODAS")) {
            listaFiltrada.setPredicate(p -> true);
        } else {
            listaFiltrada.setPredicate(dto ->
                    selecionada.equals(dto.getEspecialidade())
            );
        }
    }

    private void adicionarColunas(TableView<AtendimentoBPAiDTO> table) {

        table.getColumns().addAll(
                criarColuna("Tipo Serviço", "tipoServico"),
                criarColuna("Data Agendamento", "dataAgendamento"),
                criarColuna("Hora Atendimento", "horaAtendimento"),
                criarColuna("CNES", "cnes"),
                criarColuna("Estabelecimento", "estabelecimento"),
                criarColuna("Especialidade", "especialidade"),
                criarColuna("Médico", "medico"),
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
}
