package com.fluxia.backend.report;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

/**
 * Los tres periodos de la pantalla de reportes: mes, trimestre y ano.
 *
 * <p>Cada periodo sabe calcular su propio rango de fechas y el del
 * periodo anterior, que es lo que permite mostrar el "vs ant." sin que
 * nadie tenga que escribir la cuenta a mano en cada pantalla.
 *
 * <p>Los limites se calculan en la zona horaria del usuario y luego se
 * convierten al instante absoluto que se compara contra la base de
 * datos. Sin esa conversion, "este mes" empezaria tres horas tarde para
 * alguien en Buenos Aires.
 */
public enum ReportPeriod {

    MONTH,
    QUARTER,
    YEAR;

    /** Rango semiabierto [desde, hasta) del periodo que contiene a hoy. */
    public Range currentRange(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate start = startOf(today);
        LocalDate end = startOf(nextPeriodDate(today));
        return toRange(start, end, zone);
    }

    /** Rango del periodo inmediatamente anterior, para comparar. */
    public Range previousRange(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate currentStart = startOf(today);
        LocalDate previousStart = startOf(previousPeriodDate(today));
        return toRange(previousStart, currentStart, zone);
    }

    private LocalDate startOf(LocalDate date) {
        return switch (this) {
            case MONTH -> date.withDayOfMonth(1);
            case QUARTER -> {
                int firstMonthOfQuarter = ((date.get(IsoFields.QUARTER_OF_YEAR) - 1) * 3) + 1;
                yield LocalDate.of(date.getYear(), firstMonthOfQuarter, 1);
            }
            case YEAR -> LocalDate.of(date.getYear(), 1, 1);
        };
    }

    private LocalDate nextPeriodDate(LocalDate date) {
        return switch (this) {
            case MONTH -> date.plusMonths(1);
            case QUARTER -> date.plusMonths(3);
            case YEAR -> date.plusYears(1);
        };
    }

    private LocalDate previousPeriodDate(LocalDate date) {
        return switch (this) {
            case MONTH -> date.minusMonths(1);
            case QUARTER -> date.minusMonths(3);
            case YEAR -> date.minusYears(1);
        };
    }

    private Range toRange(LocalDate start, LocalDate end, ZoneId zone) {
        return new Range(
                start.atStartOfDay(zone).toOffsetDateTime(),
                end.atStartOfDay(zone).toOffsetDateTime());
    }

    /** Nombre en espanol, para la respuesta de la API. */
    public String label() {
        return switch (this) {
            case MONTH -> "Mes";
            case QUARTER -> "Trimestre";
            case YEAR -> "Ano";
        };
    }

    /**
     * Un intervalo de tiempo semiabierto: {@code from} incluido,
     * {@code to} excluido.
     */
    public record Range(OffsetDateTime from, OffsetDateTime to) {
    }
}
