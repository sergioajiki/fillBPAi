package br.gov.ses.fillbpai.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AtendimentoProcessor} só expõe {@code processar(dto)} publicamente
 * — os métodos internos (definirSigtap, validarCep, converterDatas, etc.) são
 * privados e testados indiretamente através dele, verificando o efeito no
 * DTO e/ou a exceção lançada.
 */
class AtendimentoProcessorTest {

	private final AtendimentoProcessor processor = new AtendimentoProcessor();

	/** DTO mínimo que satisfaz todos os campos obrigatórios, para focar cada teste num único aspecto. */
	private LinhaImportacaoDTO dtoValido() {
		LinhaImportacaoDTO dto = new LinhaImportacaoDTO();
		dto.setPaciente("MARIA SILVA");
		dto.setCpfPaciente("12345678909");
		dto.setCpfMedico("98765432100");
		dto.setEstabelecimento("12345 - HOSPITAL CENTRAL");
		dto.setEspecialidadeMedico("CARDIOLOGIA");
		dto.setCboMedico("225125");
		dto.setMunicipio("CAMPO GRANDE");
		dto.setDataAgendamentoString("25/12/2024");
		dto.setTipoServico("TELECONSULTA");
		dto.setCep("79003020");
		dto.setCnsPaciente("700207960618529");
		dto.setRacaPaciente("BRANCA");
		dto.setDataNascimentoString("01/01/1990");
		dto.setCidConsulta("I10");
		dto.setSexoPaciente("F");
		return dto;
	}

	// ===== SIGTAP =====

	@Test
	void processarDefineSigtapParaTeleconsulta() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico("TELECONSULTA");

		processor.processar(dto);

