package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.service.DasusProfissionalService;
import br.gov.ses.fillbpai.service.DasusProfissionalService.ProfissionalSusDTO;
import br.gov.ses.fillbpai.util.CnsProfissionalUtils;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.List;

/**
 * Controller para a tela de pré-cadastro de CNS profissional.
 *
 * Permite buscar profissionais por nome no arquivo CSV do DATASUS
 * (tbDadosProfissionalSus) e cadastrar o CNS no cache local (medicos_cns.csv).
 *
 * Fluxo:
 * 1. Usuário seleciona o arquivo CSV do DATASUS
 * 2. Digita o nome do profissional e clica em "Buscar"
 * 3. Seleciona o profissional correto na tabela de resultados
 * 4. Clica em "Salvar" — grava nome;cns no medicos_cns.csv
 */
public class PreCadastroController {

	private final DasusProfissionalService service = new DasusProfissionalService();

	// Componentes da UI
	private Label labelArquivo;
	private TextField campoNomeBusca;
	private Button btnBuscar;
	private TableView<ProfissionalSusDTO> tabelaResultados;
	private ObservableList<ProfissionalSusDTO> resultados = FXCollections.observableArrayList();
	private Button btnSalvar;
	private Label labelStatus;
	private TableView<String[]> tabelaCadastrados;
	private ObservableList<String[]> cadastrados = FXCollections.observableArrayList();
	private ProgressIndicator progressIndicator;

	/**
	 * Abre a janela de pré-cadastro.
	 *
	 * @param owner janela pai (para modality)
	 */
	public void abrirDialog(Window owner) {

		Stage stage = new Stage();
		stage.initModality(Modality.WINDOW_MODAL);
		stage.initOwner(owner);
		stage.setTitle("Pré-Cadastro de CNS Profissional");
		stage.setWidth(800);
		stage.setHeight(700);

		VBox root = new VBox(10);
		root.setPadding(new Insets(15));

		root.getChildren().addAll(
				criarSecaoArquivo(stage),
				new Separator(),
				criarSecaoBusca(),
				criarTabelaResultados(),
				new Separator(),
				criarSecaoSalvar(),
				new Separator(),
				criarSecaoCadastrados());

		stage.setScene(new Scene(root));
		carregarCadastrados();
		stage.show();
	}

	// ======================================================
	// SEÇÃO ARQUIVO DATASUS
	// ======================================================

	private HBox criarSecaoArquivo(Stage stage) {

		String caminhoAtual = CnsProfissionalUtils.getCaminhoDatasus();

		labelArquivo = new Label(
				caminhoAtual != null
						? caminhoAtual
						: "Nenhum arquivo selecionado");
		labelArquivo.setMaxWidth(500);

		Button btnSelecionar = new Button("Selecionar Arquivo DATASUS");

		btnSelecionar.setOnAction(e -> {

			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Selecionar arquivo CSV do DATASUS");
			fileChooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("CSV", "*.csv"));

			// Abre na pasta Documentos por padrão
			File documentos = new File(System.getProperty("user.home"), "Documents");
			if (documentos.exists()) {
				fileChooser.setInitialDirectory(documentos);
			}

			File arquivo = fileChooser.showOpenDialog(stage);

			if (arquivo != null) {
				CnsProfissionalUtils.setCaminhoDatasus(arquivo.getAbsolutePath());
				labelArquivo.setText(arquivo.getAbsolutePath());
			}
		});

		HBox box = new HBox(10, new Label("Arquivo:"), labelArquivo, btnSelecionar);
		box.setPadding(new Insets(5, 0, 5, 0));

