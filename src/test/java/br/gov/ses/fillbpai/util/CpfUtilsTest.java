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
		assertThat(CpfUtils.normalizar("123.456.789-09")).isEqualTo("12345678909");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void normalizarRetornaNullParaNuloOuVazio(String valor) {
		assertThat(CpfUtils.normalizar(valor)).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "12345678909, true", "123.456.789-09, true", "1234567890, false", "123456789090, false" })
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

	@ParameterizedTest
	@ValueSource(strings = { "00000000000", "11111111111", "999.999.999-99", "22222222222" })
	void isFalsoParaDigitosTodosIguais(String cpf) {
		assertThat(CpfUtils.isFalso(cpf)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345678909", "01234567890" })
	void isFalsoFalsoParaCpfComDigitosVariados(String cpf) {
		assertThat(CpfUtils.isFalso(cpf)).isFalse();
	}

	@Test
	void isFalsoFalsoParaNuloOuTamanhoErrado() {
		assertThat(CpfUtils.isFalso(null)).isFalse();
		assertThat(CpfUtils.isFalso("0000000000")).isFalse(); // 10 dígitos: é CPF_INVALIDO, não falso
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345678909", "123.456.789-09", "98765432100", "01234567890" })
	void isDvValidoParaCpfComDigitosVerificadoresCorretos(String cpf) {
		assertThat(CpfUtils.isDvValido(cpf)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345678900", "12345678919", "98765432101" })
	void isDvValidoFalsoComUmDigitoErrado(String cpf) {
		assertThat(CpfUtils.isDvValido(cpf)).isFalse();
	}

	@Test
	void isDvValidoFalsoParaNuloOuTamanhoErrado() {
		assertThat(CpfUtils.isDvValido(null)).isFalse();
		assertThat(CpfUtils.isDvValido("1234567890")).isFalse();
	}

	@Test
	void chaveSemCpfEEstavelECabeNaColunaDeCpf() {
		String chave = CpfUtils.chaveSemCpf("Maria da Silva", java.time.LocalDate.of(1990, 1, 1));

		assertThat(chave).hasSize(14).startsWith("SC");
		assertThat(CpfUtils.chaveSemCpf("MARIA DA SILVA ", java.time.LocalDate.of(1990, 1, 1))).isEqualTo(chave);
		assertThat(CpfUtils.chaveSemCpf("MARIA DA SILVA", java.time.LocalDate.of(1990, 1, 2))).isNotEqualTo(chave);
		assertThat(CpfUtils.isChaveSemCpf(chave)).isTrue();
		assertThat(CpfUtils.isChaveSemCpf("12345678909")).isFalse();
		assertThat(CpfUtils.isChaveSemCpf(null)).isFalse();
	}
}
