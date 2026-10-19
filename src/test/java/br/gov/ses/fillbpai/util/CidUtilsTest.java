package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CidUtilsTest {

	@Test
	void extrairAceitaOCodigoComOuSemSubcategoriaEPonto() {
		assertThat(CidUtils.extrair("I10")).isEqualTo("I10");
		assertThat(CidUtils.extrair("i10.9")).isEqualTo("I109");
		assertThat(CidUtils.extrair("I109")).isEqualTo("I109");
		assertThat(CidUtils.extrair("Z00.0")).isEqualTo("Z000");
		assertThat(CidUtils.extrair("U07.1")).isEqualTo("U071");
		assertThat(CidUtils.extrair(" r69 ")).isEqualTo("R69");
	}

	@Test
	void extrairTiraODescritivoEOPrefixo() {
		// Antes: "CIDI", "I10H" — CIDs inexistentes
		assertThat(CidUtils.extrair("CID I10")).isEqualTo("I10");
		assertThat(CidUtils.extrair("I10 - Hipertensão")).isEqualTo("I10");
		assertThat(CidUtils.extrair("I10 - Hipertensão essencial (primária)")).isEqualTo("I10");
		assertThat(CidUtils.extrair("E11 - Diabetes tipo 2")).isEqualTo("E11");
		assertThat(CidUtils.extrair("i10.9-extra")).isEqualTo("I109");
	}

	@Test
	void extrairRecusaCelulaSemCodigoOuComMaisDeUm() {
		assertThat(CidUtils.extrair("Hipertensão")).isNull();
		assertThat(CidUtils.extrair("10")).isNull();
		assertThat(CidUtils.extrair("COVID19")).isNull();
		assertThat(CidUtils.extrair("I1")).isNull();
		assertThat(CidUtils.extrair("I10, E11")).isNull();
		assertThat(CidUtils.extrair("")).isNull();
		assertThat(CidUtils.extrair(null)).isNull();
	}

	@Test
	void extrairMesmoCodigoRepetidoContaComoUm() {
		assertThat(CidUtils.extrair("I10 (I10)")).isEqualTo("I10");
	}

	@Test
	void motivoInvalidoDiferenciaNenhumDeMaisDeUm() {
		assertThat(CidUtils.motivoInvalido("I10, E11")).contains("mais de um CID");
		assertThat(CidUtils.motivoInvalido("Hipertensão")).contains("nenhum código CID-10");
	}

	@Test
	void isSemCidReconheceSoTraco() {
		// 19/10/2026: "-" = sem CID — aviso e CID em branco, não erro
		assertThat(CidUtils.isSemCid("-")).isTrue();
		assertThat(CidUtils.isSemCid(" -- ")).isTrue();
		assertThat(CidUtils.isSemCid("—")).isTrue();
		assertThat(CidUtils.isSemCid("–")).isTrue();
		assertThat(CidUtils.isSemCid("I10")).isFalse();
		assertThat(CidUtils.isSemCid("Hipertensão")).isFalse();
		assertThat(CidUtils.isSemCid("")).isFalse();
		assertThat(CidUtils.isSemCid(null)).isFalse();
	}
}
