package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.CepUtils;
import br.gov.ses.fillbpai.util.CnsUtils;
import br.gov.ses.fillbpai.util.CpfUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por validar uma planilha Excel antes da importação.
 * <p>
 * Percorre todas as linhas da planilha e aplica 3 regras de validação
 * bloqueantes sobre os dados brutos. Nenhum dado é persistido — o objetivo
 * é apenas identificar e reportar inconsistências para correção manual.
 * <p>
 * Regras validadas:
 * <ul>
 *   <li>CNS do paciente ausente ou com menos de 15 dígitos: AVISO (não bloqueia)</li>
 *   <li>CNS do paciente com mais de 15 dígitos: AVISO (não bloqueia)</li>
 *   <li>CEP: não pode ser ausente ou vazio — ERRO bloqueante</li>
 *   <li>CPF do paciente: não pode ser ausente ou vazio — ERRO bloqueante</li>
 * </ul>
 *
 * @see ErroValidacao
 * @see ExcelImportService
 */
public class ValidacaoPlanilhaService {

	private static final Logger log = LoggerFactory.getLogger(ValidacaoPlanilhaService.class);

	private static final String[] COLUNAS_ESPERADAS = {
		"Tipo de Serviço",
		"DATA DE AGENDAMENTO",
		"HORA ATENDIMENTO",
		"ESTABELECIMENTO",
		"ESPECIALIDADE/MEDICO",
		"CPF DO MEDICO",
		"CBO DO MEDICO",
		"MUNICIPIOS",
		"CPF DO PACIENTE",
		"PACIENTE",
		"CNS DO PACIENTE",
		"RACA DO PACIENTE",
		"DATA DE NASCIMENTO",
		"CID DA CONSULTA",
		"TELEFONE",
		"TIPO_ZONA",
		"Log",
		"RUA",
		"CEP",
		"NUM. IMOVEL",
		"BAIRRO",
		"END. COMPLEMENTOS",
		"SEXO"
	};

	/** Serviço de leitura de linhas Excel, reutilizado da importação. */
	private final ExcelImportService excelService = new ExcelImportService();

	/**
	 * Lê e valida todas as linhas da planilha Excel informada.
	 * <p>
	 * O cabeçalho (linha 0) é ignorado. Para cada linha de dados, as 3 regras
	 * bloqueantes são verificadas. Linhas que causem exceção durante a leitura
	 * são logadas e puladas sem interromper o processo.
	 *
	 * @param caminhoArquivo caminho absoluto para o arquivo {@code .xlsx}
	 * @return lista de erros encontrados; vazia se a planilha estiver válida
	 */
	public List<ErroValidacao> validar(String caminhoArquivo) {

		List<ErroValidacao> erros = new ArrayList<>();

		try (FileInputStream fis = new FileInputStream(caminhoArquivo);
			 Workbook workbook = new XSSFWorkbook(fis)) {

			// Considera sempre a primeira aba da planilha
			Sheet sheet = workbook.getSheetAt(0);

			// Valida estrutura antes de processar linhas — aborta se colunas incorretas
			if (!validarEstrutura(sheet, erros)) {
				return erros;
			}

			for (Row row : sheet) {

				// Pula o cabeçalho (linha de índice 0)
				if (row.getRowNum() == 0) {
					continue;
				}

				// Número da linha no relatório (1-based, inclui cabeçalho)
				int numeroLinha = row.getRowNum() + 1;

				try {
					LinhaImportacaoDTO dto = excelService.importarLinha(row);
					validarLinha(dto, numeroLinha, erros);
				} catch (Exception e) {
					log.warn("Erro ao ler linha {}: {} — linha ignorada.", numeroLinha, e.getMessage());
				}
			}

		} catch (IOException e) {
			log.error("Falha ao abrir o arquivo Excel para validação: {}", caminhoArquivo, e);
		}

		return erros;
	}

