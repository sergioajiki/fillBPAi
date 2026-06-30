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
	 * - CNS null/vazio → AVISO, retorna CNS vazio (não lança exceção)
	 * - CNS com 15 dígitos → válido, sem avisos
	 * - CNS com mais de 15 dígitos → válido (formato legado), emite aviso
	 * - CNS com menos de 15 dígitos → AVISO, retorna CNS parcial (não lança exceção)
	 *
	 * @param cns valor bruto do CNS
	 * @return resultado com CNS normalizado e lista de avisos
	 */
	public static CnsResultado processar(String cns) {

		List<String> avisos = new ArrayList<>();

		String cnsLimpo = normalizar(cns);

		// CNS ausente — registrado com aviso (não bloqueia importação)
		if (cnsLimpo == null || cnsLimpo.trim().isEmpty()) {
			avisos.add("CNS do paciente não informado — registrado com aviso (CNS_INVALIDO)");
			return new CnsResultado("", avisos);
		}

		// CNS com menos de 15 dígitos — registrado com aviso (não bloqueia importação)
		if (cnsLimpo.length() < 15) {
			avisos.add("CNS inválido (apenas " + cnsLimpo.length()
					+ " dígitos, mínimo 15) — registrado com aviso (CNS_INVALIDO): " + cns);
			return new CnsResultado(cnsLimpo, avisos);
		}

		// CNS com mais de 15 dígitos — formato incomum, aceito com aviso
		if (cnsLimpo.length() > 15) {
			avisos.add("CNS com formato incomum (" + cnsLimpo.length()
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
