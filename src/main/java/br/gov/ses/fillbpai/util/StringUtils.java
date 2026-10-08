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

    /** Código do estabelecimento: só dígitos, até 10 (tamanho da coluna {@code estabelecimento.codigo}). */
    private static final java.util.regex.Pattern CODIGO_ESTABELECIMENTO =
            java.util.regex.Pattern.compile("^\\s*(\\d{1,10})\\s*(?:" + SEPARADOR_REGEX + "\\s*(.*?))?\\s*$",
                    java.util.regex.Pattern.DOTALL);

    /**
     * Separa a célula de estabelecimento {@code "CÓDIGO - NOME"} em
     * {@code {codigo, nome}}. O código é sempre numérico (decisão de
     * 08/10/2026): só vale como código o que vem antes do primeiro separador
     * se for <b>só dígitos</b> (até 10).
     * <ul>
     *   <li>{@code "12345 - HOSPITAL"} → {@code {"12345", "HOSPITAL"}} (hífens no nome são mantidos)</li>
     *   <li>{@code "1234567"} ou {@code "12345 -"} → {@code {"1234567", null}} (só o código)</li>
     *   <li>{@code "HOSPITAL SAO JOSE - UNIDADE 2"}, {@code "UBS - CENTRO"} →
     *       {@code {null, célula inteira}} — texto antes do hífen é parte do nome. Antes virava
     *       "código": estourava a coluna (importação cancelada) ou criava estabelecimento falso.</li>
     * </ul>
     */
    /** Só separadores ou código zero (com ou sem separador): marcadores de "sem informação". */
    private static final java.util.regex.Pattern ESTABELECIMENTO_NAO_INFORMADO =
            java.util.regex.Pattern.compile("^\\s*(0+\\s*)?(" + SEPARADOR_REGEX + "\\s*)*$");

    /**
     * Célula de estabelecimento que, na prática, não informa nada: vazia, só
     * espaços, só o separador ({@code -}, {@code —}) ou código zero
     * ({@code 0}, {@code 000 -}). Tratada como "não informado" (aviso
     * ESTABELECIMENTO_AUSENTE) — antes {@code -} virava um nome e {@code 0}
     * um código.
     */
    public static boolean isEstabelecimentoNaoInformado(String valor) {
        return valor == null || ESTABELECIMENTO_NAO_INFORMADO.matcher(valor).matches();
    }

    public static String[] separarCodigoENome(String valor) {

        if (valor == null) {
            return new String[]{null, null};
        }

        java.util.regex.Matcher m = CODIGO_ESTABELECIMENTO.matcher(valor);

        if (!m.matches()) {
            return new String[]{null, valor.trim()};
        }

        String nome = m.group(2);

        return new String[]{m.group(1), nome == null || nome.isBlank() ? null : nome.trim()};
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
