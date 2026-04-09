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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
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

	/** Label exibido na primeira linha com a competência atual */
	private Label labelCompetencia = new Label("Competência: --");

	private ComboBox<String> filtroEspecialidade = new ComboBox<>();

	private ComboBox<String> filtroMedico = new ComboBox<>();

	// Campo para edição do CNS do profissional (habilitado após seleção de médico)
	private TextField campoCnsProfissional = new TextField();

	private Button btnAtualizar = new Button("OK");

	private Button btnFolha = new Button("Folha");

	private Button btnLimpar = new Button("Limpar");

	// BOTÃO GERAR BPA — gera arquivo filtrado por especialidade/médico
	private Button btnGerarBPA = new Button("Gerar BPA-I");

	// BOTÃO GERAR BPA COMPLETO — gera arquivo com todos os médicos,
	// atribuindo folhas sequenciais por especialidade (ordem alfabética)
	private Button btnGerarBPACompleto = new Button("Gerar BPA-I Completo");

	// BOTÃO AVISO — indica pendências que impedem a geração do BPA-I completo
	private Button btnAvisoGeracao = new Button("⚠");

	// BOTÃO VER LOG — exibe o log da última importação
	private Button btnVerLog = new Button("Ver Log Importação");

	// BOTÃO ANALISAR PLANILHA — valida a planilha antes de importar
	private Button btnAnalisarPlanilha = new Button("Analisar Planilha");

	/** Mensagem atual de avisos que impedem a geração completa (null = sem avisos) */
	private String avisosGeracao = null;

	/** Ação executada ao clicar em "Ver Log" — definida pelo MainController */
	private Runnable acaoVerLog;

	/** Ação executada ao clicar em "Analisar Planilha" — definida pelo MainController */
	private Runnable acaoAnalisarPlanilha;

	// ======================================================
	// CONSTRUTOR
	// ======================================================

	public RelatorioController(EntityManager entityManager) {

		this.entityManager = entityManager;
	}

	/**
	 * Define a ação do botão "Ver Log Importação".
	 * Chamado pelo MainController para injetar a lógica de exibição do log.
	 *
	 * @param acao runnable que exibe o diálogo de log
	 */
	public void setAcaoVerLog(Runnable acao) {
		this.acaoVerLog = acao;
	}

	/**
	 * Define a ação do botão "Analisar Planilha".
	 * Chamado pelo MainController para injetar a lógica de validação.
	 *
	 * @param acao runnable que executa a análise da planilha
	 */
	public void setAcaoAnalisarPlanilha(Runnable acao) {
		this.acaoAnalisarPlanilha = acao;
		btnAnalisarPlanilha.setOnAction(e -> acao.run());
	}

	/**
	 * Retorna o botão "Analisar Planilha" para ser posicionado pelo MainController.
	 */
	public Button getBtnAnalisarPlanilha() {
		return btnAnalisarPlanilha;
	}

	/**
	 * Retorna o label de competência para ser adicionado na topBar pelo MainController.
	 */
	public Label getLabelCompetencia() {
		return labelCompetencia;
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
				criarBarraAcoes(),
				scrollPane,
				totalLabel
		);

		box.setPadding(new Insets(10));

		BorderPane pane = new BorderPane();

		pane.setCenter(box);

		return pane;
	}

	// ======================================================
	// LINHA 2 — BARRA DE FILTROS (Especialidade, Médico, CNS, Folha, OK, Limpar)
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
		btnFolha.setDisable(true);

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

		// EVENTO FOLHA — abre diálogo para definir número de folha manual
		btnFolha.setOnAction(e -> definirFolha());

		// EVENTO LIMPAR
		btnLimpar.setOnAction(e -> limparFiltros());

		return new HBox(
				10,
				new Label("Especialidade:"), filtroEspecialidade,
				new Label("Médico:"), filtroMedico,
				new Label("CNS:"), campoCnsProfissional,
				btnFolha,
				btnAtualizar,
				btnLimpar
		);
	}

	// ======================================================
	// LINHA 3 — BARRA DE AÇÕES (Gerar BPA-I Completo, ⚠, Gerar BPA-I, Ver Log)
	// ======================================================

	private HBox criarBarraAcoes() {

		// EVENTO GERAR BPA COMPLETO — todos os médicos, folha auto-atribuída
		btnGerarBPACompleto.setOnAction(e -> gerarBPACompleto());

		// BOTÃO AVISO — estilo visual de alerta, visível apenas quando há pendências
		btnAvisoGeracao.setStyle(
				"-fx-background-color: #FFC107; -fx-text-fill: #000; " +
				"-fx-font-weight: bold; -fx-cursor: hand;");
		btnAvisoGeracao.setVisible(false);
		btnAvisoGeracao.setManaged(false);
		btnAvisoGeracao.setOnAction(e -> mostrarAvisosGeracao());

		// EVENTO GERAR BPA — filtrado por especialidade/médico selecionados
		btnGerarBPA.setOnAction(e -> gerarBPA());

		// EVENTO VER LOG — exibe o log da última importação
		btnVerLog.setOnAction(e -> {
			if (acaoVerLog != null) {
				acaoVerLog.run();
			}
		});

		HBox barra = new HBox(10, btnGerarBPACompleto, btnAvisoGeracao, btnGerarBPA, btnVerLog);
		barra.setPadding(new Insets(0));

		return barra;
	}

	/**
	 * Verifica no banco se existem atendimentos sem CNS do profissional.
	 * Atualiza visibilidade do botão de aviso e armazena o log de pendências.
	 */
	private void verificarAvisosGeracao() {

		try {

			@SuppressWarnings("unchecked")
			List<Object[]> semCns = entityManager.createQuery(
					"SELECT m.nome, p.nome, a.dataAgendamento " +
					"FROM AtendimentoBPAi a " +
					"JOIN a.medico m " +
					"JOIN a.paciente p " +
					"WHERE a.cnsProfissional IS NULL OR a.cnsProfissional = ''")
					.getResultList();

			if (semCns.isEmpty()) {
				avisosGeracao = null;
				btnAvisoGeracao.setVisible(false);
				btnAvisoGeracao.setManaged(false);
				return;
			}

			DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

			StringBuilder sb = new StringBuilder();
			sb.append("=== PENDÊNCIAS PARA GERAÇÃO BPA-I COMPLETO ===\n");
			sb.append(semCns.size()).append(" atendimento(s) sem CNS do profissional:\n\n");
			sb.append(String.format("%-30s| %-30s| %s%n", "Médico", "Paciente", "Data"));
			sb.append("------------------------------|------------------------------|----------\n");

			for (Object[] linha : semCns) {
				String med = linha[0] != null ? linha[0].toString() : "(sem médico)";
				String pac = linha[1] != null ? linha[1].toString() : "(sem paciente)";
				String data = linha[2] != null
						? ((java.time.LocalDate) linha[2]).format(fmt) : "";

				if (med.length() > 30) med = med.substring(0, 27) + "...";
				if (pac.length() > 30) pac = pac.substring(0, 27) + "...";

				sb.append(String.format("%-30s| %-30s| %s%n", med, pac, data));
			}

			avisosGeracao = sb.toString();
			btnAvisoGeracao.setVisible(true);
			btnAvisoGeracao.setManaged(true);
			btnAvisoGeracao.setTooltip(new Tooltip(
					semCns.size() + " atendimento(s) sem CNS do profissional"));

		} catch (Exception e) {
			// Falha silenciosa — não impede o carregamento da tabela
		}
	}

	/**
	 * Exibe o log de pendências que impedem a geração do BPA-I completo.
	 */
	private void mostrarAvisosGeracao() {

		if (avisosGeracao == null) return;

		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Pendências — Geração BPA-I Completo");
		alert.setHeaderText("Existem atendimentos sem CNS do profissional");

		TextArea area = new TextArea(avisosGeracao);
		area.setEditable(false);
		area.setWrapText(false);
		area.setPrefHeight(350);
		area.setPrefWidth(650);

		alert.getDialogPane().setContent(area);
		alert.showAndWait();
	}

	// ======================================================
	// DEFINIR FOLHA MANUAL
	// ======================================================

	/**
	 * Abre um diálogo para definir o número de folha do médico selecionado.
	 *
	 * Regras:
	 * - Se o médico já tem folha, ela será substituída pelo número informado
	 * - Se o número já está sendo usado por outro médico (na mesma competência),
	 *   exibe alerta e não permite a alteração
	 * - Atualiza todos os atendimentos do médico/especialidade selecionados
	 */
	private void definirFolha() {

		String medico = filtroMedico.getValue();
		String especialidade = filtroEspecialidade.getValue();

		if (medico == null || especialidade == null) {
			mostrarMensagem("Selecione especialidade e médico.");
			return;
		}

		// Descobre a folha atual do médico (se houver)
		String folhaAtual = obterFolhaAtual(medico, especialidade);

		TextInputDialog dialog = new TextInputDialog(folhaAtual != null ? folhaAtual : "");
		dialog.setTitle("Definir Folha");
		dialog.setHeaderText("Médico: " + medico + "\nEspecialidade: " + especialidade);
		dialog.setContentText("Número da folha:");

		Optional<String> resultado = dialog.showAndWait();

		if (resultado.isEmpty() || resultado.get().isBlank()) {
			return;
		}

		String novaFolha = resultado.get().trim();

		// Valida que é numérico
		if (!novaFolha.matches("\\d+")) {
			mostrarAlerta("Número inválido. Informe apenas dígitos.");
			return;
		}

		// Verifica se a folha já está em uso por outro médico
		String medicoUsandoFolha = verificarFolhaEmUso(novaFolha, medico, especialidade);

		if (medicoUsandoFolha != null) {
			mostrarAlerta("Folha " + novaFolha + " já está em uso pelo médico: "
					+ medicoUsandoFolha + ". Escolha outro número.");
			return;
		}

		// Aplica a folha a todos os atendimentos do médico/especialidade
		aplicarFolha(novaFolha, medico, especialidade);

		carregarDoBanco();

		mostrarMensagem("Folha " + novaFolha + " atribuída ao médico " + medico + ".");
	}

	/**
	 * Obtém a folha atual de um médico/especialidade.
	 *
	 * @return número da folha ou null se não definida
	 */
	private String obterFolhaAtual(String medico, String especialidade) {

		List<AtendimentoBPAi> atendimentos = entityManager.createQuery(
						"SELECT a FROM AtendimentoBPAi a " +
								"JOIN a.medico m " +
								"WHERE m.nome = :medico " +
								"AND a.especialidadeMedico = :esp " +
								"AND a.folha IS NOT NULL",
						AtendimentoBPAi.class)
				.setParameter("medico", medico)
				.setParameter("esp", especialidade)
				.setMaxResults(1)
				.getResultList();

		if (atendimentos.isEmpty()) {
			return null;
		}

		return atendimentos.get(0).getFolha();
	}

	/**
	 * Verifica se o número de folha já está sendo usado por outro médico.
	 *
	 * @return nome do médico que usa a folha, ou null se disponível
	 */
	private String verificarFolhaEmUso(String folha, String medicoAtual, String especialidadeAtual) {

		List<AtendimentoBPAi> atendimentos = entityManager.createQuery(
						"SELECT a FROM AtendimentoBPAi a " +
								"JOIN a.medico m " +
								"WHERE a.folha = :folha " +
								"AND (m.nome <> :medico OR a.especialidadeMedico <> :esp)",
						AtendimentoBPAi.class)
				.setParameter("folha", folha)
				.setParameter("medico", medicoAtual)
				.setParameter("esp", especialidadeAtual)
				.setMaxResults(1)
				.getResultList();

		if (atendimentos.isEmpty()) {
			return null;
		}

		return atendimentos.get(0).getMedico().getNome();
	}

	/**
	 * Aplica o número de folha a todos os atendimentos do médico/especialidade.
	 */
	private void aplicarFolha(String folha, String medico, String especialidade) {

		entityManager.getTransaction().begin();

		try {

			List<AtendimentoBPAi> atendimentos = entityManager.createQuery(
							"SELECT a FROM AtendimentoBPAi a " +
									"JOIN a.medico m " +
									"WHERE m.nome = :medico " +
									"AND a.especialidadeMedico = :esp",
							AtendimentoBPAi.class)
					.setParameter("medico", medico)
					.setParameter("esp", especialidade)
					.getResultList();

			for (AtendimentoBPAi a : atendimentos) {
				a.setFolha(folha);
			}

			entityManager.getTransaction().commit();

		} catch (Exception e) {

			if (entityManager.getTransaction().isActive()) {
				entityManager.getTransaction().rollback();
			}

			mostrarAlerta("Erro ao atribuir folha: " + e.getMessage());
		}
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

			mostrarErroGeracao(ex.getMessage());
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
	 * 3. Cada combinação (especialidade + médico) recebe uma folha sequencial:
	 *    folha 1 para o primeiro médico, folha 2 para o segundo, etc.
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

			mostrarErroGeracao(ex.getMessage());
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

		btnFolha.setDisable(true);

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

		btnFolha.setDisable(false);
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

		atualizarCompetencia();
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

		verificarAvisosGeracao();
	}

	// ======================================================
	// COMPETÊNCIA
	// ======================================================

	/**
	 * Atualiza o label de competência com base nos dados carregados.
	 * Competência = yyyyMM da data de agendamento mais recente.
	 */
	private void atualizarCompetencia() {

		if (lista.isEmpty()) {
			labelCompetencia.setText("Competência: --");
			return;
		}

		// Pega a data do primeiro registro para determinar a competência
		String dataStr = lista.get(0).getDataAgendamento();

		if (dataStr == null || dataStr.isBlank()) {
			labelCompetencia.setText("Competência: --");
			return;
		}

		try {

			LocalDate data = LocalDate.parse(dataStr,
					DateTimeFormatter.ofPattern("dd/MM/yyyy"));

			String competencia = data.format(
					DateTimeFormatter.ofPattern("MM/yyyy"));

			labelCompetencia.setText("Competência: " + competencia);

		} catch (Exception e) {
			labelCompetencia.setText("Competência: --");
		}
	}

	// ======================================================
	// MENSAGENS
	// ======================================================

	private void mostrarMensagem(String msg) {

		Alert alert =
				new Alert(Alert.AlertType.INFORMATION);

		alert.setContentText(msg);

		alert.showAndWait();
	}

	private void mostrarAlerta(String msg) {

		Alert alert =
				new Alert(Alert.AlertType.WARNING);

		alert.setContentText(msg);

		alert.showAndWait();
	}

	private void mostrarErroGeracao(String mensagem) {

		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Erro de Geração BPA-I");
		alert.setHeaderText(null);

		TextArea area = new TextArea(mensagem);
		area.setEditable(false);
		area.setWrapText(false);
		area.setPrefHeight(350);
		area.setPrefWidth(650);

		alert.getDialogPane().setContent(area);
		alert.showAndWait();
	}
}
