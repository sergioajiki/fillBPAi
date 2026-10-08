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

    /**
     * CPF falso de preenchimento: 11 dígitos todos iguais ({@code 00000000000},
     * {@code 11111111111}...). Erro bloqueante (decisão de 08/10/2026) — como o
     * CPF é a chave do paciente, todos os atendimentos com o mesmo CPF falso
     * viravam um único paciente. Paciente sem CPF vai pela coluna
     * "Paciente sem CPF" = Sim, com o CPF vazio.
     */
    public static boolean isFalso(String cpf) {
        String normalizado = normalizar(cpf);
        return normalizado != null && normalizado.length() == 11 && normalizado.chars().distinct().count() == 1;
    }

    /**
     * Confere os dois dígitos verificadores do CPF (algoritmo oficial, módulo
     * 11). Pega o CPF com um dígito digitado errado — que antes passava e podia
     * coincidir com o CPF de outra pessoa. Atenção: CPF de dígitos todos iguais
     * passa no cálculo; use {@link #isFalso} para esse caso.
     *
     * @return {@code true} se tiver 11 dígitos e os verificadores corretos
     */
    public static boolean isDvValido(String cpf) {

        String n = normalizar(cpf);

        if (n == null || n.length() != 11) {
            return false;
        }

        return digitoVerificador(n, 9) == n.charAt(9) - '0'
                && digitoVerificador(n, 10) == n.charAt(10) - '0';
    }

    /** Dígito verificador calculado sobre os {@code quantidade} primeiros dígitos. */
    private static int digitoVerificador(String cpf, int quantidade) {

        int soma = 0;

        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }

        int resto = (soma * 10) % 11;

        return resto == 10 ? 0 : resto;
    }

    /** Prefixo da chave interna de paciente sem CPF (ver {@link #chaveSemCpf}). */
    private static final String PREFIXO_SEM_CPF = "SC";

    /**
     * Chave interna para paciente sem CPF ("Paciente sem CPF" = Sim): o CPF é a
     * chave do paciente no banco (obrigatória, única, até 14 caracteres), então
     * esses pacientes são identificados por {@code "SC"} + 12 caracteres
     * derivados do nome (sem acento/caixa/espaços extras) e da data de
     * nascimento. Estável — reimportar a mesma planilha encontra o mesmo
     * paciente. Nunca vai para o BPA-I: o gerador trata a chave como CPF vazio.
     */
    public static String chaveSemCpf(String nome, java.time.LocalDate dataNascimento) {

        String base = TextoUtils.normalizar(nome).replaceAll("\\s+", " ")
                + "|" + (dataNascimento != null ? dataNascimento : "");

        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return PREFIXO_SEM_CPF + java.util.HexFormat.of().formatHex(hash).substring(0, 12).toUpperCase();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** {@code true} se o valor é uma chave interna de paciente sem CPF, e não um CPF. */
    public static boolean isChaveSemCpf(String cpf) {
        return cpf != null && cpf.length() == 14 && cpf.startsWith(PREFIXO_SEM_CPF);
    }
}
