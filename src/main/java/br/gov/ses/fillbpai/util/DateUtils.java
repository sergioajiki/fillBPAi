package br.gov.ses.fillbpai.util;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Classe utilitária responsável por conversões de datas.
 * Centraliza regras de parsing para evitar duplicação de código.
 */
public class DateUtils {

    /** Idade máxima aceita no BPA-I (prd-idade: "0 a 130 anos"). */
    public static final int IDADE_MAXIMA = 130;

    /**
     * Formatos aceitos, em modo <b>estrito</b> (decisão de 14/10/2026):
     * <ul>
     *   <li>{@code d/M/aaaa} — dia e mês com ou sem zero ({@code 01/01/1990},
     *       {@code 1/1/1990})</li>
     *   <li>{@code aaaa-MM-dd} — data de célula formatada como data no Excel</li>
     *   <li>{@code d-M-aaaa}</li>
     * </ul>
     * Estrito: data impossível ({@code 31/02/1990}, {@code 31/04/2024}) é
     * recusada — antes o Java ajustava para o último dia do mês, em silêncio.
     * Ano sempre com 4 dígitos: {@code 01/01/30} pode ser 1930 ou 2030.
     */
    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT)
    );

    /**
     * Converte uma String para LocalDate aceitando múltiplos formatos.
     *
     * @param dataStr String contendo a data
     * @return LocalDate convertido
     * @throws IllegalArgumentException se não for possível converter
     */
    public static LocalDate parse(String dataStr) {

        if (dataStr == null || dataStr.isBlank()) {
            throw new IllegalArgumentException("Data vazia");
        }

        for (DateTimeFormatter formatter : FORMATOS) {
            try {
                return LocalDate.parse(dataStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("Data inválida: " + dataStr);
    }

    private static final DateTimeFormatter FORMATO_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Data no formato das mensagens ({@code 15/03/2026}); {@code null} se nula. */
    public static String formatar(LocalDate data) {
        return data == null ? null : data.format(FORMATO_BR);
    }

    /**
     * Valor da célula para as mensagens: a data em {@code dd/MM/aaaa} quando a
     * célula é reconhecida como data (célula de data do Excel chega como
     * {@code 2026-03-15}), senão o texto como veio.
     */
    public static String formatarParaMensagem(String valor) {
        if (valor == null) {
            return "";
        }
        try {
            return formatar(parse(valor));
        } catch (IllegalArgumentException e) {
            return valor.trim();
        }
    }

    /**
     * Idade em anos completos na data do atendimento (prd-idade do BPA-I) —
     * não na data de hoje: a remessa pode ser gerada meses depois.
     *
     * @return idade, ou 0 se a data de nascimento for nula
     */
    public static int idadeNaData(LocalDate nascimento, LocalDate dataAtendimento) {

        if (nascimento == null || dataAtendimento == null) {
            return 0;
        }

        return Period.between(nascimento, dataAtendimento).getYears();
    }

    /**
     * Motivo de a data de nascimento não fazer sentido para o atendimento,
     * ou {@code null} se fizer: nascimento depois do atendimento, ou idade
     * acima de {@link #IDADE_MAXIMA} anos (limite do layout).
     */
    public static String incoerenciaNascimento(LocalDate nascimento, LocalDate dataAtendimento) {

        if (nascimento == null || dataAtendimento == null) {
            return null;
        }

        if (nascimento.isAfter(dataAtendimento)) {
            return "nascimento depois da data do atendimento";
        }

        int idade = idadeNaData(nascimento, dataAtendimento);

        if (idade > IDADE_MAXIMA) {
            return "idade de " + idade + " anos na data do atendimento (máximo " + IDADE_MAXIMA + ")";
        }

        return null;
    }

    /**
     * Data de hoje. A propriedade de sistema {@code fillbpai.hoje} (aaaa-MM-dd)
     * fixa a data — usada pelos testes (pom.xml), para o aviso de data fora do
     * esperado não mudar com o passar do tempo.
     */
    public static LocalDate hoje() {
        String fixa = System.getProperty("fillbpai.hoje");
        return fixa != null && !fixa.isBlank() ? LocalDate.parse(fixa.trim()) : LocalDate.now();
    }

    /** Atendimento mais antigo que isto (em anos, contando de hoje) gera aviso. */
    public static final int ANOS_ATENDIMENTO_ANTIGO = 2;

    /**
     * Motivo de a data do atendimento estar fora do esperado, ou {@code null}:
     * data futura, ou mais de {@link #ANOS_ATENDIMENTO_ANTIGO} anos antes de
     * {@code hoje} — em geral erro de digitação no ano, que levaria o
     * atendimento para uma competência errada (recomendação 4, 19/10/2026).
     * Aviso, não erro: a data pode estar certa.
     */
    public static String dataAtendimentoForaDoEsperado(LocalDate dataAtendimento, LocalDate hoje) {

        if (dataAtendimento == null || hoje == null) {
            return null;
        }

        if (dataAtendimento.isAfter(hoje)) {
            return "futura";
        }

        if (dataAtendimento.isBefore(hoje.minusYears(ANOS_ATENDIMENTO_ANTIGO))) {
            return "com mais de " + ANOS_ATENDIMENTO_ANTIGO + " anos";
        }

        return null;
    }

    /** Mensagem do aviso de {@link #dataAtendimentoForaDoEsperado} (análise e log de importação). */
    public static String mensagemDataForaDoEsperado(LocalDate dataAtendimento, String motivo) {
        return "Data do atendimento " + formatar(dataAtendimento) + " " + motivo
                + " — confira se o ano foi digitado certo; o atendimento entra na competência "
                + String.format("%02d/%d", dataAtendimento.getMonthValue(), dataAtendimento.getYear());
    }
}
