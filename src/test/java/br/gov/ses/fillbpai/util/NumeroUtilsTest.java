package br.gov.ses.fillbpai.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NumeroUtilsTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "  ", "S/N", "s/n", "S N", "SN", "S.N.", "S/Nº", "SEM NUMERO", "Sem número", "SEM Nº",
			"SEM N", "sem", "0", "00", "000", "-", " -- ", "–", "—" })
	void normalizarVazioOuSemNumeroViraSnSemAviso(String celula) {
		NumeroUtils.Resultado r = NumeroUtils.normalizar(celula);

		assertThat(r.valor()).isEqualTo("SN");
		assertThat(r.foraDoPadrao()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({ "'100', '100'", "' 1500 ', '1500'", "'12A', '12A'", "'12-B', '12-B'", "'12 b', '12 b'",
			"'99999', '99999'" })
	void normalizarNumeroNoPadraoFicaComoVeioSemAviso(String celula, String esperado) {
		NumeroUtils.Resultado r = NumeroUtils.normalizar(celula);

		assertThat(r.valor()).isEqualTo(esperado);
		assertThat(r.foraDoPadrao()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "X", "N/A", "NT", "LT 5", "A12" })
	void normalizarOutroValorCurtoFicaComoVeioComAviso(String celula) {
		// 16/10/2026: fora dos números e das formas de "sem número", avisar
		NumeroUtils.Resultado r = NumeroUtils.normalizar(celula);

		assertThat(r.valor()).isEqualTo(celula);
		assertThat(r.foraDoPadrao()).isTrue();
		assertThat(NumeroUtils.mensagemForaDoPadrao(celula, r))
				.contains("fora do padrão").contains("S/N").contains("será enviado \"" + celula + "\"");
	}

	@ParameterizedTest
	@CsvSource({
			"'100 apto 201', '100'",     // antes: "100 a"
			"'Km 12,5', '12'",           // antes: "Km 12"
			"'Nº 1500 fundos', '1500'",
			"'123456', 'SN'",            // antes: "12345" (outro número)
			"'Lote 10 Quadra 5', 'SN'",  // antes: "Lote "
			"'SEM NUMERO CASA 2', 'SN'" })
	void normalizarMaisDeCincoCaracteresFicaComONumeroInicialOuSnComAviso(String celula, String esperado) {
		NumeroUtils.Resultado r = NumeroUtils.normalizar(celula);

		assertThat(r.valor()).isEqualTo(esperado);
		assertThat(r.foraDoPadrao()).isTrue();
	}
}
