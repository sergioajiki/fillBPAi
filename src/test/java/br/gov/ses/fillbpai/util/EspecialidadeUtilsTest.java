package br.gov.ses.fillbpai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link EspecialidadeUtils} — remoção do prefixo "Médico"/"Médica"
 * que algumas planilhas trazem antes do nome real da especialidade.
 */
class EspecialidadeUtilsTest {

	@Test
	void normalizarRemovePrefixoMedico() {
		assertThat(EspecialidadeUtils.normalizar("Médico Endocrinologista")).isEqualTo("Endocrinologista");
		assertThat(EspecialidadeUtils.normalizar("Médico Psiquiatra")).isEqualTo("Psiquiatra");
	}

	@Test
	void normalizarRemovePrefixoMedicaFeminino() {
		assertThat(EspecialidadeUtils.normalizar("Médica Pediatra")).isEqualTo("Pediatra");
	}

	@Test
	void normalizarIgnoraAcentoCaixaNoPrefixo() {
		assertThat(EspecialidadeUtils.normalizar("medico Cardiologista")).isEqualTo("Cardiologista");
		assertThat(EspecialidadeUtils.normalizar("MÉDICO CARDIOLOGISTA")).isEqualTo("CARDIOLOGISTA");
	}

	@Test
	void normalizarPreservaGrafiaOriginalDoRestante() {
		assertThat(EspecialidadeUtils.normalizar("Médico Endocrinologista")).isEqualTo("Endocrinologista");
	}

	@Test
	void normalizarSemPrefixoDevolveTextoComTrim() {
		assertThat(EspecialidadeUtils.normalizar("Psiquiatra")).isEqualTo("Psiquiatra");
		assertThat(EspecialidadeUtils.normalizar("  Cardiologista  ")).isEqualTo("Cardiologista");
	}

	@Test
	void normalizarComNuloDevolveNulo() {
		assertThat(EspecialidadeUtils.normalizar(null)).isNull();
	}

	@Test
	void normalizarNaoRemovePrefixoNoMeioDoTexto() {
		// "Médico" no meio do nome não é removido — só quando é prefixo no início.
		assertThat(EspecialidadeUtils.normalizar("Auxiliar de Médico")).isEqualTo("Auxiliar de Médico");
	}
}
