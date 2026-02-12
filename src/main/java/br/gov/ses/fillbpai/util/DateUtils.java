package br.gov.ses.fillbpai.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Classe utilitária responsável por conversões de datas.
 * Centraliza regras de parsing para evitar duplicação de código.
 */
public class DateUtils {

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
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
}

