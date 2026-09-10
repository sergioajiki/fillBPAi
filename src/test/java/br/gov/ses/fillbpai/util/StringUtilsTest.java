package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsTest {

	@Test
	void limitarTamanhoCortaQuandoExcede() {
		assertThat(StringUtils.limitarTamanho("0123456789", 5)).isEqualTo("01234");
	}

	@Test
	void limitarTamanhoMantemQuandoMenorOuIgual() {
		assertThat(StringUtils.limitarTamanho("abc", 5)).isEqualTo("abc");
	}

	@Test
	void limitarTamanhoTrimaAntesDeCortar() {
		assertThat(StringUtils.limitarTamanho("  abc  ", 5)).isEqualTo("abc");
	}

	@Test
	void limitarTamanhoRetornaNullParaNull() {
		assertThat(StringUtils.limitarTamanho(null, 5)).isNull();
	}

	@Test
	void limitarTamanhoOuVazioRetornaVazioParaNull() {
		assertThat(StringUtils.limitarTamanhoOuVazio(null, 5)).isEmpty();
	}

	@Test
	void limitarTamanhoOuVazioCortaQuandoExcede() {
		assertThat(StringUtils.limitarTamanhoOuVazio("0123456789", 5)).isEqualTo("01234");
	}

	@Test
	void separarCodigoENomeComHifenComum() {
		String[] resultado = StringUtils.separarCodigoENome("123 - UNIDADE CENTRAL");

		assertThat(resultado).containsExactly("123", "UNIDADE CENTRAL");
	}

	@Test
	void separarCodigoENomeComEnDash() {
		String[] resultado = StringUtils.separarCodigoENome("123 – UNIDADE CENTRAL");

		assertThat(resultado).containsExactly("123", "UNIDADE CENTRAL");
	}

	@Test
	void separarCodigoENomeComEmDash() {
		String[] resultado = StringUtils.separarCodigoENome("123 — UNIDADE CENTRAL");

		assertThat(resultado).containsExactly("123", "UNIDADE CENTRAL");
	}

	@Test
	void separarCodigoENomeSemSeparadorDevolveNomeCompletoENuloNoCodigo() {
		String[] resultado = StringUtils.separarCodigoENome("UNIDADE CENTRAL");

		assertThat(resultado[0]).isNull();
		assertThat(resultado[1]).isEqualTo("UNIDADE CENTRAL");
	}

	@Test
	void separarCodigoENomeComValorNuloDevolveNuloENulo() {
		String[] resultado = StringUtils.separarCodigoENome(null);

		assertThat(resultado[0]).isNull();
		assertThat(resultado[1]).isNull();
	}

	@Test
	void separarEspecialidadeEMedicoComSeparador() {
		String[] resultado = StringUtils.separarEspecialidadeEMedico("CARDIOLOGIA - JOAO DA SILVA");

		assertThat(resultado).containsExactly("CARDIOLOGIA", "JOAO DA SILVA");
	}

	@Test
	void separarEspecialidadeEMedicoSemSeparadorDevolveEspecialidadeCompletaENuloNoMedico() {
		String[] resultado = StringUtils.separarEspecialidadeEMedico("CARDIOLOGIA");

		assertThat(resultado[0]).isEqualTo("CARDIOLOGIA");
		assertThat(resultado[1]).isNull();
	}
}
