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
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
		// Linha 1: Analisar Planilha, Ver Log, Configurações
		// (a competência de geração agora vive na barra fixa inferior do
		// RelatorioController, junto com o botão "Gerar BPA-I")
		// ==============================

		Button btnAnalisarPlanilha = relatorioController.getBtnAnalisarPlanilha();
		Button btnVerLog = relatorioController.getBtnVerLog();

		Button btnConfiguracoes = new Button("Configurações");
		btnConfiguracoes.setOnAction(e ->
				configuracoesDialog.abrir(rootLayout.getScene().getWindow()));

		HBox topBar = new HBox(10, btnAnalisarPlanilha, btnVerLog, btnConfiguracoes);
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

		// Os avisos de formato legado e de coluna opcional ausente são tratados
		// à parte: não entram na lista usada para gerarLogTxt/log salvo em
		// arquivo nem nas decisões de roteamento abaixo — aparecem só nos
		// banners dedicados (ver criarBannerFormatoLegado/
		// criarBannerColunasOpcionaisAusentes), nunca duplicados como linha
		// genérica de aviso.
		ErroValidacao avisoLegado = errosValidacao.stream()
				.filter(e -> ErroValidacao.FORMATO_LEGADO_ESPECIALIDADE_MEDICO.equals(e.tipoErro()))
				.findFirst()
				.orElse(null);

		List<ErroValidacao> avisosColunaOpcional = errosValidacao.stream()
				.filter(e -> ErroValidacao.COLUNA_OPCIONAL_AUSENTE.equals(e.tipoErro()))
				.collect(java.util.stream.Collectors.toList());

		List<ErroValidacao> demaisErros = errosValidacao.stream()
				.filter(e -> e != avisoLegado)
				.filter(e -> !avisosColunaOpcional.contains(e))
				.collect(java.util.stream.Collectors.toList());

		boolean temBloqueantes = demaisErros.stream().anyMatch(ErroValidacao::isBloqueante);

		if (demaisErros.isEmpty()) {
			// Sem erros — exibe resultado com botão de importação direta
			mostrarDialogoAnaliseOk(caminho, stage, avisoLegado, avisosColunaOpcional);
			return;
		}

		String nomeArquivo = Path.of(caminho).getFileName().toString();
		String logErros = validacaoService.gerarLogTxt(demaisErros, nomeArquivo);
		salvarLogErrosEmArquivo(logErros, nomePlanilha);

		boolean temEstrutural = demaisErros.stream().anyMatch(ErroValidacao::isEstrutural);

		if (temEstrutural) {
			// Cabeçalho com coluna ausente ou duplicada — problema estrutural,
			// não vale a pena mostrar os erros de linha (todos seriam causados
			// pela mesma coluna faltando). Diálogo dedicado aponta a causa,
			// incluindo as colunas do cabeçalho não reconhecidas como candidatas.
			PlanilhaColumnMapper.ResultadoMapeamento mapeamento =
					validacaoService.obterMapeamentoEstrutura(caminho);
			mostrarDialogoErroEstrutura(mapeamento, logErros, nomePlanilha, stage);
		} else if (temBloqueantes) {
			// Erros — sem botão de importação
			mostrarDialogoErrosValidacao(logErros, nomePlanilha, stage, avisosColunaOpcional);
		} else {
			// Apenas avisos — exibe com botão de importação direta
			mostrarDialogoAvisosAnalise(logErros, nomePlanilha, caminho, stage, avisoLegado, avisosColunaOpcional);
		}
	}

	/**
	 * Monta o banner de aviso de "formato de planilha legado" (coluna
	 * "Especialidade/Médico" combinada) — mesmas cores usadas nos runbooks do
	 * projeto (accent verde), conforme mockup validado antes da implementação.
	 */
	private VBox criarBannerFormatoLegado(ErroValidacao aviso) {

		Label kicker = new Label("⚠ Formato de planilha legado detectado");
		kicker.setStyle("-fx-font-weight: bold; -fx-text-fill: #2F6F5E;");

		Label texto = new Label(aviso.detalhe());
		texto.setWrapText(true);
		// setWrapText só quebra linha se a largura for limitada — sem isto o
		// Label cresce para caber o texto inteiro numa linha só, e o diálogo
		// (que se ajusta ao conteúdo) ultrapassa a tela.
		texto.setMaxWidth(440);

		VBox banner = new VBox(6, kicker, texto);
		banner.setMaxWidth(468);
		banner.setPadding(new Insets(12, 14, 12, 14));
		banner.setStyle("-fx-background-color: #DCEAE4; -fx-border-color: #2F6F5E; "
				+ "-fx-border-width: 0 0 0 3;");

		return banner;
	}

	/**
	 * Monta o banner de aviso "colunas opcionais não encontradas" — mesmo
	 * estilo visual do banner de formato legado ({@link #criarBannerFormatoLegado}),
	 * mas com um item de lista por coluna ausente, já que pode haver mais de
	 * uma ao mesmo tempo (ex.: COD_LOGRADOURO e SITUACAO_RUA).
	 */
	private VBox criarBannerColunasOpcionaisAusentes(List<ErroValidacao> avisos) {

		Label kicker = new Label("⚠ Colunas opcionais não encontradas");
		kicker.setStyle("-fx-font-weight: bold; -fx-text-fill: #2F6F5E;");

		VBox itens = new VBox(4);
		for (ErroValidacao aviso : avisos) {
			Label item = new Label("•  " + aviso.detalhe());
			item.setWrapText(true);
			item.setMaxWidth(440);
			itens.getChildren().add(item);
		}

		VBox banner = new VBox(6, kicker, itens);
		banner.setMaxWidth(468);
		banner.setPadding(new Insets(12, 14, 12, 14));
		banner.setStyle("-fx-background-color: #DCEAE4; -fx-border-color: #2F6F5E; "
				+ "-fx-border-width: 0 0 0 3;");

		return banner;
	}

	/**
	 * Exibe resultado positivo da análise com botão para importar diretamente.
	 */
	private void mostrarDialogoAnaliseOk(String caminho, Stage stage, ErroValidacao avisoLegado,
			List<ErroValidacao> avisosColunaOpcional) {

		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Validação da Planilha");
		alert.setHeaderText("Nenhum erro encontrado");

		VBox layout = new VBox(10);

		if (avisoLegado != null) {
			layout.getChildren().add(criarBannerFormatoLegado(avisoLegado));
		}

		if (!avisosColunaOpcional.isEmpty()) {
			layout.getChildren().add(criarBannerColunasOpcionaisAusentes(avisosColunaOpcional));
		}

		Label msg = new Label("A planilha está pronta para importação.");

		Button btnImportar = new Button("Importar Planilha");
		btnImportar.setOnAction(e -> {
			alert.close();
			processarImportacao(caminho, stage);
		});

		layout.getChildren().addAll(msg, btnImportar);
		layout.setPadding(new Insets(10));

		alert.getDialogPane().setContent(layout);
		alert.showAndWait();
	}

	/**
	 * Exibe avisos de validação (não bloqueantes) com botão para importar diretamente.
	 * Usado apenas no fluxo de análise — no fluxo de importação os avisos são exibidos
	 * sem botão, pois a importação prossegue automaticamente.
	 */
	private void mostrarDialogoAvisosAnalise(String logAvisos, String nomePlanilha, String caminho, Stage stage,
			ErroValidacao avisoLegado, List<ErroValidacao> avisosColunaOpcional) {

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

		VBox layout = new VBox(10);

		if (avisoLegado != null) {
			layout.getChildren().add(criarBannerFormatoLegado(avisoLegado));
		}

		if (!avisosColunaOpcional.isEmpty()) {
			layout.getChildren().add(criarBannerColunasOpcionaisAusentes(avisosColunaOpcional));
		}

		layout.getChildren().addAll(areaLog, new javafx.scene.layout.HBox(10, btnSalvarLog, btnImportar));
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

		if (!mapeamento.colunasNaoReconhecidas().isEmpty()) {
			List<String> entreAspas = mapeamento.colunasNaoReconhecidas().stream()
					.map(nome -> "\"" + nome + "\"")
					.collect(java.util.stream.Collectors.toList());
			conteudo.getChildren().add(blocoComTitulo(
					"Colunas do cabeçalho não reconhecidas (podem corresponder às acima):", entreAspas));
		}

		if (!mapeamento.camposFaltando().isEmpty() || !mapeamento.colunasNaoReconhecidas().isEmpty()) {
			Label instrucaoAusentes = new Label(
					"Se uma dessas colunas corresponder a um campo obrigatório da lista: "
							+ "cadastre-a como alias em Configurações → Colunas da Planilha, "
							+ "ou renomeie-a na planilha para o nome esperado.");
			instrucaoAusentes.setWrapText(true);
			conteudo.getChildren().add(instrucaoAusentes);
		}

		if (!mapeamento.camposDuplicados().isEmpty()) {

			List<String> itensDuplicados = mapeamento.camposDuplicados().stream()
					.map(campo -> {
						List<String> colunas = mapeamento.colunasPorCampoDuplicado()
								.getOrDefault(campo, List.of());
						String colunasFormatadas = colunas.stream()
								.map(nome -> "\"" + nome + "\"")
								.collect(java.util.stream.Collectors.joining(", "));
						return campo + " — colunas do cabeçalho: " + colunasFormatadas;
					})
					.collect(java.util.stream.Collectors.toList());

			conteudo.getChildren().add(blocoComTitulo("Colunas duplicadas:", itensDuplicados));

			Label instrucaoDuplicadas = new Label(
					"Duas colunas do cabeçalho apontam para o mesmo campo. Se for repetição "
							+ "por engano, remova ou renomeie uma delas na planilha. Se as duas "
							+ "forem nomes válidos, revise os aliases cadastrados em Configurações "
							+ "→ Colunas da Planilha — pode ser necessário remover um deles.");
			instrucaoDuplicadas.setWrapText(true);
			conteudo.getChildren().add(instrucaoDuplicadas);
		}

		Button btnAbrirConfiguracoes = new Button("Abrir Configurações");
		btnAbrirConfiguracoes.setOnAction(e -> {
			alert.close();
			// alert.close() só agenda o fim do loop modal do showAndWait() deste Alert;
			// abrir outro showAndWait() já no mesmo handler empilha um segundo loop modal
			// antes do primeiro desmontar, e o diálogo novo renderiza mas não recebe cliques.
			// Platform.runLater adia a abertura para o próximo pulso, já com o loop anterior encerrado.
			Platform.runLater(() -> configuracoesDialog.abrir(stage));
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
	private void mostrarDialogoErrosValidacao(String logErros, String nomePlanilha, Stage stage,
			List<ErroValidacao> avisosColunaOpcional) {

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

		VBox layout = new VBox(10);

		if (!avisosColunaOpcional.isEmpty()) {
			layout.getChildren().add(criarBannerColunasOpcionaisAusentes(avisosColunaOpcional));
		}

		layout.getChildren().addAll(areaLog, btnSalvarLog);
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

