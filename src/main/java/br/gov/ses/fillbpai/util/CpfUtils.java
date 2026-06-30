package br.gov.ses.fillbpai.util;

/**
 * Centraliza regras relacionadas ao CPF.
 */
public class CpfUtils {

    /**
     * Normaliza o CPF removendo pontos, hífens e qualquer
     * caractere não numérico.
     *
     * Exemplos:
     * "123.456.789-00" -> "12345678900"
     * "123 456 789 00" -> "12345678900"
     *
     * @param cpf CPF original
     * @return CPF apenas com números ou null
     */
    public static String normalizar(String cpf) {

        if (cpf == null || cpf.trim().isEmpty()) {
            return null;
        }

        return cpf.replaceAll("[^0-9]", "");
    }

    /**
     * Retorna true se o CPF, após normalização, tiver exatamente 11 dígitos.
     *
     * @param cpf CPF original (pode conter pontos/hífens)
     * @return true se válido (11 dígitos), false caso contrário
     */
    public static boolean isValido(String cpf) {
        String normalizado = normalizar(cpf);
        return normalizado != null && normalizado.length() == 11;
    }
}
