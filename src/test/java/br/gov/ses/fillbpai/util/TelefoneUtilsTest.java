package br.gov.ses.fillbpai.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TelefoneUtilsTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"(67) 99999-9999|67999999999",
			"67999999999|67999999999",
			"(67) 3333-3333|6733333333",
			"+55 67 99999-9999|67999999999",      // antes: 55679999999
			"5567999999999|67999999999",
			"0 67 99999-9999|67999999999",        // antes: 06799999999
			"067 3333-3333|6733333333",
			"67 3333-3333 / 67 99999-9999|6733333333", // antes: 67333333336
			"67 99999-9999; 67 3333-3333|67999999999",
			"67 99999-9999 ou 67 3333-3333|67999999999",
			"67 3333-3333 / 67 99999-9999 / 67 98888-7777|6733333333" })
	void normalizarFicaComOPrimeiroNumeroComDdd(String celula, String esperado) {
		TelefoneUtils.Resultado r = TelefoneUtils.normalizar(celula);

		assertThat(r.situacao()).isEqualTo(TelefoneUtils.Situacao.VALIDO);
		assertThat(r.numero()).isEqualTo(esperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "99999-9999", "3333-3333" })
	void normalizarSemDddNaoAdivinhaODdd(String celula) {
		TelefoneUtils.Resultado r = TelefoneUtils.normalizar(celula);

		assertThat(r.situacao()).isEqualTo(TelefoneUtils.Situacao.SEM_DDD);
		assertThat(r.numero()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "NÃO TEM", "123", "679999999999999" })
	void normalizarComTextoOuTamanhoErradoEInvalido(String celula) {
		assertThat(TelefoneUtils.normalizar(celula).situacao()).isEqualTo(TelefoneUtils.Situacao.INVALIDO);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   " })
	void normalizarVazioNaoEProblema(String celula) {
		assertThat(TelefoneUtils.normalizar(celula).situacao()).isEqualTo(TelefoneUtils.Situacao.VAZIO);
	}
}
