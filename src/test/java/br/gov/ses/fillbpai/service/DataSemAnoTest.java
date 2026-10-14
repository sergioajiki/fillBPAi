package br.gov.ses.fillbpai.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Data digitada só com dia e mês: o Excel grava a data com o ano corrente e
 * mostra só dia/mês (formato {@code dd/mm}, {@code d-mmm}). Verificado em
 * 14/10/2026: o paciente virava um bebê de meses, sem alerta. Nascimento →
 * ERRO; atendimento → AVISO.
 */
class DataSemAnoTest {

	private static final String[] CABECALHO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"Especialidade", "ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO", "END. COMPLEMENTOS", "SEXO",
			"Situação de Rua", "Paciente sem CPF" };

	private static final int COL_ATENDIMENTO = 1;
	private static final int COL_NASCIMENTO = 14;

	@TempDir
	Path tempDir;

	private EntityManagerFactory emf;
	private EntityManager entityManager;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	/** Planilha de uma linha com atendimento e nascimento como células de data, no formato de exibição informado. */
	private String planilha(LocalDate atendimento, String formatoAtendimento, LocalDate nascimento,
			String formatoNascimento) throws IOException {

		String[] linha = { "TELECONSULTA", null, "08:30", "12345 - HOSPITAL CENTRAL",
				"CARDIOLOGIA", "RODRIGO SILVA GRILO", "98765432100", "225125",
				"CAMPO GRANDE", "12345678909", "MARIA SILVA", "700207960618529",
				"BRANCA", null, null, "I10", "67999999999",
				"URBANA", "081", "RUA DAS FLORES", "79003020", "100", "CENTRO", "APTO 1", "F", "N", "N" };

		try (Workbook wb = new XSSFWorkbook()) {
			Sheet sheet = wb.createSheet();
			Row header = sheet.createRow(0);
			for (int i = 0; i < CABECALHO.length; i++) {
				header.createCell(i).setCellValue(CABECALHO[i]);
			}
			Row row = sheet.createRow(1);
			for (int i = 0; i < linha.length; i++) {
				if (linha[i] != null) {
					row.createCell(i).setCellValue(linha[i]);
				}
			}
			celulaData(wb, row.createCell(COL_ATENDIMENTO), atendimento, formatoAtendimento);
			celulaData(wb, row.createCell(COL_NASCIMENTO), nascimento, formatoNascimento);

			Path arquivo = tempDir.resolve("planilha_" + System.nanoTime() + ".xlsx");
			try (OutputStream out = Files.newOutputStream(arquivo)) {
				wb.write(out);
			}
			return arquivo.toString();
		}
	}

	private void celulaData(Workbook wb, Cell cell, LocalDate data, String formato) {
		cell.setCellValue(data);
		CellStyle estilo = wb.createCellStyle();
		estilo.setDataFormat(wb.createDataFormat().getFormat(formato));
		cell.setCellStyle(estilo);
	}

	@Test
	void nascimentoDigitadoSoComDiaEMesGeraErroNaAnaliseEBloqueiaAImportacao() throws IOException {
		// "15/03" digitado em 2026: o Excel grava 15/03/2026 e mostra "15/03"
		for (String formato : List.of("dd/mm", "d-mmm")) {
			String caminho = planilha(LocalDate.of(2026, 9, 25), "dd/mm/yyyy", LocalDate.of(2026, 3, 15), formato);

			assertThat(new ValidacaoPlanilhaService().validar(caminho)).as(formato).singleElement().satisfies(e -> {
				assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
				assertThat(e.tipoErro()).isEqualTo(ErroValidacao.DATA_NASCIMENTO_SEM_ANO);
				assertThat(e.detalhe()).contains("(15/03)").contains("formato correto (dd/MM/aaaa)").doesNotContain("Excel");
			});

			assertThatThrownBy(() -> new AtendimentoImportacaoService(entityManager).importar(caminho))
					.as(formato)
					.hasMessageContaining("nenhuma linha foi importada")
					.hasMessageContaining("Data de nascimento (15/03) não está no formato correto (dd/MM/aaaa)");
		}
	}

	@Test
	void atendimentoMostradoSemAnoGeraSoAvisoEImporta() throws IOException {
		String caminho = planilha(LocalDate.of(2026, 9, 25), "dd/mm", LocalDate.of(1990, 3, 15), "dd/mm/yyyy");

		assertThat(new ValidacaoPlanilhaService().validar(caminho)).singleElement().satisfies(e -> {
			assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.DATA_ATENDIMENTO_SEM_ANO);
			assertThat(e.detalhe()).contains("(25/09)").contains("formato correto (dd/MM/aaaa)").doesNotContain("Excel");
		});

		ImportacaoResultado resultado = new AtendimentoImportacaoService(entityManager).importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getAvisos()).anySatisfy(a -> assertThat(a)
				.contains("DATA_ATENDIMENTO_SEM_ANO").contains("(25/09)"));
	}

	@Test
	void datasComAnoNoFormatoNaoGeramAlerta() throws IOException {
		String caminho = planilha(LocalDate.of(2026, 9, 25), "dd/mm/yyyy", LocalDate.of(1990, 3, 15), "d-mmm-yy");

		assertThat(new ValidacaoPlanilhaService().validar(caminho)).isEmpty();

		new AtendimentoImportacaoService(entityManager).importar(caminho);
		assertThat(new AtendimentoBPAiRepository(entityManager).buscarTodos()).singleElement()
				.satisfies(a -> assertThat(a.getPaciente().getDataNascimento()).isEqualTo(LocalDate.of(1990, 3, 15)));
	}
}