	/**
	 * Aplica as 3 regras de validação bloqueantes sobre um DTO já lido.
	 * Cada violação gera um {@link ErroValidacao} adicionado à lista.
	 *
	 * @param dto        dados brutos lidos da linha
	 * @param linha      número da linha (1-based) para referência no relatório
	 * @param erros      lista acumuladora de erros
	 */
	private void validarLinha(LinhaImportacaoDTO dto, int linha, List<ErroValidacao> erros) {

		// -------------------------------------------------------
		// Regra 1: CNS do paciente
		// - Ausente ou < 15 dígitos → AVISO, não bloqueia
		// - > 15 dígitos (formato legado) → AVISO, não bloqueia
		// -------------------------------------------------------
		String cnsNormalizado = CnsUtils.normalizar(dto.getCnsPaciente());

		if (cnsNormalizado == null || cnsNormalizado.isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INVALIDO, "CNS do paciente não informado — registrado com aviso (CNS_INVALIDO)"));
		} else if (cnsNormalizado.length() < 15) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INVALIDO,
					"CNS inválido (apenas " + cnsNormalizado.length()
							+ " dígitos, mínimo 15) — registrado com aviso (CNS_INVALIDO)"));
		} else if (cnsNormalizado.length() > 15) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
					ErroValidacao.CNS_INCOMUM,
					"CNS com formato incomum (" + cnsNormalizado.length()
							+ " dígitos, esperado 15): " + cnsNormalizado));
		}

		// -------------------------------------------------------
		// Regra 2: CEP do endereço — ERRO bloqueante
		// -------------------------------------------------------
		String cep = dto.getCep();

		if (cep == null || cep.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CEP_AUSENTE, "CEP do endereço não informado"));
		} else if (!CepUtils.isValido(cep)) {
			String cepNormalizado = CepUtils.normalizar(cep);
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CEP_INVALIDO,
					"CEP com tamanho inválido (" + (cepNormalizado != null ? cepNormalizado.length() : 0)
							+ " dígitos, esperado 8): " + cep.trim()));
		}

		// -------------------------------------------------------
		// Regra 3: CPF do paciente — ERRO bloqueante
		// -------------------------------------------------------
		String cpf = dto.getCpfPaciente();

		if (cpf == null || cpf.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_AUSENTE, "CPF do paciente não informado"));
		} else if (!CpfUtils.isValido(cpf)) {
			String cpfNormalizado = CpfUtils.normalizar(cpf);
			erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.ERRO,
					ErroValidacao.CPF_INVALIDO,
					"CPF com tamanho inválido (" + (cpfNormalizado != null ? cpfNormalizado.length() : 0)
							+ " dígitos, esperado 11): " + cpf.trim()));
		}

		// -------------------------------------------------------
		// Regra 4: Raça Indígena — AVISO, não bloqueia
		// -------------------------------------------------------
		String raca = dto.getRacaPaciente();

		if (raca != null && !raca.trim().isEmpty()) {
			String racaNorm = Normalizer
					.normalize(raca.trim(), Normalizer.Form.NFD)
					.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
					.toUpperCase();
			if ("INDIGENA".equals(racaNorm)) {
				erros.add(new ErroValidacao(linha, ErroValidacao.Severidade.AVISO,
						ErroValidacao.RACA_INDIGENA,
						"Raca informada como Indigena - e necessario verificar a etnia do paciente"));
			}
		}
	}

	/**
	 * Verifica se o cabeçalho da planilha contém as colunas obrigatórias na ordem correta.
	 * A comparação ignora acentos e diferenças de capitalização.
	 *
	 * @return true se a estrutura estiver correta; false se houver qualquer divergência
	 */
	private boolean validarEstrutura(Sheet sheet, List<ErroValidacao> erros) {

		Row cabecalho = sheet.getRow(0);

		if (cabecalho == null) {
			erros.add(new ErroValidacao(1, ErroValidacao.Severidade.ERRO,
					ErroValidacao.ESTRUTURA_INVALIDA,
					"Planilha sem cabecalho — impossivel validar estrutura"));
			return false;
		}

		boolean valida = true;

		for (int i = 0; i < COLUNAS_ESPERADAS.length; i++) {

			Cell celula = cabecalho.getCell(i);
			String valorAtual = (celula != null) ? celula.getStringCellValue() : null;

			String esperadoNorm = normalizarTexto(COLUNAS_ESPERADAS[i]);
			String atualNorm    = normalizarTexto(valorAtual);

			if (!esperadoNorm.equals(atualNorm)) {
				erros.add(new ErroValidacao(1, ErroValidacao.Severidade.ERRO,
						ErroValidacao.ESTRUTURA_INVALIDA,
						"Coluna " + (i + 1) + ": esperado \"" + COLUNAS_ESPERADAS[i]
								+ "\", encontrado \"" + (valorAtual != null ? valorAtual : "(vazia)") + "\""));
				valida = false;
			}
		}

		return valida;
	}

	/** Remove acentos e converte para maiúsculas para comparação normalizada. */
	private static String normalizarTexto(String texto) {
		if (texto == null || texto.trim().isEmpty()) return "";
		return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
				.replaceAll("\\p{InCombiningDiacriticalMarks}", "")
				.toUpperCase();
	}

	/**
	 * Gera um relatório TXT formatado a partir da lista de erros de validação.
	 * <p>
	 * O relatório exibe cabeçalho, contagem total de erros e uma tabela com
	 * linha, tipo de erro e detalhe para cada ocorrência encontrada.
	 * <p>
	 * Se a lista de erros estiver vazia, o relatório indica que a planilha
	 * está válida e apta para importação.
	 *
	 * @param erros lista de erros retornada por {@link #validar(String)}
	 * @return string com o relatório formatado, pronto para exibição ou gravação em arquivo
	 */
	public String gerarLogTxt(List<ErroValidacao> erros, String nomeArquivo) {

		List<ErroValidacao> bloqueantes = erros.stream()
				.filter(ErroValidacao::isBloqueante)
				.collect(java.util.stream.Collectors.toList());

		List<ErroValidacao> avisos = erros.stream()
				.filter(e -> !e.isBloqueante())
				.collect(java.util.stream.Collectors.toList());

		String dataHora = LocalDateTime.now()
				.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

		StringBuilder sb = new StringBuilder();
		sb.append("=== RELATÓRIO DE VALIDAÇÃO DA PLANILHA ===\n");
		sb.append("Arquivo  : ").append(nomeArquivo).append("\n");
		sb.append("Data/Hora: ").append(dataHora).append("\n");
		sb.append("Erros bloqueantes: ").append(bloqueantes.size()).append("\n");
		sb.append("Avisos: ").append(avisos.size()).append("\n");

		if (erros.isEmpty()) {
			sb.append("\nNenhum problema encontrado. A planilha está apta para importação.\n");
			return sb.toString();
		}

		if (!bloqueantes.isEmpty()) {
			sb.append("\n--- ERROS (impedem a importação) ---\n");
			for (ErroValidacao e : bloqueantes) {
				sb.append(String.format("Linha %d - %s: %s%n", e.linha(), e.tipoErro(), e.detalhe()));
			}
		}

		if (!avisos.isEmpty()) {
			sb.append("\n--- AVISOS (não impedem a importação) ---\n");
			for (ErroValidacao e : avisos) {
				sb.append(String.format("Linha %d - %s: %s%n", e.linha(), e.tipoErro(), e.detalhe()));
			}
		}

		return sb.toString();
	}
}
