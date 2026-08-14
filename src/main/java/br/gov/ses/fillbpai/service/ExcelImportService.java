package br.gov.ses.fillbpai.service;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;
import org.apache.poi.ss.usermodel.*;

import java.text.DecimalFormat;

/**
 * Serviço responsável por:
 * - Ler uma linha da planilha Excel
 * - Converter todas as células para String
 * - NÃO realizar validações ou conversões definitivas
 *
 * IMPORTANTE:
 * Esta classe NÃO valida regras de negócio.
 * Apenas extrai dados do Excel para o DTO de importação.
 *
 * A conversão para LocalDate/LocalTime será feita posteriormente
 * pela classe AtendimentoProcessor.
 */
public class ExcelImportService {

	/**
	 * Converte uma linha do Excel em um LinhaImportacaoDTO.
	 *
	 * Todos os valores são carregados inicialmente como String.
	 * Isso evita perda de informação e delega a validação
	 * para a camada de processamento.
	 */
	public LinhaImportacaoDTO importarLinha(Row row) {

		LinhaImportacaoDTO dto = new LinhaImportacaoDTO();

		dto.setTipoServico(getString(row.getCell(0)));
		dto.setDataAgendamentoString(getString(row.getCell(1)));
		dto.setHoraAtendimentoString(getString(row.getCell(2)));
		dto.setEstabelecimento(getString(row.getCell(3)));

		// Colunas (E) e (F): especialidade e nome do médico já vêm separados
		// (antes vinham combinados em uma única coluna "ESPECIALIDADE - NOME")
		dto.setEspecialidadeMedico(getString(row.getCell(4)));
		dto.setMedico(getString(row.getCell(5)));

		dto.setCpfMedico(getString(row.getCell(6)));
		dto.setCboMedico(getString(row.getCell(7)));
		dto.setMunicipio(getString(row.getCell(8)));
		dto.setCpfPaciente(getString(row.getCell(9)));
		dto.setPaciente(getString(row.getCell(10)));
		dto.setCnsPaciente(getString(row.getCell(11)));
		dto.setRacaPaciente(getString(row.getCell(12)));

		// Data de nascimento armazenada como String (conversão posterior)
		dto.setDataNascimentoString(getString(row.getCell(13)));

		dto.setCidConsulta(getString(row.getCell(14)));
		dto.setTelefone(getString(row.getCell(15)));

		dto.setTipoZona(getString(row.getCell(16)));
		dto.setCodLogradouro(getString(row.getCell(17)));
		dto.setEndereco(getString(row.getCell(18)));
		dto.setCep(getString(row.getCell(19)));
		dto.setNumero(getString(row.getCell(20)));
		dto.setBairro(getString(row.getCell(21)));
		dto.setComplemento(getString(row.getCell(22)));
		dto.setSexoPaciente(getString(row.getCell(23)));

		return dto;
	}

	/**
	 * Método auxiliar responsável por converter qualquer tipo de célula
	 * do Excel em String, preservando o valor original.
	 *
	 * Este método trata corretamente:
	 * - Strings
	 * - Números
	 * - Datas
	 * - Horas (incluindo padrão 1899 do Excel)
	 * - Booleanos
	 * - Fórmulas
	 */
	private String getString(Cell cell) {

		if (cell == null) {
			return null;
		}

		switch (cell.getCellType()) {

			// ===============================
			// TEXTO NORMAL
			// ===============================
			case STRING:
				return cell.getStringCellValue().trim();

			// ===============================
			// NÚMEROS (incluindo datas/horas)
			// ===============================
			case NUMERIC:

				// Verifica se a célula é formatada como Data/Hora
				if (DateUtil.isCellDateFormatted(cell)) {

					var dateTime = cell.getLocalDateTimeCellValue();

					/*
					 * O Excel armazena horas como fração de dia.
					 * Quando lido como data, aparece como:
					 * 1899-12-31T08:30
					 *
					 * Se o ano for 1899, significa que é apenas hora.
					 */
					if (dateTime.getYear() == 1899) {
						return dateTime.toLocalTime().toString();
					}

					// Caso seja uma data válida
					return dateTime.toLocalDate().toString();
				}

				/*
				 * Caso seja número comum (CPF, CNS etc.)
				 * Utilizamos DecimalFormat para evitar notação científica
				 * e remover casas decimais indesejadas.
				 */
				DecimalFormat df = new DecimalFormat("0");
				df.setMaximumFractionDigits(0);
				return df.format(cell.getNumericCellValue());

			// ===============================
			// BOOLEANO
			// ===============================
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());

			// ===============================
			// FÓRMULA
			// ===============================
			case FORMULA:
				/*
				 * Mantemos a fórmula como texto.
				 * Se quiser futuramente avaliar o resultado da fórmula,
				 * podemos usar FormulaEvaluator.
				 */
				return cell.getCellFormula();

			// ===============================
			// CÉLULA EM BRANCO
			// ===============================
			case BLANK:
				return null;

			// ===============================
			// OUTROS TIPOS
			// ===============================
			default:
				return cell.toString().trim();
		}
	}
}
