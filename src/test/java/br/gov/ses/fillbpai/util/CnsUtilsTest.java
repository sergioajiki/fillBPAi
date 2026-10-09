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

	// ===== Dígito verificador (recomendação 25) =====

	@Test
	void isDvValidoAceitaCnsDefinitivoEProvisorio() {
		assertThat(CnsUtils.isDvValido("700207960618529")).isTrue();  // provisório (7)
		assertThat(CnsUtils.isDvValido("123456789010000")).isTrue();  // definitivo (1)
		assertThat(CnsUtils.isDvValido("200000000010009")).isTrue();  // definitivo (2)
		assertThat(CnsUtils.isDvValido("700 2079 6061 8529")).isTrue(); // com máscara
	}

	@Test
	void isDvValidoRecusaDigitoErradoPrimeiroDigitoInvalidoETamanhoErrado() {
		assertThat(CnsUtils.isDvValido("700207960618528")).isFalse(); // último dígito trocado
		assertThat(CnsUtils.isDvValido("700207960618592")).isFalse(); // dois dígitos invertidos
		assertThat(CnsUtils.isDvValido("300000000000000")).isFalse(); // começa com 3
		assertThat(CnsUtils.isDvValido("000000000000000")).isFalse();
		assertThat(CnsUtils.isDvValido("70020796061852")).isFalse();
		assertThat(CnsUtils.isDvValido(null)).isFalse();
	}

	@Test
	void processarComQuinzeDigitosEDvErradoGeraAviso() {
		CnsUtils.CnsResultado resultado = CnsUtils.processar("700207960618528");

		assertThat(resultado.getCns()).isEqualTo("700207960618528");
		assertThat(resultado.getAvisos()).singleElement().asString()
				.contains("CNS_DV_INVALIDO").contains("dígito verificador");
	}

	@Test
	void isCnsSemCpfValidoExigeQuinzeDigitosEDvValido() {
		assertThat(CnsUtils.isCnsSemCpfValido("700207960618529")).isTrue();
		assertThat(CnsUtils.isCnsSemCpfValido("700207960618528")).isFalse();
		assertThat(CnsUtils.motivoCnsSemCpfInvalido("700207960618528")).isEqualTo("CNS com dígito verificador inválido");
		assertThat(CnsUtils.motivoCnsSemCpfInvalido("12345")).isEqualTo("CNS com 5 dígitos");
		assertThat(CnsUtils.motivoCnsSemCpfInvalido(null)).isEqualTo("CNS não informado");
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
