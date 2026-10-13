package br.gov.ses.fillbpai.util;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolve o código de raça/cor do paciente (2 dígitos) usado no BPA-I
 * (seq 21, {@code prd-raca}) a partir do texto livre da planilha.
 * <p>
 * Usado tanto por {@code ValidacaoPlanilhaService}/{@code AtendimentoProcessor}
 * (para detectar raça ausente ou não reconhecida — grafia incorreta) quanto
 * por {@code GeradorBPAiService} (para gerar o código no arquivo) — mesma
 * régua nos dois lugares.
 */
public class RacaUtils {

	private RacaUtils() {
	}

	/** Palavra inteira (sem acento, maiúscula) → código. */
	private static final Map<String, String> PALAVRAS = Map.ofEntries(
			Map.entry("BRANCA", "01"), Map.entry("BRANCO", "01"),
			Map.entry("PRETA", "02"), Map.entry("PRETO", "02"),
			Map.entry("PARDA", "03"), Map.entry("PARDO", "03"),
			Map.entry("AMARELA", "04"), Map.entry("AMARELO", "04"),
			Map.entry("INDIGENA", "05"), Map.entry("INDIO", "05"), Map.entry("INDIA", "05"));

	/** Negações: "Não Branca" não é Branca. */
	private static final Set<String> NEGACOES = Set.of("NAO", "SEM", "NENHUMA", "NENHUM");

	/**
	 * Resolve o código de 2 dígitos a partir do texto da raça, ou
	 * {@code null} quando em branco ou não reconhecido.
	 * <ul>
	 *   <li>Por <b>palavra inteira</b>, sem acento e caixa: Branca/Branco,
	 *       Preta/Preto, Parda/Pardo, Amarela/Amarelo, Indígena/Índio/Índia.
	 *       Antes era por trecho, e "Indeterminado"/"Indefinida"/"Indisponível"
	 *       viravam Indígena (05).</li>
	 *   <li>O próprio código do BPA: {@code 01}–{@code 05} (ou {@code 1}–{@code 5}).</li>
	 *   <li>Texto com mais de uma raça ({@code Preta/Parda}) ou com negação
	 *       ({@code Não Branca}) não é reconhecido — antes ficava com a
	 *       primeira raça encontrada.</li>
	 * </ul>
	 * O código "99" (Sem informação) nunca é resolvido: está riscado no layout
	 * oficial; ausência de raça é tratada como erro, não como "99".
	 */
	public static String resolverCodigo(String raca) {

		if (raca == null || raca.trim().isEmpty()) {
			return null;
		}

		String norm = TextoUtils.normalizar(raca).trim();

		if (norm.matches("0?[1-5]")) {
			return "0" + norm.charAt(norm.length() - 1);
		}

		Set<String> codigos = new LinkedHashSet<>();

		for (String palavra : norm.split("[^A-Z]+")) {
			if (NEGACOES.contains(palavra)) {
				return null;
			}
			String codigo = PALAVRAS.get(palavra);
			if (codigo != null) {
				codigos.add(codigo);
			}
		}

		return codigos.size() == 1 ? codigos.iterator().next() : null;
	}

	/** true quando a raça informada corresponde ao código 05 (Indígena). */
	public static boolean isIndigena(String raca) {
		return "05".equals(resolverCodigo(raca));
	}

	/**
	 * Mensagem para raça preenchida mas não reconhecida. "Negra/Negro" ganha
	 * explicação própria: o IBGE separa Preta e Parda (a população negra é a
	 * soma das duas), então não há conversão automática (decisão de 13/10/2026).
	 */
	public static String mensagemNaoReconhecida(String raca) {

		String texto = raca == null ? "" : raca.trim();
		String base = "Raça do paciente \"" + texto + "\" não reconhecida";

		if (TextoUtils.normalizar(texto).matches(".*\\bNEGR[AO]S?\\b.*")) {
			return base + " — o IBGE não usa \"Negra\": informe Preta ou Parda (a população negra é a soma"
					+ " das duas).";
		}

		return base + " — verifique a grafia na planilha (aceito: Branca, Preta, Parda, Amarela, Indígena,"
				+ " ou o código 01 a 05; um valor só por célula).";
	}
}
