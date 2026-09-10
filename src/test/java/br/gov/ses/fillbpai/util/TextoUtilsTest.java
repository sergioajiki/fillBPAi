package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextoUtilsTest {

	@Test
	void normalizarRemoveAcentosMaiusculizaETrima() {
		assertThat(TextoUtils.normalizar("  José da Silva  ")).isEqualTo("JOSE DA SILVA");
	}

	@Test
	void normalizarMantemTextoJaNormalizadoIgual() {
		assertThat(TextoUtils.normalizar("CPF DO PACIENTE")).isEqualTo("CPF DO PACIENTE");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "   ")
	void normalizarRetornaVazioParaNuloOuBranco(String valor) {
		assertThat(TextoUtils.normalizar(valor)).isEmpty();
	}
}
