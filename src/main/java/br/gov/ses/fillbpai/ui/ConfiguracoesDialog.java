package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.util.ColunaAliasUtils;
import br.gov.ses.fillbpai.util.CnsProfissionalUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.TextoUtils;

import java.util.Comparator;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Tela de configurações da aplicação.
 * <p>
 * Duas abas: aliases de coluna da planilha (nomes de cabeçalho aceitos para
 * cada campo canônico, ver {@link ColunaAliasUtils}) e cadastro de CNS de
 * médicos (ver {@link CnsProfissionalUtils}), com apelidos de nome por CNS.
 */
public class ConfiguracoesDialog {

	public void abrir(Window owner) {

		Dialog<Void> dialog = new Dialog<>();
		dialog.setTitle("Configurações");
		dialog.initOwner(owner);
		dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
		dialog.getDialogPane().setPrefSize(680, 460);

		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

		Tab tabColunas = new Tab("Colunas da Planilha", criarPainelColunas());
		Tab tabCns = new Tab("CNS de Médicos", criarPainelMedicos());

		tabs.getTabs().addAll(tabColunas, tabCns);

		dialog.getDialogPane().setContent(tabs);
		dialog.showAndWait();
	}

	// ======================================================
	// ABA: COLUNAS DA PLANILHA
	// ======================================================

	private SplitPane criarPainelColunas() {

		ListView<String> listaCampos = new ListView<>();
		listaCampos.getItems().setAll(ColunaAliasUtils.obterCamposCanonicos());

		VBox detalhe = new VBox(14);
		detalhe.setPadding(new Insets(18));
		exibirPlaceholder(detalhe);

		listaCampos.getSelectionModel().selectedItemProperty().addListener(
				(obs, antigo, novo) -> atualizarDetalhe(detalhe, novo, listaCampos));

		SplitPane split = new SplitPane(listaCampos, detalhe);
		split.setDividerPositions(0.32);
		return split;
	}

	private void exibirPlaceholder(VBox detalhe) {
		detalhe.getChildren().setAll(new Label("Selecione um campo à esquerda."));
	}

	/** Reconstrói o painel de detalhe do campo selecionado — chamado após qualquer alteração. */
	private void atualizarDetalhe(VBox detalhe, String campo, ListView<String> listaCampos) {

		if (campo == null) {
			exibirPlaceholder(detalhe);
			return;
		}

		Label titulo = new Label("Aliases aceitos para " + campo);
		titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

		Label subtitulo = new Label("Nomes de cabeçalho que a planilha pode usar para esta coluna.");
		subtitulo.setWrapText(true);
		subtitulo.setStyle("-fx-text-fill: #666666;");

		FlowPane chips = new FlowPane(8, 8);
		for (ColunaAliasUtils.AliasInfo info : ColunaAliasUtils.obterAliases(campo)) {
			chips.getChildren().add(criarChip(info, campo, detalhe, listaCampos));
		}

		TextField campoNovoAlias = new TextField();
		campoNovoAlias.setPromptText("novo alias, ex.: NOME DA UNIDADE");
		HBox.setHgrow(campoNovoAlias, Priority.ALWAYS);

		Button btnAdicionar = new Button("+ Adicionar");
		btnAdicionar.setOnAction(e -> {
			String texto = campoNovoAlias.getText();
			if (texto != null && !texto.isBlank()) {
				ColunaAliasUtils.salvar(campo, texto.trim());
				atualizarDetalhe(detalhe, campo, listaCampos);
			}
		});

		HBox linhaAdicionar = new HBox(8, campoNovoAlias, btnAdicionar);

		Button btnRestaurar = new Button("Restaurar padrão desta coluna");
		btnRestaurar.setOnAction(e -> {
			ColunaAliasUtils.restaurarPadrao(campo);
			atualizarDetalhe(detalhe, campo, listaCampos);
		});

		detalhe.getChildren().setAll(titulo, subtitulo, chips, linhaAdicionar, btnRestaurar);
	}

