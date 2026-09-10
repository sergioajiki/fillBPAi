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
}
