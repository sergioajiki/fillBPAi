package br.gov.ses.fillbpai.util;

/**
 * ============================================================
 * UTILITÁRIO DE CEP
 * ============================================================
 *
 * Centraliza regras relacionadas ao CEP.
 *
 * Possibilidades futuras:
 * - Validação de CEP (8 dígitos obrigatórios)
 * - Consulta via API (Correios / IBGE)
 * - Formatação (00000-000)
 *
 * Regra atual:
 * - Normalizar CEP removendo caracteres não numéricos
 */
public class CepUtils {

    /**
     * Normaliza o CEP removendo hífens, espaços e qualquer
     * caractere não numérico.
     *
     * Exemplos:
     * "79003-020" -> "79003020"
     * "79003 020" -> "79003020"
     * "79.003-020" -> "79003020"
     *
     * @param cep CEP original
     * @return CEP apenas com números ou null
     */
    public static String normalizar(String cep) {

        if (cep == null || cep.trim().isEmpty()) {
            return null;
        }

        return cep.replaceAll("[^0-9]", "");
    }

    /**
     * Retorna true se o CEP, após normalização, tiver exatamente 8 dígitos.
     *
     * @param cep CEP original (pode conter hífens/espaços)
     * @return true se válido (8 dígitos), false caso contrário
     */
    public static boolean isValido(String cep) {
        String normalizado = normalizar(cep);
        return normalizado != null && normalizado.length() == 8;
    }
}