		assertThat(dto.getSigtap()).isEqualTo("03.01.01.030-7");
	}

	@Test
	void processarDefineSigtapParaTeleinterconsulta() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico("TELEINTERCONSULTA");

		processor.processar(dto);

		assertThat(dto.getSigtap()).isEqualTo("08.04.01.006-4");
	}

	@Test
	void processarComTipoServicoDesconhecidoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico("CONSULTA PRESENCIAL");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Tipo de serviço inválido para SIGTAP");
	}

	@Test
	void processarComTipoServicoVazioLancaExcecao() {
		// Tipo vazio deixava o SIGTAP nulo: procedimento 0000000000 no BPA-I e
		// duplicação na reimportação (chave compara o SIGTAP)
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico(null);
		dto.setEspecialidadeMedico("CARDIOLOGIA");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Tipo de serviço não informado");
	}

	@Test
	void processarComTipoServicoEmBrancoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico("   ");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Tipo de serviço não informado");
	}

	@Test
	void processarComTipoServicoVazioParaNutricionistaOuPsicologoMantemComportamentoAtual() {
		// Decisão 08/10/2026: não mexer por enquanto — essas especialidades usam
		// procedimento fixo (0301010315) na geração, qualquer que seja o tipo
		for (String especialidade : List.of("NUTRICIONISTA", "Psicólogo", "Médico Psicólogo")) {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setTipoServico(null);
			dto.setEspecialidadeMedico(especialidade);

			List<String> avisos = processor.processar(dto);

			assertThat(dto.getSigtap()).as(especialidade).isNull();
			assertThat(avisos).as(especialidade).anySatisfy(aviso -> assertThat(aviso)
					.contains("Tipo de serviço não informado")
					.contains("0301010315"));
		}
	}

	@Test
	void processarComTipoServicoPreenchidoParaNutricionistaNaoGeraAvisoDeTipoVazio() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico("NUTRICIONISTA");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneMatch(aviso -> aviso.contains("Tipo de serviço não informado"));
	}

	// ===== ESTABELECIMENTO =====

	@Test
	void processarSeparaCodigoENomeDoEstabelecimento() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEstabelecimento("12345 - HOSPITAL CENTRAL");

		processor.processar(dto);

		assertThat(dto.getCodEstabelecimento()).isEqualTo("12345");
		assertThat(dto.getEstabelecimento()).isEqualTo("HOSPITAL CENTRAL");
	}

	@Test
	void processarSemSeparadorMantemNomeDoEstabelecimentoENaoDefineCodigo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEstabelecimento("HOSPITAL CENTRAL");

		processor.processar(dto);

		assertThat(dto.getCodEstabelecimento()).isNull();
		assertThat(dto.getEstabelecimento()).isEqualTo("HOSPITAL CENTRAL");
	}

	@Test
	void processarSemSeparadorNoEstabelecimentoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEstabelecimento("HOSPITAL CENTRAL");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anyMatch(a -> a.contains("Estabelecimento")
				&& a.contains("HOSPITAL CENTRAL") && a.contains("codigo reconhecido"));
	}

	@Test
	void processarComEstabelecimentoNaoInformadoGeraAvisoEDeixaSemEstabelecimento() {
		for (String valor : new String[] { null, "-", "0" }) {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setEstabelecimento(valor);

			List<String> avisos = processor.processar(dto);

			assertThat(avisos).as(String.valueOf(valor))
					.containsExactly("Estabelecimento não informado — o atendimento ficou sem estabelecimento");
			assertThat(dto.getCodEstabelecimento()).isNull();
			assertThat(dto.getEstabelecimento()).isNull();
		}
	}

	@Test
	void processarComSeparadorNoEstabelecimentoNaoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEstabelecimento("12345 - HOSPITAL CENTRAL");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneMatch(a -> a.contains("Estabelecimento"));
	}

	// ===== ESPECIALIDADE/MÉDICO (formato legado) =====

	@Test
	void processarComEspecialidadeEMedicoCombinadosLegadoSeparaOsDoisCampos() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico("CARDIOLOGIA - JOAO DA SILVA");
		dto.setMedico("CARDIOLOGIA - JOAO DA SILVA");

		processor.processar(dto);

		assertThat(dto.getEspecialidadeMedico()).isEqualTo("CARDIOLOGIA");
		assertThat(dto.getMedico()).isEqualTo("JOAO DA SILVA");
	}

	@Test
	void processarComEspecialidadeEMedicoDiferentesNaoAlteraNadaMesmoComHifenNoNome() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico("CARDIOLOGIA");
		dto.setMedico("JOAO DA SILVA - FILHO");

		processor.processar(dto);

		assertThat(dto.getEspecialidadeMedico()).isEqualTo("CARDIOLOGIA");
		assertThat(dto.getMedico()).isEqualTo("JOAO DA SILVA - FILHO");
	}

	// ===== ESPECIALIDADE — prefixo "Médico"/"Médica" =====

	@Test
	void processarRemovePrefixoMedicoDaEspecialidade() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico("Médico Endocrinologista");
		dto.setMedico("JOAO DA SILVA");

		processor.processar(dto);

		assertThat(dto.getEspecialidadeMedico()).isEqualTo("ENDOCRINOLOGISTA");
	}

	@Test
	void processarSemPrefixoMedicoSoPadronizaAEspecialidadeEmMaiusculas() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico("Psiquiatra");
		dto.setMedico("JOAO DA SILVA");

		processor.processar(dto);

		assertThat(dto.getEspecialidadeMedico()).isEqualTo("PSIQUIATRA");
	}

	// ===== SEXO =====

	@Test
	void processarNormalizaSexoPorExtensoParaCodigoDeUmaLetra() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSexoPaciente("feminino");

		processor.processar(dto);

		assertThat(dto.getSexoPaciente()).isEqualTo("F");
	}

	@Test
	void processarNormalizaSexoAbreviadoIndependenteDeCaixa() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSexoPaciente("m");

		processor.processar(dto);

		assertThat(dto.getSexoPaciente()).isEqualTo("M");
	}

	@Test
	void processarComSexoNaoReconhecidoLancaExcecao() {
		// 16/10/2026: antes ficava como veio ("Fem" cancelava a importação no banco, "H" ia para o BPA-I)
		for (String valor : List.of("OUTRO", "H", "1", "X")) {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setSexoPaciente(valor);

			assertThatThrownBy(() -> processor.processar(dto)).as(valor)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("não reconhecido");
		}
	}

	@Test
	void processarSemSexoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSexoPaciente(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Sexo do paciente não informado");
	}

	@Test
	void processarSexoAceitaAsFormasUsuais() {
		java.util.Map<String, String> casos = java.util.Map.of(
				"Fem", "F", "Fem.", "F", "Mulher", "F", "Masc", "M", "masc.", "M", "Homem", "M", "indeterminado", "I");

		casos.forEach((valor, esperado) -> {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setSexoPaciente(valor);

			processor.processar(dto);

			assertThat(dto.getSexoPaciente()).as(valor).isEqualTo(esperado);
		});
	}

	// ===== CEP / CPF =====

	@Test
	void processarComCepDeTamanhoInvalidoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep("123");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CEP com tamanho inválido");
	}

	@Test
	void processarPacienteEmSituacaoDeRuaSemCepAceitaComAvisoECepEmBranco() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep(null);
		dto.setSituacaoRua("Sim");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getCep()).isNull();
		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("CEP_AUSENTE_SITUACAO_RUA").contains("em branco"));
	}

	@Test
	void processarPacienteEmSituacaoDeRuaComCepSegueNormal() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSituacaoRua("Sim");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getCep()).isEqualTo("79003020");
		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("CEP_AUSENTE_SITUACAO_RUA"));
	}

	@Test
	void processarSemCepLancaExcecao() {
		// Decisão de 16/10/2026: CEP vazio é erro também na importação (antes só a análise bloqueava)
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CEP do endereço não informado");
	}

	@Test
	void processarComMunicipioDeMsECepDeOutroEstadoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep("01310-100"); // São Paulo; município do dtoValido: CAMPO GRANDE

		assertThat(processor.processar(dto)).anySatisfy(a -> assertThat(a)
				.contains("CEP_FORA_DE_MS").contains("01310100").contains("CAMPO GRANDE"));
	}

	@Test
	void processarComMunicipioDeMsECepDeMsNaoGeraAvisoDeCep() {
		assertThat(processor.processar(dtoValido())).noneSatisfy(a -> assertThat(a).contains("CEP_FORA_DE_MS"));
	}

	@Test
	void processarComCpfDeTamanhoInvalidoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("123");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF com tamanho inválido");
	}

	@Test
	void processarSemCpfDoMedicoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfMedico(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do médico não informado");
	}

	@Test
	void processarComCpfDoMedicoDeTamanhoInvalidoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfMedico("123456789012345");
		dto.setMedico("JOAO DA SILVA");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do médico com tamanho inválido")
				.hasMessageContaining("JOAO DA SILVA");
	}

	// ===== Município =====

	@Test
	void processarSemMunicipioLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setMunicipio(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Município do paciente não informado");
	}

	// ===== CBO e especialidade =====

	@Test
	void processarNormalizaMascaraDoCbo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCboMedico("2251-25");

		processor.processar(dto);

		assertThat(dto.getCboMedico()).isEqualTo("225125");
	}

	@Test
	void processarSemCboLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCboMedico(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CBO do médico não informado");
	}

	@Test
	void processarComCboSemSeisDigitosLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCboMedico("22512");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CBO do médico inválido")
				.hasMessageContaining("22512");
	}

	@Test
	void processarSemEspecialidadeLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEspecialidadeMedico(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Especialidade não informada");
	}

	@Test
	void processarNormalizaMascaraDoCpfDoMedico() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfMedico("987.654.321-00");

		processor.processar(dto);

		assertThat(dto.getCpfMedico()).isEqualTo("98765432100");
	}

	@Test
	void processarSemCpfComPacienteSemCpfSimNaoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente(null);
		dto.setPacienteSemCpf("Sim");

		processor.processar(dto);

		assertThat(dto.getPacienteSemCpf()).isEqualTo("S");
	}

	@Test
	void processarComCpfPreenchidoEPacienteSemCpfSimLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setPacienteSemCpf("Sim");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF preenchido com \"Paciente sem CPF\" = Sim");
	}

	@Test
	void processarSemCpfSemColunaPacienteSemCpfLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do paciente não informado");
	}

	@Test
	void processarComCpfDoPacienteComDigitoVerificadorErradoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("12345678900");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do paciente inválido (dígitos verificadores não conferem)");
	}

	@Test
	void processarComCpfDoMedicoComDigitoVerificadorErradoOuRepetidoLancaExcecao() {
		for (String cpf : new String[] { "98765432101", "11111111111" }) {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setCpfMedico(cpf);

			assertThatThrownBy(() -> processor.processar(dto)).as(cpf)
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("CPF do médico inválido");
		}
	}

	@Test
	void processarComCpfFalsoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("000.000.000-00");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do paciente inválido (dígitos repetidos)");
	}

	@Test
	void processarNormalizaMascaraDeCpfECep() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("123.456.789-09");
		dto.setCep("79003-020");

		processor.processar(dto);

		assertThat(dto.getCpfPaciente()).isEqualTo("12345678909");
		assertThat(dto.getCep()).isEqualTo("79003020");
	}

	// ===== RAÇA (obrigatória, ERRO bloqueante) =====

	@Test
	void processarSemRacaLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Raça do paciente não informada");
	}

	@Test
	void processarComRacaEmBrancoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("   ");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Raça do paciente não informada");
	}

	@Test
	void processarComRacaNaoReconhecidaLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("XYZ");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("XYZ")
				.hasMessageContaining("não reconhecida");
	}

	@Test
	void processarComVariacaoDeGrafiaDaRacaNaoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("Pardo");

		assertThatCode(() -> processor.processar(dto)).doesNotThrowAnyException();
	}

	// ===== CAMPOS OBRIGATÓRIOS =====

	@Test
	void processarSemPacienteLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setPaciente(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Paciente não informado");
	}

	@Test
	void processarSemCpfDoPacienteLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF do paciente não informado");
	}

	@Test
	void processarSemDataDeAgendamentoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setDataAgendamentoString(null);

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Data de agendamento não informada");
	}

	// ===== CNS (aviso, não bloqueia) =====

	@Test
	void processarComCnsDeQuinzeDigitosNaoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCnsPaciente("700207960618529");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).isEmpty();
	}

	@Test
	void processarComCnsCurtoGeraAvisoMasNaoBloqueia() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCnsPaciente("123");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("CNS_INVALIDO"));
		assertThat(dto.getCnsPaciente()).isEqualTo("123");
	}

	@Test
	void processarComCnsLongoGeraAvisoDeFormatoIncomumMasNaoBloqueia() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCnsPaciente("7000094731924063");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("formato incomum"));
	}

	// ===== RAÇA INDÍGENA: etnia obrigatória (erro, decisão de 13/10/2026) =====

	@Test
	void processarComRacaIndigenaSemEtniaRejeitaALinha() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("Indígena");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Raça Indígena exige a etnia");
	}

	@Test
	void processarComRacaNaoIndigenaNaoGeraAvisoDeRaca() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("PARDA");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("etnia"));
	}

	@Test
	void processarComRacaIndigenaEEtniaReconhecidaNaoGeraAvisoDeRaca() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("INDIGENA");
		dto.setEtniaPaciente("BANIWA");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("necessario preencher"));
	}

	// ===== ETNIA NÃO ENCONTRADA (erro com raça Indígena; ignorada com aviso nas demais) =====

	@Test
	void processarComEtniaPreenchidaNaoReconhecidaERacaIndigenaRejeitaALinha() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("INDIGENA");
		dto.setEtniaPaciente("ETNIA QUE NAO EXISTE XYZ");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ETNIA QUE NAO EXISTE XYZ")
				.hasMessageContaining("não reconhecida");
	}

	@Test
	void processarComEtniaPreenchidaERacaNaoIndigenaNaoGeraAvisoDeEtnia() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("PARDA");
		dto.setEtniaPaciente("TERENA");

		assertThat(processor.processar(dto)).noneSatisfy(a -> assertThat(a).containsIgnoringCase("etnia"));
	}

	@Test
	void processarComEtniaPreenchidaNaoReconhecidaMasRacaNaoIndigenaNaoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("PARDA");
		dto.setEtniaPaciente("ETNIA QUE NAO EXISTE XYZ");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("nao encontrada"));
	}

	@Test
	void processarComEtniaPreenchidaReconhecidaNaoGeraAvisoDeEtnia() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("INDIGENA");
		dto.setEtniaPaciente("Baniwa");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("nao encontrada"));
	}

	// ===== SITUAÇÃO DE RUA (aviso, não bloqueia) =====

	@Test
	void processarSemColunaDeSituacaoDeRuaNaoGeraAvisoEDeixaCampoNulo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSituacaoRua(null);

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).isEmpty();
		assertThat(dto.getSituacaoRua()).isNull();
	}

	@Test
	void processarNormalizaSituacaoDeRuaPorExtensoParaCodigoDeUmaLetra() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSituacaoRua("Sim");

		processor.processar(dto);

		assertThat(dto.getSituacaoRua()).isEqualTo("S");
	}

	@Test
	void processarNormalizaSituacaoDeRuaAbreviadaIndependenteDeCaixa() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSituacaoRua("n");

		processor.processar(dto);

		assertThat(dto.getSituacaoRua()).isEqualTo("N");
	}

	@Test
	void processarComSituacaoDeRuaNaoReconhecidaGeraAvisoMasNaoBloqueiaEDeixaCampoNulo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSituacaoRua("TALVEZ");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anySatisfy(a -> assertThat(a)
				.contains("TALVEZ").contains("nao reconhecida"));
		assertThat(dto.getSituacaoRua()).isNull();
	}

	// ===== PACIENTE SEM CPF (aviso, não bloqueia) =====

	@Test
	void processarSemColunaDePacienteSemCpfNaoGeraAvisoEDeixaCampoNulo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setPacienteSemCpf(null);

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).isEmpty();
		assertThat(dto.getPacienteSemCpf()).isNull();
	}

	@Test
	void processarNormalizaPacienteSemCpfPorExtensoParaCodigoDeUmaLetra() {
		// "Sim" só é válido com o CPF vazio (CPF preenchido + Sim é erro de conflito)
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente(null);
		dto.setPacienteSemCpf("Sim");

		processor.processar(dto);

		assertThat(dto.getPacienteSemCpf()).isEqualTo("S");
	}

	@Test
	void processarComPacienteSemCpfNaoReconhecidoGeraAvisoMasNaoBloqueiaEDeixaCampoNulo() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setPacienteSemCpf("TALVEZ");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anySatisfy(a -> assertThat(a)
				.contains("TALVEZ").contains("nao reconhecido"));
		assertThat(dto.getPacienteSemCpf()).isNull();
	}

	// ===== CONVERSÃO DE DATA/HORA =====

	@Test
	void processarConverteDataDeAgendamentoValida() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setDataAgendamentoString("25/12/2024");

		processor.processar(dto);

		assertThat(dto.getDataAgendamento()).isEqualTo(LocalDate.of(2024, 12, 25));
	}

	@Test
	void processarComDataDeAgendamentoInvalidaLancaExcecaoComMensagemEspecifica() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setDataAgendamentoString("não é uma data");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Data de agendamento inválida");
	}

	@Test
	void processarConverteHoraDeAtendimentoValida() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setHoraAtendimentoString("08:30");

		processor.processar(dto);

		assertThat(dto.getHoraAtendimento()).isEqualTo(LocalTime.of(8, 30));
	}

	@Test
	void processarComHoraDeAtendimentoInvalidaImportaSemHoraComAviso() {
		// A hora não vai para o BPA-I — valor não reconhecido não pode
		// descartar a linha inteira (produção perdida); vira aviso.
		LinhaImportacaoDTO dto = dtoValido();
		dto.setHoraAtendimentoString("25h99");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getHoraAtendimento()).isNull();
		assertThat(avisos).anySatisfy(aviso -> assertThat(aviso)
				.contains("Horário de atendimento não reconhecido")
				.contains("25h99")
				.contains("remessa BPA-I não utiliza o horário"));
	}

	@Test
	void processarComHoraDeAtendimentoHifenImportaSemHoraComAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setHoraAtendimentoString("-");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getHoraAtendimento()).isNull();
		assertThat(avisos).anySatisfy(aviso -> assertThat(aviso).contains("Horário de atendimento não reconhecido"));
	}

	@Test
	void processarComHoraDeAtendimentoVaziaNaoGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setHoraAtendimentoString(null);

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getHoraAtendimento()).isNull();
		assertThat(avisos).noneMatch(aviso -> aviso.contains("Horário de atendimento"));
	}

	@Test
	void processarConverteDataDeNascimentoValida() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setDataNascimentoString("01/01/2000");

		processor.processar(dto);

		assertThat(dto.getDataNascimento()).isEqualTo(LocalDate.of(2000, 1, 1));
	}

	@Test
	void processarComDataDeNascimentoInvalidaLancaExcecaoComMensagemEspecifica() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setDataNascimentoString("não é uma data");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Data de nascimento inválida");
	}

	// ===== LIMITE DE TAMANHO DE CAMPOS =====

	@Test
	void processarTruncaCamposDeEnderecoConformeLimitesDoLayoutBpai() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setEndereco("A".repeat(40));
		dto.setBairro("B".repeat(40));
		dto.setComplemento("C".repeat(20));
		dto.setNumero("1234567890");

		processor.processar(dto);

		assertThat(dto.getEndereco()).hasSize(30);
		assertThat(dto.getBairro()).hasSize(30);
		assertThat(dto.getComplemento()).hasSize(10);
		// Número com mais de 5 dígitos não é cortado (viraria outro número): vai SN, com aviso (16/10/2026)
		assertThat(dto.getNumero()).isEqualTo("SN");
	}

	@Test
	void processarBairroEComplementoNormalizaEAvisaSoOComplementoCortado() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setBairro("Bairro Conjunto Habitacional Professor Fulano de Tal");
		dto.setComplemento("Apartamento 201 Bloco B");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getBairro()).isEqualTo("Conjunto Habitacional");
		assertThat(dto.getComplemento()).isEqualTo("AP 201 BL");
		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("COMPLEMENTO_CORTADO"));
		assertThat(avisos).noneSatisfy(a -> assertThat(a).containsIgnoringCase("bairro \""));
	}

	@Test
	void processarBairroEComplementoDePreenchimentoViramVazio() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setBairro("-");
		dto.setComplemento("N/A");

		processor.processar(dto);

		assertThat(dto.getBairro()).isNull();
		assertThat(dto.getComplemento()).isNull();
	}

	@Test
	void processarNumeroDoEnderecoForaDoPadraoGeraAvisoEFicaComONumeroInicial() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setNumero("100 apto 201");

		List<String> avisos = processor.processar(dto);

		assertThat(dto.getNumero()).isEqualTo("100");
		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("NUMERO_FORA_DO_PADRAO").contains("\"100\""));
	}

	@Test
	void processarNumeroVazioOuSemNumeroViraSnSemAviso() {
		for (String valor : java.util.Arrays.asList(null, "", "S/N", "sem número", "0")) {
			LinhaImportacaoDTO dto = dtoValido();
			dto.setNumero(valor);

			List<String> avisos = processor.processar(dto);

			assertThat(dto.getNumero()).as(String.valueOf(valor)).isEqualTo("SN");
			assertThat(avisos).as(String.valueOf(valor)).noneSatisfy(a -> assertThat(a).contains("NUMERO_FORA_DO_PADRAO"));
		}
	}

	@Test
	void processarCepDePreenchimentoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep("00000-000");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dígitos repetidos");
	}

	@Test
	void processarCepComSeteDigitosOrientaFormatarComoTexto() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCep("1310100");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("7 dígitos")
				.hasMessageContaining("formate a coluna como texto");
	}

	// ===== Vigência dos campos novos do layout (decisão 26, 16/10/2026) =====

	@Test
	void processarSimAntesDaVigenciaDoCampoGeraAviso() {
		LinhaImportacaoDTO rua = dtoValido();
		rua.setDataAgendamentoString("30/11/2024");
		rua.setSituacaoRua("Sim");

		assertThat(processor.processar(rua))
				.anySatisfy(a -> assertThat(a).contains("competência 11/2024", "Situação de Rua",
						"CAMPO_ANTES_DA_VIGENCIA"));

		LinhaImportacaoDTO semCpf = dtoValido();
		semCpf.setDataAgendamentoString("30/06/2026");
		semCpf.setCpfPaciente(null);
		semCpf.setPacienteSemCpf("Sim");

		assertThat(processor.processar(semCpf))
				.anySatisfy(a -> assertThat(a).contains("competência 06/2026", "Paciente sem CPF"));
	}

	@Test
	void processarNaoAntesDaVigenciaOuSimJaVigenteNaoGeraAviso() {
		LinhaImportacaoDTO nao = dtoValido();
		nao.setDataAgendamentoString("30/11/2024");
		nao.setSituacaoRua("Não");
		nao.setPacienteSemCpf("Não");

		LinhaImportacaoDTO vigente = dtoValido();
		vigente.setDataAgendamentoString("01/12/2024");
		vigente.setSituacaoRua("Sim");

		assertThat(processor.processar(nao)).noneMatch(a -> a.contains("CAMPO_ANTES_DA_VIGENCIA"));
		assertThat(processor.processar(vigente)).noneMatch(a -> a.contains("CAMPO_ANTES_DA_VIGENCIA"));
	}

	@Test
	void processarComCnsVazioECpfPreenchidoNaoGeraAvisoDeCns() {
		// Decisão 13 (16/10/2026): com CPF o CNS não vai para o BPA-I
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCnsPaciente(null);

		assertThat(processor.processar(dto)).noneMatch(a -> a.contains("CNS"));
		assertThat(dto.getCnsPaciente()).isEmpty();
	}
}
