package br.gov.ses.fillbpai.util;

/**
 * Classe utilitária para manipulação de Strings.
 */
public class StringUtils {

    /**
     * Separa código e nome do estabelecimento.
     *
     * Entrada esperada:
     * "123456 - HOSPITAL MUNICIPAL"
     *
     * Retorno:
     * [0] -> código
     * [1] -> nome
     *
     * Caso não exista hífen:
     * código = null
     * nome = valor original
     */
    public static String[] separarCodigoENome(String valor) {

        if (valor == null || valor.trim().isEmpty()) {
            return new String[]{null, null};
        }

        // Garante que o split ocorra apenas na primeira ocorrência
        String[] partes = valor.split("\\s*-\\s*", 2);

        if (partes.length < 2) {
            return new String[]{null, valor.trim()};
        }

        String codigo = partes[0].trim();
        String nome = partes[1].trim();

        return new String[]{codigo, nome};
    }
}
