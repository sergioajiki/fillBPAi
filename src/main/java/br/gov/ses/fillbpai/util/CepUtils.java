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
}
