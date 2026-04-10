package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ErroValidacao;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.service.ValidacaoPlanilhaService;
import br.gov.ses.fillbpai.ui.FileChooserService;
import br.gov.ses.fillbpai.ui.RelatorioController;
import jakarta.persistence.EntityManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Controller principal da aplicação.
 *
 * Gerencia a barra superior (importação, competência)
 * e delega a exibição de dados ao RelatorioController.
 *
 * O log de importação é persistido em arquivo (database/log_importacao.txt)
 * para consulta a qualquer momento, mesmo após reiniciar a aplicação.
 */
public class MainController {

	private static final Logger log = LoggerFactory.getLogger(MainController.class);

	/** Caminho do arquivo de log de importação (mesmo diretório do H2) */
	private static final Path CAMINHO_LOG = Path.of("database", "log_importacao.txt");

	/** Caminho do arquivo de log de erros de validação */
	private static final Path CAMINHO_LOG_ERROS = Path.of("database", "log_erros_validacao.txt");

	private final EntityManager entityManager;
	private final FileChooserService fileChooserService;
	private final RelatorioController relatorioController;
	private final BorderPane rootLayout;

	public MainController(EntityManager entityManager, BorderPane rootLayout) {

		this.entityManager = entityManager;
		this.fileChooserService = new FileChooserService();
		this.relatorioController = new RelatorioController(entityManager);
		this.rootLayout = rootLayout;

		configurarLayout();

		// Carrega registros já existentes no banco
		relatorioController.carregarDoBanco();
	}

	private void configurarLayout() {

		// ==============================
		// Linha 1: Analisar Planilha, Importar Planilha, ... Competência
		// ==============================

		Button btnImportar = new Button("Importar Planilha");

		btnImportar.setOnAction(event -> {
			Stage stage = (Stage) rootLayout.getScene().getWindow();
			importar(stage);
		});

		// Spacer empurra o label de competência para a direita
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Label labelCompetencia = relatorioController.getLabelCompetencia();
		labelCompetencia.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

		Button btnAnalisarPlanilha = relatorioController.getBtnAnalisarPlanilha();
		Button btnVerLog = relatorioController.getBtnVerLog();

		HBox topBar = new HBox(10, btnAnalisarPlanilha, btnImportar, btnVerLog, spacer, labelCompetencia);
		topBar.setPadding(new Insets(10));

		rootLayout.setTop(topBar);

		// Registra a ação do botão "Ver Log" — lê do arquivo persistido
		relatorioController.setAcaoVerLog(this::exibirLogSalvo);

		// Registra a ação do botão "Analisar Planilha" — valida sem importar
		// O botão é posicionado na topBar (à esquerda de Importar Planilha)
		relatorioController.setAcaoAnalisarPlanilha(() -> {
			Stage stage = (Stage) rootLayout.getScene().getWindow();
			analisarPlanilha(stage);
		});

		rootLayout.setCenter(relatorioController.criarComponente());
	}

	private void importar(Stage stage) {

		String caminho = fileChooserService.selecionarPlanilha(stage);

		if (caminho == null) {
			return;
		}

		// Validação pré-importação — bloqueia apenas se houver erros bloqueantes
		ValidacaoPlanilhaService validacaoService = new ValidacaoPlanilhaService();
		List<ErroValidacao> errosValidacao = validacaoService.validar(caminho);

		boolean temBloqueantes = errosValidacao.stream().anyMatch(ErroValidacao::isBloqueante);

		if (!errosValidacao.isEmpty()) {
			String logErros = validacaoService.gerarLogTxt(errosValidacao);
			salvarLogErrosEmArquivo(logErros);
			if (temBloqueantes) {
				mostrarDialogoErrosValidacao(logErros, stage);
				return;
			}
			// Apenas avisos — exibe mas deixa importação prosseguir
			mostrarDialogoAvisosValidacao(logErros, stage);
		}

		processarImportacao(caminho, stage);
	}

	/**
	 * Executa a importação do arquivo já validado, exibe o log e recarrega a tabela.
	 * Chamado tanto pelo fluxo normal de importação quanto pelo botão da janela de análise.
	 */
	private void processarImportacao(String caminho, Stage stage) {

		AtendimentoImportacaoService service =
				new AtendimentoImportacaoService(entityManager);

		ImportacaoResultado resultado = service.importar(caminho);

		String conteudoLog = montarConteudoLog(resultado);
		salvarLogEmArquivo(conteudoLog);
		mostrarLog(conteudoLog);

		if (resultado.getTotalSucesso() > 0) {
			relatorioController.carregarDoBanco();
		}
	}

	// ======================================================
	// LOG DE IMPORTAÇÃO
	// ======================================================

