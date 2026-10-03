package com.fluxia.backend.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contratos de salida de los reportes.
 *
 * <p>Sustituyen al arreglo {@code PERIODS} del prototipo, donde cada
 * periodo traia sus totales escritos a mano y ninguno coincidia con la
 * suma real de los movimientos. Aqui todo valor viene de un
 * {@code SUM()} sobre la tabla de transacciones.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    @Schema(description = "Saldo y totales del periodo, con la variacion respecto al anterior")
    public record SummaryResponse(

            String period,
            String periodLabel,
            OffsetDateTime from,
            OffsetDateTime to,

            @Schema(description = "Saldo historico acumulado hasta el fin del periodo")
            BigDecimal balance,

            BigDecimal income,
            BigDecimal expense,

            @Schema(description = "Ingresos menos gastos DEL PERIODO (no el saldo historico)")
            BigDecimal net,

            @Schema(description = "Porcion de los ingresos que no se gasto, en porcentaje. Null si no hubo ingresos.")
            BigDecimal savingsRate,

            @Schema(description = "Variacion porcentual frente al periodo anterior")
            Comparison comparison
    ) {
    }

    @Schema(description = "Variacion frente al periodo anterior. Null cuando el anterior fue cero y no hay base de comparacion.")
    public record Comparison(
            BigDecimal previousIncome,
            BigDecimal previousExpense,
            BigDecimal incomeChangePercent,
            BigDecimal expenseChangePercent
    ) {
    }

    @Schema(description = "Gasto por categoria, para el grafico de dona")
    public record ByCategoryResponse(
            String period,
            OffsetDateTime from,
            OffsetDateTime to,
            BigDecimal total,
            List<CategorySlice> slices
    ) {
    }

    @Schema(description = "Una porcion del grafico")
    public record CategorySlice(
            UUID categoryId,
            String slug,
            String name,
            String icon,
            String color,
            BigDecimal amount,
            @Schema(description = "Porcentaje sobre el total del periodo")
            BigDecimal percent,
            long movements
    ) {
    }

    @Schema(description = "Serie mensual para el grafico de barras")
    public record TrendResponse(
            List<MonthPoint> months
    ) {
    }

    @Schema(description = "Un mes de la serie")
    public record MonthPoint(
            int year,
            int month,
            String label,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal net,
            @Schema(description = "true si es el mes en curso: el prototipo lo pintaba distinto")
            boolean current
    ) {
    }
}
