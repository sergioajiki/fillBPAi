package br.gov.ses.fillbpai.util;

import java.util.regex.Pattern;

/**
 * Normaliza o texto da especialidade do médico vindo da planilha.
 * <p>
 * Algumas planilhas trazem um prefixo "Médico "/"Médica " antes do nome real
 * da especialidade (ex.: "Médico Endocrinologista" em vez de apenas
 * "Endocrinologista"). Isso não é o mesmo problema tratado por
 * {@code StringUtils.separarEspecialidadeEMedico} (planilha legado com
 * especialidade e nome do médico combinados numa única célula) — aqui a
 * célula já é só a especialidade, só que com um prefixo redundante.
 * <p>
 * Usado tanto na importação ({@code AtendimentoProcessor}, para persistir o
 * valor já limpo) quanto na geração ({@code GeradorBPAiService}, como
 * proteção para atendimentos já persistidos antes desta normalização
 * existir — mesmo padrão de defesa em duas pontas já usado para raça/"99").
 */
public class EspecialidadeUtils {

	private static final Pattern PREFIXO_MEDICO = Pattern.compile("(?iu)^m[eé]dic[oa]\\s+");

	private EspecialidadeUtils() {
	}

	/**
	 * Remove o prefixo "Médico"/"Médica" do início do texto, se presente,
	 * preservando a grafia original do restante (só o prefixo é
	 * reconhecido por acento/caixa; o nome da especialidade não é alterado).
	 * Sem o prefixo, devolve o texto apenas com {@code trim()}. Nulo
	 * permanece nulo.
	 */
	public static String normalizar(String especialidade) {

		if (especialidade == null) {
			return null;
		}

		String trimmed = especialidade.trim();

		return PREFIXO_MEDICO.matcher(trimmed).replaceFirst("");
	}
}
