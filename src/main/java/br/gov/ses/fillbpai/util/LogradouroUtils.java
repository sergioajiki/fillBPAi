package br.gov.ses.fillbpai.util;

/**
 * Utilitário para identificar o tipo de logradouro a partir do campo endereço.
 *
 * Estratégia: verifica se o endereço começa com um prefixo reconhecido
 * (rua, avenida, travessa e variantes), deriva o código do logradouro BPA-I
 * e retorna o endereço sem o prefixo.
 *
 * Códigos BPA-I:
 *   081 — Rua
 *   008 — Avenida
 *   100 — Travessa
 *
 * Se nenhum prefixo for reconhecido, codLogradouro retorna null
 * e o endereço é devolvido sem alteração.
 */
public class LogradouroUtils {

	public static LogradouroResultado resolver(String enderecoRaw) {

		if (enderecoRaw == null || enderecoRaw.isBlank()) {
			return new LogradouroResultado(null, enderecoRaw);
		}

		String trim  = enderecoRaw.trim();
		String upper = trim.toUpperCase();

		// ── Travessa (verificar antes de "TV" para evitar match parcial) ──
		if (upper.startsWith("TRAVESSA ") || upper.equals("TRAVESSA")) {
			return new LogradouroResultado("100", trim.substring("TRAVESSA".length()).trim());
		}
		if (upper.startsWith("TRAV. ") || upper.equals("TRAV.")) {
			return new LogradouroResultado("100", trim.substring("TRAV.".length()).trim());
		}
		if (upper.startsWith("TV ") || upper.equals("TV")) {
			return new LogradouroResultado("100", trim.substring("TV".length()).trim());
		}

		// ── Avenida (verificar "AVENIDA" e "AV." antes de "AV") ──
		if (upper.startsWith("AVENIDA ") || upper.equals("AVENIDA")) {
			return new LogradouroResultado("008", trim.substring("AVENIDA".length()).trim());
		}
		if (upper.startsWith("AV. ") || upper.equals("AV.")) {
			return new LogradouroResultado("008", trim.substring("AV.".length()).trim());
		}
		if (upper.startsWith("AV ") || upper.equals("AV")) {
			return new LogradouroResultado("008", trim.substring("AV".length()).trim());
		}

		// ── Rua ──
		if (upper.startsWith("RUA ") || upper.equals("RUA")) {
			return new LogradouroResultado("081", trim.substring("RUA".length()).trim());
		}

		// Prefixo não reconhecido — devolve original sem alteração
		return new LogradouroResultado(null, trim);
	}

	public static class LogradouroResultado {

		private final String codLogradouro;
		private final String endereco;

		public LogradouroResultado(String codLogradouro, String endereco) {
			this.codLogradouro = codLogradouro;
			this.endereco      = endereco;
		}

		/** Código BPA-I do tipo de logradouro, ou null se não reconhecido. */
		public String getCodLogradouro() {
			return codLogradouro;
		}

		/** Endereço sem o prefixo do tipo de logradouro. */
		public String getEndereco() {
			return endereco;
		}
	}
}