	/** Chip visual de um alias — padrão (não removível) ou local (com botão de remoção). */
	private HBox criarChip(ColunaAliasUtils.AliasInfo info, String campo, VBox detalhe, ListView<String> listaCampos) {

		boolean padrao = info.origem() == ColunaAliasUtils.Origem.PADRAO;

		HBox chip = new HBox(6, new Label(info.nome()));
		chip.setPadding(new Insets(4, 10, 4, 10));
		chip.setStyle("-fx-border-radius: 100; -fx-background-radius: 100; -fx-border-width: 1; "
				+ "-fx-border-color: " + (padrao ? "#2F6F5E" : "#B0B0B0") + ";"
				+ (padrao ? " -fx-background-color: #DCEAE4;" : ""));

		if (!padrao) {
			Button remover = new Button("×");
			remover.setStyle("-fx-background-color: transparent; -fx-padding: 0 0 0 2; -fx-cursor: hand;");
			remover.setOnAction(e -> {
				ColunaAliasUtils.remover(campo, info.nome());
				atualizarDetalhe(detalhe, campo, listaCampos);
			});
			chip.getChildren().add(remover);
		}

		return chip;
	}

	// ======================================================
	// ABA: CNS DE MÉDICOS
	// ======================================================

	private SplitPane criarPainelMedicos() {

		ListView<String> listaMedicos = new ListView<>();

		TextField campoBusca = new TextField();
		campoBusca.setPromptText("Buscar médico...");

		VBox detalhe = new VBox(14);
		detalhe.setPadding(new Insets(18));
		exibirPlaceholderMedico(detalhe);

		campoBusca.textProperty().addListener(
				(obs, antigo, novo) -> recarregarListaMedicos(listaMedicos, novo));

		listaMedicos.getSelectionModel().selectedItemProperty().addListener((obs, antigo, novoNome) -> {
			if (novoNome == null) {
				exibirPlaceholderMedico(detalhe);
				return;
			}
			buscarMedicoPorNomePrincipal(novoNome)
					.ifPresentOrElse(
							info -> exibirFormularioEdicao(detalhe, info, listaMedicos, campoBusca),
							() -> exibirPlaceholderMedico(detalhe));
		});

		Button btnNovo = new Button("+ Novo Médico");
		btnNovo.setOnAction(e -> {
			listaMedicos.getSelectionModel().clearSelection();
			exibirFormularioCriacao(detalhe, listaMedicos, campoBusca);
		});

		recarregarListaMedicos(listaMedicos, "");

		VBox esquerda = new VBox(8, campoBusca, listaMedicos, btnNovo);
		esquerda.setPadding(new Insets(10));
		VBox.setVgrow(listaMedicos, Priority.ALWAYS);

		SplitPane split = new SplitPane(esquerda, detalhe);
		split.setDividerPositions(0.32);
		return split;
	}

	private void exibirPlaceholderMedico(VBox detalhe) {
		detalhe.getChildren().setAll(new Label("Selecione um médico à esquerda ou cadastre um novo."));
	}

	/** Repõe a lista de médicos (nome principal), filtrada pelo texto de busca (nome principal ou qualquer apelido). */
	private void recarregarListaMedicos(ListView<String> listaMedicos, String filtro) {

		String filtroNorm = TextoUtils.normalizar(filtro == null ? "" : filtro);

		List<String> nomes = CnsProfissionalUtils.obterTodosMedicos().stream()
				.filter(info -> filtroNorm.isEmpty() || info.apelidos().stream()
						.anyMatch(apelido -> TextoUtils.normalizar(apelido).contains(filtroNorm)))
				.map(CnsProfissionalUtils.MedicoInfo::nomePrincipal)
				.sorted(Comparator.comparing(String::toString, String.CASE_INSENSITIVE_ORDER))
				.toList();

		listaMedicos.getItems().setAll(nomes);
	}

	private java.util.Optional<CnsProfissionalUtils.MedicoInfo> buscarMedicoPorNomePrincipal(String nomePrincipal) {
		return CnsProfissionalUtils.obterTodosMedicos().stream()
				.filter(info -> info.nomePrincipal().equals(nomePrincipal))
				.findFirst();
	}

