package br.gov.ses.fillbpai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * UTILITÁRIO DE VALIDAÇÃO DE CNS
 * ============================================================
 *
 * Centraliza validações relacionadas ao CNS (Cartão Nacional de Saúde).
 *
 * Regras:
 * - CNS padrão atual: exatamente 15 dígitos numéricos
 * - CNS legado (antigo): pode ter mais de 15 dígitos — aceito com aviso
 * - CNS ausente: erro (campo obrigatório)
 *
 * Novas regras podem ser adicionadas aqui futuramente
 * sem alterar código de importação ou processamento.
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
	 * Processa o CNS: normaliza e valida.
	 * Retorna o resultado contendo o CNS limpo e eventuais avisos.
	 *
	 * Regras de validação:
	 * - CNS null/vazio → erro (IllegalArgumentException)
	 * - CNS com 15 dígitos → válido, sem avisos
	 * - CNS com mais de 15 dígitos → válido (formato legado), emite aviso
	 * - CNS com menos de 15 dígitos → erro (formato inválido)
	 *
	 * @param cns valor bruto do CNS
	 * @return resultado com CNS normalizado e lista de avisos
	 * @throws IllegalArgumentException se CNS for nulo, vazio ou com menos de 15 dígitos
	 */
	public static CnsResultado processar(String cns) {

		List<String> avisos = new ArrayList<>();

		String cnsLimpo = normalizar(cns);

		// CNS ausente — erro bloqueante
		if (cnsLimpo == null || cnsLimpo.trim().isEmpty()) {
			throw new IllegalArgumentException("CNS do paciente não informado.");
		}

		// CNS com menos de 15 dígitos — formato inválido
		if (cnsLimpo.length() < 15) {
			throw new IllegalArgumentException(
					"CNS inválido. Deve conter pelo menos 15 dígitos: " + cns
			);
		}

		// CNS com mais de 15 dígitos — formato legado (antigo), aceito com aviso
		if (cnsLimpo.length() > 15) {
			avisos.add("CNS com formato legado (" + cnsLimpo.length()
					+ " dígitos, esperado 15): " + cnsLimpo);
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
