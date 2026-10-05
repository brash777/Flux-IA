package com.fluxia.backend.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de los limites de los periodos.
 *
 * <p>Protegen el calculo mas delicado del backend: si el inicio de un
 * mes se corre unas horas, un movimiento se cuenta en el periodo
 * equivocado y todas las cifras de los reportes se desplazan.
 */
class ReportPeriodTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    @DisplayName("El rango del mes empieza el dia 1 a medianoche de la zona del usuario")
    void monthStartsAtLocalMidnight() {
        ReportPeriod.Range range = ReportPeriod.MONTH.currentRange(BUENOS_AIRES);

        assertThat(range.from().getDayOfMonth()).isEqualTo(1);
        assertThat(range.from().getHour()).isZero();
        assertThat(range.from().getMinute()).isZero();
        // Buenos Aires es UTC-3: la medianoche local son las 03:00 UTC.
        assertThat(range.from().getOffset().getTotalSeconds()).isEqualTo(-3 * 3600);
    }

    @Test
    @DisplayName("El fin de un periodo es exactamente el inicio del siguiente")
    void periodsAreContiguousWithoutOverlap() {
        // Invariante que evita contar un movimiento dos veces o perderlo
        // entre dos periodos contiguos.
        for (ReportPeriod period : ReportPeriod.values()) {
            ReportPeriod.Range current = period.currentRange(BUENOS_AIRES);
            ReportPeriod.Range previous = period.previousRange(BUENOS_AIRES);

            assertThat(previous.to())
                    .as("el periodo anterior de %s debe terminar donde arranca el actual", period)
                    .isEqualTo(current.from());
        }
    }

    @Test
    @DisplayName("Ningun rango esta invertido")
    void rangesAreWellOrdered() {
        for (ReportPeriod period : ReportPeriod.values()) {
            assertThat(period.currentRange(BUENOS_AIRES).from())
                    .isBefore(period.currentRange(BUENOS_AIRES).to());
            assertThat(period.previousRange(BUENOS_AIRES).from())
                    .isBefore(period.previousRange(BUENOS_AIRES).to());
        }
    }

    @Test
    @DisplayName("El trimestre empieza en enero, abril, julio u octubre")
    void quarterStartsOnQuarterBoundary() {
        ReportPeriod.Range range = ReportPeriod.QUARTER.currentRange(BUENOS_AIRES);

        assertThat(range.from().getMonthValue()).isIn(1, 4, 7, 10);
        assertThat(range.from().getDayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("El ano empieza el 1 de enero y dura doce meses")
    void yearStartsOnJanuaryFirst() {
        ReportPeriod.Range range = ReportPeriod.YEAR.currentRange(BUENOS_AIRES);

        assertThat(range.from().getMonthValue()).isEqualTo(1);
        assertThat(range.from().getDayOfMonth()).isEqualTo(1);
        assertThat(range.to().getYear()).isEqualTo(range.from().getYear() + 1);
    }

    @Test
    @DisplayName("La zona horaria cambia el instante en que arranca el mes")
    void timezoneShiftsTheBoundary() {
        // Si fallara, significaria que el backend ignora la zona
        // configurada y agrupa por mes en UTC.
        ReportPeriod.Range buenosAires = ReportPeriod.MONTH.currentRange(BUENOS_AIRES);
        ReportPeriod.Range utc = ReportPeriod.MONTH.currentRange(ZoneId.of("UTC"));

        assertThat(buenosAires.from().toInstant()).isNotEqualTo(utc.from().toInstant());
    }

    @Test
    @DisplayName("Cada periodo tiene su nombre en espanol")
    void eachPeriodHasSpanishLabel() {
        assertThat(ReportPeriod.MONTH.label()).isEqualTo("Mes");
        assertThat(ReportPeriod.QUARTER.label()).isEqualTo("Trimestre");
        assertThat(ReportPeriod.YEAR.label()).isEqualTo("Ano");
    }
}