	private java.util.Optional<CnsProfissionalUtils.MedicoInfo> buscarMedicoPorCns(String cns) {
		return CnsProfissionalUtils.obterTodosMedicos().stream()
				.filter(info -> info.cns().equals(cns))
				.findFirst();
	}

	/** Recarrega a lista e reabre o médico (por CNS) no painel de detalhe — chamado após qualquer alteração. */
	private void atualizarAposMudancaMedico(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca, String cns) {

		recarregarListaMedicos(listaMedicos, campoBusca.getText());

		buscarMedicoPorCns(cns).ifPresentOrElse(info -> {
			listaMedicos.getSelectionModel().select(info.nomePrincipal());
			exibirFormularioEdicao(detalhe, info, listaMedicos, campoBusca);
		}, () -> {
			listaMedicos.getSelectionModel().clearSelection();
			exibirPlaceholderMedico(detalhe);
		});
	}

	private void mostrarErro(Label lblErro, String mensagem) {
		lblErro.setText(mensagem);
		lblErro.setVisible(true);
		lblErro.setManaged(true);
	}

	private Label criarLabelErro() {
		Label lblErro = new Label();
		lblErro.setWrapText(true);
		lblErro.setStyle("-fx-text-fill: #B00020;");
		lblErro.setVisible(false);
		lblErro.setManaged(false);
		return lblErro;
	}

	/** Reconstrói o painel de detalhe em modo edição de um médico já cadastrado. */
	private void exibirFormularioEdicao(VBox detalhe, CnsProfissionalUtils.MedicoInfo info,
			ListView<String> listaMedicos, TextField campoBusca) {

		String cns = info.cns();

		Label titulo = new Label("Editando: " + info.nomePrincipal());
		titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

		Label lblCns = new Label("CNS:");
		TextField campoCns = new TextField(cns);
		campoCns.setPromptText("15 dígitos");
		HBox linhaCns = new HBox(8, lblCns, campoCns);

		Label lblApelidos = new Label("Apelidos (nomes usados nas planilhas):");
		lblApelidos.setStyle("-fx-text-fill: #666666;");

		Label lblErro = criarLabelErro();

		FlowPane chips = new FlowPane(8, 8);
		for (String apelido : info.apelidos()) {
			chips.getChildren().add(criarChipApelido(apelido, info, detalhe, listaMedicos, campoBusca, lblErro));
		}

		TextField campoNovoApelido = new TextField();
		campoNovoApelido.setPromptText("novo apelido, ex.: nome abreviado");
		HBox.setHgrow(campoNovoApelido, Priority.ALWAYS);

		Button btnAdicionarApelido = new Button("+ Adicionar apelido");
		btnAdicionarApelido.setOnAction(e -> {

			String texto = campoNovoApelido.getText();

			if (texto == null || texto.isBlank()) {
				return;
			}

			try {
				CnsProfissionalUtils.adicionarApelido(cns, texto.trim());
				atualizarAposMudancaMedico(detalhe, listaMedicos, campoBusca, cns);
			} catch (IllegalArgumentException ex) {
				mostrarErro(lblErro, ex.getMessage());
			}
		});

		HBox linhaAdicionarApelido = new HBox(8, campoNovoApelido, btnAdicionarApelido);

		Button btnSalvarCns = new Button("Salvar CNS");
		btnSalvarCns.setOnAction(e -> {

			String cnsNormalizado = CnsUtils.normalizar(campoCns.getText());

			if (cnsNormalizado == null || cnsNormalizado.length() != 15) {
				mostrarErro(lblErro, "CNS deve ter exatamente 15 dígitos.");
				return;
			}

			try {
				CnsProfissionalUtils.alterarCns(cns, cnsNormalizado);
				atualizarAposMudancaMedico(detalhe, listaMedicos, campoBusca, cnsNormalizado);
			} catch (IllegalArgumentException ex) {
				mostrarErro(lblErro, ex.getMessage());
			}
		});

		Button btnRemoverMedico = new Button("Remover Médico");
		btnRemoverMedico.setOnAction(e -> {

			Alert confirmacao = new Alert(Alert.AlertType.CONFIRMATION,
					"Remover o médico \"" + info.nomePrincipal() + "\" e todos os seus apelidos?",
					ButtonType.OK, ButtonType.CANCEL);
			confirmacao.setHeaderText(null);

			confirmacao.showAndWait().filter(botao -> botao == ButtonType.OK).ifPresent(botao -> {
				CnsProfissionalUtils.removerMedico(cns);
				recarregarListaMedicos(listaMedicos, campoBusca.getText());
				listaMedicos.getSelectionModel().clearSelection();
				exibirPlaceholderMedico(detalhe);
			});
		});

		HBox linhaBotoes = new HBox(8, btnSalvarCns, btnRemoverMedico);

		detalhe.getChildren().setAll(
				titulo, linhaCns, lblApelidos, chips, linhaAdicionarApelido, lblErro, linhaBotoes);
	}

