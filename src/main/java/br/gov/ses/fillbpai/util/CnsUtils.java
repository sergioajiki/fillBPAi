package br.gov.ses.fillbpai.util;

/**
 * ============================================================
 * UTILITÁRIO DE VALIDAÇÃO DE CNS
 * ============================================================
 *
 * Centraliza validações relacionadas ao CNS.
 * Novas regras podem ser adicionadas aqui futuramente
 * sem alterar código de importação ou processamento.
 */
public class CnsUtils {

    /**
     * Remove caracteres não numéricos.
     */
    public static String normalizar(String cns) {

        if (cns == null) {
            return null;
        }

        return cns.replaceAll("[^0-9]", "");
    }

    /**
     * Valida CNS (15 dígitos).
     */
    public static void validarCns(String cns) {

        if (cns == null || cns.trim().isEmpty()) {
            throw new IllegalArgumentException("CNS do paciente não informado.");
        }

        String cnsLimpo = normalizar(cns);

        if (cnsLimpo.length() != 15) {
            throw new IllegalArgumentException(
                    "CNS inválido. Deve conter 15 dígitos: " + cns
            );
        }
    }

    /**
     * Método completo:
     * normaliza + valida + retorna CNS pronto.
     */
    public static String processar(String cns) {

        String cnsLimpo = normalizar(cns);

        validarCns(cnsLimpo);

        return cnsLimpo;
    }
}