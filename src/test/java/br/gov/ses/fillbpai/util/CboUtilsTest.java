package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CboUtilsTest {

	@Test
	void normalizarMantemSoOsDigitos() {
		assertThat(CboUtils.normalizar("225125")).isEqualTo("225125");
		assertThat(CboUtils.normalizar("2251-25")).isEqualTo("225125");
		assertThat(CboUtils.normalizar("225.125")).isEqualTo("225125");
		assertThat(CboUtils.normalizar(" 2251 25 ")).isEqualTo("225125");
		assertThat(CboUtils.normalizar("MEDICO CARDIOLOGISTA")).isEmpty();
	}

	@Test
	void normalizarComNuloDevolveVazio() {
		assertThat(CboUtils.normalizar(null)).isEmpty();
	}

	@Test
	void isValidoExigeSeisDigitosDepoisDeNormalizar() {
		assertThat(CboUtils.isValido("225125")).isTrue();
		assertThat(CboUtils.isValido("2251-25")).isTrue();
		assertThat(CboUtils.isValido("22512")).isFalse();
		assertThat(CboUtils.isValido("2251250")).isFalse();
		assertThat(CboUtils.isValido("MEDICO")).isFalse();
		assertThat(CboUtils.isValido(null)).isFalse();
	}
}
