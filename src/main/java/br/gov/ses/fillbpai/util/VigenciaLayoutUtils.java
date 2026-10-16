package br.gov.ses.fillbpai.util;

import java.time.LocalDate;

/**
 * Vigência dos campos novos do layout BPA-I (data/Layout_Exportacao_BPA_2026.pdf):
 * {@code prd_situacao_rua} vale a partir da competência 12/2024 e
 * {@code prd_sem_cpf} a partir da competência 07/2026. Para atendimentos de
 * competência anterior o campo vai em branco no arquivo (decisão 26, 16/10/2026).
 * <p>
 * A competência do registro é o mês da data do atendimento.
 */
public class VigenciaLayoutUtils {

	public static final LocalDate INICIO_SITUACAO_RUA = LocalDate.of(2024, 12, 1);
	public static final LocalDate INICIO_SEM_CPF = LocalDate.of(2026, 7, 1);

	private VigenciaLayoutUtils() {
	}

	/** true quando a data é anterior à competência 12/2024 (data nula = vigente). */
	public static boolean isAntesDaSituacaoRua(LocalDate dataAtendimento) {
		return dataAtendimento != null && dataAtendimento.isBefore(INICIO_SITUACAO_RUA);
	}

	/** true quando a data é anterior à competência 07/2026 (data nula = vigente). */
	public static boolean isAntesDoSemCpf(LocalDate dataAtendimento) {
		return dataAtendimento != null && dataAtendimento.isBefore(INICIO_SEM_CPF);
	}

	public static String mensagemSituacaoRua(String competencia) {
		return "Atendimento da competência " + competencia + ": o campo \"Situação de Rua\" "
				+ "só existe no BPA-I a partir da competência 12/2024 — o \"Sim\" da planilha será gerado em branco.";
	}

	public static String mensagemSemCpf(String competencia) {
		return "Atendimento da competência " + competencia + ": o campo \"Paciente sem CPF\" "
				+ "só existe no BPA-I a partir da competência 07/2026 — o \"Sim\" da planilha será gerado em branco.";
	}

	/** Competência MM/aaaa da data do atendimento. */
	public static String competencia(LocalDate data) {
		return String.format("%02d/%d", data.getMonthValue(), data.getYear());
	}
}
