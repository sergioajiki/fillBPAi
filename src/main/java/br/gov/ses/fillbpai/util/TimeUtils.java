package br.gov.ses.fillbpai.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Classe utilitária responsável por conversões de horário.
 *
 * Aceita múltiplos formatos comuns encontrados em planilhas:
 * - HH:mm
 * - HH:mm:ss
 * - H:mm
 * - HHmm
 * - HH.mm
 */
public class TimeUtils {

    private static final List<DateTimeFormatter> FORMATOS = List.of(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm:ss"),
            DateTimeFormatter.ofPattern("HHmm")
    );

    /**
     * Converte String para LocalTime aceitando múltiplos formatos.
     *
     * @param timeStr horário em formato texto
     * @return LocalTime convertido
     * @throws IllegalArgumentException se não for possível converter
     */
    public static LocalTime parse(String timeStr) {

        if (timeStr == null || timeStr.isBlank()) {
            throw new IllegalArgumentException("Hora vazia");
        }

        String valor = timeStr.trim();

        // Substitui ponto por dois pontos (ex: 08.30 → 08:30)
        valor = valor.replace(".", ":");

        // Tenta múltiplos formatos
        for (DateTimeFormatter formatter : FORMATOS) {
            try {
                return LocalTime.parse(valor, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("Hora inválida: " + timeStr);
    }
}

