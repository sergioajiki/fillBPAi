package br.gov.ses.fillbpai.util;

/**
 * Normaliza o telefone do paciente para o BPA-I (seq 35, {@code prd_ddtel_pcnte},
 * 11 posições NUM, opcional): DDD + número, só dígitos.
 * <p>
 * Antes o gerador tirava o que não era dígito e ficava com os 11 primeiros:
 * {@code +55 67 99999-9999} virava {@code 55679999999}, dois telefones na
 * célula viravam um número inexistente, e uma célula com três telefones
 * passava dos 20 caracteres da coluna e cancelava a importação.
 */
public class TelefoneUtils {

	private TelefoneUtils() {
	}

	/** Situação do telefone da célula. */
	public enum Situacao { VAZIO, VALIDO, SEM_DDD, INVALIDO }

	/**
	 * @param situacao situação do telefone
	 * @param numero   DDD + número (10 dígitos fixo, 11 celular) quando {@link Situacao#VALIDO}; senão {@code null}
	 */
	public record Resultado(Situacao situacao, String numero) {
	}

	/**
	 * Lê o <b>primeiro</b> telefone da célula (separadores {@code /}, {@code ;},
	 * {@code ,}, {@code |}, " e ", " ou "), tira o DDI {@code +55}/{@code 55} e o
	 * {@code 0} de longa distância e confere o tamanho:
	 * <ul>
	 *   <li>10 ou 11 dígitos → {@link Situacao#VALIDO}</li>
	 *   <li>8 ou 9 dígitos → {@link Situacao#SEM_DDD} (o DDD não pode ser adivinhado)</li>
	 *   <li>outro tamanho ou sem dígitos → {@link Situacao#INVALIDO}</li>
	 *   <li>célula vazia → {@link Situacao#VAZIO}</li>
	 * </ul>
	 */
	public static Resultado normalizar(String celula) {

		if (celula == null || celula.isBlank()) {
			return new Resultado(Situacao.VAZIO, null);
		}

		String primeiro = celula.trim().split("[/;,|]|\\s+(?i:e|ou)\\s+")[0];
		boolean comDdi = primeiro.trim().startsWith("+");
		String digitos = primeiro.replaceAll("\\D", "");

		// DDI do Brasil: "+55 ..." ou 12/13 dígitos começando com 55
		if (digitos.startsWith("55") && (comDdi || digitos.length() == 12 || digitos.length() == 13)) {
			digitos = digitos.substring(2);
		}

		// Zero de longa distância antes do DDD ("0 67 3333-3333"); DDD nunca começa com 0
		if (digitos.startsWith("0") && (digitos.length() == 11 || digitos.length() == 12)) {
			digitos = digitos.substring(1);
		}

		if (digitos.length() == 10 || digitos.length() == 11) {
			return new Resultado(Situacao.VALIDO, digitos);
		}

		if (digitos.length() == 8 || digitos.length() == 9) {
			return new Resultado(Situacao.SEM_DDD, null);
		}

		return new Resultado(Situacao.INVALIDO, null);
	}
}
