package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.util.ColunaAliasUtils;

import javafx.geometry.Insets;
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
 * Hoje só a aba de aliases de coluna da planilha (nomes de cabeçalho
 * aceitos para cada campo canônico, ver {@link ColunaAliasUtils}). A aba de
 * CNS de médicos fica reservada para quando o cadastro que já existe em
 * {@code CnsProfissionalUtils} ganhar interface própria — mesma estrutura
 * de lista + detalhe, só trocando campo canônico por nome de médico.
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

		Tab tabCns = new Tab("CNS de Médicos");
		tabCns.setDisable(true);
		tabCns.setContent(new Label("Em breve"));

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
}
