package br.gov.ses.fillbpai.controller;

import br.gov.ses.fillbpai.service.AtendimentoImportacaoService;
import br.gov.ses.fillbpai.service.ErroValidacao;
import br.gov.ses.fillbpai.service.ImportacaoResultado;
import br.gov.ses.fillbpai.service.PlanilhaColumnMapper;
import br.gov.ses.fillbpai.service.ValidacaoPlanilhaService;
import br.gov.ses.fillbpai.ui.ConfiguracoesDialog;
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

	/** Caminho do último log de importação salvo — atualizado a cada importação */
	private Path caminhoLog = Path.of("database", "log_importacao.txt");

	/** Caminho do último log de erros de validação salvo */
	private Path caminhoLogErros = Path.of("database", "log_erros_validacao.txt");

	private final EntityManager entityManager;
	private final FileChooserService fileChooserService;
	private final RelatorioController relatorioController;
	private final ConfiguracoesDialog configuracoesDialog;
	private final BorderPane rootLayout;

	public MainController(EntityManager entityManager, BorderPane rootLayout) {

		this.entityManager = entityManager;
		this.fileChooserService = new FileChooserService();
		this.relatorioController = new RelatorioController(entityManager);
		this.configuracoesDialog = new ConfiguracoesDialog();
		this.rootLayout = rootLayout;

		configurarLayout();

		// Carrega registros já existentes no banco
		relatorioController.carregarDoBanco();
	}

	private void configurarLayout() {

		// ==============================
		// Linha 1: Analisar Planilha, Importar Planilha, ... Competência
		// ==============================

		// Spacer empurra o label de competência para a direita
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		Label labelCompetencia = relatorioController.getLabelCompetencia();
		labelCompetencia.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

		Button btnAnalisarPlanilha = relatorioController.getBtnAnalisarPlanilha();
		Button btnVerLog = relatorioController.getBtnVerLog();

		Button btnConfiguracoes = new Button("Configurações");
		btnConfiguracoes.setOnAction(e ->
				configuracoesDialog.abrir(rootLayout.getScene().getWindow()));

		HBox topBar = new HBox(10, btnAnalisarPlanilha, btnVerLog, btnConfiguracoes, spacer, labelCompetencia);
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

	/**
	 * Executa a importação do arquivo já validado, exibe o log e recarrega a tabela.
	 * Chamado tanto pelo fluxo normal de importação quanto pelo botão da janela de análise.
	 */
	private void processarImportacao(String caminho, Stage stage) {

		AtendimentoImportacaoService service =
				new AtendimentoImportacaoService(entityManager);

		ImportacaoResultado resultado;

		try {
			resultado = service.importar(caminho);
		} catch (RuntimeException e) {

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("Erro na Importação");
			alert.setHeaderText("Não foi possível importar a planilha");
			alert.setContentText(e.getMessage());
			alert.showAndWait();
			return;
		}

		String conteudoLog = montarConteudoLog(resultado);
		salvarLogEmArquivo(conteudoLog, nomePlanilhaSemExtensao(caminho));
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
	 * Salva o log de importação em arquivo nomeado com a planilha de origem.
	 */
	private void salvarLogEmArquivo(String conteudo, String nomePlanilha) {

		caminhoLog = Path.of("database", "log_importacao_" + nomePlanilha + ".txt");

		try {

			Files.createDirectories(caminhoLog.getParent());
			Files.writeString(caminhoLog, conteudo, StandardCharsets.UTF_8);

			log.info("Log de importação salvo em: {}", caminhoLog.toAbsolutePath());

		} catch (IOException e) {
			log.error("Erro ao salvar log de importação: {}", e.getMessage());
		}
	}

	/**
	 * Lê o log salvo em arquivo e exibe na tela.
	 * Chamado pelo botão "Ver Log Importação".
	 */
	private void exibirLogSalvo() {

		if (!Files.exists(caminhoLog)) {

			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setContentText("Nenhum log de importação encontrado.");
			alert.showAndWait();
			return;
		}

		try {

			String conteudo = Files.readString(caminhoLog, StandardCharsets.UTF_8);
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

		String nomePlanilha = nomePlanilhaSemExtensao(caminho);

		ValidacaoPlanilhaService validacaoService = new ValidacaoPlanilhaService();
		List<ErroValidacao> errosValidacao = validacaoService.validar(caminho);

		boolean temBloqueantes = errosValidacao.stream().anyMatch(ErroValidacao::isBloqueante);

		if (errosValidacao.isEmpty()) {
			// Sem erros — exibe resultado com botão de importação direta
			mostrarDialogoAnaliseOk(caminho, stage);
			return;
		}

		String nomeArquivo = Path.of(caminho).getFileName().toString();
		String logErros = validacaoService.gerarLogTxt(errosValidacao, nomeArquivo);
		salvarLogErrosEmArquivo(logErros, nomePlanilha);

		boolean temEstrutural = errosValidacao.stream().anyMatch(ErroValidacao::isEstrutural);

		if (temEstrutural) {
			// Cabeçalho com coluna ausente ou duplicada — problema estrutural,
			// não vale a pena mostrar os erros de linha (todos seriam causados
			// pela mesma coluna faltando). Diálogo dedicado aponta a causa,
			// incluindo as colunas do cabeçalho não reconhecidas como candidatas.
			PlanilhaColumnMapper.ResultadoMapeamento mapeamento =
					validacaoService.obterMapeamentoEstrutura(caminho);
			mostrarDialogoErroEstrutura(mapeamento, logErros, nomePlanilha, stage);
		} else if (temBloqueantes) {
			// Erros bloqueantes — sem botão de importação
			mostrarDialogoErrosValidacao(logErros, nomePlanilha, stage);
		} else {
			// Apenas avisos — exibe com botão de importação direta
			mostrarDialogoAvisosAnalise(logErros, nomePlanilha, caminho, stage);
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
	private void mostrarDialogoAvisosAnalise(String logAvisos, String nomePlanilha, String caminho, Stage stage) {

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
			fileChooser.setInitialFileName("log_avisos_validacao_" + nomePlanilha + ".txt");
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
	 * Salva o log de erros de validação em arquivo nomeado com a planilha de origem.
	 */
	private void salvarLogErrosEmArquivo(String conteudo, String nomePlanilha) {

		caminhoLogErros = Path.of("database", "log_erros_validacao_" + nomePlanilha + ".txt");

		try {

			Files.createDirectories(caminhoLogErros.getParent());
			Files.writeString(caminhoLogErros, conteudo, StandardCharsets.UTF_8);

			log.info("Log de erros de validação salvo em: {}", caminhoLogErros.toAbsolutePath());

		} catch (IOException e) {
			log.error("Erro ao salvar log de erros: {}", e.getMessage());
		}
	}

	/**
	 * Exibe diálogo dedicado para problemas de estrutura do cabeçalho (coluna
	 * obrigatória ausente ou duplicada). Além de apontar exatamente qual(is)
	 * coluna(s) causaram o problema — em vez da lista de erros de linha, que
	 * nesse caso seria toda causada pela mesma coluna faltando — lista também
	 * as colunas do cabeçalho que não bateram com nenhum alias cadastrado:
	 * candidatas óbvias quando a planilha tem a informação, só que com um
	 * nome diferente do esperado. Oferece atalho para a tela onde um alias
	 * pode ser cadastrado sem precisar editar a planilha.
	 */
	private void mostrarDialogoErroEstrutura(PlanilhaColumnMapper.ResultadoMapeamento mapeamento, String logErros, String nomePlanilha, Stage stage) {

		int totalProblemas = mapeamento.camposFaltando().size() + mapeamento.camposDuplicados().size();

		Alert alert = new Alert(Alert.AlertType.ERROR);
		alert.setTitle("Estrutura da Planilha Inválida");
		alert.setHeaderText(totalProblemas == 1
				? "1 problema encontrado no cabeçalho da planilha"
				: totalProblemas + " problemas encontrados no cabeçalho da planilha");

		VBox conteudo = new VBox(14);

		if (!mapeamento.camposFaltando().isEmpty()) {
			conteudo.getChildren().add(
					blocoComTitulo("Colunas obrigatórias não encontradas:", mapeamento.camposFaltando()));
		}

		if (!mapeamento.camposDuplicados().isEmpty()) {
			conteudo.getChildren().add(
					blocoComTitulo("Colunas duplicadas:", mapeamento.camposDuplicados()));
		}

		if (!mapeamento.colunasNaoReconhecidas().isEmpty()) {
			List<String> entreAspas = mapeamento.colunasNaoReconhecidas().stream()
					.map(nome -> "\"" + nome + "\"")
					.collect(java.util.stream.Collectors.toList());
			conteudo.getChildren().add(blocoComTitulo(
					"Colunas do cabeçalho não reconhecidas (podem corresponder às acima):", entreAspas));
		}

		Label instrucao = new Label(
				"Se uma dessas colunas corresponder a um campo obrigatório da lista: "
						+ "cadastre-a como alias em Configurações → Colunas da Planilha, "
						+ "ou renomeie-a na planilha para o nome esperado.");
		instrucao.setWrapText(true);
		conteudo.getChildren().add(instrucao);

		Button btnAbrirConfiguracoes = new Button("Abrir Configurações");
		btnAbrirConfiguracoes.setOnAction(e -> {
			alert.close();
			configuracoesDialog.abrir(stage);
		});

		Button btnSalvarLog = new Button("Salvar Log TXT");
		btnSalvarLog.setOnAction(e -> {

			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Salvar Log de Erros");
			fileChooser.setInitialFileName("log_erros_validacao_" + nomePlanilha + ".txt");
			fileChooser.getExtensionFilters().add(
					new FileChooser.ExtensionFilter("Arquivo Texto", "*.txt"));

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

		conteudo.getChildren().add(new javafx.scene.layout.HBox(10, btnAbrirConfiguracoes, btnSalvarLog));
		conteudo.setPadding(new Insets(10));

		alert.getDialogPane().setContent(conteudo);
		alert.showAndWait();
	}

	/** Monta um bloco com título em negrito seguido de uma lista de itens com marcador. */
	private VBox blocoComTitulo(String titulo, List<String> itens) {

		Label labelTitulo = new Label(titulo);
		labelTitulo.setStyle("-fx-font-weight: bold;");

		VBox bloco = new VBox(2, labelTitulo);
		for (String item : itens) {
			bloco.getChildren().add(new Label("•  " + item));
		}

		return bloco;
	}

	/**
	 * Exibe diálogo com erros de validação e botão para download do log TXT.
	 */
	private void mostrarDialogoErrosValidacao(String logErros, String nomePlanilha, Stage stage) {

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
			fileChooser.setInitialFileName("log_erros_validacao_" + nomePlanilha + ".txt");
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

	/** Extrai o nome do arquivo da planilha sem extensão, sanitizado para uso em nomes de arquivo. */
	private String nomePlanilhaSemExtensao(String caminho) {
		String nome = Path.of(caminho).getFileName().toString();
		int dot = nome.lastIndexOf('.');
		if (dot > 0) nome = nome.substring(0, dot);
		return nome.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

}

