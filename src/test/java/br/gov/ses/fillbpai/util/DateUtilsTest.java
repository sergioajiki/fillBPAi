package br.gov.ses.fillbpai.util;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateUtilsTest {

	@ParameterizedTest
	@CsvSource({
			"25/12/2024, 2024-12-25",
			"2024-12-25, 2024-12-25",
			"25-12-2024, 2024-12-25"
	})
	void parseAceitaOsTresFormatosSuportados(String entrada, String esperadoIso) {
		assertThat(DateUtils.parse(entrada)).isEqualTo(LocalDate.parse(esperadoIso));
	}

	@Test
	void parseAplicaTrimAntesDeInterpretar() {
		assertThat(DateUtils.parse("  25/12/2024  ")).isEqualTo(LocalDate.of(2024, 12, 25));
	}

	@ParameterizedTest
	@ValueSource(strings = { "25122024", "2024/12/25", "não é uma data" })
	void parseComFormatoNaoReconhecidoLancaExcecao(String valor) {
		assertThatThrownBy(() -> DateUtils.parse(valor))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void parseComValorNuloOuVazioLancaExcecao(String valor) {
		assertThatThrownBy(() -> DateUtils.parse(valor))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ===== Modo estrito, dia/mês sem zero, idade e coerência (14/10/2026) =====

	@ParameterizedTest
	@ValueSource(strings = { "31/02/1990", "31/04/2024", "29/02/2023", "1990-02-30", "00/01/1990" })
	void parseRecusaDataInexistenteEmVezDeAjustarODia(String valor) {
		assertThatThrownBy(() -> DateUtils.parse(valor)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void parseAceitaDiaEMesSemZeroEAnoBissexto() {
		assertThat(DateUtils.parse("1/1/1990")).isEqualTo(LocalDate.of(1990, 1, 1));
		assertThat(DateUtils.parse("5/3/2024")).isEqualTo(LocalDate.of(2024, 3, 5));
		assertThat(DateUtils.parse("29/02/2024")).isEqualTo(LocalDate.of(2024, 2, 29));
	}

	@Test
	void parseRecusaAnoComDoisDigitos() {
		assertThatThrownBy(() -> DateUtils.parse("01/01/90")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void idadeNaDataUsaADataDoAtendimentoENaoAdeHoje() {
		LocalDate nascimento = LocalDate.of(2000, 1, 1);

		assertThat(DateUtils.idadeNaData(nascimento, LocalDate.of(2024, 12, 25))).isEqualTo(24);
		assertThat(DateUtils.idadeNaData(nascimento, LocalDate.of(2024, 1, 1))).isEqualTo(24);
		assertThat(DateUtils.idadeNaData(nascimento, LocalDate.of(2023, 12, 31))).isEqualTo(23);
		assertThat(DateUtils.idadeNaData(null, LocalDate.of(2024, 1, 1))).isZero();
	}

	@Test
	void incoerenciaNascimentoApontaNascimentoDepoisDoAtendimentoEIdadeAcimaDe130() {
		LocalDate atendimento = LocalDate.of(2024, 12, 25);

		assertThat(DateUtils.incoerenciaNascimento(LocalDate.of(2024, 12, 26), atendimento))
				.contains("depois da data do atendimento");
		assertThat(DateUtils.incoerenciaNascimento(LocalDate.of(1850, 1, 1), atendimento))
				.contains("174 anos").contains("máximo 130");
		assertThat(DateUtils.incoerenciaNascimento(LocalDate.of(1990, 1, 1), atendimento)).isNull();
		assertThat(DateUtils.incoerenciaNascimento(atendimento, atendimento)).isNull(); // recém-nascido
	}
}
