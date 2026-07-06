package br.gov.ses.fillbpai.util;

public class StringUtils {
    /**
     * ============================================================
     * LIMITAR TAMANHO DE STRING
     * ============================================================
     *
     * Regra:
     * - Se null → retorna null
     * - Se menor que o tamanho → retorna normal
     * - Se maior → corta no limite
     *
     * Uso:
     * - Banco de dados (VARCHAR limitado)
     * - Layouts fixos (BPA, arquivos, etc)
     *
     * Exemplo:
     * limitarTamanho("Rua ABC", 30) -> "Rua ABC"
     * limitarTamanho("Texto muito grande...", 10) -> "Texto muit"
     */
    public static String limitarTamanho(String valor, int tamanhoMaximo) {

        if (valor == null) {
            return null;
        }

        valor = valor.trim();

        if (valor.length() <= tamanhoMaximo) {
            return valor;
        }

        return valor.substring(0, tamanhoMaximo);
    }

    /**
     * ============================================================
     * LIMITAR TAMANHO + DEFAULT VAZIO
     * ============================================================
     *
     * Variante segura para evitar null em campos de banco.
     */
    public static String limitarTamanhoOuVazio(String valor, int tamanhoMaximo) {

        if (valor == null) {
            return "";
        }

        valor = valor.trim();

        if (valor.length() <= tamanhoMaximo) {
            return valor;
        }

        return valor.substring(0, tamanhoMaximo);
    }
    /**
     * Separador aceito entre código/especialidade e nome: hífen comum ou
     * variantes que o Word/Excel costumam gerar por autocorreção
     * (en dash "–" e em dash "—") quando o texto é digitado ou colado
     * de outra fonte.
     */
    private static final String SEPARADOR_REGEX = "[-–—]";

    public static String[] separarCodigoENome(String valor) {

        if (valor == null || !valor.matches(".*" + SEPARADOR_REGEX + ".*")) {
            return new String[]{null, valor};
        }

        String[] partes = valor.split(SEPARADOR_REGEX, 2);

        String codigo = partes[0].trim();
        String nome = partes[1].trim();

        return new String[]{codigo, nome};
    }

    /**
     * Separa especialidade e nome do médico.
     *
     * Exemplo de entrada:
     * "CARDIOLOGIA - JOÃO DA SILVA"
     *
     * Retorna:
     * [0] -> especialidade
     * [1] -> nome do médico
     */
    public static String[] separarEspecialidadeEMedico(String valor) {

        if (valor == null || !valor.matches(".*" + SEPARADOR_REGEX + ".*")) {
            return new String[]{valor, null};
        }

        String[] partes = valor.split(SEPARADOR_REGEX, 2);

        String especialidade = partes[0].trim();
        String medico = partes[1].trim();

        return new String[]{especialidade, medico};
    }
}
