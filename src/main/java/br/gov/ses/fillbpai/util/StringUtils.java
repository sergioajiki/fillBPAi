package br.gov.ses.fillbpai.util;

public class StringUtils {
    /**
     * Separa código e nome do estabelecimento.
     *
     * Exemplo de entrada:
     * "123456 - HOSPITAL MUNICIPAL"
     *
     * Retorna:
     * [0] -> código
     * [1] -> nome
     */
    public static String[] separarCodigoENome(String valor) {

        if (valor == null || !valor.contains("-")) {
            return new String[]{null, valor};
        }

        String[] partes = valor.split("-", 2);

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

        if (valor == null || !valor.contains("-")) {
            return new String[]{valor, null};
        }

        String[] partes = valor.split("-", 2);

        String especialidade = partes[0].trim();
        String medico = partes[1].trim();

        return new String[]{especialidade, medico};
    }
}
