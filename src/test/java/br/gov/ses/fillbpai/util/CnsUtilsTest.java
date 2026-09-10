package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CnsUtilsTest {

	@Test
	void normalizarRemoveNaoDigitos() {
		assertThat(CnsUtils.normalizar("700 207.960-618529")).isEqualTo("700207960618529");
	}

	@Test
	void normalizarRetornaNullParaNull() {
		assertThat(CnsUtils.normalizar(null)).isNull();
	}

	@Test
	void processarComQuinzeDigitosNaoGeraAvisos() {
		CnsUtils.CnsResultado resultado = CnsUtils.processar("700207960618529");

		assertThat(resultado.getCns()).isEqualTo("700207960618529");
		assertThat(resultado.getAvisos()).isEmpty();
	}

	@Test
	void processarComMenosDeQuinzeDigitosGeraAvisoCnsInvalido() {
		CnsUtils.CnsResultado resultado = CnsUtils.processar("12345");

		assertThat(resultado.getCns()).isEqualTo("12345");
		assertThat(resultado.getAvisos()).hasSize(1)
				.first().asString().contains("CNS_INVALIDO");
	}

	@Test
	void processarComValorNuloGeraAvisoEDevolveCnsVazio() {
		CnsUtils.CnsResultado resultado = CnsUtils.processar(null);

		assertThat(resultado.getCns()).isEmpty();
		assertThat(resultado.getAvisos()).hasSize(1)
				.first().asString().contains("CNS_INVALIDO");
	}

	@Test
	void processarComValorVazioGeraAvisoEDevolveCnsVazio() {
		CnsUtils.CnsResultado resultado = CnsUtils.processar("   ");

		assertThat(resultado.getCns()).isEmpty();
		assertThat(resultado.getAvisos()).hasSize(1);
	}

	@Test
	void processarComMaisDeQuinzeDigitosAceitaComAvisoDeFormatoIncomum() {
		String cnsLegado = "7000094731924063";

		CnsUtils.CnsResultado resultado = CnsUtils.processar(cnsLegado);

		assertThat(resultado.getCns()).isEqualTo(cnsLegado);
		assertThat(resultado.getAvisos()).hasSize(1)
				.first().asString().contains("formato incomum");
	}
}
