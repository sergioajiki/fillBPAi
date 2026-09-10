package br.gov.ses.fillbpai.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportServiceTest {

	private final ExcelImportService service = new ExcelImportService();

	private Workbook workbook;
	private Row row;

	private Cell celula(int indice) {
		if (workbook == null) {
			workbook = new XSSFWorkbook();
			Sheet sheet = workbook.createSheet();
			row = sheet.createRow(0);
		}
		return row.createCell(indice);
	}

	@Test
	void importarLinhaComCelulaTextoTrimaOValor() {
		celula(0).setCellValue("  TELECONSULTA  ");

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("TIPO_SERVICO", 0));

		assertThat(dto.getTipoServico()).isEqualTo("TELECONSULTA");
	}

	@Test
	void importarLinhaComCelulaNumericaFormataSemNotacaoCientifica() {
		celula(0).setCellValue(12345678900.0);

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("CPF_PACIENTE", 0));

		assertThat(dto.getCpfPaciente()).isEqualTo("12345678900");
	}

	@Test
	void importarLinhaComCelulaDeDataFormataComoIso() {
		Cell cell = celula(0);
		CellStyle estiloData = workbook.createCellStyle();
		estiloData.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
		cell.setCellStyle(estiloData);
		cell.setCellValue(LocalDate.of(2024, 12, 25));

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("DATA_AGENDAMENTO", 0));

		assertThat(dto.getDataAgendamentoString()).isEqualTo("2024-12-25");
	}

	@Test
	void importarLinhaComCelulaSoDeHoraBase1899FormataComoHora() {
		Cell cell = celula(0);
		CellStyle estiloHora = workbook.createCellStyle();
		estiloHora.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("hh:mm"));
		cell.setCellStyle(estiloHora);
		cell.setCellValue(LocalDateTime.of(1899, 12, 31, 8, 30));

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("HORA_ATENDIMENTO", 0));

		assertThat(dto.getHoraAtendimentoString()).isEqualTo("08:30");
	}

	@Test
	void importarLinhaComCelulaBooleanaConverteParaTexto() {
		celula(0).setCellValue(true);

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("CNS_PACIENTE", 0));

		assertThat(dto.getCnsPaciente()).isEqualTo("true");
	}

	@Test
	void importarLinhaComCelulaDeFormulaMantemAFormulaComoTexto() {
		celula(0).setCellFormula("A1+B1");

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("TELEFONE", 0));

		assertThat(dto.getTelefone()).isEqualTo("A1+B1");
	}

	@Test
	void importarLinhaComCelulaEmBrancoRetornaNulo() {
		celula(0);

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of("BAIRRO", 0));

		assertThat(dto.getBairro()).isNull();
	}

	@Test
	void importarLinhaComCampoNaoMapeadoRetornaNuloSemLancarExcecao() {
		celula(0).setCellValue("valor qualquer");

		LinhaImportacaoDTO dto = service.importarLinha(row, Map.of());

		assertThat(dto.getBairro()).isNull();
	}
}
