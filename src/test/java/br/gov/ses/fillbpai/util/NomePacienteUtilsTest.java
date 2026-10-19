package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Recomendações 29 e 30 (19/10/2026). */
class NomePacienteUtilsTest {

	@Test
	void padronizarDeixaEmMaiusculasComEspacosReduzidosEMantemAcentos() {
		assertThat(NomePacienteUtils.padronizar("  Maria  da   Sílva ")).isEqualTo("MARIA DA SÍLVA");
		assertThat(NomePacienteUtils.padronizar(null)).isNull();
	}

	@Test
	void cortadoNoBpaMostraOsTrintaPrimeirosCaracteres() {
		assertThat(NomePacienteUtils.cortadoNoBpa("Maria Aparecida dos Santos Oliveira"))
				.isEqualTo("MARIA APARECIDA DOS SANTOS OLI");
		assertThat(NomePacienteUtils.cortadoNoBpa("MARIA DA SILVA")).isNull();
		assertThat(NomePacienteUtils.cortadoNoBpa("A".repeat(30))).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"TESTE, com uma palavra só",
			"MARIA 2, com números",
			"-, sem letras",
			"'..', sem letras",
			"MARIA @ SILVA, com símbolos",
			"RN DE MARIA SILVA, recém-nascido sem nome",
			"rn, recém-nascido sem nome"
	})
	void motivoSuspeitoReconheceNomesQueNaoParecemCompletos(String nome, String motivo) {
		assertThat(NomePacienteUtils.motivoSuspeito(nome)).isEqualTo(motivo);
	}

	@ParameterizedTest
	@ValueSource(strings = { "MARIA DA SILVA", "José D'Ávila", "ANA-LUÍSA SOUZA", "JOÃO S. PEREIRA", "RONALDO LIMA" })
	void motivoSuspeitoAceitaNomesComuns(String nome) {
		assertThat(NomePacienteUtils.motivoSuspeito(nome)).isNull();
	}
}
