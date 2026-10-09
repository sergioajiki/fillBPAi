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
	private static final int COL_CBO = 7;
	private static final int COL_MUNICIPIO = 8;
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
				"CAMPO GRANDE", "12345678909", "MARIA SILVA", "700207960618529",
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
				"CAMPO GRANDE", "12345678909", "MARIA SILVA", "700207960618529",
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
	void validarComCpfPreenchidoEPacienteSemCpfSimGeraErroDeConflito() throws IOException {
		String[] linha = linhaValida();
		linha[COL_PACIENTE_SEM_CPF] = "Sim";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_CONFLITO_SEM_CPF);
					assertThat(erro.valor()).isEqualTo("Paciente: MARIA SILVA");
				});
	}

	// ===== Município =====

	@Test
	void validarComMunicipioForaDaTabelaEIbgeNaoEncontradoPeloCepGeraErro() throws IOException {
		// Busca do IBGE pelo CEP simulada (banco + APIs) — sem rede no teste
		ValidacaoPlanilhaService semIbgePeloCep = new ValidacaoPlanilhaService(cpf -> null, (cep, municipio) -> null);
		String[] linha = linhaValida();
		linha[COL_MUNICIPIO] = "Cuiabá";

		assertThat(semIbgePeloCep.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.MUNICIPIO_NAO_ENCONTRADO);
					assertThat(erro.valor()).isEqualTo("\"Cuiabá\" — CEP 79003020");
					assertThat(erro.detalhe()).contains("Município \"Cuiabá\" não encontrado").contains("CEP 79003020 também não foi encontrado");
				});
	}

	@Test
	void validarComMunicipioForaDaTabelaMasIbgeEncontradoPeloCepAvisaQualMunicipioFoiEncontrado() throws IOException {
		// Não bloqueia, mas mostra o município que o CEP indica, para o usuário
		// conferir se é o mesmo que quis informar na planilha
		java.util.List<String> consultas = new java.util.ArrayList<>();
		ValidacaoPlanilhaService ibgePeloCep = new ValidacaoPlanilhaService(cpf -> null, (cep, municipio) -> {
			consultas.add(cep + "|" + municipio);
			return "5103403";
		});
		String[] linha = linhaValida();
		linha[COL_MUNICIPIO] = "Cuiaba centro";
		linha[COL_CEP] = "79003-020";

		assertThat(ibgePeloCep.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.MUNICIPIO_PELO_CEP);
					assertThat(erro.valor()).isEqualTo("\"Cuiaba centro\" — CEP 79003020 → Cuiabá/MT");
					assertThat(erro.detalhe()).contains("Cuiabá/MT").contains("confira");
				});
		assertThat(consultas).containsExactly("79003020|Cuiaba centro"); // CEP já normalizado
	}

	@Test
	void validarComMunicipioDaTabelaNaoConsultaOCep() throws IOException {
		ValidacaoPlanilhaService falhaSeConsultar = new ValidacaoPlanilhaService(cpf -> null, (cep, municipio) -> {
			throw new AssertionError("não deveria consultar o CEP para município da tabela de MS");
		});

		assertThat(falhaSeConsultar.validar(salvarPlanilha(CABECALHO_COMPLETO, linhaValida()))).isEmpty();
	}

	@Test
	void validarSemMunicipioGeraErroDeMunicipioAusenteComOPaciente() throws IOException {
		// Decisão 08/10/2026: sem o nome não há a primeira busca (tabela de MS) — ERRO
		String[] linha = linhaValida();
		linha[COL_MUNICIPIO] = null;

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.MUNICIPIO_AUSENTE);
					assertThat(erro.valor()).isEqualTo("Paciente: MARIA SILVA — município ausente");
					assertThat(erro.detalhe()).contains("vazio");
				});
		assertThat(AgrupamentoValidacao.info(ErroValidacao.MUNICIPIO_NAO_ENCONTRADO).titulo())
				.isEqualTo("Município e CEP não encontrados");
		assertThat(AgrupamentoValidacao.info(ErroValidacao.MUNICIPIO_AUSENTE).titulo())
				.isEqualTo("Município não informado na planilha");
	}

	@Test
	void validarComMunicipioComSiglaDaUfNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_MUNICIPIO] = "Campo Grande - MS";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).isEmpty();
	}

	@Test
	void validarSemCpfComPacienteSemCpfSimNaoGeraErro() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = null;
		linha[COL_PACIENTE_SEM_CPF] = "Sim";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).isEmpty();
	}

	@Test
	void validarSemCpfComPacienteSemCpfNaoContinuaErro() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = null;
		linha[COL_PACIENTE_SEM_CPF] = "Não";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_AUSENTE);
					assertThat(erro.detalhe()).contains("Paciente sem CPF");
				});
	}

	@Test
	void validarComCpfFalsoDeDigitosRepetidosGeraErroCpfFalso() throws IOException {
		String[] zeros = linhaValida();
		zeros[COL_CPF_PACIENTE] = "00000000000";
		String[] uns = linhaValida();
		uns[COL_CPF_PACIENTE] = "111.111.111-11";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, zeros, uns));

		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_FALSO);
		});
	}

	@Test
	void validarComCpfDoPacienteComDigitoVerificadorErradoGeraErroCpfDvInvalido() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = "123.456.789-00";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_DV_INVALIDO);
					assertThat(erro.valor()).isEqualTo("Paciente: MARIA SILVA — \"123.456.789-00\"");
				});
	}

	@Test
	void validarComCpfDoMedicoComDigitoVerificadorErradoGeraErroAgrupadoPeloMedico() throws IOException {
		String[] digitoErrado = linhaValida();
		digitoErrado[COL_CPF_MEDICO] = "98765432101";
		String[] repetido = linhaValida();
		repetido[COL_CPF_MEDICO] = "11111111111";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, digitoErrado, repetido));

		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_MEDICO_DV_INVALIDO);
		});
		assertThat(erros).extracting(ErroValidacao::valor).containsExactly(
				"Médico: RODRIGO SILVA GRILO — \"98765432101\"",
				"Médico: RODRIGO SILVA GRILO — \"11111111111\"");
	}

	@Test
	void validarComCpfDeDezDigitosOrientaAFormatarComoTexto() throws IOException {
		// Decisão 08/10/2026: não completar zeros — não dá para garantir que o
		// dígito que falta é um zero à esquerda; a mensagem orienta a corrigir
		String[] linha = linhaValida();
		linha[COL_CPF_PACIENTE] = "1234567890";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement()
				.satisfies(erro -> {
					assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CPF_INVALIDO);
					assertThat(erro.detalhe()).contains("texto");
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
	void validarComNomeComHifenESemCodigoGeraAvisoSemCodigoEmVezDeCodigoFalso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESTABELECIMENTO] = "HOSPITAL SAO JOSE - UNIDADE 2";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement().satisfies(erro -> {
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTABELECIMENTO_SEM_CODIGO);
			assertThat(erro.valor()).isEqualTo("\"HOSPITAL SAO JOSE - UNIDADE 2\"");
		});
	}

	@Test
	void validarComEstabelecimentoSoComCodigoGeraAvisoSemNome() throws IOException {
		String[] soCodigo = linhaValida();
		soCodigo[COL_ESTABELECIMENTO] = "1234567";
		String[] codigoHifen = linhaValida();
		codigoHifen[COL_ESTABELECIMENTO] = "12345 -";
		codigoHifen[COL_DATA_AGENDAMENTO] = "26/12/2024"; // linhas distintas (não repetidas)

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, soCodigo, codigoHifen));

		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTABELECIMENTO_SEM_NOME);
		});
		assertThat(erros).extracting(ErroValidacao::valor).containsExactly("código 1234567", "código 12345");
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
	void validarComEstabelecimentoNaoInformadoGeraAvisoAusenteAgrupavel() throws IOException {
		// Vazio, só o separador ou "0" (marcadores comuns de "sem informação")
		String[] vazio = linhaValida();
		vazio[COL_ESTABELECIMENTO] = null;
		String[] espacos = linhaValida();
		espacos[COL_ESTABELECIMENTO] = "   ";
		espacos[COL_DATA_AGENDAMENTO] = "26/12/2024"; // linhas distintas (não repetidas)
		String[] hifen = linhaValida();
		hifen[COL_ESTABELECIMENTO] = " - ";
		hifen[COL_DATA_AGENDAMENTO] = "27/12/2024";
		String[] travessao = linhaValida();
		travessao[COL_ESTABELECIMENTO] = "—";
		travessao[COL_DATA_AGENDAMENTO] = "28/12/2024";
		String[] zero = linhaValida();
		zero[COL_ESTABELECIMENTO] = "0";
		zero[COL_DATA_AGENDAMENTO] = "29/12/2024";

		List<ErroValidacao> erros = service.validar(
				salvarPlanilha(CABECALHO_COMPLETO, vazio, espacos, hifen, travessao, zero));

		assertThat(erros).hasSize(5).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESTABELECIMENTO_AUSENTE);
			assertThat(erro.valor()).isEqualTo("não informado");
		});
	}

	@Test
	void validarComHorarioNoFormatoComHNaoGeraAviso() throws IOException {
		String[] linha = linhaValida();
		linha[COL_HORA_ATENDIMENTO] = "8h30";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).isEmpty();
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

	// ===== CBO do médico =====

	@Test
	void validarSemCboGeraErroCboAusenteComOMedico() throws IOException {
		String[] linha = linhaValida();
		linha[COL_CBO] = null;

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CBO_AUSENTE);
			assertThat(erro.valor()).isEqualTo("Médico: RODRIGO SILVA GRILO");
		});
	}

	@Test
	void validarComCboSemSeisDigitosGeraErroCboInvalido() throws IOException {
		String[] cincoDigitos = linhaValida();
		cincoDigitos[COL_CBO] = "22512";
		String[] texto = linhaValida();
		texto[COL_CBO] = "MEDICO CARDIOLOGISTA";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, cincoDigitos, texto));

		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CBO_INVALIDO);
		});
		assertThat(erros).extracting(ErroValidacao::valor).containsExactly(
				"Médico: RODRIGO SILVA GRILO — \"22512\"",
				"Médico: RODRIGO SILVA GRILO — \"MEDICO CARDIOLOGISTA\"");
	}

	@Test
	void validarComCboComMascaraNaoGeraErro() throws IOException {
		String[] hifen = linhaValida();
		hifen[COL_CBO] = "2251-25";
		String[] ponto = linhaValida();
		ponto[COL_CBO] = "225.125";
		ponto[COL_DATA_AGENDAMENTO] = "26/12/2024"; // linhas distintas (não repetidas)

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, hifen, ponto))).isEmpty();
	}

	// ===== Especialidade =====

	@Test
	void validarSemEspecialidadeGeraErroEspecialidadeAusenteComOMedico() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESPECIALIDADE] = null;

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement().satisfies(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESPECIALIDADE_AUSENTE);
			assertThat(erro.valor()).isEqualTo("Médico: RODRIGO SILVA GRILO");
		});
	}

	@Test
	void validarComEspecialidadeSoComEspacosGeraErroEspecialidadeAusente() throws IOException {
		String[] linha = linhaValida();
		linha[COL_ESPECIALIDADE] = "  ";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha)))
				.singleElement()
				.satisfies(erro -> assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.ESPECIALIDADE_AUSENTE));
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
			assertThat(erro.valor()).isEqualTo("Paciente: MARIA SILVA");
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
		psicologo[COL_DATA_AGENDAMENTO] = "26/12/2024"; // linhas distintas (não repetidas)

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, nutricionista, psicologo));

		// Não bloqueia (procedimento fixo 0301010315), mas a coluna vazia é indicada
		assertThat(erros).hasSize(2).allSatisfy(erro -> {
			assertThat(erro.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.TIPO_SERVICO_VAZIO_PROCEDIMENTO_FIXO);
			assertThat(erro.detalhe()).contains("0301010315");
		});
		assertThat(erros).extracting(ErroValidacao::valor).containsExactly("NUTRICIONISTA", "PSICÓLOGO");
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
			assertThat(erro.valor()).isEqualTo("Paciente: MARIA SILVA");
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
		hifen[COL_DATA_AGENDAMENTO] = "26-12-2024"; // dia diferente: linhas distintas

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
				linhaComMedico("MEDICO NAO CADASTRADO XYZ", "11122233396", "12345678909"),
				linhaValida(),
				linhaComMedico("Médico Não Cadastrado XYZ", "11122233396", "98765432100"));

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
			assertThat(e.valor()).isEqualTo("Paciente: MARIA SILVA");
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
				linhaComMedico("RODRIGO S. GRILO", "98765432100", "11144477735"), // outro paciente: linhas distintas
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
				linhaComMedico("RODRIGO S. GRILO", "98765432100", "11144477735"), // outro paciente: linhas distintas
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
				new ValidacaoPlanilhaService(cpf -> "11122233396".equals(cpf) ? "700000000000001" : null);

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("DR CONHECIDO SO PELO CPF", "111.222.333-96", "12345678909"));

		List<ErroValidacao> erros = comBanco.validar(caminho);

		assertThat(erros).singleElement().satisfies(erro -> {
			assertThat(erro.tipoErro()).isEqualTo(ErroValidacao.CNS_PROFISSIONAL_NAO_CADASTRADO);
			assertThat(erro.detalhe()).contains("herdado do mesmo CPF").contains("700000000000001");
		});
	}

	@Test
	void validarComMedicoCadastradoPorApelidoSemAcentoNaoGeraAviso() throws IOException {
		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaComMedico("Rodrigo Silva Grilo", "98765432100", "12345678909"));

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
		assertThat(log).contains("--- ERROS (necessário corrigir na planilha para gerar a remessa BPA-I) ---");
		assertThat(log).contains("--- AVISOS (não impedem a importação) ---");
		assertThat(log).contains(ErroValidacao.CEP_AUSENTE);
		assertThat(log).contains(ErroValidacao.CNS_INVALIDO);
	}

	@Test
	void gerarLogTxtComListaVaziaIndicaPlanilhaApta() {
		String log = service.gerarLogTxt(List.of(), "planilha_teste.xlsx");

		assertThat(log).contains("apta para importação");
	}

	// ===== Todas as linhas importadas: nome, nascimento, duplicidade, linhas vazias =====

	@Test
	void validarSemNomeDoPacienteGeraErro() throws IOException {
		String[] linha = linhaValida();
		linha[10] = null; // PACIENTE

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement().satisfies(e -> {
			assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.PACIENTE_AUSENTE);
			assertThat(e.valor()).isEqualTo("Paciente: (sem nome) — CPF 12345678909");
		});
	}

	@Test
	void validarComDataDeNascimentoInvalidaGeraErro() throws IOException {
		String[] linha = linhaValida();
		linha[14] = "31/02/1990x"; // DATA DE NASCIMENTO

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linha))).singleElement().satisfies(e -> {
			assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.DATA_NASCIMENTO_INVALIDA);
			assertThat(e.valor()).isEqualTo("Paciente: MARIA SILVA — \"31/02/1990x\"");
		});
	}

	@Test
	void validarComLinhaRepetidaNoMesmoHorarioGeraErro() throws IOException {
		List<ErroValidacao> erros = service.validar(
				salvarPlanilha(CABECALHO_COMPLETO, linhaValida(), linhaValida()));

		assertThat(erros).singleElement().satisfies(e -> {
			assertThat(e.linha()).isEqualTo(3);
			assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.ERRO);
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.LINHA_DUPLICADA);
			assertThat(e.detalhe()).contains("da linha 2");
			assertThat(e.valor()).contains("MARIA SILVA").contains("25/12/2024").contains("linhas 2 e 3");
		});
	}

	@Test
	void validarMesmoAtendimentoComHorarioDiferenteGeraSoAvisoDeSuspeita() throws IOException {
		String[] tarde = linhaValida();
		tarde[COL_HORA_ATENDIMENTO] = "14:00";

		List<ErroValidacao> erros = service.validar(salvarPlanilha(CABECALHO_COMPLETO, linhaValida(), tarde));

		assertThat(erros).singleElement().satisfies(e -> {
			assertThat(e.severidade()).isEqualTo(ErroValidacao.Severidade.AVISO);
			assertThat(e.tipoErro()).isEqualTo(ErroValidacao.SUSPEITA_DUPLICIDADE);
			assertThat(e.valor()).endsWith("08:30 × 14:00");
		});
	}

	@Test
	void validarIgnoraLinhasVazias() throws IOException {
		String[] vazia = new String[CABECALHO_COMPLETO.length];
		String[] soEspacos = new String[CABECALHO_COMPLETO.length];
		soEspacos[COL_CEP] = "   ";

		assertThat(service.validar(salvarPlanilha(CABECALHO_COMPLETO, linhaValida(), vazia, soEspacos))).isEmpty();
	}
}
