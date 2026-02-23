package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RelatorioController {

    private final EntityManager entityManager;

    private final ObservableList<AtendimentoBPAiDTO> lista =
            FXCollections.observableArrayList();

    private final FilteredList<AtendimentoBPAiDTO> listaFiltrada =
            new FilteredList<>(lista, p -> true);

    private TableView<AtendimentoBPAiDTO> tabela;
    private Label totalLabel;

    private ComboBox<String> filtroEspecialidade = new ComboBox<>();
    private ComboBox<String> filtroMedico = new ComboBox<>();

    private TextField campoFolha = new TextField();
    private TextField campoCnsProfissional = new TextField();
    private Button btnAtualizar = new Button("OK");

    public RelatorioController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public BorderPane criarComponente() {

        tabela = new TableView<>();
        tabela.setItems(listaFiltrada);

        totalLabel = new Label("Total: 0");

        configurarColunas();

        VBox box = new VBox(10,
                criarBarraFiltros(),
                tabela,
                totalLabel);

        box.setPadding(new Insets(10));

        BorderPane pane = new BorderPane();
        pane.setCenter(box);

        return pane;
    }

    // ======================================================
    // ===================== FILTROS ========================
    // ======================================================

    private HBox criarBarraFiltros() {

        filtroEspecialidade.setPromptText("Selecione Especialidade");
        filtroMedico.setPromptText("Selecione Médico");

        campoFolha.setPromptText("Nova Folha");
        campoCnsProfissional.setPromptText("Novo CNS Profissional");

        filtroMedico.setDisable(true);
        campoFolha.setDisable(true);
        campoCnsProfissional.setDisable(true);
        btnAtualizar.setDisable(true);

        filtroEspecialidade.setOnAction(e -> {
            atualizarMedicosPorEspecialidade();
            aplicarFiltros();
        });

        filtroMedico.setOnAction(e -> {
            aplicarFiltros();
            habilitarEdicao();
        });

        btnAtualizar.setOnAction(e -> atualizarFolhaECns());

        Button btnLimpar = new Button("Limpar");
        btnLimpar.setOnAction(e -> limparFiltros());

        return new HBox(10,
                new Label("Especialidade:"), filtroEspecialidade,
                new Label("Médico:"), filtroMedico,
                new Label("Folha:"), campoFolha,
                new Label("CNS Prof:"), campoCnsProfissional,
                btnAtualizar,
                btnLimpar
        );
    }

    private void limparFiltros() {

        filtroEspecialidade.getSelectionModel().clearSelection();
        filtroMedico.getSelectionModel().clearSelection();

        filtroMedico.getItems().clear();
        filtroMedico.setDisable(true);

        campoFolha.clear();
        campoCnsProfissional.clear();

        campoFolha.setDisable(true);
        campoCnsProfissional.setDisable(true);
        btnAtualizar.setDisable(true);

        aplicarFiltros();
    }

    private void atualizarCombos() {

        filtroEspecialidade.setItems(
                FXCollections.observableArrayList(
                        lista.stream()
                                .map(AtendimentoBPAiDTO::getEspecialidadeMedico)
                                .filter(s -> s != null && !s.isEmpty())
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList())
                )
        );
    }

    private void atualizarMedicosPorEspecialidade() {

        String especialidade = filtroEspecialidade.getValue();

        filtroMedico.getSelectionModel().clearSelection();
        filtroMedico.getItems().clear();

        if (especialidade == null) {
            filtroMedico.setDisable(true);
            return;
        }

        List<String> medicos =
                lista.stream()
                        .filter(dto -> especialidade.equals(dto.getEspecialidadeMedico()))
                        .map(AtendimentoBPAiDTO::getMedico)
                        .filter(s -> s != null && !s.isEmpty())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

        filtroMedico.setItems(FXCollections.observableArrayList(medicos));
        filtroMedico.setDisable(false);
    }

    private void aplicarFiltros() {

        listaFiltrada.setPredicate(dto -> {

            boolean filtroEsp = true;
            boolean filtroMed = true;

            if (filtroEspecialidade.getValue() != null) {
                filtroEsp = filtroEspecialidade.getValue()
                        .equals(dto.getEspecialidadeMedico());
            }

            if (filtroMedico.getValue() != null) {
                filtroMed = filtroMedico.getValue()
                        .equals(dto.getMedico());
            }

            return filtroEsp && filtroMed;
        });

        totalLabel.setText("Total: " + listaFiltrada.size());
    }

    private void habilitarEdicao() {

        if (filtroMedico.getValue() != null) {
            campoFolha.setDisable(false);
            campoCnsProfissional.setDisable(false);
            btnAtualizar.setDisable(false);
        }
    }

    // ======================================================
    // ================= ATUALIZAÇÃO BD =====================
    // ======================================================

    private void atualizarFolhaECns() {

        String medico = filtroMedico.getValue();
        String especialidade = filtroEspecialidade.getValue();

        if (medico == null || especialidade == null) {
            return;
        }

        String novaFolha = campoFolha.getText();
        String novoCns = campoCnsProfissional.getText();

        entityManager.getTransaction().begin();

        try {

            List<AtendimentoBPAi> registros =
                    entityManager.createQuery(
                                    "SELECT a FROM AtendimentoBPAi a " +
                                            "WHERE a.medico = :medico " +
                                            "AND a.especialidadeMedico = :esp",
                                    AtendimentoBPAi.class)
                            .setParameter("medico", medico)
                            .setParameter("esp", especialidade)
                            .getResultList();

            for (AtendimentoBPAi a : registros) {
                a.setFolha(novaFolha);
                a.setCnsProfissional(novoCns);
            }

            entityManager.getTransaction().commit();

            mostrarMensagem("Atualização realizada com sucesso!");

            carregarDoBanco();
            limparCamposEdicao();

        } catch (Exception ex) {

            entityManager.getTransaction().rollback();
            mostrarMensagem("Erro ao atualizar: " + ex.getMessage());
        }
    }

    private void mostrarMensagem(String msg) {

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void limparCamposEdicao() {

        campoFolha.clear();
        campoCnsProfissional.clear();

        campoFolha.setDisable(true);
        campoCnsProfissional.setDisable(true);
        btnAtualizar.setDisable(true);
    }

    // ======================================================
    // ===================== COLUNAS ========================
    // ======================================================

    private void configurarColunas() {

        tabela.getColumns().clear();

        TableColumn<AtendimentoBPAiDTO, String> grupoServico =
                new TableColumn<>("DADOS DO SERVIÇO");

        grupoServico.getColumns().addAll(
                criarColuna("Tipo Serviço", AtendimentoBPAiDTO::getTipoServico),
                criarColuna("SIGTAP", AtendimentoBPAiDTO::getSigtap),
                criarColuna("Data Agendamento", AtendimentoBPAiDTO::getDataAgendamento),
                criarColuna("Hora Atendimento", AtendimentoBPAiDTO::getHoraAtendimento)
        );

        TableColumn<AtendimentoBPAiDTO, String> grupoEstabelecimento =
                new TableColumn<>("DADOS DO ESTABELECIMENTO");

        grupoEstabelecimento.getColumns().addAll(
                criarColuna("Código Estab.", AtendimentoBPAiDTO::getCodEstabelecimento),
                criarColuna("Estabelecimento", AtendimentoBPAiDTO::getEstabelecimento),
                criarColuna("CNES NTS", AtendimentoBPAiDTO::getCnesNts),
                criarColuna("Código INE", AtendimentoBPAiDTO::getCodIne),
                criarColuna("Folha", AtendimentoBPAiDTO::getFolha)
        );

        TableColumn<AtendimentoBPAiDTO, String> grupoProfissional =
                new TableColumn<>("DADOS DO PROFISSIONAL");

        grupoProfissional.getColumns().addAll(
                criarColuna("Médico", AtendimentoBPAiDTO::getMedico),
                criarColuna("Especialidade", AtendimentoBPAiDTO::getEspecialidadeMedico),
                criarColuna("CPF Médico", AtendimentoBPAiDTO::getCpfMedico),
                criarColuna("CBO Médico", AtendimentoBPAiDTO::getCboMedico),
                criarColuna("CNS Profissional", AtendimentoBPAiDTO::getCnsProfissional)
        );

        TableColumn<AtendimentoBPAiDTO, String> grupoPaciente =
                new TableColumn<>("DADOS DO PACIENTE");

        grupoPaciente.getColumns().addAll(
                criarColuna("Paciente", AtendimentoBPAiDTO::getPaciente),
                criarColuna("CPF Paciente", AtendimentoBPAiDTO::getCpfPaciente),
                criarColuna("CNS Paciente", AtendimentoBPAiDTO::getCnsPaciente),
                criarColuna("Raça", AtendimentoBPAiDTO::getRacaPaciente),
                criarColuna("Data Nascimento", AtendimentoBPAiDTO::getDataNascimento),
                criarColuna("Telefone", AtendimentoBPAiDTO::getTelefone),
                criarColuna("Município", AtendimentoBPAiDTO::getMunicipio),
                criarColuna("Tipo Zona", AtendimentoBPAiDTO::getTipoZona),
                criarColuna("Endereço Completo", AtendimentoBPAiDTO::getEnderecoCompleto),
                criarColuna("CID Consulta", AtendimentoBPAiDTO::getCidConsulta)
        );

        tabela.getColumns().addAll(
                grupoServico,
                grupoEstabelecimento,
                grupoProfissional,
                grupoPaciente
        );

        tabela.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private TableColumn<AtendimentoBPAiDTO, String> criarColuna(
            String titulo,
            Function<AtendimentoBPAiDTO, String> mapper) {

        TableColumn<AtendimentoBPAiDTO, String> col =
                new TableColumn<>(titulo);

        col.setCellValueFactory(
                c -> new SimpleStringProperty(mapper.apply(c.getValue()))
        );

        col.setPrefWidth(140);

        return col;
    }

    // ======================================================
    // ===================== DADOS ==========================
    // ======================================================

    public void atualizarDados(List<AtendimentoBPAi> registros) {

        lista.clear();

        for (AtendimentoBPAi a : registros) {
            lista.add(AtendimentoBPAiDTO.fromEntity(a));
        }

        atualizarCombos();
        aplicarFiltros();
    }

    public void carregarDoBanco() {

        TypedQuery<AtendimentoBPAi> query =
                entityManager.createQuery(
                        "SELECT a FROM AtendimentoBPAi a",
                        AtendimentoBPAi.class
                );

        atualizarDados(query.getResultList());
    }
}