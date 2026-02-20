package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import jakarta.persistence.EntityManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class RelatorioController {

    private final EntityManager entityManager;

    private TableView<AtendimentoBPAiDTO> table;
    private ComboBox<String> comboEspecialidade;
    private ComboBox<String> comboMedico;
    private TextField campoFolha;
    private TextField campoCnsProfissional;
    private Button btnOk;

    private ObservableList<AtendimentoBPAiDTO> listaBase;
    private FilteredList<AtendimentoBPAiDTO> listaFiltrada;
    private SortedList<AtendimentoBPAiDTO> listaOrdenada;

    public RelatorioController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public BorderPane criarComponente() {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        table = new TableView<>();

        comboEspecialidade = new ComboBox<>();
        comboEspecialidade.setPromptText("Especialidade");

        comboMedico = new ComboBox<>();
        comboMedico.setPromptText("Médico");
        comboMedico.setDisable(true);

        campoFolha = new TextField();
        campoFolha.setPromptText("Folha");

        campoCnsProfissional = new TextField();
        campoCnsProfissional.setPromptText("CNS Profissional");

        btnOk = new Button("OK");

        listaBase = FXCollections.observableArrayList();
        listaFiltrada = new FilteredList<>(listaBase, p -> true);
        listaOrdenada = new SortedList<>(listaFiltrada);
        listaOrdenada.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(listaOrdenada);

        adicionarColunas();

        comboEspecialidade.setOnAction(e -> {
            atualizarComboMedicos();
            aplicarFiltro();
        });

        comboMedico.setOnAction(e -> aplicarFiltro());

        btnOk.setOnAction(e -> salvarRegistrosFiltrados());

        HBox topBar = new HBox(10,
                comboEspecialidade,
                comboMedico,
                campoFolha,
                campoCnsProfissional,
                btnOk
        );

        root.setTop(topBar);
        root.setCenter(table);

        lblTotalRegistros = new Label("Total de registros: 0");

        HBox rodape = new HBox(lblTotalRegistros);
        rodape.setPadding(new Insets(5));
        rodape.setStyle("-fx-background-color: #f4f4f4;");

        root.setBottom(rodape);

        return root;
    }

    // ===============================
    // ATUALIZAR DADOS (AGORA EXISTE)
    // ===============================

    public void atualizarDados(List<AtendimentoBPAi> registros) {

        listaBase.clear();

        if (registros == null || registros.isEmpty()) {
            return;
        }

        DateTimeFormatter dataFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter horaFormat = DateTimeFormatter.ofPattern("HH:mm");

        for (AtendimentoBPAi a : registros) {

            AtendimentoBPAiDTO dto = new AtendimentoBPAiDTO(
                    a.getId(),
                    a.getTipoServico(),
                    a.getSigtap(),
                    a.getDataAgendamento() != null ? a.getDataAgendamento().format(dataFormat) : "",
                    a.getHoraAtendimento() != null ? a.getHoraAtendimento().format(horaFormat) : "",
                    a.getCodEstabelecimento(),
                    a.getEstabelecimento(),
                    a.getEspecialidadeMedico(),
                    a.getMedico(),
                    a.getCpfMedico(),
                    a.getCboMedico(),
                    a.getMunicipio(),
                    a.getCpfPaciente(),
                    a.getPaciente(),
                    a.getCnsPaciente(),
                    a.getRacaPaciente(),
                    a.getDataNascimento() != null ? a.getDataNascimento().format(dataFormat) : "",
                    a.getCidConsulta(),
                    a.getTelefone(),
                    a.getTipoZona(),
                    a.getEnderecoCompleto()
            );

            listaBase.add(dto);

            atualizarTotalRegistros();
        }

        atualizarComboEspecialidades();
    }

    // ===============================
    // SALVAR
    // ===============================

    private void salvarRegistrosFiltrados() {

        String folha = campoFolha.getText();
        String cnsProfissional = campoCnsProfissional.getText();

        if (folha == null || folha.isBlank() ||
                cnsProfissional == null || cnsProfissional.isBlank()) {
            mostrarAlerta("Informe Folha e CNS Profissional.");
            return;
        }

        List<AtendimentoBPAiDTO> registrosSelecionados =
                listaFiltrada.stream().toList();

        if (registrosSelecionados.isEmpty()) {
            mostrarAlerta("Nenhum registro selecionado.");
            return;
        }

        entityManager.getTransaction().begin();

        for (AtendimentoBPAiDTO dto : registrosSelecionados) {

            AtendimentoBPAi entity =
                    entityManager.find(AtendimentoBPAi.class, dto.getId());

            if (entity != null) {

                // 🔥 Atualiza apenas os dois campos na entidade
                entity.setFolha(folha);
                entity.setCnsProfissional(cnsProfissional);

                // 🔥 Atualiza também o DTO (IMPORTANTE)
                dto.setFolha(folha);
                dto.setCnsProfissional(cnsProfissional);
            }
        }

        entityManager.getTransaction().commit();

        // 🔥 Força atualização visual da tabela
        table.refresh();

        mostrarAlerta("Registros atualizados com sucesso.");
    }

    private void mostrarAlerta(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ===============================
    // FILTROS
    // ===============================

    private void atualizarComboEspecialidades() {

        Set<String> especialidades = listaBase.stream()
                .map(AtendimentoBPAiDTO::getEspecialidade)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        comboEspecialidade.getItems().clear();
        comboEspecialidade.getItems().add("TODAS");
        comboEspecialidade.getItems().addAll(especialidades);
        comboEspecialidade.getSelectionModel().selectFirst();
    }

    private void atualizarComboMedicos() {

        String especialidadeSelecionada = comboEspecialidade.getValue();

        comboMedico.getItems().clear();

        if (especialidadeSelecionada == null ||
                especialidadeSelecionada.equals("TODAS")) {
            comboMedico.setDisable(true);
            return;
        }

        Set<String> medicos = listaBase.stream()
                .filter(dto -> especialidadeSelecionada.equals(dto.getEspecialidade()))
                .map(AtendimentoBPAiDTO::getMedico)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));

        comboMedico.getItems().add("TODOS");
        comboMedico.getItems().addAll(medicos);
        comboMedico.getSelectionModel().selectFirst();
        comboMedico.setDisable(false);
    }

    private void aplicarFiltro() {

        String especialidade = comboEspecialidade.getValue();
        String medico = comboMedico.getValue();

        listaFiltrada.setPredicate(dto -> {

            boolean filtroEspecialidade =
                    especialidade == null
                            || especialidade.equals("TODAS")
                            || especialidade.equals(dto.getEspecialidade());

            boolean filtroMedico =
                    medico == null
                            || medico.equals("TODOS")
                            || medico.equals(dto.getMedico());

            return filtroEspecialidade && filtroMedico;
        });

        atualizarTotalRegistros();
    }

    private void adicionarColunas() {

        table.getColumns().addAll(
                criarColuna("CNES-NTS", "cnesNts"),
                criarColuna("Cod-INE", "codIne"),
                criarColuna("Folha", "folha"),
                criarColuna("CNS Profissional", "cnsProfissional"),
                criarColuna("Especialidade", "especialidade"),
                criarColuna("Médico", "medico"),
                criarColuna("Paciente", "paciente")
        );
    }

    private TableColumn<AtendimentoBPAiDTO, String> criarColuna(String titulo, String prop) {

        TableColumn<AtendimentoBPAiDTO, String> col = new TableColumn<>(titulo);

        col.setCellValueFactory(data ->
                new SimpleStringProperty(
                        getValor(data.getValue(), prop)
                )
        );

        col.setPrefWidth(150);
        return col;
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

    private Label lblTotalRegistros;

    private void atualizarTotalRegistros() {
        lblTotalRegistros.setText(
                "Total de registros: " + listaFiltrada.size()
        );
    }

}