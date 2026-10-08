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
	void padronizarRemovePrefixoColocaEmMaiusculasEJuntaEspacos() {
		// Decisão 08/10/2026: "Cardiologia" e "CARDIOLOGIA" eram duas
		// especialidades (dois nós na árvore, duas folhas para o mesmo médico)
		assertThat(EspecialidadeUtils.padronizar("Cardiologia")).isEqualTo("CARDIOLOGIA");
		assertThat(EspecialidadeUtils.padronizar("  cardiologia  ")).isEqualTo("CARDIOLOGIA");
		assertThat(EspecialidadeUtils.padronizar("Médico Cardiologista")).isEqualTo("CARDIOLOGISTA");
		assertThat(EspecialidadeUtils.padronizar("Clínica   Médica")).isEqualTo("CLÍNICA MÉDICA");
		assertThat(EspecialidadeUtils.padronizar("Psicólogo")).isEqualTo("PSICÓLOGO");
	}

	@Test
	void padronizarComNuloOuVazioDevolveNulo() {
		assertThat(EspecialidadeUtils.padronizar(null)).isNull();
		assertThat(EspecialidadeUtils.padronizar("   ")).isNull();
	}

	@Test
	void usaProcedimentoFixoParaNutricionistaEPsicologoComOuSemPrefixoECaixa() {
		assertThat(EspecialidadeUtils.usaProcedimentoFixo("NUTRICIONISTA")).isTrue();
		assertThat(EspecialidadeUtils.usaProcedimentoFixo("nutricionista")).isTrue();
		assertThat(EspecialidadeUtils.usaProcedimentoFixo("Psicólogo")).isTrue();
		assertThat(EspecialidadeUtils.usaProcedimentoFixo("Médico Psicólogo")).isTrue();
		assertThat(EspecialidadeUtils.usaProcedimentoFixo("CARDIOLOGIA")).isFalse();
		assertThat(EspecialidadeUtils.usaProcedimentoFixo(null)).isFalse();
	}

	@Test
	void normalizarNaoRemovePrefixoNoMeioDoTexto() {
		// "Médico" no meio do nome não é removido — só quando é prefixo no início.
		assertThat(EspecialidadeUtils.normalizar("Auxiliar de Médico")).isEqualTo("Auxiliar de Médico");
	}
}
