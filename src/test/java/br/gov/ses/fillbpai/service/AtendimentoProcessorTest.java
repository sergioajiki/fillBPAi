package br.gov.ses.fillbpai.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.gov.ses.fillbpai.dto.LinhaImportacaoDTO;

import static org.assertj.core.api.Assertions.assertThat;
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
		dto.setCpfPaciente("12345678900");
		dto.setDataAgendamentoString("25/12/2024");
		dto.setTipoServico("TELECONSULTA");
		dto.setCep("79003020");
		dto.setCnsPaciente("700207960618529");
		dto.setRacaPaciente("BRANCA");
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
	void processarComTipoServicoNuloNaoDefineSigtapNemLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setTipoServico(null);

		processor.processar(dto);

		assertThat(dto.getSigtap()).isNull();
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
	void processarComSexoNaoReconhecidoMantemValorOriginal() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setSexoPaciente("OUTRO");

		processor.processar(dto);

		assertThat(dto.getSexoPaciente()).isEqualTo("OUTRO");
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
	void processarComCpfDeTamanhoInvalidoLancaExcecao() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("123");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("CPF com tamanho inválido");
	}

	@Test
	void processarNormalizaMascaraDeCpfECep() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setCpfPaciente("123.456.789-00");
		dto.setCep("79003-020");

		processor.processar(dto);

		assertThat(dto.getCpfPaciente()).isEqualTo("12345678900");
		assertThat(dto.getCep()).isEqualTo("79003020");
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

	// ===== RAÇA INDÍGENA (aviso, não bloqueia) =====

	@Test
	void processarComRacaIndigenaGeraAviso() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("Indígena");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).anySatisfy(a -> assertThat(a).contains("Indigena"));
	}

	@Test
	void processarComRacaNaoIndigenaNaoGeraAvisoDeRaca() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setRacaPaciente("PARDA");

		List<String> avisos = processor.processar(dto);

		assertThat(avisos).noneSatisfy(a -> assertThat(a).contains("etnia"));
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
	void processarComHoraDeAtendimentoInvalidaLancaExcecaoComMensagemEspecifica() {
		LinhaImportacaoDTO dto = dtoValido();
		dto.setHoraAtendimentoString("25h99");

		assertThatThrownBy(() -> processor.processar(dto))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Hora de atendimento inválida");
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
		assertThat(dto.getNumero()).hasSize(5);
	}
}
