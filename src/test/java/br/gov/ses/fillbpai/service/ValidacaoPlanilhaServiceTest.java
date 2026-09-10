package br.gov.ses.fillbpai.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ValidacaoPlanilhaServiceTest {

	/** Cabeçalho com os 25 campos obrigatórios, usando aliases reais de {@code dados/colunas_aliases.csv}. */
	private static final String[] CABECALHO_COMPLETO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"Especialidade", "ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO",
			"END. COMPLEMENTOS", "SEXO"
	};

	/** Cabeçalho legado: igual ao completo, mas sem a coluna própria "Especialidade". */
	private static final String[] CABECALHO_LEGADO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO",
			"END. COMPLEMENTOS", "SEXO"
	};

	private static final int COL_CPF_PACIENTE = 9;
	private static final int COL_CNS_PACIENTE = 11;
	private static final int COL_RACA_PACIENTE = 12;
	private static final int COL_ETNIA_PACIENTE = 13;
	private static final int COL_CEP = 20;

	private final ValidacaoPlanilhaService service = new ValidacaoPlanilhaService();

	@TempDir
	Path tempDir;

	private String[] linhaValida() {
		return new String[] {
				"TELECONSULTA", "25/12/2024", "08:30", "12345 - HOSPITAL CENTRAL",
				"CARDIOLOGIA", "JOAO DA SILVA", "98765432100", "225125",
				"CAMPO GRANDE", "12345678900", "MARIA SILVA", "700207960618529",
				"BRANCA", null, "01/01/1990", "I10", "67999999999",
				"URBANA", "081", "RUA DAS FLORES", "79003020", "100", "CENTRO",
				"APTO 1", "F"
		};
	}

	/** Linha de dados no formato legado — combinado no lugar de "Especialidade" + "ESPECIALIDADE/MEDICO". */
	private String[] linhaLegado() {
		return new String[] {
				"TELECONSULTA", "25/12/2024", "08:30", "12345 - HOSPITAL CENTRAL",
				"CARDIOLOGIA - RODRIGO SILVA GRILO", "98765432100", "225125",
				"CAMPO GRANDE", "12345678900", "MARIA SILVA", "700207960618529",
				"BRANCA", null, "01/01/1990", "I10", "67999999999",
				"URBANA", "081", "RUA DAS FLORES", "79003020", "100", "CENTRO",
				"APTO 1", "F"
		};
	}

	private String salvarPlanilha(String[] cabecalho, String[]... linhasDados) throws IOException {

		try (Workbook workbook = new XSSFWorkbook()) {

			Sheet sheet = workbook.createSheet();

			Row headerRow = sheet.createRow(0);
			for (int i = 0; i < cabecalho.length; i++) {
				headerRow.createCell(i).setCellValue(cabecalho[i]);
			}

			int numeroLinha = 1;
			for (String[] linha : linhasDados) {
				Row row = sheet.createRow(numeroLinha++);
				for (int i = 0; i < linha.length; i++) {
					if (linha[i] != null) {
						row.createCell(i).setCellValue(linha[i]);
					}
				}
			}

			Path arquivo = tempDir.resolve("planilha_" + System.nanoTime() + ".xlsx");
			try (OutputStream out = Files.newOutputStream(arquivo)) {
				workbook.write(out);
			}
			return arquivo.toString();
		}
	}

	@Test
	void validarComPlanilhaTotalmenteValidaNaoRetornaErros() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linhaValida());

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).isEmpty();
	}

	@Test
	void validarComCnsAusenteGeraAvisoCnsInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CNS_PACIENTE] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_INVALIDO);
		});
	}

	@Test
	void validarComCnsLongoGeraAvisoCnsIncomum() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CNS_PACIENTE] = "7000094731924063";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_INCOMUM);
		});
	}

	@Test
	void validarComCepAusenteGeraErroCepAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CEP] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CEP_AUSENTE);
		});
	}

	@Test
	void validarComCepInvalidoGeraErroCepInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CEP] = "123";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CEP_INVALIDO);
		});
	}

	@Test
	void validarComCpfAusenteGeraErroCpfAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_AUSENTE);
		});
	}

	@Test
	void validarComCpfInvalidoGeraErroCpfInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = "123";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_INVALIDO);
		});
	}

	@Test
	void validarComRacaIndigenaGeraAvisoRacaIndigena() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "Indígena";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.RACA_INDIGENA);
		});
	}

	@Test
	void validarComRacaIndigenaEEtniaReconhecidaNaoGeraAvisoDeRaca() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "Indígena";
		linha[COL_ETNIA_PACIENTE] = "BANIWA";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.RACA_INDIGENA));
	}

	@Test
	void validarComEtniaPreenchidaNaoReconhecidaERacaIndigenaGeraAvisoEtniaNaoEncontrada() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "Indígena";
		linha[COL_ETNIA_PACIENTE] = "ETNIA QUE NAO EXISTE XYZ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ETNIA_NAO_ENCONTRADA);
			assertThat(erro.detalhe()).contains("ETNIA QUE NAO EXISTE XYZ");
		});
	}

	@Test
	void validarComEtniaPreenchidaNaoReconhecidaMasRacaNaoIndigenaNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "PARDA";
		linha[COL_ETNIA_PACIENTE] = "ETNIA QUE NAO EXISTE XYZ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.ETNIA_NAO_ENCONTRADA));
	}

	@Test
	void validarComColunaObrigatoriaAusenteNoCabecalhoParaAntesDeValidarLinhas() throws IOException {
		String[] cabecalhoIncompleto = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length - 1);
		String[] linhaComErrosDeLinha = linhaValida();
		linhaComErrosDeLinha[COL_CEP] = null; // erro que NÃO deve ser reportado, pois a validação para antes

		String caminho = salvarPlanilha(cabecalhoIncompleto, linhaComErrosDeLinha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro ->
				assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTRUTURA_INVALIDA));
	}

	@Test
	void validarComPlanilhaLegadoNaoReportaEstruturaInvalidaEGeraAvisoComExemploReal() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_LEGADO, linhaLegado());

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(ErroValidacao::isEstrutural);

		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.FORMATO_LEGADO_ESPECIALIDADE_MEDICO);
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.detalhe()).contains("CARDIOLOGIA - RODRIGO SILVA GRILO");
			assertThat(erro.detalhe()).contains("Especialidade: CARDIOLOGIA");
			assertThat(erro.detalhe()).contains("Médico: RODRIGO SILVA GRILO");
		});
	}

	@Test
	void gerarLogTxtSeparaErrosEAvisosEmSecoesDistintas() {
		List<ErroValidacao> erros = List.of(
				new ErroValidacao(2, ErroValidacao.Severidade.ERRO, ErroValidacao.CEP_AUSENTE, "CEP do endereço não informado"),
				new ErroValidacao(3, ErroValidacao.Severidade.AVISO, ErroValidacao.CNS_INVALIDO, "CNS do paciente não informado"));

		String log = service.gerarLogTxt(erros, "planilha_teste.xlsx");

		assertThat(log).contains("Erros bloqueantes: 1");
		assertThat(log).contains("Avisos: 1");
		assertThat(log).contains("--- ERROS (impedem a importação) ---");
		assertThat(log).contains("--- AVISOS (não impedem a importação) ---");
		assertThat(log).contains(ErroValidacao.CEP_AUSENTE);
		assertThat(log).contains(ErroValidacao.CNS_INVALIDO);
	}

	@Test
	void gerarLogTxtComListaVaziaIndicaPlanilhaApta() {
		String log = service.gerarLogTxt(List.of(), "planilha_teste.xlsx");

		assertThat(log).contains("apta para importação");
	}
}
