package br.gov.ses.fillbpai.util;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeUtilsTest {

	@ParameterizedTest
	@CsvSource({
			"08:30, 08:30",
			"8:30, 08:30",
			"08:30:00, 08:30",
			"0830, 08:30"
	})
	void parseAceitaOsFormatosSuportados(String entrada, String esperadoIso) {
		assertThat(TimeUtils.parse(entrada)).isEqualTo(LocalTime.parse(esperadoIso));
	}

	@Test
	void parseSubstituiPontoPorDoisPontos() {
		assertThat(TimeUtils.parse("08.30")).isEqualTo(LocalTime.of(8, 30));
	}

	@Test
	void parseAceitaFormatoComH() {
		// Forma comum de escrever horário no Brasil
		assertThat(TimeUtils.parse("8h30")).isEqualTo(LocalTime.of(8, 30));
		assertThat(TimeUtils.parse("08h30")).isEqualTo(LocalTime.of(8, 30));
		assertThat(TimeUtils.parse("8H30")).isEqualTo(LocalTime.of(8, 30));
		assertThat(TimeUtils.parse("8 h 30")).isEqualTo(LocalTime.of(8, 30));
		assertThat(TimeUtils.parse("8h30min")).isEqualTo(LocalTime.of(8, 30));
		assertThat(TimeUtils.parse("14h")).isEqualTo(LocalTime.of(14, 0));
		assertThat(TimeUtils.parse("8h")).isEqualTo(LocalTime.of(8, 0));
	}

	@Test
	void parseComFormatoComHForaDoIntervaloContinuaInvalido() {
		assertThatThrownBy(() -> TimeUtils.parse("25h00")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TimeUtils.parse("8h75")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TimeUtils.parse("h30")).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "25h30", "não é uma hora" })
	void parseComFormatoNaoReconhecidoLancaExcecao(String valor) {
		assertThatThrownBy(() -> TimeUtils.parse(valor))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void parseComValorNuloOuVazioLancaExcecao(String valor) {
		assertThatThrownBy(() -> TimeUtils.parse(valor))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
