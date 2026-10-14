package br.gov.ses.fillbpai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai o código CID-10 da célula "CID da consulta" (BPA-I seq 13,
 * {@code prd-cid}, 4 posições ALFA).
 * <p>
 * Antes o gerador só tirava o que não era letra ou número e cortava em 4
 * caracteres: {@code "CID I10"} virava {@code CIDI}, {@code "I10 - Hipertensão"}
 * virava {@code I10H}, {@code "I10, E11"} virava {@code I10E} — CIDs
 * inexistentes, que o BPA critica e descarta. E uma célula com a descrição
 * passava do tamanho da coluna no banco e cancelava a importação inteira.
 */
public class CidUtils {

	private CidUtils() {
	}

	/**
	 * Código CID-10: letra + 2 dígitos + subcategoria opcional (dígito ou X),
	 * com ou sem ponto ({@code I10}, {@code I10.9}, {@code I109}). Não pode
	 * estar colado a outra letra ou dígito ({@code COVID19} não é CID).
	 */
	private static final Pattern CODIGO = Pattern.compile("(?<![A-Z0-9])([A-Z])(\\d{2})(?:\\.?([0-9X]))?(?![A-Z0-9])");

	/**
	 * Todos os códigos CID-10 encontrados na célula, já no formato do BPA-I
	 * (sem ponto, maiúsculas: {@code I10}, {@code I109}).
	 */
	public static List<String> codigos(String celula) {

		List<String> codigos = new ArrayList<>();

		if (celula == null || celula.isBlank()) {
			return codigos;
		}

		Matcher m = CODIGO.matcher(TextoUtils.normalizar(celula));

		while (m.find()) {
			String codigo = m.group(1) + m.group(2) + (m.group(3) != null ? m.group(3) : "");
			if (!codigos.contains(codigo)) {
				codigos.add(codigo);
			}
		}

		return codigos;
	}

	/**
	 * O código CID-10 da célula, quando há exatamente um ({@code "CID I10"},
	 * {@code "I10 - Hipertensão"} → {@code I10}); {@code null} se vazia, sem
	 * código ({@code "Hipertensão"}) ou com mais de um ({@code "I10, E11"}).
	 */
	public static String extrair(String celula) {
		List<String> codigos = codigos(celula);
		return codigos.size() == 1 ? codigos.get(0) : null;
	}

	/** Motivo de a célula preenchida não ter um CID aceito — para as mensagens de erro. */
	public static String motivoInvalido(String celula) {
		return codigos(celula).size() > 1
				? "mais de um CID na mesma célula — informe só o CID principal"
				: "nenhum código CID-10 encontrado (formato: letra + 2 dígitos, ex.: I10 ou I10.9)";
	}
}
