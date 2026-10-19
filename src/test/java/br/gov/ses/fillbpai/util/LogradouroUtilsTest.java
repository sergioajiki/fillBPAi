package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogradouroUtilsTest {

	@ParameterizedTest
	@CsvSource({
			"'RUA DAS FLORES', '081', 'DAS FLORES'",
			"'Rua das Flores', '081', 'das Flores'",
			"'AVENIDA BRASIL', '008', 'BRASIL'",
			"'AV. BRASIL', '008', 'BRASIL'",
			"'AV BRASIL', '008', 'BRASIL'",
			"'TRAVESSA DA PAZ', '100', 'DA PAZ'",
			"'TRAV. DA PAZ', '100', 'DA PAZ'",
			"'TV DA PAZ', '100', 'DA PAZ'"
	})
	void resolverDetectaPrefixoERemoveDoEndereco(String enderecoRaw, String codigoEsperado, String enderecoEsperado) {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver(enderecoRaw);

		assertThat(resultado.getCodLogradouro()).isEqualTo(codigoEsperado);
		assertThat(resultado.getEndereco()).isEqualTo(enderecoEsperado);
	}

	@ParameterizedTest
	@CsvSource({
			// Tipos da tabela DNE (15/10/2026) — antes iam como Rua (081) com o tipo dentro do endereço
			"'Rodovia BR-163', '090', 'BR-163'",
			"'Alameda dos Ipês', '004', 'dos Ipês'",
			"'Praça Ary Coelho', '065', 'Ary Coelho'",
			"'Estrada do Aeroporto', '031', 'do Aeroporto'",
			"'QUADRA 5 LOTE 3', '077', '5 LOTE 3'",
			"'Rua Projetada 5', '698', '5'",
			"'2ª Travessa Bahia', '508', 'Bahia'",
			// Abreviações e pontuação depois do tipo
			"'R. Dom Aquino', '081', 'Dom Aquino'",
			"'Rua:Dom Aquino', '081', 'Dom Aquino'",
			"'Al. Santos', '004', 'Santos'",
			"'Rod. MS-080', '090', 'MS-080'",
			"'Pça. da Sé', '065', 'da Sé'"
	})
	void resolverReconheceOsTiposDaTabelaDneEAbreviacoes(String enderecoRaw, String codigoEsperado,
			String enderecoEsperado) {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver(enderecoRaw);

		assertThat(resultado.getCodLogradouro()).isEqualTo(codigoEsperado);
		assertThat(resultado.getEndereco()).isEqualTo(enderecoEsperado);
	}

	@Test
	void resolverPrefereOTipoMaisLongoSoQuandoSobraUmNome() {
		// "AVENIDA CENTRAL" (829) existe na tabela, mas tiraria o nome inteiro da rua
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("Avenida Central");

		assertThat(resultado.getCodLogradouro()).isEqualTo("008");
		assertThat(resultado.getEndereco()).isEqualTo("Central");
	}

	@Test
	void resolverNaoConfundePalavraQueComecaComOTipo() {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("Ruazinha da Paz");

		assertThat(resultado.getCodLogradouro()).isNull();
		assertThat(resultado.getEndereco()).isEqualTo("Ruazinha da Paz");
	}

	@Test
	void resolverSemPrefixoConhecidoMantemEnderecoOriginalTrimado() {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("  DOM AQUINO 100  ");

		assertThat(resultado.getCodLogradouro()).isNull();
		assertThat(resultado.getEndereco()).isEqualTo("DOM AQUINO 100");
	}

	@Test
	void resolverComEnderecoNuloDevolveCodigoNuloEEnderecoNulo() {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver(null);

		assertThat(resultado.getCodLogradouro()).isNull();
		assertThat(resultado.getEndereco()).isNull();
	}

	@Test
	void resolverComEnderecoVazioDevolveCodigoNuloEEnderecoVazio() {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("");

		assertThat(resultado.getCodLogradouro()).isNull();
		assertThat(resultado.getEndereco()).isEmpty();
	}

	// ===== Coluna "Cód. Logradouro" =====

	@ParameterizedTest
	@CsvSource({
			"'081', '081'", "'81', '081'", "'8', '008'", "'090', '090'",
			"'Rua', '081'", "'R', '081'", "'R.', '081'", "'Avenida', '008'", "'Av', '008'",
			"'travessa', '100'", "'Rodovia', '090'", "'Praça', '065'"
	})
	void codigoDaColunaAceitaCodigoDaTabelaOuNomeDoTipo(String celula, String esperado) {
		assertThat(LogradouroUtils.codigoDaColuna(celula)).isEqualTo(esperado);
	}

	@Test
	void codigoDaColunaRecusaCodigoForaDaTabelaETextoDesconhecido() {
		// Antes: "Rua" virava 000 e "Avenida" cancelava a importação (coluna de 3 caracteres)
		assertThat(LogradouroUtils.codigoDaColuna("999")).isNull();
		assertThat(LogradouroUtils.codigoDaColuna("XYZ")).isNull();
		assertThat(LogradouroUtils.isColunaNaoReconhecida("999")).isTrue();
		assertThat(LogradouroUtils.isColunaNaoReconhecida("")).isFalse();
		assertThat(LogradouroUtils.isColunaNaoReconhecida(null)).isFalse();
	}

	@Test
	void campoDesativadoNaoEReconhecidoComoTipoDeLogradouro() {
		// 023 (Campo) comentado na tabela em 19/10/2026: "Campo ..." fica no endereço
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("Campo dos Bandeirantes");

		assertThat(resultado.getCodLogradouro()).isNotEqualTo("023");
		assertThat(resultado.getEndereco()).isEqualTo("Campo dos Bandeirantes");
		assertThat(LogradouroUtils.codigoDaColuna("023")).isNull();
		assertThat(LogradouroUtils.codigoDaColuna("Campo")).isNull();
	}
}
