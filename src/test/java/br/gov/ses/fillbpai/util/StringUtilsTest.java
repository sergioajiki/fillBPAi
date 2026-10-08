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
	void separarCodigoENomeSemEspacosEMantemHifenDoNome() {
		assertThat(StringUtils.separarCodigoENome("12345-HOSPITAL CENTRAL"))
				.containsExactly("12345", "HOSPITAL CENTRAL");
		assertThat(StringUtils.separarCodigoENome("12345 - HOSPITAL SAO JOSE - UNIDADE 2"))
				.containsExactly("12345", "HOSPITAL SAO JOSE - UNIDADE 2");
	}

	@Test
	void separarCodigoENomeComTextoAntesDoHifenTrataACelulaInteiraComoNome() {
		// O código é sempre numérico (decisão de 08/10/2026): texto antes do
		// hífen é parte do nome. Antes "HOSPITAL SAO JOSE" virava código (17
		// caracteres numa coluna de 10 → importação cancelada) e "UBS" criava
		// um estabelecimento falso.
		assertThat(StringUtils.separarCodigoENome("HOSPITAL SAO JOSE - UNIDADE 2"))
				.containsExactly(null, "HOSPITAL SAO JOSE - UNIDADE 2");
		assertThat(StringUtils.separarCodigoENome("UBS - CENTRO"))
				.containsExactly(null, "UBS - CENTRO");
		assertThat(StringUtils.separarCodigoENome("UBS01 - CENTRO"))
				.containsExactly(null, "UBS01 - CENTRO");
	}

	@Test
	void separarCodigoENomeComCodigoMaiorQueDezDigitosTrataComoNome() {
		assertThat(StringUtils.separarCodigoENome("12345678901 - HOSPITAL"))
				.containsExactly(null, "12345678901 - HOSPITAL");
	}

	@Test
	void estabelecimentoNaoInformadoParaVazioSeparadorOuZero() {
		assertThat(StringUtils.isEstabelecimentoNaoInformado(null)).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("   ")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("-")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado(" - ")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("—")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("0")).isTrue();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("000 -")).isTrue();

		assertThat(StringUtils.isEstabelecimentoNaoInformado("12345")).isFalse();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("0 - HOSPITAL")).isFalse();
		assertThat(StringUtils.isEstabelecimentoNaoInformado("HOSPITAL")).isFalse();
	}

	@Test
	void separarCodigoENomeSoComCodigoDevolveCodigoENomeNulo() {
		assertThat(StringUtils.separarCodigoENome("1234567")).containsExactly("1234567", null);
		assertThat(StringUtils.separarCodigoENome(" 1234567 ")).containsExactly("1234567", null);
		assertThat(StringUtils.separarCodigoENome("12345 -")).containsExactly("12345", null);
		assertThat(StringUtils.separarCodigoENome("12345 -   ")).containsExactly("12345", null);
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
