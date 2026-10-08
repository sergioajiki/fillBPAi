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

	/** Cabeçalho com os 25 campos obrigatórios + Situação de Rua e Paciente sem CPF (opcionais), usando aliases reais de {@code dados/colunas_aliases.csv}. */
	private static final String[] CABECALHO_COMPLETO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"Especialidade", "ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO",
			"END. COMPLEMENTOS", "SEXO", "Situação de Rua", "Paciente sem CPF"
	};

	/** Cabeçalho legado: igual ao completo, mas sem a coluna própria "Especialidade". */
	private static final String[] CABECALHO_LEGADO = {
			"Tipo de Serviço", "DATA DE AGENDAMENTO", "HORA ATENDIMENTO", "ESTABELECIMENTO",
			"ESPECIALIDADE/MEDICO", "CPF DO MEDICO", "CBO DO MEDICO",
			"MUNICIPIOS", "CPF DO PACIENTE", "PACIENTE", "CNS DO PACIENTE",
			"RACA DO PACIENTE", "ETNIA DO PACIENTE", "DATA DE NASCIMENTO", "CID DA CONSULTA", "TELEFONE",
			"TIPO_ZONA", "Log", "RUA", "CEP", "NUM. IMOVEL", "BAIRRO",
			"END. COMPLEMENTOS", "SEXO", "Situação de Rua", "Paciente sem CPF"
	};

	private static final int COL_TIPO_SERVICO = 0;
	private static final int COL_DATA_AGENDAMENTO = 1;
	private static final int COL_HORA_ATENDIMENTO = 2;
	private static final int COL_ESPECIALIDADE = 4;
	private static final int COL_ESTABELECIMENTO = 3;
	private static final int COL_MEDICO = 5;
	private static final int COL_CPF_MEDICO = 6;
	private static final int COL_CPF_PACIENTE = 9;
	private static final int COL_CNS_PACIENTE = 11;
	private static final int COL_RACA_PACIENTE = 12;
	private static final int COL_ETNIA_PACIENTE = 13;
	private static final int COL_CEP = 20;
	private static final int COL_SITUACAO_RUA = 25;
	private static final int COL_PACIENTE_SEM_CPF = 26;

	private final ValidacaoPlanilhaService service = new ValidacaoPlanilhaService();

	@TempDir
	Path tempDir;

	private String[] linhaValida() {
		return new String[] {
				"TELECONSULTA", "25/12/2024", "08:30", "12345 - HOSPITAL CENTRAL",
				"CARDIOLOGIA", "RODRIGO SILVA GRILO", "98765432100", "225125",
				"CAMPO GRANDE", "12345678900", "MARIA SILVA", "700207960618529",
				"BRANCA", null, "01/01/1990", "I10", "67999999999",
				"URBANA", "081", "RUA DAS FLORES", "79003020", "100", "CENTRO",
				"APTO 1", "F", "N", "N"
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
				"APTO 1", "F", "N", "N"
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
	void validarSemCpfDoMedicoGeraErroCpfMedicoAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_MEDICO] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_MEDICO_AUSENTE);
			assertThat(erro.detalhe()).contains("RODRIGO SILVA GRILO");
		});
	}

	@Test
	void validarComCpfDoMedicoInvalidoGeraErroCpfMedicoInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_MEDICO] = "123";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_MEDICO_INVALIDO);
			assertThat(erro.detalhe()).contains("123").contains("RODRIGO SILVA GRILO");
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
	void validarComRacaAusenteGeraErroRacaAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.RACA_AUSENTE);
		});
		assertThat(erros).anyMatch(ErroValidacao::isBloqueante);
	}

	@Test
	void validarComRacaNaoReconhecidaGeraErroRacaInvalida() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "XYZ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.RACA_INVALIDA);
			assertThat(erro.detalhe()).contains("XYZ");
		});
	}

	@Test
	void validarComVariacaoDeGrafiaDaRacaNaoGeraErroDeRaca() throws IOException {
		String[] linha = linhaValida();
		linha[COL_RACA_PACIENTE] = "Pardo";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.RACA_AUSENTE)
				|| erro.tipoErro().equals(ErroValidacao.RACA_INVALIDA));
	}

	@Test
	void validarComColunaObrigatoriaAusenteNoCabecalhoParaAntesDeValidarLinhas() throws IOException {
		List<String> cabecalhoIncompleto = new java.util.ArrayList<>(java.util.Arrays.asList(CABECALHO_COMPLETO));
		List<String> linhaComErrosDeLinha = new java.util.ArrayList<>(java.util.Arrays.asList(linhaValida()));

		// Remove "SEXO" (obrigatório) — não "Situação de Rua" (opcional, no final do cabeçalho).
		int indiceSexo = cabecalhoIncompleto.indexOf("SEXO");
		cabecalhoIncompleto.remove(indiceSexo);
		linhaComErrosDeLinha.remove(indiceSexo);

		linhaComErrosDeLinha.set(COL_CEP, null); // erro que NÃO deve ser reportado, pois a validação para antes

		String caminho = salvarPlanilha(
				cabecalhoIncompleto.toArray(new String[0]),
				linhaComErrosDeLinha.toArray(new String[0]));

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro ->
				assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTRUTURA_INVALIDA));
	}

	@Test
	void validarComColunaCodLogradouroAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente() throws IOException {
		List<String> cabecalhoSemLogradouro = new java.util.ArrayList<>(java.util.Arrays.asList(CABECALHO_COMPLETO));
		List<String> linhaSemLogradouro = new java.util.ArrayList<>(java.util.Arrays.asList(linhaValida()));

		int indiceLogradouro = cabecalhoSemLogradouro.indexOf("Log");
		cabecalhoSemLogradouro.remove(indiceLogradouro);
		linhaSemLogradouro.remove(indiceLogradouro);

		String caminho = salvarPlanilha(
				cabecalhoSemLogradouro.toArray(new String[0]),
				linhaSemLogradouro.toArray(new String[0]));

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(ErroValidacao::isBloqueante);
		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.COLUNA_OPCIONAL_AUSENTE);
			assertThat(erro.detalhe()).contains("Cód. Logradouro");
		});
	}

	@Test
	void validarComColunaSituacaoRuaAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente() throws IOException {
		List<String> cabecalhoSemSituacaoRua = new java.util.ArrayList<>(java.util.Arrays.asList(CABECALHO_COMPLETO));
		List<String> linhaSemSituacaoRua = new java.util.ArrayList<>(java.util.Arrays.asList(linhaValida()));

		int indice = cabecalhoSemSituacaoRua.indexOf("Situação de Rua");
		cabecalhoSemSituacaoRua.remove(indice);
		linhaSemSituacaoRua.remove(indice);

		String caminho = salvarPlanilha(
				cabecalhoSemSituacaoRua.toArray(new String[0]),
				linhaSemSituacaoRua.toArray(new String[0]));

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(ErroValidacao::isBloqueante);
		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.COLUNA_OPCIONAL_AUSENTE);
			assertThat(erro.detalhe()).contains("Situação de Rua").contains("'N'");
		});
	}

	@Test
	void validarComSituacaoRuaNaoReconhecidaGeraAvisoSituacaoRuaInvalida() throws IOException {
		String[] linha = linhaValida();
		linha[COL_SITUACAO_RUA] = "TALVEZ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.SITUACAO_RUA_INVALIDA);
			assertThat(erro.detalhe()).contains("TALVEZ");
		});
	}

	@Test
	void validarComSituacaoRuaReconhecidaNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_SITUACAO_RUA] = "Sim";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.SITUACAO_RUA_INVALIDA));
	}

	@Test
	void validarComColunaPacienteSemCpfAusenteNaoBloqueiaEGeraAvisoColunaOpcionalAusente() throws IOException {
		List<String> cabecalhoSemColuna = new java.util.ArrayList<>(java.util.Arrays.asList(CABECALHO_COMPLETO));
		List<String> linhaSemColuna = new java.util.ArrayList<>(java.util.Arrays.asList(linhaValida()));

		int indice = cabecalhoSemColuna.indexOf("Paciente sem CPF");
		cabecalhoSemColuna.remove(indice);
		linhaSemColuna.remove(indice);

		String caminho = salvarPlanilha(
				cabecalhoSemColuna.toArray(new String[0]),
				linhaSemColuna.toArray(new String[0]));

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(ErroValidacao::isBloqueante);
		assertThat(erros).anySatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.COLUNA_OPCIONAL_AUSENTE);
			assertThat(erro.detalhe()).contains("Paciente sem CPF").contains("derivará");
		});
	}

	@Test
	void validarComPacienteSemCpfNaoReconhecidoGeraAvisoPacienteSemCpfInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_PACIENTE_SEM_CPF] = "TALVEZ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.PACIENTE_SEM_CPF_INVALIDO);
			assertThat(erro.detalhe()).contains("TALVEZ");
		});
	}

	@Test
	void validarComPacienteSemCpfReconhecidoNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_PACIENTE_SEM_CPF] = "Sim";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.PACIENTE_SEM_CPF_INVALIDO));
	}

	@Test
	void validarComEstabelecimentoSemSeparadorGeraAvisoEstabelecimentoSemCodigo() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESTABELECIMENTO] = "HOSPITAL CENTRAL";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO);
			assertThat(erro.detalhe()).contains("HOSPITAL CENTRAL");
		});
	}

	@Test
	void validarComEstabelecimentoComCodigoNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESTABELECIMENTO] = "12345 - HOSPITAL CENTRAL";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO));
	}

	@Test
	void validarComEstabelecimentoVazioNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESTABELECIMENTO] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).noneMatch(erro -> erro.tipoErro().equals(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO));
	}

	@Test
	void validarComHoraNaoReconhecidaGeraAvisoHoraInvalida() throws IOException {
		String[] linha = linhaValida();
		linha[COL_HORA_ATENDIMENTO] = "-";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.HORA_INVALIDA);
			assertThat(erro.detalhe()).contains("\"-\"").contains("remessa BPA-I nao utiliza o horario");
		});
	}

	@Test
	void validarComHoraVaziaNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_HORA_ATENDIMENTO] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).isEmpty();
	}

	@Test
	void validarComHoraSoComEspacoNaoSeparavelNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_HORA_ATENDIMENTO] = " ";
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).isEmpty();
	}

	// ===== Tipo de serviço =====

	@Test
	void validarSemTipoDeServicoGeraErroTipoServicoAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_TIPO_SERVICO] = null;

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.TIPO_SERVICO_AUSENTE);
			assertThat(erro.valor()).isEqualTo("MARIA SILVA");
		});
	}

	@Test
	void validarSemTipoDeServicoParaNutricionistaOuPsicologoGeraSoAvisoComAEspecialidade() throws IOException {
		String[] nutricionista = linhaValida();
		nutricionista[COL_TIPO_SERVICO] = null;
		nutricionista[COL_ESPECIALIDADE] = "Nutricionista";
		String[] psicologo = linhaValida();
		psicologo[COL_TIPO_SERVICO] = null;
		psicologo[COL_ESPECIALIDADE] = "Médico Psicólogo";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, nutricionista, psicologo));

		// Não bloqueia (procedimento fixo 0301010315), mas a coluna vazia é indicada
		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO);
			assertThat(erro.detalhe()).contains("0301010315");
		});
		assertThat(erros).extracting(ErroValidacao::valor).containsExactly("Nutricionista", "Psicólogo");
	}

	@Test
	void validarComTipoDeServicoNaoReconhecidoGeraErroTipoServicoInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_TIPO_SERVICO] = "Consulta";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.TIPO_SERVICO_INVALIDO);
			assertThat(erro.valor()).isEqualTo("\"Consulta\"");
			assertThat(erro.detalhe()).contains("Teleconsulta").contains("Teleinterconsulta");
		});
	}

	@Test
	void validarComTipoDeServicoEmMinusculasNaoGeraErro() throws IOException {
		String[] linha = linhaValida();
		linha[COL_TIPO_SERVICO] = "teleinterconsulta";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).isEmpty();
	}

	// ===== Data de agendamento (data do atendimento) =====

	@Test
	void validarSemDataDeAgendamentoGeraErroDataAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_DATA_AGENDAMENTO] = null;

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.DATA_AUSENTE);
			assertThat(erro.valor()).isEqualTo("MARIA SILVA");
		});
	}

	@Test
	void validarComDataDeAgendamentoEmFormatoNaoReconhecidoGeraErroDataInvalida() throws IOException {
		String[] linha = linhaValida();
		linha[COL_DATA_AGENDAMENTO] = "25.12.2024";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.DATA_INVALIDA);
			assertThat(erro.valor()).isEqualTo("\"25.12.2024\"");
			assertThat(erro.detalhe()).contains("dd/MM/aaaa");
		});
	}

	@Test
	void validarComDataDeAgendamentoNosFormatosAceitosNaoGeraErro() throws IOException {
		String[] iso = linhaValida();
		iso[COL_DATA_AGENDAMENTO] = "2024-12-25";
		String[] hifen = linhaValida();
		hifen[COL_DATA_AGENDAMENTO] = "25-12-2024";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, iso, hifen))).isEmpty();
	}

	// ===== CNS do profissional =====

	private String[] linhaComMedico(String nome, String cpfMedico, String cpfPaciente) {
		String[] linha = linhaValida();
		linha[COL_MEDICO] = nome;
		linha[COL_CPF_MEDICO] = cpfMedico;
		linha[COL_CPF_PACIENTE] = cpfPaciente;
		return linha;
	}

	@Test
	void validarComMedicoSemCnsCadastradoGeraAvisoEmCadaLinhaComOMesmoValorDaGrafia() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("MEDICO NAO CADASTRADO XYZ", "11122233344", "12345678900"),
				linhaValida(),
				linhaComMedico("Médico Não Cadastrado XYZ", "11122233344", "98765432100"));

		List<ErroValidacao> erros = service.validar(caminho);

		// Um aviso por linha, todos com o mesmo valor (primeira grafia vista) —
		// a tela e o log agrupam por valor (AgrupamentoValidacao).
		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO);
			assertThat(erro.valor()).isEqualTo("\"MEDICO NAO CADASTRADO XYZ\"");
			assertThat(erro.detalhe())
					.contains("MEDICO NAO CADASTRADO XYZ")
					.contains("2 linhas")
					.contains("bloqueada");
		});
		assertThat(erros).extracting(ErroValidacao::linha).containsExactly(2, 4);
	}

	@Test
	void validarPreencheOValorDoAvisoSeparadoDaExplicacao() throws IOException {
		String[] semCep = linhaValida();
		semCep[COL_CEP] = null;
		String[] horaHifen = linhaValida();
		horaHifen[COL_HORA_ATENDIMENTO] = "-";
		String[] estabSemCodigo = linhaValida();
		estabSemCodigo[COL_ESTABELECIMENTO] = "HOSPITAL CENTRAL";

		List<ErroValidacao> erros = service.validar(
				salvarPlanilha(CABECALHO_COMPLETO, semCep, horaHifen, estabSemCodigo));

		assertThat(erros).anySatisfy(e -> {
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.CEP_AUSENTE);
			assertThat(e.valor()).isEqualTo("MARIA SILVA");
		});
		assertThat(erros).anySatisfy(e -> {
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.HORA_INVALIDA);
			assertThat(e.valor()).isEqualTo("\"-\"");
		});
		assertThat(erros).anySatisfy(e -> {
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO);
			assertThat(e.valor()).isEqualTo("\"HOSPITAL CENTRAL\"");
		});
	}

	@Test
	void validarComHerancaDeCnsIndicaAHerancaNoValor() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("RODRIGO S. GRILO", "98765432100", "12345678900"),
				linhaValida());

		assertThat(service.validar(caminho)).singleElement()
				.satisfies(e -> assertThat(e.valor())
						.isEqualTo("\"RODRIGO S. GRILO\" — herdará o CNS 700207960618529 do mesmo CPF"));
	}

	@Test
	void gerarLogTxtAgrupaPorTipoComExplicacaoUmaVezEValoresComLinhas() {
		List<ErroValidacao> erros = List.of(
				new ErroValidacao(2, ErroValidacao.Severidade.AVISO, ErroValidacao.HORA_INVALIDA, "detalhe 2", "\"-\""),
				new ErroValidacao(5, ErroValidacao.Severidade.AVISO, ErroValidacao.HORA_INVALIDA, "detalhe 5", "\"-\""),
				new ErroValidacao(7, ErroValidacao.Severidade.AVISO, ErroValidacao.HORA_INVALIDA, "detalhe 7", "\"--:--\""));

		String log = service.gerarLogTxt(erros, "planilha_teste.xlsx");

		assertThat(log)
				.contains("[HORA_INVALIDA] Horário de atendimento não reconhecido (3)")
				.contains("\"-\" — 2 linhas: 2, 5")
				.contains("\"--:--\" — 1 linha: 7");
		assertThat(log.split("não utiliza o horário", -1)).hasSize(2); // explicação uma vez só
	}

	@Test
	void validarComGrafiaNaoCadastradaMasMesmoCpfComGrafiaCadastradaNaPlanilhaAvisaHeranca() throws IOException {
		// Grafia abreviada vem ANTES da cadastrada — a herança não depende da ordem
		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("RODRIGO S. GRILO", "98765432100", "12345678900"),
				linhaValida());

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO);
			assertThat(erro.detalhe())
					.contains("RODRIGO S. GRILO")
					.contains("herdado do mesmo CPF")
					.contains("700207960618529")
					.contains("apelido");
		});
	}

	@Test
	void validarComGrafiaNaoCadastradaMasCpfComCnsConhecidoNoBancoAvisaHeranca() throws IOException {
		ValidacaoPlanilhaService comBanco =
				new ValidacaoPlanilhaService(cpf -> "11122233344".equals(cpf) ? "700000000000001" : null);

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("DR CONHECIDO SO PELO CPF", "111.222.333-44", "12345678900"));

		List<ErroValidacao> erros = comBanco.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO);
			assertThat(erro.detalhe()).contains("herdado do mesmo CPF").contains("700000000000001");
		});
	}

	@Test
	void validarComMedicoCadastradoPorApelidoSemAcentoNaoGeraAviso() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("Rodrigo Silva Grilo", "98765432100", "12345678900"));

		List<ErroValidacao> erros = service.validar(caminho);

		assertThat(erros).isEmpty();
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

		assertThat(log).contains("Erros: 1");
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
