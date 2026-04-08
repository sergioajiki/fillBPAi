package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import br.gov.ses.fillbpai.util.CnsUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
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
 *   <li>CNS do paciente: deve possuir ao menos 15 dígitos após normalização</li>
 *   <li>CEP: não pode ser ausente ou vazio</li>
 *   <li>CPF do paciente: não pode ser ausente ou vazio</li>
 * </ul>
 *
 * @see ErroValidacao
 * @see ExcelImportService
 */
public class ValidacaoPlanilhaService {

	private static final Logger log = LoggerFactory.getLogger(ValidacaoPlanilhaService.class);

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
		// Após normalização (somente dígitos), deve ter ao menos 15 caracteres.
		// -------------------------------------------------------
		String cnsNormalizado = CnsUtils.normalizar(dto.getCnsPaciente());

		if (cnsNormalizado == null || cnsNormalizado.isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.CNS_INVALIDO,
					"CNS do paciente não informado"));
		} else if (cnsNormalizado.length() < 15) {
			erros.add(new ErroValidacao(linha, ErroValidacao.CNS_INVALIDO,
					"CNS do paciente possui apenas " + cnsNormalizado.length()
							+ " dígitos (mínimo: 15)"));
		}

		// -------------------------------------------------------
		// Regra 2: CEP do endereço
		// Campo obrigatório — necessário para resolução do código IBGE via ViaCEP.
		// -------------------------------------------------------
		String cep = dto.getCep();

		if (cep == null || cep.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.CEP_AUSENTE,
					"CEP do endereço não informado"));
		}

		// -------------------------------------------------------
		// Regra 3: CPF do paciente
		// Campo obrigatório — usado como chave natural na entidade Paciente.
		// -------------------------------------------------------
		String cpf = dto.getCpfPaciente();

		if (cpf == null || cpf.trim().isEmpty()) {
			erros.add(new ErroValidacao(linha, ErroValidacao.CPF_AUSENTE,
					"CPF do paciente não informado"));
		}
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
	public String gerarLogTxt(List<ErroValidacao> erros) {

		StringBuilder sb = new StringBuilder();

		sb.append("=== RELATÓRIO DE VALIDAÇÃO DA PLANILHA ===\n");
		sb.append("Total de erros encontrados: ").append(erros.size()).append("\n");

		if (erros.isEmpty()) {
			sb.append("\nNenhum erro encontrado. A planilha está apta para importação.\n");
			return sb.toString();
		}

		// Cabeçalho da tabela
		sb.append("\n");
		sb.append(String.format("%-6s| %-15s| %s%n", "Linha", "Tipo do Erro", "Detalhe"));
		sb.append("------|-----------------|--------\n");

		// Uma linha por erro
		for (ErroValidacao erro : erros) {
			sb.append(String.format("%-6d| %-15s| %s%n",
					erro.linha(),
					erro.tipoErro(),
					erro.detalhe()));
		}

		return sb.toString();
	}
}
