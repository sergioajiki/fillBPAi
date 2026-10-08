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
 * - 8h30, 08h30, 8h30min, 14h (formato com "h")
 */
public class TimeUtils {

    /** {@code 8h30}, {@code 08h30}, {@code 8H30}, {@code 8 h 30}, {@code 8h30min}, {@code 14h}. */
    private static final java.util.regex.Pattern FORMATO_COM_H =
            java.util.regex.Pattern.compile("^(\\d{1,2})\\s*[hH]\\s*(\\d{2})?\\s*(?:min)?$");

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

        // Formato com "h", comum no Brasil: 8h30, 08h30, 8H30, 8h30min, 14h → H:mm.
        // A conversão por H:mm abaixo continua recusando horas/minutos fora do intervalo.
        java.util.regex.Matcher comH = FORMATO_COM_H.matcher(valor);
        if (comH.matches()) {
            valor = comH.group(1) + ":" + (comH.group(2) != null ? comH.group(2) : "00");
        }

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

