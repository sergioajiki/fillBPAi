package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.service.GeradorBPAiService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;

import javafx.geometry.Insets;

import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

import javafx.stage.Window;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RelatorioController {

	// ======================================================
	// ENTITY MANAGER
	// ======================================================

	private final EntityManager entityManager;

	// ======================================================
	// LISTAS
	// ======================================================

	private final ObservableList<AtendimentoBPAiDTO> lista =
			FXCollections.observableArrayList();

	private final FilteredList<AtendimentoBPAiDTO> listaFiltrada =
			new FilteredList<>(lista, p -> true);

	// ======================================================
	// COMPONENTES
	// ======================================================

	private TableView<AtendimentoBPAiDTO> tabela;

	private Label totalLabel;

	private ComboBox<String> filtroEspecialidade = new ComboBox<>();

	private ComboBox<String> filtroMedico = new ComboBox<>();

	// Campo para edição do CNS do profissional (habilitado após seleção de médico)
	private TextField campoCnsProfissional = new TextField();

	private Button btnAtualizar = new Button("OK");

	// BOTÃO GERAR BPA — gera arquivo filtrado por especialidade/médico
	private Button btnGerarBPA = new Button("Gerar BPA-I");

	// BOTÃO GERAR BPA COMPLETO — gera arquivo com todos os médicos,
	// atribuindo folhas sequenciais por especialidade (ordem alfabética)
	private Button btnGerarBPACompleto = new Button("Gerar BPA-I Completo");

	// ======================================================
	// CONSTRUTOR
	// ======================================================

	public RelatorioController(EntityManager entityManager) {

		this.entityManager = entityManager;
	}

	// ======================================================
	// CRIAR COMPONENTE PRINCIPAL
	// ======================================================

	public BorderPane criarComponente() {

		tabela = new TableView<>();

		// IMPORTANTE:
		// sempre usar listaFiltrada, nunca alterar colunas dinamicamente
		tabela.setItems(listaFiltrada);

		// Desativa o resize automático que comprime colunas para caber na janela.
		// Com UNCONSTRAINED, cada coluna mantém seu prefWidth (150px),
		// e o conteúdo total da tabela pode ultrapassar a largura visível.
		tabela.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

		totalLabel = new Label("Total: 0");

		configurarColunas(); // cria TODAS as colunas

		// Calcula a largura mínima necessária para exibir todas as colunas.
		// 27 colunas × 150px = 4050px + margem para scrollbar vertical.
		double larguraTotal = tabela.getColumns().size() * 150 + 20;
		tabela.setMinWidth(larguraTotal);
		tabela.setPrefWidth(larguraTotal);

		// ScrollPane gerencia a rolagem horizontal de forma explícita.
		// Sem ele, o BorderPane/VBox pai expande a tabela para a largura da janela
		// (1500px) e as colunas à direita ficam inacessíveis.
		ScrollPane scrollPane = new ScrollPane(tabela);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.setFitToHeight(true);

		// ScrollPane cresce para ocupar todo o espaço vertical disponível
		VBox.setVgrow(scrollPane, Priority.ALWAYS);

		VBox box = new VBox(
				10,
				criarBarraFiltros(),
				scrollPane,
				totalLabel
		);

		box.setPadding(new Insets(10));

		BorderPane pane = new BorderPane();

		pane.setCenter(box);

		return pane;
	}

	// ======================================================
	// BARRA DE FILTROS
	// ======================================================

	private HBox criarBarraFiltros() {

		// Filtros de seleção — definem o contexto de edição
		filtroEspecialidade.setPromptText("Especialidade");
		filtroMedico.setPromptText("Médico");

		// Campo de edição do CNS do profissional
		campoCnsProfissional.setPromptText("Novo CNS");

		// Campos desabilitados até que um médico seja selecionado
		filtroMedico.setDisable(true);
		campoCnsProfissional.setDisable(true);
		btnAtualizar.setDisable(true);

		// EVENTO FILTRO ESPECIALIDADE
		filtroEspecialidade.setOnAction(e -> {

			atualizarMedicosPorEspecialidade();

			aplicarFiltros();
		});

		// EVENTO FILTRO MÉDICO
		filtroMedico.setOnAction(e -> {

			aplicarFiltros();

			habilitarEdicao();
		});

		// EVENTO ATUALIZAR BD — persiste apenas o CNS do profissional
		btnAtualizar.setOnAction(e -> atualizarCns());

		// EVENTO GERAR BPA — filtrado por especialidade/médico selecionados
		btnGerarBPA.setOnAction(e -> gerarBPA());

		// EVENTO GERAR BPA COMPLETO — todos os médicos, folha auto-atribuída
		btnGerarBPACompleto.setOnAction(e -> gerarBPACompleto());

		Button btnLimpar = new Button("Limpar");

		btnLimpar.setOnAction(e -> limparFiltros());

		return new HBox(
				10,
				new Label("Especialidade:"), filtroEspecialidade,
				new Label("Médico:"), filtroMedico,
				new Label("CNS:"), campoCnsProfissional,
				btnAtualizar,
				btnGerarBPA,
				btnGerarBPACompleto,
				btnLimpar
		);
	}

	// ======================================================
	// GERAR BPA COM FILECHOOSER
	// ======================================================

	private void gerarBPA() {

		String especialidade = filtroEspecialidade.getValue();

		String medico = filtroMedico.getValue();

		if (especialidade == null || medico == null) {

			mostrarMensagem("Selecione especialidade e médico.");

			return;
		}

		try {

			GeradorBPAiService service =
					new GeradorBPAiService(entityManager);

			Window window =
					tabela.getScene().getWindow();

			service.gerarArquivoComFileChooser(
					window,
					especialidade,
					medico
			);

			mostrarMensagem("Arquivo gerado com sucesso.");

		}
		catch (Exception ex) {

			mostrarMensagem("Erro: " + ex.getMessage());
		}
	}

	// ======================================================
	// GERAR BPA COMPLETO — TODOS OS MÉDICOS
	// ======================================================

	/**
	 * Gera o arquivo BPA-I completo com todos os médicos.
	 *
	 * Regra de folha:
	 * 1. Especialidades em ordem alfabética
	 * 2. Médicos dentro de cada especialidade em ordem alfabética
	 * 3. Folha sequencial: médico 1 = folha 1, médico 2 = folha 2, etc.
	 * 4. A sequência de folha é por competência (mês) — ao mudar o mês,
	 *    a numeração é recalculada automaticamente.
	 *
	 * Após a geração, a tabela é recarregada para exibir
	 * as folhas atribuídas a cada atendimento.
	 */
	private void gerarBPACompleto() {

		try {

			GeradorBPAiService service =
					new GeradorBPAiService(entityManager);

			Window window =
					tabela.getScene().getWindow();

			service.gerarArquivoCompletoComFileChooser(window);

			// Recarrega tabela para exibir as folhas atribuídas
			carregarDoBanco();

			mostrarMensagem("Arquivo BPA-I completo gerado com sucesso.");

		} catch (Exception ex) {

			mostrarMensagem("Erro: " + ex.getMessage());
		}
	}

	// ======================================================
	// FILTROS
	// ======================================================

	private void limparFiltros() {

		filtroEspecialidade.getSelectionModel().clearSelection();

		filtroMedico.getSelectionModel().clearSelection();

		filtroMedico.getItems().clear();

		filtroMedico.setDisable(true);

		campoCnsProfissional.clear();

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

		String especialidade =
				filtroEspecialidade.getValue();

		filtroMedico.getItems().clear();

		if (especialidade == null) {

			filtroMedico.setDisable(true);

			return;
		}

		List<String> medicos =
				lista.stream()

						.filter(dto ->
								especialidade.equals(
										dto.getEspecialidadeMedico()))

						.map(AtendimentoBPAiDTO::getMedico)

						.distinct()

						.sorted()

						.collect(Collectors.toList());

		filtroMedico.setItems(
				FXCollections.observableArrayList(medicos));

		filtroMedico.setDisable(false);
	}

	private void aplicarFiltros() {

		listaFiltrada.setPredicate(dto -> {

			boolean esp = true;

			boolean med = true;

			if (filtroEspecialidade.getValue() != null)
				esp = filtroEspecialidade.getValue()
						.equals(dto.getEspecialidadeMedico());

			if (filtroMedico.getValue() != null)
				med = filtroMedico.getValue()
						.equals(dto.getMedico());

			return esp && med;
		});

		totalLabel.setText(
				"Total: " + listaFiltrada.size());
	}

	private void habilitarEdicao() {

		campoCnsProfissional.setDisable(false);

		btnAtualizar.setDisable(false);
	}

	// ======================================================
	// ATUALIZAÇÃO BANCO
	// ======================================================

	/**
	 * Atualiza o CNS do profissional para todos os atendimentos
	 * do médico e especialidade selecionados.
	 *
	 * A folha não é mais editável pela UI — é controlada pelo gerador.
	 */
	private void atualizarCns() {

		String medico = filtroMedico.getValue();

		String especialidade =
				filtroEspecialidade.getValue();

		entityManager.getTransaction().begin();

		try {

			List<AtendimentoBPAi> lista =
					entityManager.createQuery(
									"SELECT a FROM AtendimentoBPAi a " +
											"JOIN a.medico m " +
											"WHERE m.nome = :medico " +
											"AND a.especialidadeMedico = :esp",
									AtendimentoBPAi.class)

							.setParameter("medico", medico)

							.setParameter("esp", especialidade)

							.getResultList();

			for (AtendimentoBPAi a : lista) {

				// Atualiza apenas o CNS do profissional
				a.setCnsProfissional(
						campoCnsProfissional.getText());
			}

			entityManager.getTransaction().commit();

			mostrarMensagem("Atualizado.");

			carregarDoBanco();

		}
		catch (Exception ex) {

			entityManager.getTransaction().rollback();

			mostrarMensagem(ex.getMessage());
		}
	}

	// ======================================================
	// COLUNAS — SEMPRE TODAS VISÍVEIS
	// ======================================================

	private void configurarColunas() {

		tabela.getColumns().clear();

		tabela.getColumns().addAll(

				criarColuna("Tipo Serviço",
						AtendimentoBPAiDTO::getTipoServico),

				criarColuna("SIGTAP",
						AtendimentoBPAiDTO::getSigtap),

				criarColuna("Data",
						AtendimentoBPAiDTO::getDataAgendamento),

				criarColuna("Hora",
						AtendimentoBPAiDTO::getHoraAtendimento),

				criarColuna("Estabelecimento",
						AtendimentoBPAiDTO::getEstabelecimento),

				criarColuna("INE",
						AtendimentoBPAiDTO::getCodIne),

				criarColuna("Folha",
						AtendimentoBPAiDTO::getFolha),

				criarColuna("Médico",
						AtendimentoBPAiDTO::getMedico),

				criarColuna("Especialidade",
						AtendimentoBPAiDTO::getEspecialidadeMedico),

				criarColuna("CPF Médico",
						AtendimentoBPAiDTO::getCpfMedico),

				criarColuna("CBO",
						AtendimentoBPAiDTO::getCboMedico),

				criarColuna("CNS Prof",
						AtendimentoBPAiDTO::getCnsProfissional),

				criarColuna("Paciente",
						AtendimentoBPAiDTO::getPaciente),

				criarColuna("CPF Paciente",
						AtendimentoBPAiDTO::getCpfPaciente),

				criarColuna("CNS Paciente",
						AtendimentoBPAiDTO::getCnsPaciente),

				criarColuna("Sexo",
						AtendimentoBPAiDTO::getSexoPaciente),

				criarColuna("Raça",
						AtendimentoBPAiDTO::getRacaPaciente),

				criarColuna("Nascimento",
						AtendimentoBPAiDTO::getDataNascimento),

				criarColuna("Telefone",
						AtendimentoBPAiDTO::getTelefone),

				criarColuna("Município",
						AtendimentoBPAiDTO::getMunicipio),

				criarColuna("CEP",
						AtendimentoBPAiDTO::getCep),

				criarColuna("Cód. Logradouro",
						AtendimentoBPAiDTO::getCodLogradouro),

				criarColuna("Endereço",
						AtendimentoBPAiDTO::getEndereco),

				criarColuna("Complemento",
						AtendimentoBPAiDTO::getComplemento),

				criarColuna("Número",
						AtendimentoBPAiDTO::getNumero),

				criarColuna("Bairro",
						AtendimentoBPAiDTO::getBairro),

				criarColuna("Cód. IBGE",
						AtendimentoBPAiDTO::getCodigoIbge),

				criarColuna("CID",
						AtendimentoBPAiDTO::getCidConsulta)
		);
	}

	private TableColumn<AtendimentoBPAiDTO, String> criarColuna(
			String nome,
			Function<AtendimentoBPAiDTO, String> mapper) {

		TableColumn<AtendimentoBPAiDTO, String> col =
				new TableColumn<>(nome);

		col.setCellValueFactory(
				c -> new SimpleStringProperty(
						mapper.apply(c.getValue())));

		col.setPrefWidth(150);

		return col;
	}

	// ======================================================
	// DADOS
	// ======================================================

	public void atualizarDados(
			List<AtendimentoBPAi> registros) {

		lista.clear();

		registros.forEach(r ->
				lista.add(
						AtendimentoBPAiDTO.fromEntity(r)));

		atualizarCombos();

		aplicarFiltros();
	}

	public void carregarDoBanco() {

		TypedQuery<AtendimentoBPAi> query =
				entityManager.createQuery(
						"SELECT a FROM AtendimentoBPAi a " +
								"JOIN FETCH a.paciente p " +
								"LEFT JOIN FETCH p.endereco " +
								"LEFT JOIN FETCH a.medico " +
								"LEFT JOIN FETCH a.estabelecimento",
						AtendimentoBPAi.class);

		atualizarDados(query.getResultList());
	}

	// ======================================================
	// MENSAGEM
	// ======================================================

	private void mostrarMensagem(String msg) {

		Alert alert =
				new Alert(Alert.AlertType.INFORMATION);

		alert.setContentText(msg);

		alert.showAndWait();
	}
}
