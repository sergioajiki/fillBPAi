package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EnderecoUtilsTest {

	// ===== Bairro =====

	@ParameterizedTest
	@CsvSource({
			"'CENTRO', 'CENTRO'",
			"'Bairro Centro', 'Centro'",
			"'BAIRRO: Vila Nova', 'Vila Nova'",
			"'Conjunto Habitacional Professor Fulano de Tal', 'Conjunto Habitacional'", // antes: "...Professo"
			"'Jardim  São   Bento', 'Jardim São Bento'" })
	void bairroTiraOPrefixoECortaNaPalavra(String celula, String esperado) {
		assertThat(EnderecoUtils.bairro(celula)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "-", "--", ".", "X", "0", "N/A", "NA", "Não informado", "NAO SE APLICA", "Sem", "Bairro" })
	void bairroDePreenchimentoViraVazio(String celula) {
		assertThat(EnderecoUtils.bairro(celula)).isNull();
	}

	@Test
	void bairroIgualAoMunicipioIgnoraAcentoCaixaEUf() {
		assertThat(EnderecoUtils.isBairroIgualMunicipio("CAMPO GRANDE", "Campo Grande")).isTrue();
		assertThat(EnderecoUtils.isBairroIgualMunicipio("Três Lagoas", "TRES LAGOAS - MS")).isTrue();
		assertThat(EnderecoUtils.isBairroIgualMunicipio("Centro", "Campo Grande")).isFalse();
		assertThat(EnderecoUtils.isBairroIgualMunicipio("-", "Campo Grande")).isFalse();
		assertThat(EnderecoUtils.isBairroIgualMunicipio("Centro", null)).isFalse();
	}

	// ===== Complemento =====

	@ParameterizedTest
	@CsvSource({ "'APTO 1', 'APTO 1'", "'Casa 2', 'Casa 2'", "'Fundos', 'Fundos'", "'Ap. 101 B', 'Ap. 101 B'" })
	void complementoQueCabeFicaComoFoiDigitadoSemAviso(String celula, String esperado) {
		EnderecoUtils.Complemento c = EnderecoUtils.complemento(celula);

		assertThat(c.valor()).isEqualTo(esperado);
		assertThat(c.cortado()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({
			"'Apartamento 201', 'AP 201', false",          // antes: "Apartament"
			"'Casa 3 Fundos', 'CS 3 FDS', false",
			"'Quadra 10 Lote 5', 'QD 10 LT 5', false",
			"'Apartamento 201 Bloco B', 'AP 201 BL', true", // abreviado "AP 201 BL B" (11) → corta na palavra
			"'Ap. 101 Bl. C fundos', 'AP 101 BL', true" })
	void complementoQueNaoCabeEAbreviadoECortadoNaPalavra(String celula, String esperado, boolean cortado) {
		EnderecoUtils.Complemento c = EnderecoUtils.complemento(celula);

		assertThat(c.valor()).isEqualTo(esperado);
		assertThat(c.cortado()).isEqualTo(cortado);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "-", "X", "0", "N/A", "não informado" })
	void complementoDePreenchimentoViraVazio(String celula) {
		EnderecoUtils.Complemento c = EnderecoUtils.complemento(celula);

		assertThat(c.valor()).isNull();
		assertThat(c.cortado()).isFalse();
	}

	@Test
	void mensagemComplementoCortadoMostraOQueVaiNoBpa() {
		EnderecoUtils.Complemento c = EnderecoUtils.complemento("Apartamento 201 Bloco B");

		assertThat(EnderecoUtils.mensagemComplementoCortado("Apartamento 201 Bloco B", c))
				.contains("será enviado \"AP 201 BL\"").contains("abreviado: \"AP 201 BL B\"");
	}
}