	/** Chip visual de um apelido, com botão de remoção (recusado pelo backend se for o único restante). */
	private HBox criarChipApelido(String apelido, CnsProfissionalUtils.MedicoInfo info, VBox detalhe,
			ListView<String> listaMedicos, TextField campoBusca, Label lblErro) {

		HBox chip = new HBox(6, new Label(apelido));
		chip.setPadding(new Insets(4, 10, 4, 10));
		chip.setStyle("-fx-border-radius: 100; -fx-background-radius: 100; -fx-border-width: 1; "
				+ "-fx-border-color: #B0B0B0;");

		Button remover = new Button("×");
		remover.setStyle("-fx-background-color: transparent; -fx-padding: 0 0 0 2; -fx-cursor: hand;");
		remover.setOnAction(e -> {
			try {
				CnsProfissionalUtils.removerApelido(info.cns(), apelido);
				atualizarAposMudancaMedico(detalhe, listaMedicos, campoBusca, info.cns());
			} catch (IllegalArgumentException ex) {
				mostrarErro(lblErro, ex.getMessage());
			}
		});
		chip.getChildren().add(remover);

		return chip;
	}

	/** Reconstrói o painel de detalhe em modo criação de um médico novo. */
	private void exibirFormularioCriacao(VBox detalhe, ListView<String> listaMedicos, TextField campoBusca) {

		Label titulo = new Label("Novo médico");
		titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

		Label lblCns = new Label("CNS:");
		TextField campoCns = new TextField();
		campoCns.setPromptText("15 dígitos");
		HBox linhaCns = new HBox(8, lblCns, campoCns);

		Label lblNome = new Label("Nome:");
		TextField campoNome = new TextField();
		campoNome.setPromptText("nome do médico, como aparece na planilha");
		HBox linhaNome = new HBox(8, lblNome, campoNome);

		Label lblErro = criarLabelErro();

		Button btnCadastrar = new Button("Cadastrar");
		btnCadastrar.setOnAction(e -> {

			String nome = campoNome.getText();
			String cnsNormalizado = CnsUtils.normalizar(campoCns.getText());

			if (nome == null || nome.isBlank()) {
				mostrarErro(lblErro, "Informe o nome do médico.");
				return;
			}

			if (cnsNormalizado == null || cnsNormalizado.length() != 15) {
				mostrarErro(lblErro, "CNS deve ter exatamente 15 dígitos.");
				return;
			}

			try {
				CnsProfissionalUtils.cadastrar(cnsNormalizado, nome.trim());
				atualizarAposMudancaMedico(detalhe, listaMedicos, campoBusca, cnsNormalizado);
			} catch (IllegalArgumentException ex) {
				mostrarErro(lblErro, ex.getMessage());
			}
		});

		Button btnCancelar = new Button("Cancelar");
		btnCancelar.setOnAction(e -> {
			listaMedicos.getSelectionModel().clearSelection();
			exibirPlaceholderMedico(detalhe);
		});

		HBox linhaBotoes = new HBox(8, btnCadastrar, btnCancelar);

		detalhe.getChildren().setAll(titulo, linhaCns, linhaNome, lblErro, linhaBotoes);
	}
}
