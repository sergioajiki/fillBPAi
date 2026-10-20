package br.gov.ses.fillbpai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Validações do CNS (Cartão Nacional de Saúde) do paciente, usadas pela
 * análise e pela importação.
 * <ul>
 *   <li>O CNS tem sempre 15 dígitos; o dígito verificador segue o algoritmo do
 *       Ministério da Saúde ({@link #isDvValido}).</li>
 *   <li>Paciente com CPF: o CNS é opcional (não vai para o BPA-I); vazio não
 *       gera aviso, inválido gera aviso e não é gravado ({@link #processar}).</li>
 *   <li>Paciente sem CPF: o CNS é obrigatório e válido — é o documento do
 *       paciente no BPA-I e a chave do cadastro ({@link #isCnsSemCpfValido}).</li>
 * </ul>
 */
public class CnsUtils {

	/**
	 * Remove caracteres não numéricos do CNS.
	 *
	 * @param cns valor bruto do CNS (pode conter pontos, traços, espaços)
	 * @return CNS contendo apenas dígitos, ou null se entrada for null
	 */
	public static String normalizar(String cns) {

		if (cns == null) {
			return null;
		}

		return cns.replaceAll("[^0-9]", "");
	}

	/**
	 * CNS de paciente sem CPF ("Paciente sem CPF" = Sim): é o documento que
	 * identifica o paciente no BPA-I (seq 10, {@code prd-cnspac}), então é
	 * obrigatório com exatamente 15 dígitos (decisão de 09/10/2026).
	 *
	 * @param cns valor bruto da célula (pontos/espaços são ignorados)
	 */
	public static boolean isCnsSemCpfValido(String cns) {
		String limpo = normalizar(cns);
		return limpo != null && limpo.length() == 15 && isDvValido(limpo);
	}

	/**
	 * Confere o dígito verificador do CNS (algoritmo do Ministério da Saúde,
	 * sem consulta externa): 15 dígitos, primeiro dígito 1 ou 2 (definitivo)
	 * ou 7, 8, 9 (provisório), e soma dos dígitos multiplicados pelos pesos
	 * 15 a 1 divisível por 11. O layout do BPA-I pede "CNS do paciente com
	 * dígito verificador válido" (seq 10).
	 *
	 * @param cns valor bruto ou normalizado (o que não é dígito é ignorado)
	 * @return {@code false} também para vazio ou tamanho diferente de 15
	 */
	public static boolean isDvValido(String cns) {

		String limpo = normalizar(cns);

		if (limpo == null || limpo.length() != 15 || "12789".indexOf(limpo.charAt(0)) < 0) {
			return false;
		}

		int soma = 0;
		for (int i = 0; i < 15; i++) {
			soma += (limpo.charAt(i) - '0') * (15 - i);
		}

		return soma % 11 == 0;
	}

	/** Motivo do CNS de paciente sem CPF não ser aceito — para as mensagens de erro. */
	public static String motivoCnsSemCpfInvalido(String cns) {
		String limpo = normalizar(cns);
		if (limpo == null || limpo.isEmpty()) {
			return "CNS não informado";
		}
		if (limpo.length() != 15) {
			return "CNS com " + limpo.length() + " dígitos";
		}
		return "CNS com dígito verificador inválido";
	}

	/** Mensagem do CNS com tamanho diferente de 15 dígitos (análise e importação). */
	public static String mensagemTamanhoInvalido(String cnsNormalizado) {
		return "CNS do paciente com " + cnsNormalizado.length() + " dígitos (" + cnsNormalizado
				+ ") — o CNS tem 15 dígitos; não será gravado";
	}

	/**
	 * Processa o CNS do paciente: normaliza (só dígitos) e valida. Nunca lança
	 * exceção — paciente sem CPF, que exige o CNS, é conferido antes
	 * ({@link #isCnsSemCpfValido}).
	 * <ul>
	 *   <li>vazio → CNS vazio, sem aviso (Decisão 13: com CPF o CNS não vai para o BPA-I);</li>
	 *   <li>15 dígitos → gravado; dígito verificador errado → aviso CNS_DV_INVALIDO;</li>
	 *   <li>sem 15 dígitos (a menos ou a mais) → aviso CNS_INVALIDO e CNS vazio:
	 *       o valor errado não é gravado (recomendação 26, 19/10/2026).</li>
	 * </ul>
	 *
	 * @param cns valor bruto do CNS
	 * @return resultado com o CNS a gravar e a lista de avisos
	 */
	public static CnsResultado processar(String cns) {

		List<String> avisos = new ArrayList<>();

		String cnsLimpo = normalizar(cns);

		// CNS ausente — sem aviso (Decisão 13, 16/10/2026): com CPF o CNS não vai
		// para o BPA-I; paciente sem CPF já é barrado antes (CNS_OBRIGATORIO_SEM_CPF)
		if (cnsLimpo == null || cnsLimpo.trim().isEmpty()) {
			return new CnsResultado("", avisos);
		}

		// CNS sem 15 dígitos (a menos ou a mais) — inválido: aviso e não é gravado
		// (recomendação 26, 19/10/2026 — antes o de mais dígitos era "formato
		// incomum" e era gravado; o CNS tem sempre 15 dígitos)
		if (cnsLimpo.length() != 15) {
			avisos.add(mensagemTamanhoInvalido(cnsLimpo) + " (CNS_INVALIDO)");
			return new CnsResultado("", avisos);
		}

		if (!isDvValido(cnsLimpo)) {
			// 15 dígitos com dígito verificador errado — aceito com aviso
			// (paciente sem CPF é barrado antes, em AtendimentoProcessor)
			avisos.add("CNS do paciente com dígito verificador inválido (CNS_DV_INVALIDO): " + cnsLimpo
					+ " — confira se algum dígito foi digitado errado");
		}

		return new CnsResultado(cnsLimpo, avisos);
	}

	/**
	 * Resultado do processamento de CNS.
	 * Contém o valor normalizado e uma lista de avisos (warnings) gerados.
	 */
	public static class CnsResultado {

		private final String cns;
		private final List<String> avisos;

		public CnsResultado(String cns, List<String> avisos) {
			this.cns = cns;
			this.avisos = avisos;
		}

		/** CNS normalizado (somente dígitos). */
		public String getCns() {
			return cns;
		}

		/** Lista de avisos gerados durante a validação. Vazia se tudo OK. */
		public List<String> getAvisos() {
			return avisos;
		}
	}
}
