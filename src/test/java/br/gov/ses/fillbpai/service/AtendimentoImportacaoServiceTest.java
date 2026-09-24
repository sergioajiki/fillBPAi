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
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

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
	void importarPersisteTextoDaEtniaNoPaciente() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA",
						"INDIGENA", "BANIWA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getEtnia()).isEqualTo("BANIWA");
	}

	@Test
	void importarSemColunaSituacaoRuaDeixaCampoNuloNoPaciente() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getSituacaoRua()).isNull();
	}

	@Test
	void importarComColunaSituacaoRuaPersisteValorNormalizadoNoPaciente() throws IOException {

		String[] cabecalhoComSituacaoRua = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComSituacaoRua[CABECALHO_COMPLETO.length] = "Situação de Rua";

		String[] linha = java.util.Arrays.copyOf(
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
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
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linhaComSituacaoRua[CABECALHO_COMPLETO.length] = "Sim";

		service.importar(salvarPlanilha(cabecalhoComSituacaoRua, linhaComSituacaoRua));

		// Reimporta a mesma pessoa (mesmo CPF) via uma planilha sem a coluna —
		// não deve apagar a informação já conhecida do paciente.
		String caminhoSemColuna = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));
		service.importar(caminhoSemColuna);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPaciente().getSituacaoRua()).isEqualTo("S");
	}

	@Test
	void importarSemColunaPacienteSemCpfDeixaCampoNuloNoAtendimento() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPacienteSemCpf()).isNull();
	}

	@Test
	void importarComColunaPacienteSemCpfPersisteValorNormalizadoNoAtendimento() throws IOException {

		String[] cabecalhoComColuna = java.util.Arrays.copyOf(CABECALHO_COMPLETO, CABECALHO_COMPLETO.length + 1);
		cabecalhoComColuna[CABECALHO_COMPLETO.length] = "Paciente sem CPF";

		String[] linha = java.util.Arrays.copyOf(
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"),
				CABECALHO_COMPLETO.length + 1);
		linha[CABECALHO_COMPLETO.length] = "Sim";

		String caminho = salvarPlanilha(cabecalhoComColuna, linha);

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getPacienteSemCpf()).isEqualTo("S");
	}

	@Test
	void importarPlanilhaLegadoSeparaEspecialidadeEMedicoAoPersistir() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_LEGADO,
				linhaLegado("12345678900", "MARIA SILVA", "98765432100", "CARDIOLOGIA - JOAO DA SILVA"));

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
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

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
		pacienteAnterior.setCpf("11111111111");
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
				linhaValida("22222222222", "PACIENTE NOVO", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi novoAtendimento = atendimentoRepository.buscarTodos().stream()
				.filter(a -> a.getPaciente().getCpf().equals("22222222222"))
				.findFirst()
				.orElseThrow();

		assertThat(novoAtendimento.getFolha()).isEqualTo("7");
	}

	@Test
	void importarResolveCnsDoProfissionalPeloNomeQuandoConhecido() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "11122233344", "RODRIGO SILVA GRILO", "CARDIOLOGIA"));

		service.importar(caminho);

		AtendimentoBPAi atendimento = atendimentoRepository.buscarTodos().get(0);
		assertThat(atendimento.getCnsProfissional()).isEqualTo("700207960618529");
	}

	@Test
	void importarComMedicoDesconhecidoGeraAvisoDeCnsNaoEncontrado() throws IOException {

		String caminho = salvarPlanilha(CABECALHO_COMPLETO,
				linhaValida("12345678900", "MARIA SILVA", "99988877766", "MEDICO QUE NAO EXISTE XYZ", "CARDIOLOGIA"));

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
				linhaValida("12345678900", "MARIA SILVA", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA"));

		assertThatThrownBy(() -> service.importar(caminho))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Estrutura da planilha inválida");

		assertThat(atendimentoRepository.buscarTodos()).isEmpty();
	}

	@Test
	void importarComLinhaInvalidaRegistraErroSemInterromperAsDemais() throws IOException {

		String[] linhaSemPaciente = linhaValida("12345678900", null, "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");
		String[] linhaOk = linhaValida("22222222222", "PACIENTE VALIDO", "98765432100", "JOAO DA SILVA", "CARDIOLOGIA");

		String caminho = salvarPlanilha(CABECALHO_COMPLETO, linhaSemPaciente, linhaOk);

		ImportacaoResultado resultado = service.importar(caminho);

		assertThat(resultado.getTotalErro()).isEqualTo(1);
		assertThat(resultado.getTotalSucesso()).isEqualTo(1);
		assertThat(atendimentoRepository.buscarTodos()).hasSize(1);
	}
}
