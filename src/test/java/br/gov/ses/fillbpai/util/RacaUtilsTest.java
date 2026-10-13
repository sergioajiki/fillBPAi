package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link RacaUtils} — a régua de reconhecimento compartilhada entre
 * validação (ValidacaoPlanilhaService/AtendimentoProcessor) e geração do
 * BPA-I (GeradorBPAiService).
 */
class RacaUtilsTest {

	@Test
	void resolverCodigoReconheceAsCincoCategoriasOficiais() {
		assertThat(RacaUtils.resolverCodigo("BRANCA")).isEqualTo("01");
		assertThat(RacaUtils.resolverCodigo("PRETA")).isEqualTo("02");
		assertThat(RacaUtils.resolverCodigo("PARDA")).isEqualTo("03");
		assertThat(RacaUtils.resolverCodigo("AMARELA")).isEqualTo("04");
		assertThat(RacaUtils.resolverCodigo("INDIGENA")).isEqualTo("05");
	}

	@Test
	void resolverCodigoIgnoraAcentoCaixaEEspacos() {
		assertThat(RacaUtils.resolverCodigo("indígena")).isEqualTo("05");
		assertThat(RacaUtils.resolverCodigo("  Branca  ")).isEqualTo("01");
	}

	@Test
	void resolverCodigoToleraVariacoesDeGeneroPorSubstring() {
		assertThat(RacaUtils.resolverCodigo("Branco")).isEqualTo("01");
		assertThat(RacaUtils.resolverCodigo("Pardo")).isEqualTo("03");
		assertThat(RacaUtils.resolverCodigo("Preto")).isEqualTo("02");
	}

	@Test
	void resolverCodigoComNuloOuVazioDevolveNull() {
		assertThat(RacaUtils.resolverCodigo(null)).isNull();
		assertThat(RacaUtils.resolverCodigo("")).isNull();
		assertThat(RacaUtils.resolverCodigo("   ")).isNull();
	}

	@Test
	void resolverCodigoComTextoNaoReconhecidoDevolveNull() {
		assertThat(RacaUtils.resolverCodigo("XYZ")).isNull();
	}

	@Test
	void resolverCodigoNaoResolveOCodigoDescontinuado99() {
		// "99 Sem informação" está marcado como descontinuado no layout oficial
		// (2024 e 2026) — nunca deve ser um valor válido vindo da planilha.
		assertThat(RacaUtils.resolverCodigo("99")).isNull();
		assertThat(RacaUtils.resolverCodigo("Sem informação")).isNull();
	}

	@Test
	void resolverCodigoReconhecePorPalavraInteiraENaoPorTrecho() {
		// Antes "IND" casava com qualquer palavra e estes viravam Indígena (05)
		assertThat(RacaUtils.resolverCodigo("Indeterminado")).isNull();
		assertThat(RacaUtils.resolverCodigo("Indefinida")).isNull();
		assertThat(RacaUtils.resolverCodigo("Indisponível")).isNull();
		assertThat(RacaUtils.resolverCodigo("Índio")).isEqualTo("05");
		assertThat(RacaUtils.resolverCodigo("Índia")).isEqualTo("05");
		assertThat(RacaUtils.resolverCodigo("Cor: Parda")).isEqualTo("03");
	}

	@Test
	void resolverCodigoRecusaTextoAmbiguoOuComNegacao() {
		assertThat(RacaUtils.resolverCodigo("Preta/Parda")).isNull();
		assertThat(RacaUtils.resolverCodigo("Não Branca")).isNull();
		assertThat(RacaUtils.resolverCodigo("Sem raça")).isNull();
	}

	@Test
	void resolverCodigoAceitaOCodigoDoBpa() {
		assertThat(RacaUtils.resolverCodigo("01")).isEqualTo("01");
		assertThat(RacaUtils.resolverCodigo("3")).isEqualTo("03");
		assertThat(RacaUtils.resolverCodigo("05")).isEqualTo("05");
		assertThat(RacaUtils.resolverCodigo("06")).isNull();
		assertThat(RacaUtils.resolverCodigo("0")).isNull();
	}

	@Test
	void mensagemNaoReconhecidaExplicaNegraEPedeGrafiaNosDemais() {
		assertThat(RacaUtils.mensagemNaoReconhecida("Negra"))
				.contains("o IBGE não usa \"Negra\"").contains("Preta ou Parda");
		assertThat(RacaUtils.mensagemNaoReconhecida("negro")).contains("Preta ou Parda");
		assertThat(RacaUtils.mensagemNaoReconhecida("Morena"))
				.contains("verifique a grafia").contains("código 01 a 05");
	}

	@Test
	void isIndigenaTrueSoParaRacaIndigena() {
		assertThat(RacaUtils.isIndigena("Indígena")).isTrue();
		assertThat(RacaUtils.isIndigena("INDIGENA")).isTrue();
		assertThat(RacaUtils.isIndigena("PARDA")).isFalse();
		assertThat(RacaUtils.isIndigena(null)).isFalse();
	}
}