	/**
	 * Monta o conteúdo textual completo do log de importação.
	 */
	private String montarConteudoLog(ImportacaoResultado resultado) {

		StringBuilder sb = new StringBuilder();

		sb.append("Total processados: ").append(resultado.getTotalProcessados()).append("\n");
		sb.append("Sucesso: ").append(resultado.getTotalSucesso()).append("\n");
		sb.append("Erros: ").append(resultado.getTotalErro()).append("\n");
		sb.append("Avisos: ").append(resultado.getTotalAvisos()).append("\n");
		sb.append("\n");

		if (!resultado.getErros().isEmpty()) {
			sb.append("=== ERROS (linhas não importadas) ===\n");
			for (String erro : resultado.getErros()) {
				sb.append(erro).append("\n");
			}
		}

		if (!resultado.getAvisos().isEmpty()) {
			if (!resultado.getErros().isEmpty()) {
				sb.append("\n");
			}
			sb.append("=== AVISOS (linhas importadas com ressalvas) ===\n");
			for (String aviso : resultado.getAvisos()) {
				sb.append(aviso).append("\n");
			}
		}

		if (resultado.getErros().isEmpty() && resultado.getAvisos().isEmpty()) {
			sb.append("Importação concluída sem erros ou avisos.");
		}

		return sb.toString();
	}

	/**
	 * Salva o log em arquivo para persistência entre sessões.
	 */
	private void salvarLogEmArquivo(String conteudo) {

		try {

			Files.createDirectories(CAMINHO_LOG.getParent());
			Files.writeString(CAMINHO_LOG, conteudo, StandardCharsets.UTF_8);

			log.info("Log de importação salvo em: {}", CAMINHO_LOG.toAbsolutePath());

		} catch (IOException e) {
			log.error("Erro ao salvar log de importação: {}", e.getMessage());
		}
	}

	/**
	 * Lê o log salvo em arquivo e exibe na tela.
	 * Chamado pelo botão "Ver Log Importação".
	 */
	private void exibirLogSalvo() {

		if (!Files.exists(CAMINHO_LOG)) {

			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setContentText("Nenhum log de importação encontrado.");
			alert.showAndWait();
			return;
		}

		try {

			String conteudo = Files.readString(CAMINHO_LOG, StandardCharsets.UTF_8);
			mostrarLog(conteudo);

		} catch (IOException e) {

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setContentText("Erro ao ler log: " + e.getMessage());
			alert.showAndWait();
		}
	}

	/**
	 * Exibe o conteúdo do log em um diálogo.
	 */
	private void mostrarLog(String conteudo) {

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Log de Importação");
		alert.setHeaderText("Última Importação");

		TextArea areaLog = new TextArea(conteudo);
		areaLog.setEditable(false);
		areaLog.setWrapText(true);
		areaLog.setPrefHeight(400);

		VBox layout = new VBox(10, areaLog);
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}

	// ======================================================
	// ANÁLISE E VALIDAÇÃO DE PLANILHA
	// ======================================================

	/**
	 * Analisa a planilha à procura de erros de validação sem importar.
	 * Exibe o resultado e permite download do log de erros em TXT.
	 */
	private void analisarPlanilha(Stage stage) {

		String caminho = fileChooserService.selecionarPlanilha(stage);

		if (caminho == null) {
			return;
		}

		ValidacaoPlanilhaService validacaoService = new ValidacaoPlanilhaService();
		List<ErroValidacao> errosValidacao = validacaoService.validar(caminho);

		boolean temBloqueantes = errosValidacao.stream().anyMatch(ErroValidacao::isBloqueante);

		if (errosValidacao.isEmpty()) {
			// Sem erros — exibe resultado com botão de importação direta
			mostrarDialogoAnaliseOk(caminho, stage);
			return;
		}

		String logErros = validacaoService.gerarLogTxt(errosValidacao);
		salvarLogErrosEmArquivo(logErros);

		if (temBloqueantes) {
			// Erros bloqueantes — sem botão de importação
			mostrarDialogoErrosValidacao(logErros, stage);
		} else {
			// Apenas avisos — exibe com botão de importação direta
			mostrarDialogoAvisosAnalise(logErros, caminho, stage);
		}
	}

	/**
	 * Exibe resultado positivo da análise com botão para importar diretamente.
	 */
	private void mostrarDialogoAnaliseOk(String caminho, Stage stage) {

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Validação da Planilha");
		alert.setHeaderText("Nenhum erro encontrado");

		Label msg = new Label("A planilha está pronta para importação.");

		Button btnImportar = new Button("Importar Planilha");
		btnImportar.setOnAction(e -> {
			alert.close();
			processarImportacao(caminho, stage);
		});

		VBox layout = new VBox(10, msg, btnImportar);
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}

