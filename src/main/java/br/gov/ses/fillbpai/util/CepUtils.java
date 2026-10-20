package br.gov.ses.fillbpai.util;

/**
 * Regras do CEP do paciente (BPA-I seq 29), usadas pela análise e pela
 * importação: normalização (só dígitos), tamanho (8 dígitos, sem completar
 * zeros), CEP de preenchimento (dígitos iguais) e CEP de outro estado para
 * município de MS. A busca do IBGE pelo CEP fica em {@link IbgeUtils}.
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

    /** CEP de 8 dígitos todos iguais (00000000, 99999999) — CEP de preenchimento (16/10/2026). */
    public static boolean isFalso(String cep) {
        String normalizado = normalizar(cep);
        return normalizado != null && normalizado.length() == 8 && normalizado.chars().distinct().count() == 1;
    }

    /**
     * Mensagem de CEP com tamanho errado; com 7 dígitos orienta formatar a
     * coluna como texto (o Excel tira o zero à esquerda de CEP digitado como
     * número). Não se completa com zero — mesma decisão do CPF.
     */
    public static String mensagemTamanhoInvalido(String cep) {
        String normalizado = normalizar(cep);
        int digitos = normalizado != null ? normalizado.length() : 0;
        return "CEP com tamanho inválido (" + digitos + " dígitos, esperado 8): " + (cep == null ? "" : cep.trim())
                + (digitos == 7 ? " — se o CEP começa com zero, formate a coluna como texto e digite os 8 dígitos" : "");
    }

    /**
     * CEP válido (8 dígitos) que não é de Mato Grosso do Sul (CEPs de MS
     * começam com 79) para um município encontrado na tabela de municípios de
     * MS. Quando o município está na tabela o CEP não é usado para achar o
     * IBGE, então um CEP de outro estado passava sem conferência (decisão de
     * 16/10/2026: aviso).
     *
     * @param cep       CEP como veio da planilha
     * @param municipio município como veio da planilha
     */
    public static boolean isForaDeMsComMunicipioDeMs(String cep, String municipio) {
        String normalizado = normalizar(cep);
        return normalizado != null && normalizado.length() == 8 && !normalizado.startsWith("79")
                && municipio != null && !municipio.isBlank() && IbgeUtils.buscarPorNome(municipio) != null;
    }

    /** Mensagem do aviso de CEP fora de MS — análise e log de importação. */
    public static String mensagemForaDeMs(String cep, String municipio) {
        return "CEP " + normalizar(cep) + " não é de Mato Grosso do Sul (os CEPs de MS começam com 79), mas o"
                + " município \"" + municipio.trim() + "\" é de MS — confira o CEP e o município";
    }
}
