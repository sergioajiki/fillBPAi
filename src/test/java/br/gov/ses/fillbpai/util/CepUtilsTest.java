package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CepUtilsTest {

	@Test
	void normalizarRemoveMascara() {
		assertThat(CepUtils.normalizar("01234-567")).isEqualTo("01234567");
	}

	@Test
	void normalizarRetornaNullParaNull() {
		assertThat(CepUtils.normalizar(null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void normalizarRetornaNullParaVazioOuBranco(String valor) {
		assertThat(CepUtils.normalizar(valor)).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "01234567, true", "01234-567, true", "1234567, false", "012345678, false" })
	void isValidoExige8Digitos(String cep, boolean esperado) {
		assertThat(CepUtils.isValido(cep)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void isValidoFalsoParaNuloOuVazio(String cep) {
		assertThat(CepUtils.isValido(cep)).isFalse();
	}
}
