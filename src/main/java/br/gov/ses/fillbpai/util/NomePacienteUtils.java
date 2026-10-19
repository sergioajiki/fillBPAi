package br.gov.ses.fillbpai.util;

import java.util.Locale;

/**
 * Nome do paciente (BPA-I seq 19, {@code prd-nmpac}, 30 posições) —
 * recomendações 29 e 30 (19/10/2026).
 * <p>
 * Usado pela análise, pela importação e pelo gerador (dados já gravados), para
 * o nome sair igual nos três.
 */
public class NomePacienteUtils {

	/** Tamanho do campo {@code prd-nmpac} no layout. */
	public static final int TAMANHO_BPA = 30;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private NomePacienteUtils() {
	}

	/**
	 * Nome padronizado: maiúsculas, sem espaços nas pontas e com espaços
	 * repetidos reduzidos a um. Acentos mantidos (ver Decisão 14 do runbook).
	 */
	public static String padronizar(String nome) {

		if (nome == null) {
			return null;
		}

		return nome.replace(' ', ' ').trim().replaceAll("\\s+", " ").toUpperCase(PT_BR);
	}

	/** Como o nome sai no BPA-I quando passa de 30 caracteres, ou {@code null} se couber. */
	public static String cortadoNoBpa(String nome) {

		String padronizado = padronizar(nome);

		if (padronizado == null || padronizado.length() <= TAMANHO_BPA) {
			return null;
		}

		return padronizado.substring(0, TAMANHO_BPA).trim();
	}

	/**
	 * Motivo de o nome parecer não ser um nome completo de pessoa, ou
	 * {@code null}: recém-nascido sem nome ("RN DE MARIA" — caso real, aceito), sem letras (-),
	 * com números, com símbolos ou com uma palavra só (TESTE, MARIA 2, -).
	 * Letras (com acento), espaço, hífen, apóstrofo e ponto são aceitos.
	 */
	public static String motivoSuspeito(String nome) {

		String padronizado = padronizar(nome);

		if (padronizado == null || padronizado.isEmpty()) {
			return null;
		}

		if (padronizado.equals("RN") || padronizado.startsWith("RN ")) {
			return "recém-nascido sem nome";
		}

		if (!padronizado.matches(".*\\p{L}.*") && !padronizado.matches(".*\\d.*")) {
			return "sem letras";
		}

		if (padronizado.matches(".*\\d.*")) {
			return "com números";
		}

		if (!padronizado.matches("[\\p{L} .'´`-]+")) {
			return "com símbolos";
		}

		if (!padronizado.contains(" ")) {
			return "com uma palavra só";
		}

		return null;
	}
}
