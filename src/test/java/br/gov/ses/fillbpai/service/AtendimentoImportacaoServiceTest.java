package br.gov.ses.fillbpai.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.gov.ses.fillbpai.model.AtendimentoBPAi;
import br.gov.ses.fillbpai.model.Estabelecimento;
import br.gov.ses.fillbpai.model.Medico;
import br.gov.ses.fillbpai.model.Paciente;
import br.gov.ses.fillbpai.repository.AtendimentoBPAiRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teste fim a fim de {@link AtendimentoImportacaoService} contra a
 * persistence unit de teste {@code bpaPU-test} (H2 em memória). Usa
 * "CAMPO GRANDE" como município (resolve por nome via CSV, sem rede) e
 * "RODRIGO SILVA GRILO" como médico real cadastrado em
 * {@code dados/medicos_cns.csv} (CNS 700207960618529) para o teste de
 * resolução de CNS — leitura real, sem mutar o arquivo.
 */
class AtendimentoImportacaoServiceTest {

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

	private EntityManagerFactory emf;
	private EntityManager entityManager;
	private AtendimentoImportacaoService service;
	private AtendimentoBPAiRepository atendimentoRepository;

	@TempDir
	Path tempDir;

	@BeforeEach
	void abrirBanco() {
		emf = Persistence.createEntityManagerFactory("bpaPU-test");
		entityManager = emf.createEntityManager();
		service = new AtendimentoImportacaoService(entityManager);
		atendimentoRepository = new AtendimentoBPAiRepository(entityManager);
	}

	@AfterEach
	void fecharBanco() {
		entityManager.close();
		emf.close();
	}

	private String[] linhaValida(String cpfPaciente, String nomePaciente, String cpfMedico, String nomeMedico,
			String especialidade) {
		return linhaValida(cpfPaciente, nomePaciente, cpfMedico, nomeMedico, especialidade, "BRANCA", null);
	}

	private String[] linhaValida(String cpfPaciente, String nomePaciente, String cpfMedico, String nomeMedico,
			String especialidade, String raca, String etnia) {
		return new String[] {
				"TELECONSULTA", "25/12/2024", "08:30", "12345 - HOSPITAL CENTRAL",
				especialidade, nomeMedico, cpfMedico, "225125",
				"CAMPO GRANDE", cpfPaciente, nomePaciente, "700207960618529",
				raca, etnia, "01/01/1990", "I10", "67999999999",
				"URBANA", "081", "RUA DAS FLORES", "79003020", "100", "CENTRO",
				"APTO 1", "F"
		};
	}

	/** Linha de dados no formato legado — especialidade e médico combinados numa célula só. */
	private String[] linhaLegado(String cpfPaciente, String nomePaciente, String cpfMedico,
			String especialidadeMedicoCombinado) {
		return new String[] {
				"TELECONSULTA", "25/12/2024", "08:30", "12345 - HOSPITAL CENTRAL",
				especialidadeMedicoCombinado, cpfMedico, "225125",
				"CAMPO GRANDE", cpfPaciente, nomePaciente, "700207960618529",
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
	void importarCriaPacienteMedicoEstabelecimentoEAtendimento() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getTotalErro()).isEqualTo(0);

		List<AtendimentoBPAi> atendimentos = atendimentoRepository.buscarTodos();
		assertThat(atendimentos).hasSize(1);

		AtendimentoBPAi atendimento = atendimentos.get(0);
		assertThat(atendimento.getPaciente().getNome()).isEqualTo("MARIA SILVA");
		assertThat(atendimento.getMedico().getNome()).isEqualTo("JOAO DA SILVA");
		assertThat(atendimento.getEstabelecimento()).isNotNull();
		assertThat(atendimento.getEstabelecimento().getNome()).isEqualTo("HOSPITAL CENTRAL");
		assertThat(atendimento.getDataAgendamento()).isEqualTo(LocalDate.of(2024, 12, 25));
	}

