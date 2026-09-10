package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link EtniaUtils} — resolução por nome via o CSV do classpath,
 * sem qualquer chamada de rede (tabela é estática, sem fallback externo).
 */
class EtniaUtilsTest {

	@Test
	void resolverComNomeConhecidoDevolveCodigoDeQuatroCaracteres() {
		assertThat(EtniaUtils.resolver("BANIWA")).isEqualTo("0032");
	}

	@Test
	void resolverIgnoraAcentoECaixa() {
		assertThat(EtniaUtils.resolver("baniwa")).isEqualTo("0032");
		assertThat(EtniaUtils.resolver("  Baniwa  ")).isEqualTo("0032");
	}

	@Test
	void resolverComNomeDesconhecidoDevolveNull() {
		assertThat(EtniaUtils.resolver("ETNIA QUE NAO EXISTE XYZ")).isNull();
	}

	@Test
	void resolverComNuloOuVazioDevolveNull() {
		assertThat(EtniaUtils.resolver(null)).isNull();
		assertThat(EtniaUtils.resolver("")).isNull();
		assertThat(EtniaUtils.resolver("   ")).isNull();
	}

	@Test
	void resolverComNomeDuplicadoEntreFaixasUsaCodigoDaFaixaPrincipal() {
		// "ARARA DE RONDONIA" aparece como 0014 (faixa principal) e 0270 (faixa
		// suplementar) — putIfAbsent garante que a primeira ocorrência no
		// arquivo (faixa principal) vence.
		assertThat(EtniaUtils.resolver("ARARA DE RONDONIA")).isEqualTo("0014");
	}

	@Test
	void normalizarRemoveAcentosEMaiusculiza() {
		assertThat(EtniaUtils.normalizar("Baniwa")).isEqualTo("BANIWA");
		assertThat(EtniaUtils.normalizar("Ñañaguas")).isEqualTo("NANAGUAS");
	}

	@Test
	void normalizarComNuloDevolveVazio() {
		assertThat(EtniaUtils.normalizar(null)).isEmpty();
	}
}
