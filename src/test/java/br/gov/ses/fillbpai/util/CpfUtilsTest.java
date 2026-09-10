package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfUtilsTest {

	@Test
	void normalizarRemoveMascara() {
		assertThat(CpfUtils.normalizar("123.456.789-00")).isEqualTo("12345678900");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void normalizarRetornaNullParaNuloOuVazio(String valor) {
		assertThat(CpfUtils.normalizar(valor)).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "12345678900, true", "123.456.789-00, true", "1234567890, false", "123456789000, false" })
	void isValidoExige11Digitos(String cpf, boolean esperado) {
		assertThat(CpfUtils.isValido(cpf)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void isValidoFalsoParaVazioOuBranco(String cpf) {
		assertThat(CpfUtils.isValido(cpf)).isFalse();
	}

	@Test
	void isValidoFalsoParaNulo() {
		assertThat(CpfUtils.isValido(null)).isFalse();
	}
}