		return box;
	}

	// ======================================================
	// SEÇÃO BUSCA POR NOME
	// ======================================================

	private HBox criarSecaoBusca() {

		campoNomeBusca = new TextField();
		campoNomeBusca.setPromptText("Nome do profissional");
		campoNomeBusca.setPrefWidth(350);

		btnBuscar = new Button("Buscar");

		progressIndicator = new ProgressIndicator();
		progressIndicator.setPrefSize(20, 20);
		progressIndicator.setVisible(false);

		btnBuscar.setOnAction(e -> executarBusca());

		// Enter no campo de busca também aciona a pesquisa
		campoNomeBusca.setOnAction(e -> executarBusca());

		return new HBox(10,
				new Label("Nome:"), campoNomeBusca,
				btnBuscar, progressIndicator);
	}

	/**
	 * Executa a busca por nome no arquivo DATASUS em thread separada.
	 * O arquivo CSV tem 7+ milhões de registros, então a busca
	 * roda em background para não travar a UI.
	 */
	private void executarBusca() {

		String caminhoDatasus = CnsProfissionalUtils.getCaminhoDatasus();

		if (caminhoDatasus == null) {
			mostrarStatus("Selecione o arquivo CSV do DATASUS primeiro.");
			return;
		}

		String nome = campoNomeBusca.getText();

		if (nome == null || nome.isBlank()) {
			mostrarStatus("Digite um nome para buscar.");
			return;
		}

		// Desabilita controles durante a busca
		btnBuscar.setDisable(true);
		progressIndicator.setVisible(true);
		mostrarStatus("Buscando...");

		Task<List<ProfissionalSusDTO>> task = new Task<>() {
			@Override
			protected List<ProfissionalSusDTO> call() {
				return service.buscarPorNome(caminhoDatasus, nome);
			}
		};

		task.setOnSucceeded(e -> {
			List<ProfissionalSusDTO> lista = task.getValue();
			resultados.setAll(lista);
			btnBuscar.setDisable(false);
			progressIndicator.setVisible(false);

			String msg = lista.size() + " resultado(s) encontrado(s)";
			if (lista.size() >= 100) {
				msg += " (limite atingido — refine a busca)";
			}
			mostrarStatus(msg);
		});

		task.setOnFailed(e -> {
			btnBuscar.setDisable(false);
			progressIndicator.setVisible(false);
			mostrarStatus("Erro na busca: " + task.getException().getMessage());
		});

		new Thread(task, "datasus-search").start();
	}

	// ======================================================
	// TABELA DE RESULTADOS DA BUSCA
	// ======================================================

	private VBox criarTabelaResultados() {

		tabelaResultados = new TableView<>();
		tabelaResultados.setItems(resultados);
		tabelaResultados.setPrefHeight(200);
		tabelaResultados.setPlaceholder(new Label("Nenhum resultado"));

		TableColumn<ProfissionalSusDTO, String> colNome = new TableColumn<>("Nome");
		colNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nome()));
		colNome.setPrefWidth(450);

		TableColumn<ProfissionalSusDTO, String> colCns = new TableColumn<>("CNS");
		colCns.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cns()));
		colCns.setPrefWidth(200);

		tabelaResultados.getColumns().addAll(colNome, colCns);

		// Ao selecionar um resultado, habilita o botão Salvar
		tabelaResultados.getSelectionModel().selectedItemProperty().addListener(
				(obs, oldVal, newVal) -> {

					if (newVal != null) {
						btnSalvar.setDisable(false);
					}
				});

		VBox box = new VBox(5, new Label("Resultados:"), tabelaResultados);
		VBox.setVgrow(tabelaResultados, Priority.ALWAYS);

		return box;
	}

	// ======================================================
	// SEÇÃO SALVAR
	// ======================================================

	private HBox criarSecaoSalvar() {

		btnSalvar = new Button("Salvar no Cadastro");
		btnSalvar.setDisable(true);

		labelStatus = new Label();

		btnSalvar.setOnAction(e -> salvar());

		return new HBox(10, btnSalvar, labelStatus);
	}

	/**
	 * Salva o profissional selecionado no arquivo medicos_cns.csv.
	 * Grava nome;cns diretamente — sem necessidade de CPF.
	 */
	private void salvar() {

		ProfissionalSusDTO selecionado =
				tabelaResultados.getSelectionModel().getSelectedItem();

		if (selecionado == null) {
			mostrarStatus("Selecione um profissional na tabela.");
			return;
		}

		// Verifica se já existe
		CnsProfissionalUtils.CnsResultado existente =
				CnsProfissionalUtils.buscar(selecionado.nome());

		if (existente.getCns() != null) {

			if (existente.getCns().equals(selecionado.cns())) {
				mostrarStatus("Profissional já cadastrado com CNS: " + existente.getCns());
			} else {
				mostrarStatus("Profissional já cadastrado com CNS diferente: "
						+ existente.getCns() + " (DATASUS: " + selecionado.cns() + ")");
			}

			return;
		}

		// Salva no CSV (nome;cns)
		try {
			CnsProfissionalUtils.salvar(selecionado.nome(), selecionado.cns());

			mostrarStatus("Salvo: " + selecionado.nome()
					+ " — CNS: " + selecionado.cns());

			btnSalvar.setDisable(true);

			// Atualiza tabela de cadastrados
			carregarCadastrados();

		} catch (Exception ex) {
			mostrarStatus("Erro ao salvar: " + ex.getMessage());
		}
	}

	// ======================================================
	// SEÇÃO CADASTRADOS (PROFISSIONAIS JÁ REGISTRADOS)
	// ======================================================

	private VBox criarSecaoCadastrados() {

		tabelaCadastrados = new TableView<>();
		tabelaCadastrados.setItems(cadastrados);
		tabelaCadastrados.setPrefHeight(150);
		tabelaCadastrados.setPlaceholder(new Label("Nenhum profissional cadastrado"));

		TableColumn<String[], String> colNome = new TableColumn<>("Nome");
		colNome.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
		colNome.setPrefWidth(450);

		TableColumn<String[], String> colCns = new TableColumn<>("CNS");
		colCns.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
		colCns.setPrefWidth(200);

		tabelaCadastrados.getColumns().addAll(colNome, colCns);

		VBox box = new VBox(5, new Label("Profissionais cadastrados:"), tabelaCadastrados);

		return box;
	}

	/**
	 * Carrega os profissionais já cadastrados no medicos_cns.csv
	 * e exibe na tabela inferior.
	 */
	private void carregarCadastrados() {

		cadastrados.setAll(CnsProfissionalUtils.obterTodosRegistrados());
	}

	// ======================================================
	// UTILITÁRIOS
	// ======================================================

	private void mostrarStatus(String msg) {
		if (labelStatus != null) {
			labelStatus.setText(msg);
		}
	}
}