	/**
	 * Exibe avisos de validação (não bloqueantes) com botão para importar diretamente.
	 * Usado apenas no fluxo de análise — no fluxo de importação os avisos são exibidos
	 * sem botão, pois a importação prossegue automaticamente.
	 */
	private void mostrarDialogoAvisosAnalise(String logAvisos, String caminho, Stage stage) {

		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Avisos de Validação");
		alert.setHeaderText("A planilha contém avisos mas pode ser importada");

		TextArea areaLog = new TextArea(logAvisos);
		areaLog.setEditable(false);
		areaLog.setWrapText(true);
		areaLog.setPrefHeight(300);

		Button btnSalvarLog = new Button("Salvar Log TXT");
		btnSalvarLog.setOnAction(e -> {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Salvar Log de Avisos");
			fileChooser.setInitialFileName("log_avisos_validacao.txt");
			fileChooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo Texto", "*.txt"));
			java.io.File arquivo = fileChooser.showSaveDialog(stage);
			if (arquivo != null) {
				try {
					Files.writeString(arquivo.toPath(), logAvisos, StandardCharsets.UTF_8);
				} catch (IOException ex) {
					log.error("Erro ao exportar log: {}", ex.getMessage());
				}
			}
		});

		Button btnImportar = new Button("Importar Planilha");
		btnImportar.setOnAction(e -> {
			alert.close();
			processarImportacao(caminho, stage);
		});

		VBox layout = new VBox(10, areaLog, new javafx.scene.layout.HBox(10, btnSalvarLog, btnImportar));
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}

	/**
	 * Salva o log de erros de validação em arquivo TXT.
	 */
	private void salvarLogErrosEmArquivo(String conteudo) {

		try {

			Files.createDirectories(CAMINHO_LOG_ERROS.getParent());
			Files.writeString(CAMINHO_LOG_ERROS, conteudo, StandardCharsets.UTF_8);

			log.info("Log de erros de validação salvo em: {}", CAMINHO_LOG_ERROS.toAbsolutePath());

		} catch (IOException e) {
			log.error("Erro ao salvar log de erros: {}", e.getMessage());
		}
	}

	/**
	 * Exibe diálogo com erros de validação e botão para download do log TXT.
	 */
	private void mostrarDialogoErrosValidacao(String logErros, Stage stage) {

		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Erros de Validação");
		alert.setHeaderText("A planilha contém erros que impedem a importação");

		TextArea areaLog = new TextArea(logErros);
		areaLog.setEditable(false);
		areaLog.setWrapText(true);
		areaLog.setPrefHeight(400);

		Button btnSalvarLog = new Button("Salvar Log TXT");
		btnSalvarLog.setOnAction(e -> {

			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Salvar Log de Erros");
			fileChooser.setInitialFileName("log_erros_validacao.txt");
			fileChooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo Texto", "*.txt")
			);

			java.io.File arquivo = fileChooser.showSaveDialog(stage);

			if (arquivo != null) {
				try {
					Files.writeString(arquivo.toPath(), logErros, StandardCharsets.UTF_8);
					log.info("Log de erros exportado para: {}", arquivo.getAbsolutePath());
				} catch (IOException ex) {
					log.error("Erro ao exportar log: {}", ex.getMessage());
				}
			}
		});

		VBox layout = new VBox(10, areaLog, btnSalvarLog);
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}

	/**
	 * Exibe diálogo informativo com avisos de validação (não bloqueantes).
	 * A importação prosseguirá normalmente após o fechamento.
	 */
	private void mostrarDialogoAvisosValidacao(String logAvisos, Stage stage) {

		Alert alert = new Alert(Alert.AlertType.WARNING);
		alert.setTitle("Avisos de Validação");
		alert.setHeaderText("A planilha contém avisos (a importação prosseguirá normalmente)");

		TextArea areaLog = new TextArea(logAvisos);
		areaLog.setEditable(false);
		areaLog.setWrapText(true);
		areaLog.setPrefHeight(300);

		Button btnSalvarLog = new Button("Salvar Log TXT");
		btnSalvarLog.setOnAction(e -> {

			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Salvar Log de Avisos");
			fileChooser.setInitialFileName("log_avisos_validacao.txt");
			fileChooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo Texto", "*.txt")
			);

			java.io.File arquivo = fileChooser.showSaveDialog(stage);

			if (arquivo != null) {
				try {
					Files.writeString(arquivo.toPath(), logAvisos, StandardCharsets.UTF_8);
					log.info("Log de avisos exportado para: {}", arquivo.getAbsolutePath());
				} catch (IOException ex) {
					log.error("Erro ao exportar log: {}", ex.getMessage());
				}
			}
		});

		VBox layout = new VBox(10, areaLog, btnSalvarLog);
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}
}
