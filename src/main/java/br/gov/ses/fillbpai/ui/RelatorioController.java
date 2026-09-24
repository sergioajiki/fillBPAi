package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.dto.AtendimentoBPAiDTO;
import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.service.GeradorBPAiService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;

import javafx.stage.Window;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RelatorioController {

	// ======================================================
	// ENTITY MANAGER
	// ======================================================

	private final EntityManager entityManager;

	private static final DateTimeFormatter FORMATO_YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

	// ======================================================
	// LISTAS
	// ======================================================

	private final ObservableList<AtendimentoBPAiDTO> lista =
			FXCollections.observableArrayList();

	private final FilteredList<AtendimentoBPAiDTO> listaFiltrada =
			new FilteredList<>(lista, p -> true);

	private final SortedList<AtendimentoBPAiDTO> listaSorted =
			new SortedList<>(listaFiltrada);

	// ======================================================
	// ÁRVORE DE ESPECIALIDADES / MÉDICOS (navegação lateral)
	// ======================================================

	private enum TipoNo { ESPECIALIDADE, MEDICO }

	/** Nó da árvore lateral: uma especialidade (medico == null) ou um médico dentro dela. */
	private static class NoArvore {
		final TipoNo tipo;
		final String especialidade;
		final String medico;
		final String rotulo;

		NoArvore(TipoNo tipo, String especialidade, String medico, String rotulo) {
			this.tipo = tipo;
			this.especialidade = especialidade;
			this.medico = medico;
			this.rotulo = rotulo;
		}
	}

	/** Especialidade -> médicos distintos, usado para (re)construir a árvore ao filtrar. */
	private final Map<String, List<String>> especialidadeMedicoMap = new TreeMap<>();

	private TreeView<NoArvore> arvore;

	private TextField campoBuscaArvore = new TextField();

	/** Especialidade e médico atualmente selecionados na árvore (null = nada selecionado). */
	private String especialidadeSelecionada = null;
	private String medicoSelecionado = null;

	// ======================================================
	// COMPONENTES
	// ======================================================

	private TableView<AtendimentoBPAiDTO> tabela;

	private Label totalLabel;

	/** Breadcrumb exibido acima da tabela com o contexto da seleção atual na árvore. */
	private Label crumbLabel = new Label("Todos os atendimentos");

	/** Legenda do modo de exibição ativo (Competência/Período/Completo), ao lado do toggle. */
	private Label lblModoExibicao = new Label("Competência selecionada");

	// Campo para edição do CNS do profissional (habilitado quando um médico está selecionado na árvore)
	private TextField campoCnsProfissional = new TextField();

	private Button btnAtualizar = new Button("Atualizar CNS");

	private Button btnFolha = new Button("Definir Folha");

	private Button btnLimpar = new Button("Limpar");

	// BOTÃO DE GERAÇÃO ÚNICO — texto e ação se adaptam à seleção da árvore:
	// médico selecionado -> gera parcial (aquele médico); nada selecionado -> gera completo
	private Button btnGerar = new Button("Gerar BPA-I — Completo (todos os médicos)");

	// TOGGLE EXIBIÇÃO — alterna o filtro da tabela entre "competência
	// selecionada", "período" (intervalo de competências) e "completo"
	// (todas as competências). Nunca afeta competenciaSelecionada nem a
	// geração do BPA-I, que sempre usa a competência selecionada no badge
	// da barra inferior, independentemente do modo de exibição.
	private enum ModoExibicao { COMPETENCIA, PERIODO, COMPLETO }
	private ModoExibicao modoExibicao = ModoExibicao.COMPETENCIA;

	private final ToggleGroup grupoExibicao = new ToggleGroup();
	private ToggleButton toggleExibirCompetencia = new ToggleButton("Competência");
	private ToggleButton toggleExibirPeriodo = new ToggleButton("Período");
	private ToggleButton toggleExibirCompleto = new ToggleButton("Completo");

	/** Intervalo de competências (formato YYYYMM) usado no modo de exibição "Período" */
	private String periodoInicio = null;
	private String periodoFim = null;

	private Button btnSelecionarPeriodo = new Button("Selecionar Período");

	// BADGE/BOTÃO DE COMPETÊNCIA — única exibição da competência de geração,
	// fica na barra fixa inferior; clicar abre o seletor de mês
	private Button btnCompetencia = new Button("📅 Competência: --");

	// CHIP DE AVISO CONTEXTUAL — mostra pendências da geração completa
	// (nada selecionado) ou da geração parcial (médico selecionado)
	private Button btnAvisoBottom = new Button("⚠ Pendências");

	/** Mensagem de pendências do BPA-I parcial (null = sem pendências) para o médico selecionado */
	private String avisosParcial = null;

	/** Mensagem de pendências do BPA-I completo (null = sem pendências) */
	private String avisosGeracao = null;

	// BOTÃO VER LOG — exibe o log da última importação
	private Button btnVerLog = new Button("Ver Log Importação");

	// BOTÃO ANALISAR PLANILHA — valida a planilha antes de importar
	private Button btnAnalisarPlanilha = new Button("Analisar Planilha");

	/** Barra de edição (CNS e Folha) — visível apenas quando um médico está selecionado na árvore */
	private HBox barraEdicao;

	/** Competência selecionada para geração BPA-I (formato YYYYMM do mês de atendimento) */
	private String competenciaSelecionada = null;

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
		btnVerLog.setOnAction(e -> acao.run());
	}

	/**
	 * Retorna o botão "Ver Log Importação" para ser posicionado pelo MainController.
	 */
	public Button getBtnVerLog() {
		return btnVerLog;
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

	// ======================================================
	// CRIAR COMPONENTE PRINCIPAL
	// ======================================================

	public BorderPane criarComponente() {

		tabela = new TableView<>();

		// IMPORTANTE:
		// sempre usar listaFiltrada, nunca alterar colunas dinamicamente
		listaSorted.comparatorProperty().bind(tabela.comparatorProperty());
		tabela.setItems(listaSorted);

		// Desativa o resize automático que comprime colunas para caber na janela.
		// Com UNCONSTRAINED, cada coluna mantém seu prefWidth (150px), e a própria
		// TableView exibe sua barra de rolagem horizontal nativa quando o total das
		// colunas ultrapassa a largura visível — não precisa (e não deve) ser
		// envolvida por um ScrollPane extra, senão aparecem duas barras horizontais
		// (a nativa da tabela e a do ScrollPane).
		tabela.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

		totalLabel = new Label("Total: 0");

		configurarColunas(); // cria TODAS as colunas

		// Tabela cresce para ocupar todo o espaço vertical disponível
		VBox.setVgrow(tabela, Priority.ALWAYS);

		BorderPane pane = new BorderPane();
		pane.setTop(criarBarraExibicao());
		pane.setLeft(criarSidebarNavegacao());
		pane.setCenter(criarAreaConteudo());
		pane.setBottom(criarBarraGeracao());

		return pane;
	}

	// ======================================================
	// LINHA SUPERIOR — MODO DE EXIBIÇÃO DA TABELA
	// (nunca afeta a geração do BPA-I, só o que a tabela mostra)
	// ======================================================

	private HBox criarBarraExibicao() {

		toggleExibirCompetencia.setToggleGroup(grupoExibicao);
		toggleExibirPeriodo.setToggleGroup(grupoExibicao);
		toggleExibirCompleto.setToggleGroup(grupoExibicao);
		toggleExibirCompetencia.setSelected(true);
		toggleExibirCompetencia.setStyle("-fx-background-radius: 4 0 0 4; -fx-border-radius: 4 0 0 4;");
		toggleExibirPeriodo.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;");
		toggleExibirCompleto.setStyle("-fx-background-radius: 0 4 4 0; -fx-border-radius: 0 4 4 0;");

		// Botão "Selecionar Período" só aparece no modo Período — nasce oculto
		btnSelecionarPeriodo.setVisible(false);
		btnSelecionarPeriodo.setManaged(false);
		btnSelecionarPeriodo.setOnAction(e -> abrirDialogSelecionarPeriodo());

		toggleExibirCompetencia.setOnAction(e -> {
			modoExibicao = ModoExibicao.COMPETENCIA;
			atualizarBotaoSelecao();
			atualizarLabelModoExibicao();
			aplicarFiltros();
			atualizarContexto();
		});
		toggleExibirPeriodo.setOnAction(e -> {
			modoExibicao = ModoExibicao.PERIODO;
			atualizarBotaoSelecao();
			if (periodoInicio == null || periodoFim == null) {
				// Primeira vez neste modo — já pede o intervalo
				abrirDialogSelecionarPeriodo();
			} else {
				atualizarLabelModoExibicao();
				aplicarFiltros();
				atualizarContexto();
			}
		});
		toggleExibirCompleto.setOnAction(e -> {
			modoExibicao = ModoExibicao.COMPLETO;
			atualizarBotaoSelecao();
			atualizarLabelModoExibicao();
			aplicarFiltros();
			atualizarContexto();
		});

		HBox grupoToggleExibicao = new HBox(0,
				toggleExibirCompetencia, toggleExibirPeriodo, toggleExibirCompleto);

		lblModoExibicao.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

		HBox barra = new HBox(10,
				new Label("Exibir tabela:"), grupoToggleExibicao, btnSelecionarPeriodo, lblModoExibicao);
		barra.setPadding(new Insets(10));
		barra.setAlignment(Pos.CENTER_LEFT);
		barra.setStyle("-fx-border-color: transparent transparent #E5E7EB transparent; -fx-border-width: 1;");

		return barra;
	}

	// ======================================================
	// SIDEBAR — BUSCA + ÁRVORE DE ESPECIALIDADES/MÉDICOS
	// Substitui a antiga busca livre + combos de especialidade/médico
	// ======================================================

	private VBox criarSidebarNavegacao() {

		Label titulo = new Label("ESPECIALIDADES E MÉDICOS");
		titulo.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #6B7280;");

		campoBuscaArvore.setPromptText("Buscar...");
		campoBuscaArvore.textProperty().addListener((obs, antigo, novo) -> filtrarArvore(novo));

		btnLimpar.setOnAction(e -> limparSelecao());

		HBox linhaBusca = new HBox(6, campoBuscaArvore, btnLimpar);
		HBox.setHgrow(campoBuscaArvore, Priority.ALWAYS);

		arvore = new TreeView<>();
		arvore.setShowRoot(false);
		arvore.setRoot(new TreeItem<>());
		arvore.setCellFactory(tv -> new TreeCell<>() {
			@Override
			protected void updateItem(NoArvore item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.rotulo);
			}
		});
		arvore.getSelectionModel().selectedItemProperty().addListener(
				(obs, antigo, novo) -> selecionarNaArvore(novo));

		VBox.setVgrow(arvore, Priority.ALWAYS);

		VBox sidebar = new VBox(8, titulo, linhaBusca, arvore);
		sidebar.setPadding(new Insets(10));
		sidebar.setPrefWidth(230);
		sidebar.setMinWidth(190);
		sidebar.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: transparent #E5E7EB transparent transparent; -fx-border-width: 1;");

		return sidebar;
	}

	/**
	 * Chamado quando a seleção na árvore muda (clique do usuário ou
	 * restauração automática após recarregar os dados).
	 */
	private void selecionarNaArvore(TreeItem<NoArvore> item) {

		if (item == null || item.getValue() == null) {
			especialidadeSelecionada = null;
			medicoSelecionado = null;
		} else {
			NoArvore no = item.getValue();
			especialidadeSelecionada = no.especialidade;
			medicoSelecionado = no.tipo == TipoNo.MEDICO ? no.medico : null;
		}

		aplicarFiltros();
		atualizarContexto();
		atualizarBarraGeracao();

		if (medicoSelecionado != null) {
			barraEdicao.setVisible(true);
			barraEdicao.setManaged(true);
		} else {
			barraEdicao.setVisible(false);
			barraEdicao.setManaged(false);
		}
	}

	/**
	 * Reconstrói o mapa especialidade -> médicos a partir dos dados carregados
	 * e repopula a árvore (aplicando o texto de busca atual, se houver).
	 */
	private void construirArvore() {

		especialidadeMedicoMap.clear();

		Map<String, TreeSet<String>> agrupado = lista.stream()
				.filter(dto -> dto.getEspecialidadeMedico() != null && !dto.getEspecialidadeMedico().isEmpty())
				.collect(Collectors.groupingBy(
						AtendimentoBPAiDTO::getEspecialidadeMedico,
						TreeMap::new,
						Collectors.mapping(AtendimentoBPAiDTO::getMedico, Collectors.toCollection(TreeSet::new))));

		agrupado.forEach((esp, medicos) -> especialidadeMedicoMap.put(
				esp, medicos.stream().filter(m -> m != null && !m.isBlank()).collect(Collectors.toList())));

		filtrarArvore(campoBuscaArvore.getText());
	}

	/**
	 * Repopula a árvore a partir de {@code especialidadeMedicoMap}, mostrando
	 * apenas especialidades/médicos que casam com o termo de busca (ou tudo,
	 * se o termo estiver vazio), e tenta restaurar a seleção atual.
	 */
	private void filtrarArvore(String termo) {

		String t = termo == null ? "" : termo.trim().toUpperCase();

		TreeItem<NoArvore> raiz = new TreeItem<>();

		for (Map.Entry<String, List<String>> entrada : especialidadeMedicoMap.entrySet()) {

			String esp = entrada.getKey();
			boolean espCasa = t.isEmpty() || esp.toUpperCase().contains(t);

			List<String> medicosCasam = entrada.getValue().stream()
					.filter(m -> espCasa || m.toUpperCase().contains(t))
					.collect(Collectors.toList());

			if (t.isEmpty() || espCasa || !medicosCasam.isEmpty()) {

				TreeItem<NoArvore> noEsp = new TreeItem<>(new NoArvore(
						TipoNo.ESPECIALIDADE, esp, null,
						esp + " (" + entrada.getValue().size() + ")"));

				for (String medico : medicosCasam) {
					noEsp.getChildren().add(new TreeItem<>(
							new NoArvore(TipoNo.MEDICO, esp, medico, medico)));
				}

				noEsp.setExpanded(!t.isEmpty() || esp.equals(especialidadeSelecionada));

				raiz.getChildren().add(noEsp);
			}
		}

		arvore.setRoot(raiz);

		restaurarSelecaoArvore();
	}

	/** Reaplica a seleção de especialidade/médico atual na árvore recém-reconstruída. */
	private void restaurarSelecaoArvore() {

		if (especialidadeSelecionada == null) {
			return;
		}

		for (TreeItem<NoArvore> noEsp : arvore.getRoot().getChildren()) {

			if (!noEsp.getValue().especialidade.equals(especialidadeSelecionada)) {
				continue;
			}

			if (medicoSelecionado == null) {
				arvore.getSelectionModel().select(noEsp);
				return;
			}

			for (TreeItem<NoArvore> noMedico : noEsp.getChildren()) {
				if (medicoSelecionado.equals(noMedico.getValue().medico)) {
					noEsp.setExpanded(true);
					arvore.getSelectionModel().select(noMedico);
					return;
				}
			}

			return;
		}
	}

	private void limparSelecao() {

		campoBuscaArvore.clear();

		especialidadeSelecionada = null;
		medicoSelecionado = null;
		arvore.getSelectionModel().clearSelection();

		campoCnsProfissional.clear();
		barraEdicao.setVisible(false);
		barraEdicao.setManaged(false);

		filtrarArvore("");
		aplicarFiltros();
		atualizarContexto();
		atualizarBarraGeracao();
	}

	// ======================================================
	// ÁREA CENTRAL — BREADCRUMB + EDIÇÃO CONTEXTUAL + TABELA
	// ======================================================

	private VBox criarAreaConteudo() {

		crumbLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B7280;");

		VBox box = new VBox(8, crumbLabel, criarBarraEdicao(), tabela, totalLabel);
		box.setPadding(new Insets(10));

		return box;
	}

	/** Atualiza o texto do breadcrumb com o contexto atual da árvore. */
	private void atualizarContexto() {

		if (medicoSelecionado != null) {
			crumbLabel.setText(especialidadeSelecionada + "  ›  " + medicoSelecionado
					+ "  —  " + listaFiltrada.size() + " atendimento(s) na competência selecionada");
		} else if (especialidadeSelecionada != null) {
			crumbLabel.setText(especialidadeSelecionada + "  —  todos os médicos  —  "
					+ listaFiltrada.size() + " atendimento(s)");
		} else {
			crumbLabel.setText("Todos os atendimentos  —  " + listaFiltrada.size() + " registro(s)");
		}
	}

	// ======================================================
	// BARRA DE EDIÇÃO (CNS e Folha) — contextual ao médico selecionado
	// ======================================================

	private HBox criarBarraEdicao() {

		campoCnsProfissional.setPromptText("Novo CNS");

		btnAtualizar.setOnAction(e -> atualizarCns());
		btnFolha.setOnAction(e -> definirFolha());

		barraEdicao = new HBox(10,
				new Label("CNS:"), campoCnsProfissional,
				btnAtualizar,
				btnFolha
		);
		barraEdicao.setVisible(false);
		barraEdicao.setManaged(false);

		return barraEdicao;
	}

	// ======================================================
	// BARRA FIXA INFERIOR — COMPETÊNCIA + AVISO + GERAR BPA-I
	// Única fonte de verdade da competência de geração; o botão de gerar
	// muda de texto/ação conforme a seleção atual na árvore.
	// ======================================================

	private HBox criarBarraGeracao() {

		btnCompetencia.setOnAction(e -> abrirDialogSelecionarCompetencia());
		btnCompetencia.setStyle(
				"-fx-background-color: #DCEAE4; -fx-text-fill: #2F6F5E; -fx-font-weight: bold; " +
				"-fx-padding: 6 14 6 14; -fx-background-radius: 100; -fx-cursor: hand;");

		btnAvisoBottom.setStyle(
				"-fx-background-color: #FFC107; -fx-text-fill: #000; " +
				"-fx-font-weight: bold; -fx-cursor: hand;");
		btnAvisoBottom.setVisible(false);
		btnAvisoBottom.setManaged(false);

		btnGerar.setStyle(
				"-fx-background-color: #2F6F5E; -fx-text-fill: white; " +
				"-fx-font-weight: bold; -fx-padding: 8 16 8 16; -fx-background-radius: 5;");
		btnGerar.setOnAction(e -> {
			if (medicoSelecionado != null) {
				gerarBPA();
			} else {
				gerarBPACompleto();
			}
		});

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox barra = new HBox(10, btnCompetencia, spacer, btnAvisoBottom, btnGerar);
		barra.setPadding(new Insets(10, 16, 10, 16));
		barra.setAlignment(Pos.CENTER_LEFT);
		barra.setStyle("-fx-background-color: #1F2A24;");

		return barra;
	}

	/**
	 * Atualiza o texto do botão de geração e o chip de aviso contextual
	 * conforme a seleção atual na árvore (médico selecionado -> parcial;
	 * nada selecionado -> completo).
	 */
	private void atualizarBarraGeracao() {

		if (medicoSelecionado != null) {

			btnGerar.setText("Gerar BPA-I — " + medicoSelecionado + " (" + especialidadeSelecionada + ")");
			verificarAvisosParcial();
			atualizarChipAviso(avisosParcial, this::mostrarAvisosParcial);

		} else {

			btnGerar.setText("Gerar BPA-I — Completo (todos os médicos)");
			atualizarChipAviso(avisosGeracao, this::mostrarAvisosGeracao);
		}
	}

	private void atualizarChipAviso(String mensagem, Runnable acaoClique) {

		if (mensagem == null) {
			btnAvisoBottom.setVisible(false);
			btnAvisoBottom.setManaged(false);
			return;
		}

		btnAvisoBottom.setVisible(true);
		btnAvisoBottom.setManaged(true);
		btnAvisoBottom.setOnAction(e -> acaoClique.run());
	}

	// ======================================================
	// SELEÇÃO DE COMPETÊNCIA
	// ======================================================

	private void abrirDialogSelecionarCompetencia() {

		int anoInicial  = LocalDate.now().getYear();
		int mesInicial  = LocalDate.now().getMonthValue();

		if (competenciaSelecionada != null) {
			anoInicial = Integer.parseInt(competenciaSelecionada.substring(0, 4));
			mesInicial = Integer.parseInt(competenciaSelecionada.substring(4, 6));
		}

		Spinner<Integer> spinnerMes = new Spinner<>(1, 12, mesInicial);
		spinnerMes.setEditable(true);
		spinnerMes.setPrefWidth(70);

		Spinner<Integer> spinnerAno = new Spinner<>(2000, 2100, anoInicial);
		spinnerAno.setEditable(true);
		spinnerAno.setPrefWidth(90);

		HBox content = new HBox(10,
				new Label("Mês:"), spinnerMes,
				new Label("Ano:"), spinnerAno);
		content.setPadding(new Insets(10));

		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Selecionar Competência");
		dialog.setHeaderText("Escolha o mês de competência para geração do BPA-I");
		dialog.getDialogPane().setContent(content);

		ButtonType confirmar = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);

		dialog.setResultConverter(btn -> {
			if (btn == confirmar) {
				return String.format("%04d%02d", spinnerAno.getValue(), spinnerMes.getValue());
			}
			return null;
		});

		dialog.showAndWait().ifPresent(comp -> {
			competenciaSelecionada = comp;
			int m = Integer.parseInt(comp.substring(4, 6));
			int a = Integer.parseInt(comp.substring(0, 4));
			atualizarTextoCompetencia(String.format("Competência: %02d/%04d", m, a));

			// A tabela deve refletir exatamente os atendimentos da competência
			// selecionada — o mesmo conjunto que seria incluído no BPA-I completo.
			aplicarFiltros();
			atualizarContexto();
			atualizarBarraGeracao();
		});
	}

	/**
	 * Abre o diálogo de seleção de período (mês/ano inicial e final) usado
	 * pelo modo de exibição "Período" — só afeta o filtro da tabela, nunca
	 * a competência usada na geração do BPA-I.
	 */
	private void abrirDialogSelecionarPeriodo() {

		int anoInicial = LocalDate.now().getYear();
		int mesInicial = LocalDate.now().getMonthValue();

		int anoInicialDe = anoInicial;
		int mesInicialDe = mesInicial;
		int anoInicialAte = anoInicial;
		int mesInicialAte = mesInicial;

		if (periodoInicio != null) {
			anoInicialDe = Integer.parseInt(periodoInicio.substring(0, 4));
			mesInicialDe = Integer.parseInt(periodoInicio.substring(4, 6));
		}
		if (periodoFim != null) {
			anoInicialAte = Integer.parseInt(periodoFim.substring(0, 4));
			mesInicialAte = Integer.parseInt(periodoFim.substring(4, 6));
		}

		Spinner<Integer> spinnerMesDe = new Spinner<>(1, 12, mesInicialDe);
		spinnerMesDe.setEditable(true);
		spinnerMesDe.setPrefWidth(70);

		Spinner<Integer> spinnerAnoDe = new Spinner<>(2000, 2100, anoInicialDe);
		spinnerAnoDe.setEditable(true);
		spinnerAnoDe.setPrefWidth(90);

		Spinner<Integer> spinnerMesAte = new Spinner<>(1, 12, mesInicialAte);
		spinnerMesAte.setEditable(true);
		spinnerMesAte.setPrefWidth(70);

		Spinner<Integer> spinnerAnoAte = new Spinner<>(2000, 2100, anoInicialAte);
		spinnerAnoAte.setEditable(true);
		spinnerAnoAte.setPrefWidth(90);

		VBox content = new VBox(10,
				new HBox(10, new Label("De:"), spinnerMesDe, spinnerAnoDe),
				new HBox(10, new Label("Até:"), spinnerMesAte, spinnerAnoAte));
		content.setPadding(new Insets(10));

		Dialog<String[]> dialog = new Dialog<>();
		dialog.setTitle("Selecionar Período de Exibição");
		dialog.setHeaderText("Escolha o intervalo de competências para exibir na tabela");
		dialog.getDialogPane().setContent(content);

		ButtonType confirmar = new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(confirmar, ButtonType.CANCEL);

		dialog.setResultConverter(btn -> {
			if (btn == confirmar) {
				String de = String.format("%04d%02d", spinnerAnoDe.getValue(), spinnerMesDe.getValue());
				String ate = String.format("%04d%02d", spinnerAnoAte.getValue(), spinnerMesAte.getValue());
				return new String[]{de, ate};
			}
			return null;
		});

		dialog.showAndWait().ifPresent(par -> {
			String de = par[0];
			String ate = par[1];

			// Troca automaticamente se "até" vier antes de "de" — evita
			// período vazio por engano de digitação.
			if (ate.compareTo(de) < 0) {
				String tmp = de;
				de = ate;
				ate = tmp;
			}

			periodoInicio = de;
			periodoFim = ate;
		});

		atualizarLabelModoExibicao();
		aplicarFiltros();
		atualizarContexto();
	}

	// ======================================================
	// AVISOS / PENDÊNCIAS
	// ======================================================

	/**
	 * Verifica no banco se existem atendimentos sem CNS do profissional.
	 * Usado pelo chip de aviso da barra inferior quando nenhum médico está
	 * selecionado (contexto de geração "Completo").
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

		} catch (Exception e) {
			// Falha silenciosa — não impede o carregamento da tabela
		}
	}

	/**
	 * Verifica folha e CNS ausentes para o médico/especialidade selecionados
	 * na árvore. Usado pelo chip de aviso da barra inferior quando um médico
	 * está selecionado (contexto de geração "parcial").
	 */
	private void verificarAvisosParcial() {

		String especialidade = especialidadeSelecionada;
		String medico = medicoSelecionado;

		if (especialidade == null || medico == null) {
			avisosParcial = null;
			return;
		}

		try {

			long semFolha = (long) entityManager.createQuery(
					"SELECT COUNT(a) FROM AtendimentoBPAi a JOIN a.medico m " +
					"WHERE m.nome = :medico AND a.especialidadeMedico = :esp " +
					"AND (a.folha IS NULL OR a.folha = '')")
					.setParameter("medico", medico)
					.setParameter("esp", especialidade)
					.getSingleResult();

			long semCns = (long) entityManager.createQuery(
					"SELECT COUNT(a) FROM AtendimentoBPAi a JOIN a.medico m " +
					"WHERE m.nome = :medico AND a.especialidadeMedico = :esp " +
					"AND (a.cnsProfissional IS NULL OR a.cnsProfissional = '')")
					.setParameter("medico", medico)
					.setParameter("esp", especialidade)
					.getSingleResult();

			if (semFolha == 0 && semCns == 0) {
				avisosParcial = null;
				return;
			}

			StringBuilder sb = new StringBuilder();
			sb.append("=== PENDÊNCIAS — GERAÇÃO BPA-I PARCIAL ===\n");
			sb.append("Médico: ").append(medico).append("\n");
			sb.append("Especialidade: ").append(especialidade).append("\n\n");
			if (semFolha > 0)
				sb.append("⚠ Folha ausente: ").append(semFolha).append(" atendimento(s)\n");
			if (semCns > 0)
				sb.append("⚠ CNS do profissional ausente: ").append(semCns).append(" atendimento(s)\n");

			avisosParcial = sb.toString();

		} catch (Exception e) {
			// Falha silenciosa
		}
	}

	private void mostrarAvisosParcial() {

		if (avisosParcial == null) return;

		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Pendências — Geração BPA-I Parcial");
		alert.setHeaderText("Existem pendências que impedem a geração");

		TextArea area = new TextArea(avisosParcial);
		area.setEditable(false);
		area.setWrapText(false);
		area.setPrefHeight(200);
		area.setPrefWidth(500);

		alert.getDialogPane().setContent(area);
		alert.showAndWait();
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

		String medico = medicoSelecionado;
		String especialidade = especialidadeSelecionada;

		if (medico == null || especialidade == null) {
			mostrarMensagem("Selecione um médico na árvore.");
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

		String especialidade = especialidadeSelecionada;

		String medico = medicoSelecionado;

		if (especialidade == null || medico == null) {
			mostrarMensagem("Selecione um médico na árvore.");
			return;
		}

		if (competenciaSelecionada == null) {
			mostrarMensagem("Selecione a competência (mês) antes de gerar o BPA-I.");
			return;
		}

		if (avisosParcial != null) {
			mostrarAvisosParcial();
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
					medico,
					competenciaSelecionada
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

		if (competenciaSelecionada == null) {
			mostrarMensagem("Selecione a competência antes de gerar o BPA-I Completo.");
			return;
		}

		try {

			GeradorBPAiService service =
					new GeradorBPAiService(entityManager);

			Window window =
					tabela.getScene().getWindow();

			service.gerarArquivoCompletoComFileChooser(window, competenciaSelecionada);

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

	private void aplicarFiltros() {

		listaFiltrada.setPredicate(dto -> {

			boolean esp = true;
			boolean med = true;

			if (especialidadeSelecionada != null)
				esp = especialidadeSelecionada.equals(dto.getEspecialidadeMedico());

			if (medicoSelecionado != null)
				med = medicoSelecionado.equals(dto.getMedico());

			return esp && med && competenciaCoincide(dto);
		});

		totalLabel.setText(
				"Total: " + listaFiltrada.size());
	}

	/**
	 * Verifica se a data de agendamento do atendimento pertence à competência
	 * selecionada — mesma regra usada por {@code GeradorBPAiService} ao filtrar
	 * por YEAR/MONTH(dataAgendamento) na geração do BPA-I completo. Mantém a
	 * tabela mostrando exatamente o que seria incluído no arquivo gerado.
	 * Sem competência selecionada (banco vazio) ou no modo "Completo", não filtra.
	 */
	private boolean competenciaCoincide(AtendimentoBPAiDTO dto) {

		if (modoExibicao == ModoExibicao.COMPLETO) {
			return true;
		}

		String dataStr = dto.getDataAgendamento();

		if (dataStr == null || dataStr.isBlank()) {
			return false;
		}

		try {
			YearMonth ym = YearMonth.from(LocalDate.parse(dataStr, DateTimeFormatter.ofPattern("dd/MM/yyyy")));

			if (modoExibicao == ModoExibicao.PERIODO) {

				if (periodoInicio == null || periodoFim == null) {
					return true;
				}

				YearMonth inicio = YearMonth.parse(periodoInicio, FORMATO_YYYYMM);
				YearMonth fim = YearMonth.parse(periodoFim, FORMATO_YYYYMM);

				return !ym.isBefore(inicio) && !ym.isAfter(fim);
			}

			// ModoExibicao.COMPETENCIA
			if (competenciaSelecionada == null) {
				return true;
			}

			return competenciaSelecionada.equals(ym.format(FORMATO_YYYYMM));

		} catch (Exception e) {
			return false;
		}
	}

	/** Mostra/oculta [Selecionar Período] conforme o modo de exibição ativo. */
	private void atualizarBotaoSelecao() {

		boolean periodo = modoExibicao == ModoExibicao.PERIODO;

		btnSelecionarPeriodo.setVisible(periodo);
		btnSelecionarPeriodo.setManaged(periodo);
	}

	/** Atualiza a legenda do modo de exibição ativo, ao lado do toggle. */
	private void atualizarLabelModoExibicao() {

		switch (modoExibicao) {

			case PERIODO -> {
				if (periodoInicio == null || periodoFim == null) {
					lblModoExibicao.setText("Período: --");
				} else {
					lblModoExibicao.setText("Período: "
							+ formatarCompetenciaExibicao(periodoInicio) + " – "
							+ formatarCompetenciaExibicao(periodoFim));
				}
			}

			case COMPLETO -> lblModoExibicao.setText("Todas as competências");

			default -> lblModoExibicao.setText("Competência selecionada");
		}
	}

	/** Formata uma competência YYYYMM como MM/YYYY para exibição. */
	private String formatarCompetenciaExibicao(String competenciaYyyyMM) {
		return competenciaYyyyMM.substring(4, 6) + "/" + competenciaYyyyMM.substring(0, 4);
	}

	// ======================================================
	// ATUALIZAÇÃO BANCO
	// ======================================================

	/**
	 * Atualiza o CNS do profissional para todos os atendimentos
	 * do médico e especialidade selecionados na árvore.
	 *
	 * A folha não é mais editável pela UI — é controlada pelo gerador.
	 */
	private void atualizarCns() {

		String medico = medicoSelecionado;
		String especialidade = especialidadeSelecionada;

		if (medico == null || especialidade == null) {
			mostrarMensagem("Selecione um médico na árvore.");
			return;
		}

		entityManager.getTransaction().begin();

		try {

			List<AtendimentoBPAi> atendimentos = entityManager.createQuery(
							"SELECT a FROM AtendimentoBPAi a JOIN a.medico m " +
							"WHERE m.nome = :medico AND a.especialidadeMedico = :esp",
							AtendimentoBPAi.class)
					.setParameter("medico", medico)
					.setParameter("esp", especialidade)
					.getResultList();

			for (AtendimentoBPAi a : atendimentos) {
				a.setCnsProfissional(campoCnsProfissional.getText());
			}

			entityManager.getTransaction().commit();

			mostrarMensagem("Atualizado.");

			carregarDoBanco();

		} catch (Exception ex) {

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

				criarColuna("Etnia",
						AtendimentoBPAiDTO::getEtniaPaciente),

				criarColuna("Situação de Rua",
						AtendimentoBPAiDTO::getSituacaoRuaPaciente),

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

		construirArvore();

		atualizarCompetencia();

		aplicarFiltros();

		atualizarContexto();

		atualizarBarraGeracao();
	}

	public void carregarDoBanco() {

		// Evita que o cache L1 do EntityManager devolva entidades antigas —
		// garante que a query sempre leia o estado atual do banco.
		entityManager.clear();

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

		atualizarBarraGeracao();
	}

	// ======================================================
	// COMPETÊNCIA
	// ======================================================

	/**
	 * Atualiza o texto do badge de competência com base nos dados carregados.
	 * Competência = yyyyMM da data de agendamento mais recente.
	 */
	private void atualizarCompetencia() {

		// Se o usuário já selecionou manualmente, preserva a seleção
		if (competenciaSelecionada != null) {
			return;
		}

		DateTimeFormatter fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy");

		Optional<LocalDate> dataMaisRecente = lista.stream()
				.map(AtendimentoBPAiDTO::getDataAgendamento)
				.filter(d -> d != null && !d.isBlank())
				.map(d -> {
					try {
						return LocalDate.parse(d, fmtData);
					} catch (Exception e) {
						return null;
					}
				})
				.filter(java.util.Objects::nonNull)
				.max(LocalDate::compareTo);

		if (dataMaisRecente.isEmpty()) {
			atualizarTextoCompetencia("Competência: --");
			return;
		}

		LocalDate data = dataMaisRecente.get();

		// Auto-detecta a competência mais recente entre os dados carregados
		competenciaSelecionada = data.format(DateTimeFormatter.ofPattern("yyyyMM"));

		atualizarTextoCompetencia("Competência: "
				+ data.format(DateTimeFormatter.ofPattern("MM/yyyy")));
	}

	/** Atualiza o texto do badge/botão de competência na barra inferior. */
	private void atualizarTextoCompetencia(String texto) {
		btnCompetencia.setText("📅 " + texto);
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
