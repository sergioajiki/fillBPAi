package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogradouroUtilsTest {

	@ParameterizedTest
	@CsvSource({
			"'RUA DAS FLORES', '081', 'DAS FLORES'",
			"'RUA DAS FLORES', '081', 'DAS FLORES'",
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

	@Test
	void resolverSemPrefixoConhecidoMantemEnderecoOriginalTrimado() {
		LogradouroUtils.LogradouroResultado resultado = LogradouroUtils.resolver("  QUADRA 5 LOTE 3  ");

		assertThat(resultado.getCodLogradouro()).isNull();
		assertThat(resultado.getEndereco()).isEqualTo("QUADRA 5 LOTE 3");
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
}
