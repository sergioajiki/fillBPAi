package br.gov.ses.fillbpai.ui;

import br.gov.ses.fillbpai.service.SobreService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Janela "Sobre o PonteBPA" (20/10/2026), aberta pelo botão "Sobre" no
 * canto direito da barra superior: versão e data da compilação, unidade
 * (os mesmos dados do cabeçalho do BPA-I), layout, ambiente e as pastas de
 * dados com "Abrir pasta". "Copiar informações" copia o resumo em texto,
 * para colar num pedido de ajuda. Os dados vêm de {@link SobreService},
 * lidos na hora em que a janela abre.
 */
public class SobreDialog {

	private static final String COR_SUAVE = "#57645C";
	private static final String COR_INFO = "#2F6F5E";
	private static final String MONO = "-fx-font-family: 'Consolas', monospace;";

	private final SobreService sobreService;

	public SobreDialog(SobreService sobreService) {
		this.sobreService = sobreService;
	}

	public void abrir(Window owner) {

		SobreService.InformacoesSobre info = sobreService.carregar();

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.initOwner(owner);
		alert.setTitle("Sobre o " + SobreService.NOME);
		alert.setHeaderText(null);
		alert.setGraphic(null);
		// Botões próprios no rodapé; a barra padrão fica escondida, mas o tipo
		// CANCEL_CLOSE continua registrado para o "X" da janela e o Esc fecharem
		alert.getButtonTypes().setAll(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
		Node barraPadrao = alert.getDialogPane().lookup(".button-bar");
		if (barraPadrao != null) {
			barraPadrao.setVisible(false);
			barraPadrao.setManaged(false);
			barraPadrao.setStyle("-fx-pref-height: 0; -fx-padding: 0;");
		}

		Label aviso = new Label();
		aviso.setWrapText(true);
		aviso.setVisible(false);
		aviso.setManaged(false);
		aviso.setStyle("-fx-text-fill: #93630F;");

		VBox conteudo = new VBox(14,
				criarMarca(info),
				criarBlocos(info),
				criarBlocoDados(info, aviso),
				aviso,
				criarRodape(info, alert));
		conteudo.setPadding(new Insets(16, 18, 14, 18));
		conteudo.setPrefWidth(720);

		alert.getDialogPane().setContent(conteudo);
		alert.showAndWait();
	}

	/** Logo, nome, descrição e, à direita, versão e data da compilação. */
	private Node criarMarca(SobreService.InformacoesSobre info) {

		Label logo = new Label("BPA-I");
		logo.setAlignment(Pos.CENTER);
		logo.setMinSize(46, 46);
		logo.setPrefSize(46, 46);
		logo.setStyle(MONO + "-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: " + COR_INFO + ";"
				+ "-fx-background-color: #DCEAE4; -fx-background-radius: 10;"
				+ "-fx-border-color: " + COR_INFO + "; -fx-border-radius: 10;");

		Label nome = new Label(SobreService.NOME);
		nome.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
		Label descricao = new Label("Importação de atendimentos e geração do BPA-I");
		descricao.setStyle("-fx-text-fill: " + COR_SUAVE + ";");

		Label versao = new Label("versão " + info.versao());
		versao.setStyle(MONO + "-fx-font-size: 15px; -fx-font-weight: bold;");
		Label compilado = new Label(info.compiladoFormatado());
		compilado.setStyle("-fx-text-fill: " + COR_SUAVE + "; -fx-font-size: 12px;");
		VBox ladoVersao = new VBox(2, versao, compilado);
		ladoVersao.setAlignment(Pos.CENTER_RIGHT);

		Region espaco = new Region();
		HBox.setHgrow(espaco, Priority.ALWAYS);

		HBox marca = new HBox(14, logo, new VBox(2, nome, descricao), espaco, ladoVersao);
		marca.setAlignment(Pos.CENTER_LEFT);
		marca.setPadding(new Insets(0, 0, 12, 0));
		marca.setStyle("-fx-border-color: transparent transparent #D5DBD7 transparent;");

		return marca;
	}

	/** Unidade à esquerda; Layout BPA-I e Ambiente à direita. */
	private Node criarBlocos(SobreService.InformacoesSobre info) {

		VBox unidade = bloco("Unidade", tabela(
				"CNES", info.cnes(), true,
				"Órgão", info.orgao(), false,
				"Sigla", info.sigla(), false,
				"CNPJ", info.cnpj(), true,
				"INE", info.ine(), false));

		VBox layout = bloco("Layout BPA-I", tabela(
				"Layout", info.layout(), false,
				"Versão no header", info.versaoHeader(), true));

		VBox ambiente = bloco("Ambiente", tabela(
				"Java", info.java(), true,
				"Sistema", info.sistema(), false,
				"Banco", info.banco(), false));

		GridPane grade = new GridPane();
		grade.setHgap(22);
		grade.setVgap(14);
		ColumnConstraints metade = new ColumnConstraints();
		metade.setPercentWidth(50);
		grade.getColumnConstraints().addAll(metade, metade);
		grade.add(unidade, 0, 0, 1, 2);
		grade.add(layout, 1, 0);
		grade.add(ambiente, 1, 1);

		return grade;
	}

	/** Pastas de dados, cada uma com "Abrir pasta". */
	private Node criarBlocoDados(SobreService.InformacoesSobre info, Label aviso) {

		VBox linhas = new VBox(6);

		for (SobreService.PastaDados pasta : info.pastas()) {

			Label rotulo = new Label(pasta.rotulo());
			rotulo.setMinWidth(74);
			rotulo.setStyle("-fx-text-fill: " + COR_SUAVE + ";");

			Label caminho = new Label(pasta.caminho().toString());
			caminho.setWrapText(true);
			caminho.setStyle(MONO + "-fx-font-size: 12px;");
			caminho.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(caminho, Priority.ALWAYS);

			Button abrir = new Button("Abrir pasta");
			abrir.setStyle("-fx-font-size: 12px;");
			// não encolhe para "..." quando o caminho é longo (visto no teste do pacote, 21/10/2026)
			abrir.setMinWidth(Region.USE_PREF_SIZE);
			rotulo.setMinWidth(Math.max(74, rotulo.getMinWidth()));
			caminho.setMinWidth(0);
			abrir.setOnAction(e -> abrirPasta(pasta.caminho(), aviso));

			HBox linha = new HBox(10, rotulo, caminho, abrir);
			linha.setAlignment(Pos.CENTER_LEFT);
			linha.setPadding(new Insets(6, 8, 6, 8));
			linha.setStyle("-fx-background-color: #FBFCFB; -fx-border-color: #D5DBD7;"
					+ "-fx-border-radius: 5; -fx-background-radius: 5;");
			linhas.getChildren().add(linha);
		}

		return bloco("Dados", linhas);
	}

	/** Licença à esquerda; "Copiar informações" e "Fechar" à direita. */
	private Node criarRodape(SobreService.InformacoesSobre info, Alert alert) {

		Label licenca = new Label("Uso interno — Superintendência de Saúde Digital / SES-MS");
		licenca.setStyle("-fx-text-fill: " + COR_SUAVE + "; -fx-font-size: 12px;");

		Button copiar = new Button("Copiar informações");
		copiar.setOnAction(e -> {
			ClipboardContent texto = new ClipboardContent();
			texto.putString(info.textoParaCopiar());
			Clipboard.getSystemClipboard().setContent(texto);
			copiar.setText("Copiado");
			PauseTransition volta = new PauseTransition(Duration.seconds(1.5));
			volta.setOnFinished(f -> copiar.setText("Copiar informações"));
			volta.play();
		});

		Button fechar = new Button("Fechar");
		fechar.setCancelButton(true);
		fechar.setDefaultButton(true);
		fechar.setOnAction(e -> alert.close());

		Region espaco = new Region();
		HBox.setHgrow(espaco, Priority.ALWAYS);

		HBox rodape = new HBox(10, licenca, espaco, copiar, fechar);
		rodape.setAlignment(Pos.CENTER_LEFT);
		rodape.setPadding(new Insets(10, 0, 0, 0));
		rodape.setStyle("-fx-border-color: #D5DBD7 transparent transparent transparent;");

		return rodape;
	}

	/**
	 * Abre a pasta no Explorador de Arquivos, criando-a se ainda não existir
	 * (ex.: pasta de logs antes da primeira importação). Roda fora da thread
	 * do JavaFX; falha vira um aviso na própria janela.
	 */
	private static void abrirPasta(Path pasta, Label aviso) {

		Thread t = new Thread(() -> {
			try {
				Files.createDirectories(pasta);
				if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
					throw new IOException("abrir pastas não é suportado neste sistema");
				}
				Desktop.getDesktop().open(pasta.toFile());
			} catch (IOException | RuntimeException ex) {
				Platform.runLater(() -> {
					aviso.setText("Não foi possível abrir a pasta " + pasta + ": " + ex.getMessage());
					aviso.setVisible(true);
					aviso.setManaged(true);
				});
			}
		}, "abrir-pasta");
		t.setDaemon(true);
		t.start();
	}

	/** Título do bloco em maiúsculas, cinza, sobre o conteúdo. */
	private static VBox bloco(String titulo, Node corpo) {
		Label rotulo = new Label(titulo.toUpperCase());
		rotulo.setStyle("-fx-text-fill: " + COR_SUAVE + "; -fx-font-size: 11.5px; -fx-font-weight: bold;");
		return new VBox(6, rotulo, corpo);
	}

	/** Pares rótulo → valor em duas colunas; trincas (rótulo, valor, monoespaçado). */
	private static GridPane tabela(Object... trincas) {

		GridPane g = new GridPane();
		g.setHgap(12);
		g.setVgap(3);

		for (int i = 0; i < trincas.length; i += 3) {
			Label rotulo = new Label((String) trincas[i]);
			rotulo.setStyle("-fx-text-fill: " + COR_SUAVE + ";");
			rotulo.setMinWidth(Region.USE_PREF_SIZE);
			Label valor = new Label((String) trincas[i + 1]);
			valor.setWrapText(true);
			if ((Boolean) trincas[i + 2]) {
				valor.setStyle(MONO);
			}
			g.addRow(i / 3, rotulo, valor);
		}

		return g;
	}
}