	@Test
	void importarComHoraNaoReconhecidaImportaALinhaSemHoraEComAviso() throws IOException {

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[2] = "-"; // HORA ATENDIMENTO

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getTotalErro()).isEqualTo(0);
		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso).startsWith("Linha 2").contains("Horário de atendimento não reconhecido"));

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getHoraAtendimento()).isNull();
	}

	// ===== Estabelecimento: código sempre numérico =====

	private void cadastrarEstabelecimento(String codigo, String nome) {
		Estabelecimento existente = new Estabelecimento();
		existente.setCodigo(codigo);
		existente.setNome(nome);
		entityManager.getTransaction().begin();
		entityManager.persist(existente);
		entityManager.getTransaction().commit();
		entityManager.clear();
	}

	private long contarEstabelecimentos() {
		return (long) entityManager.createQuery("SELECT COUNT(e) FROM Estabelecimento e").getSingleResult();
	}

	@Test
	void importarComNomeComHifenESemCodigoNaoCancelaAImportacao() throws IOException {

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "HOSPITAL SAO JOSE - UNIDADE 2";

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getAvisos()).anySatisfy(aviso -> assertThat(aviso).contains("nao traz codigo reconhecido"));
		assertThat(atendimentoRepository.buscarTodos().get(0).getEstabelecimento()).isNull();
		assertThat(contarEstabelecimentos()).isZero();
	}

	@Test
	void importarComEstabelecimentoNaoInformadoAvisaNoLogENaoCriaNada() throws IOException {

		String[] vazio = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		vazio[3] = null;
		String[] hifen = linhaValida("11122233396", "JOSE SOUZA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		hifen[3] = "-";

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO, vazio, hifen));

		assertThat(resultado.getTotalSucesso()).isEqualTo(2);
		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso).startsWith("Linha 2").contains("Estabelecimento não informado"))
				.anySatisfy(aviso -> assertThat(aviso).startsWith("Linha 3").contains("Estabelecimento não informado"))
				.noneMatch(aviso -> aviso.contains("nao traz codigo reconhecido"));
		assertThat(atendimentoRepository.buscarTodos())
				.allSatisfy(a -> assertThat(a.getEstabelecimento()).isNull());
		assertThat(contarEstabelecimentos()).isZero();
	}

	@Test
	void importarComTextoCurtoAntesDoHifenNaoCriaEstabelecimentoFalso() throws IOException {

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "UBS - CENTRO";

		service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(contarEstabelecimentos()).isZero();
	}

	@Test
	void importarComCelulaSoComCodigoCadastradoVinculaEMantemONome() throws IOException {

		cadastrarEstabelecimento("1234567", "HOSPITAL CENTRAL");

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "1234567";

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getEstabelecimento()).isNotNull();
		assertThat(atendimento.getEstabelecimento().getCodigo()).isEqualTo("1234567");
		assertThat(atendimento.getEstabelecimento().getNome()).isEqualTo("HOSPITAL CENTRAL");
		assertThat(resultado.getAvisos()).noneMatch(aviso -> aviso.contains("Estabelecimento"));
	}

	@Test
	void importarComCodigoSemNomeNaoApagaONomeCadastrado() throws IOException {

		cadastrarEstabelecimento("12345", "HOSPITAL CENTRAL");

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "12345 -";

		service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos().get(0).getEstabelecimento().getNome())
				.isEqualTo("HOSPITAL CENTRAL");
	}

	@Test
	void importarComCelulaSoComCodigoNaoCadastradoFicaSemEstabelecimentoComAviso() throws IOException {

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "7654321";

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(atendimentoRepository.buscarTodos().get(0).getEstabelecimento()).isNull();
		assertThat(resultado.getAvisos()).anySatisfy(aviso -> assertThat(aviso)
				.startsWith("Linha 2")
				.contains("7654321")
				.contains("não está cadastrado"));
		assertThat(contarEstabelecimentos()).isZero();
	}

	// ===== Paciente sem CPF =====

	@Test
	void importarPacienteSemCpfComColunaSimGravaComChaveInternaEReimportarNaoDuplica() throws IOException {

		String[] cabecalho = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalho[CABECALHO_COMPLETO.length] = "Paciente sem CPF";
		String[] linha = java.util.Arrays.copyOf(
				linhaValida(null, "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linha[CABECALHO_COMPLETO.length] = "Sim";
		String caminho = salvarPlanilha(cabecalho, linha);

		ImportacaoResultado primeira = service.importar(caminho);
		service.importar(caminho);

		assertThat(primeira.getTotalSucesso()).isEqualTo(1);
		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos()).singleElement().satisfies(a -> {
			assertThat(br.gov.ses.fillbpai.util.CpfUtils.isChaveSemCpf(a.getPaciente().getCpf())).isTrue();
			assertThat(a.getPacienteSemCpf()).isEqualTo("S");
		});
	}

	// ===== Município / IBGE =====

	@Test
	void importarComMunicipioSemIbgeBloqueiaAPlanilhaInteiraSemGravarNada() throws IOException {
		// Decisão 08/10/2026: linhas não podem ser rejeitadas por falta do IBGE —
		// a importação da planilha inteira é bloqueada, mesmo com linhas válidas
		String[] valida = linhaValida("11144477735", "PACIENTE VALIDO", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		String[] semIbge = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		semIbge[8] = "Cuiabá";
		semIbge[20] = "79999990";
		String[] semMunicipio = linhaValida("22255588846", "JOSE SOUZA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		semMunicipio[8] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, valida, semIbge, semMunicipio);

		try {
			// APIs de CEP simuladas (sem rede): todas respondem "CEP não existe"
			br.gov.ses.fillbpai.util.IbgeUtils.usarBuscadorHttpParaTeste(
					url -> new br.gov.ses.fillbpai.util.IbgeUtils.RespostaHttp(404, "{}"));

			assertThatThrownBy(() -> service.importar(caminho))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("Importação bloqueada")
					.hasMessageContaining("nenhuma linha foi importada")
					.hasMessageContaining("Linha 3: Município \"Cuiabá\" não encontrado e o CEP 79999990 também não foi encontrado")
					.hasMessageContaining("Linha 4: Município do paciente não informado");
		} finally {
			br.gov.ses.fillbpai.util.IbgeUtils.usarBuscadorHttpParaTeste(null);
		}

		entityManager.clear();
		assertThat((long) entityManager.createQuery("SELECT COUNT(p) FROM Paciente p").getSingleResult()).isZero();
		assertThat(atendimentoRepository.buscarTodos()).isEmpty();
	}

	@Test
	void importarComMunicipioForaDaTabelaEncontradoPeloCepGravaEAvisaOMunicipioEncontrado() throws IOException {
		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[8] = "Cuiaba centro";
		linha[20] = "79999991";

		ImportacaoResultado resultado;
		try {
			br.gov.ses.fillbpai.util.IbgeUtils.usarBuscadorHttpParaTeste(
					url -> new br.gov.ses.fillbpai.util.IbgeUtils.RespostaHttp(200, "{\"ibge\": \"5103403\"}"));
			resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO, linha));
		} finally {
			br.gov.ses.fillbpai.util.IbgeUtils.usarBuscadorHttpParaTeste(null);
		}

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getAvisos()).anySatisfy(aviso -> assertThat(aviso)
				.startsWith("Linha 2").contains("\"Cuiaba centro\"").contains("Cuiabá/MT"));
		assertThat(atendimentoRepository.buscarTodos().get(0).getPaciente().getEndereco().getCodigoIbge())
				.isEqualTo("5103403");
	}

	// ===== Especialidade padronizada =====

	@Test
	void importarGravaEspecialidadeEmMaiusculasParaGrafiasDiferentesViraremAMesma() throws IOException {

		String[] minusculas = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "Cardiologia");
		String[] maiusculas = linhaValida("11122233396", "JOSE SOUZA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");

		service.importar(salvarPlanilha(CABECALHO_COMPLETO, minusculas, maiusculas));

		assertThat(atendimentoRepository.buscarTodos())
				.hasSize(2)
				.allSatisfy(a -> assertThat(a.getEspecialidadeMedico()).isEqualTo("CARDIOLOGIA"));
	}

	// ===== Tipo de serviço vazio =====

	@Test
	void importarComTipoDeServicoVazioRejeitaALinhaEReimportarNaoDuplica() throws IOException {

		String[] semTipo = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		semTipo[0] = null;
		String caminho = salvarPlanilha(CABECALHO_COMPLETO, semTipo);

		ImportacaoResultado primeira = service.importar(caminho);
		service.importar(caminho);

		assertThat(primeira.getTotalSucesso()).isEqualTo(0);
		assertThat(primeira.getErros()).singleElement()
				.satisfies(erro -> assertThat(erro).contains("Tipo de serviço não informado"));

		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos()).isEmpty();
	}

	// ===== Médico sem CPF / erro de gravação =====

	@Test
	void importarComMedicoSemCpfRejeitaSoAquelaLinhaEGravaAsDemais() throws IOException {

		String[] valida = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		String[] semCpfMedico = linhaValida("11122233396", "JOSE SOUZA", null, "DR SEM CPF", "CARDIOLOGIA");

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, semCpfMedico, valida);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getTotalErro()).isEqualTo(1);
		assertThat(resultado.getErros()).singleElement()
				.satisfies(erro -> assertThat(erro).startsWith("Linha 2").contains("CPF do médico não informado"));

		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos()).singleElement()
				.satisfies(a -> assertThat(a.getPaciente().getNome()).isEqualTo("MARIA SILVA"));
	}

	@Test
	void importarComErroDeGravacaoNoBancoCancelaAImportacaoEmVezDeReportarSucesso() throws IOException {

		String[] valida = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		String[] cidLongo = linhaValida("11122233396", "JOSE SOUZA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		cidLongo[15] = "CID COM TEXTO LONGO DEMAIS PARA A COLUNA"; // cid_consulta tem 20 posições

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, valida, cidLongo);

		assertThatThrownBy(() -> service.importar(caminho))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Linha 3")
				.hasMessageContaining("nenhuma linha foi importada");

		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos()).isEmpty();
	}

	// ===== CNS do profissional: nome + reserva pelo CPF =====

	@Test
	void importarGuardaNoMedicoOCnsEncontradoPeloNome() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "RODRIGO SILVA GRILO", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getCnsProfissional()).isEqualTo("700207960618529");
		assertThat(atendimento.getMedico().getCns()).isEqualTo("700207960618529");
	}

	@Test
	void importarComGrafiaNaoCadastradaHerdaCnsDoMesmoCpfComAviso() throws IOException {

		service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "RODRIGO SILVA GRILO", "CARDIOLOGIA")));

		// Nova planilha: mesmo CPF, grafia abreviada que não está em medicos_cns.csv
		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("11122233396", "JOSE SOUZA", "98765432100", "RODRIGO S. GRILO", "CARDIOLOGIA")));

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso)
						.contains("RODRIGO S. GRILO")
						.contains("CNS herdado do mesmo CPF"))
				.noneMatch(aviso -> aviso.contains("CNS do profissional não encontrado"));

		assertThat(atendimentoRepository.buscarTodos())
				.allSatisfy(a -> assertThat(a.getCnsProfissional()).isEqualTo("700207960618529"));
	}

	@Test
	void importarHerdaCnsDoMesmoCpfMesmoQuandoAGrafiaCadastradaVemDepoisNaPlanilha() throws IOException {

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("11122233396", "JOSE SOUZA", "98765432100", "RODRIGO S. GRILO", "CARDIOLOGIA"),
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "RODRIGO SILVA GRILO", "CARDIOLOGIA")));

		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso).startsWith("Linha 2").contains("CNS herdado do mesmo CPF"))
				.noneMatch(aviso -> aviso.contains("CNS do profissional não encontrado"));

		entityManager.clear();
		assertThat(atendimentoRepository.buscarTodos())
				.hasSize(2)
				.allSatisfy(a -> assertThat(a.getCnsProfissional()).isEqualTo("700207960618529"));
	}

	@Test
	void importarComGrafiaNaoCadastradaECpfSemCnsConhecidoMantemAvisoDeNaoEncontrado() throws IOException {

		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "MEDICO NAO CADASTRADO XYZ", "CARDIOLOGIA")));

		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso).contains("CNS do profissional não encontrado"));
		assertThat(atendimentoRepository.buscarTodos().get(0).getCnsProfissional()).isNull();
	}

	@Test
	void importarComCnsDoNomeDiferenteDoCnsJaConhecidoParaOCpfUsaONomeEAvisa() throws IOException {

		service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "RODRIGO SILVA GRILO", "CARDIOLOGIA")));

		// Mesmo CPF, mas o nome casa com outro médico do cadastro (outro CNS)
		ImportacaoResultado resultado = service.importar(salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("11122233396", "JOSE SOUZA", "98765432100", "ELOILDA MARIA DE AGUIAR LUSTOSA", "CARDIOLOGIA")));

		assertThat(resultado.getAvisos())
				.anySatisfy(aviso -> assertThat(aviso)
						.contains("709007893069218")
						.contains("700207960618529"));

		entityManager.clear();
		AtendimentoBPAi novo = atendimentoRepository.buscarTodos().stream()
				.filter(a -> a.getPaciente().getNome().equals("JOSE SOUZA"))
				.findFirst().orElseThrow();
		assertThat(novo.getCnsProfissional()).isEqualTo("709007893069218");
		assertThat(novo.getMedico().getCns()).isEqualTo("709007893069218");
	}

	@Test
	void importarComEstabelecimentoSemCodigoReaproveitaEstabelecimentoJaCadastradoPeloNome() throws IOException {

		Estabelecimento existente = new Estabelecimento();
		existente.setCodigo("999");
		existente.setNome("HOSPITAL CENTRAL");
		entityManager.getTransaction().begin();
		entityManager.persist(existente);
		entityManager.getTransaction().commit();
		entityManager.clear();

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "HOSPITAL CENTRAL"; // sem separador "código - nome"

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getEstabelecimento()).isNotNull();
		assertThat(atendimento.getEstabelecimento().getCodigo()).isEqualTo("999");
	}

	@Test
	void importarComEstabelecimentoSemCodigoENomeDesconhecidoFicaSemEstabelecimento() throws IOException {

		String[] linha = linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		linha[3] = "UNIDADE NUNCA CADASTRADA"; // sem separador "código - nome"

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linha);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getEstabelecimento()).isNull();
	}

	@Test
	void importarPersisteTextoDaEtniaNoPaciente() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA",
						"INDIGENA", "BANIWA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getEtnia()).isEqualTo("BANIWA");
	}

	@Test
	void importarSemColunaSituacaoRuaDeixaCampoNuloNoPaciente() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getSituacaoRua()).isNull();
	}

	@Test
	void importarComColunaSituacaoRuaPersisteValorNormalizadoNoPaciente() throws IOException {

		String[] cabecalhoComSituacaoRua = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComSituacaoRua[CABECALHO_COMPLETO.length] = "Situação de Rua";

		String[] linha = java.util.Arrays.copyOf(
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linha[CABECALHO_COMPLETO.length] = "Sim";

		String caminho = salvarPlanilha(cabecalhoComSituacaoRua, linha);

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getSituacaoRua()).isEqualTo("S");
	}

	@Test
	void reimportarSemColunaSituacaoRuaPreservaValorJaConhecidoDoPaciente() throws IOException {

		String[] cabecalhoComSituacaoRua = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComSituacaoRua[CABECALHO_COMPLETO.length] = "Situação de Rua";

		String[] linhaComSituacaoRua = java.util.Arrays.copyOf(
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linhaComSituacaoRua[CABECALHO_COMPLETO.length] = "Sim";

		service.importar(salvarPlanilha(cabecalhoComSituacaoRua, linhaComSituacaoRua));

		// Reimporta a mesma pessoa (mesmo CPF) via uma planilha sem a coluna —
		// não deve apagar a informação já conhecida do paciente.
		String caminhoSemColuna = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));
		service.importar(caminhoSemColuna);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getSituacaoRua()).isEqualTo("S");
	}

	@Test
	void importarSemColunaPacienteSemCpfDeixaCampoNuloNoAtendimento() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPacienteSemCpf()).isNull();
	}

	@Test
	void importarComColunaPacienteSemCpfPersisteValorNormalizadoNoAtendimento() throws IOException {

		String[] cabecalhoComColuna = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComColuna[CABECALHO_COMPLETO.length] = "Paciente sem CPF";

		// "Sim" só é válido com o CPF vazio (CPF preenchido + Sim é erro de conflito)
		String[] linha = java.util.Arrays.copyOf(
				linhaValida(null, "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linha[CABECALHO_COMPLETO.length] = "Sim";

		String caminho = salvarPlanilha(cabecalhoComColuna, linha);

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPacienteSemCpf()).isEqualTo("S");
	}

	@Test
	void importarComCpfPreenchidoEPacienteSemCpfSimRejeitaALinha() throws IOException {

		String[] cabecalhoComColuna = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComColuna[CABECALHO_COMPLETO.length] = "Paciente sem CPF";
		String[] linha = java.util.Arrays.copyOf(
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linha[CABECALHO_COMPLETO.length] = "Sim";

		ImportacaoResultado resultado = service.importar(salvarPlanilha(cabecalhoComColuna, linha));

		assertThat(resultado.getTotalSucesso()).isZero();
		assertThat(resultado.getErros()).singleElement()
				.satisfies(erro -> assertThat(erro).contains("CPF preenchido com \"Paciente sem CPF\" = Sim"));
	}

	@Test
	void importarPlanilhaLegadoSeparaEspecialidadeEMedicoAoPersistir() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_LEGADO,
				linhaLegado("12345678909", "MARIA SILVA", "98765432100", "CARDIOLOGIA - JOAO DA SILVA"));

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(resultado.getTotalErro()).isEqualTo(0);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getEspecialidadeMedico()).isEqualTo("CARDIOLOGIA");
		assertThat(atendimento.getMedico().getNome()).isEqualTo("JOAO DA SILVA");
	}

	@Test
	void importarDuasVezesAMesmaLinhaNaoDuplicaAtendimento() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);
		ImportacaoResultado segundaImportacao = service.importar(caminho);

		assertThat(atendimentoRepository.buscarTodos()).hasSize(1);
		assertThat(segundaImportacao.getAvisos())
				.anySatisfy(a -> assertThat(a).contains("Atendimento já existente atualizado"));
	}

	@Test
	void importarHerdaFolhaJaAtribuidaParaMesmoMedicoEspecialidade() throws IOException {

		Medico medico = new Medico();
		medico.setCpf("98765432100");
		medico.setNome("JOAO DA SILVA");

		Paciente pacienteAnterior = new Paciente();
		pacienteAnterior.setCpf("11122233477");
		pacienteAnterior.setNome("PACIENTE ANTERIOR");

		AtendimentoBPAi atendimentoAnterior = new AtendimentoBPAi();
		atendimentoAnterior.setPaciente(pacienteAnterior);
		atendimentoAnterior.setMedico(medico);
		atendimentoAnterior.setEspecialidadeMedico("CARDIOLOGIA");
		atendimentoAnterior.setDataAgendamento(LocalDate.of(2024, 11, 1));
		atendimentoAnterior.setSigtap("03.01.01.030-7");
		atendimentoAnterior.setFolha("7");

		entityManager.getTransaction().begin();
		entityManager.persist(medico);
		entityManager.persist(pacienteAnterior);
		entityManager.persist(atendimentoAnterior);
		entityManager.getTransaction().commit();

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("22233344405", "PACIENTE NOVO", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi novoAtendimento = atendimentoRepository.buscarTodos().stream()
				.filter(a -> a.getPaciente().getCpf().equals("22233344405"))
				.findFirst()
				.orElseThrow();

		assertThat(novoAtendimento.getFolha()).isEqualTo("7");
	}

	@Test
	void importarResolveCnsDoProfissionalPeloNomeQuandoConhecido() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "11122233396", "RODRIGO SILVA GRILO", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getCnsProfissional()).isEqualTo("700207960618529");
	}

	@Test
	void importarComMedicoDesconhecidoGeraAvisoDeCnsNaoEncontrado() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678909", "MARIA SILVA", "99988877714", "MEDICO QUE NAO EXISTE XYZ", "CARDIOLOGIA"));

		ImportacaoResultado resultado = service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getCnsProfissional()).isNull();
		assertThat(resultado.getAvisos())
				.anySatisfy(a -> assertThat(a).contains("MEDICO QUE NAO EXISTE XYZ"));
	}

	@Test
	void importarComColunaObrigatoriaAusenteLancaExcecaoComMensagemDeEstruturaInvalida() throws IOException {

		// A IllegalStateException lançada internamente para estrutura inválida é
		// recapturada pelo catch (Exception e) do próprio importar() e reembalada
		// como RuntimeException — o tipo concreto não chega ao chamador, só a
		// mensagem (comportamento real confirmado rodando o teste).
		String[] cabecalhoIncompleto = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length - 1);
		String caminho = salvarPlanilha(cabecalhoIncompleto,
				linhaValida("12345678909", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		assertThatThrownBy(() -> service.importar(caminho))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Estrutura da planilha inválida");

		assertThat(atendimentoRepository.buscarTodos()).isEmpty();
	}

	@Test
	void importarComLinhaInvalidaRegistraErroSemInterromperAsDemais() throws IOException {

		String[] linhaSemPaciente = linhaValida("12345678909", null, "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		String[] linhaOk = linhaValida("22233344405", "PACIENTE VALIDO", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linhaSemPaciente, linhaOk);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalErro()).isEqualTo(1);
		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(atendimentoRepository.buscarTodos()).hasSize(1);
	}
}
