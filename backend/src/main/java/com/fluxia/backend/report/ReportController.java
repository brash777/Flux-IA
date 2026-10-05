package com.fluxia.backend.report;

import com.fluxia.backend.auth.AuthenticatedUser;
import com.fluxia.backend.transaction.TransactionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reportes del usuario autenticado.
 *
 * <p>Los tres endpoints de aqui son los que alimentan la pantalla de
 * reportes y la tarjeta de saldo de la pantalla de inicio. Todos sus
 * numeros salen de sumas sobre la tabla de transacciones.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes", description = "Saldo, totales por periodo, gasto por categoria y tendencia mensual")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Resumen del periodo",
            description = """
                    Saldo historico, ingresos y gastos del periodo, tasa de ahorro
                    y variacion frente al periodo anterior.

                    Alimenta la tarjeta de saldo de la pantalla de inicio y las dos
                    tarjetas de cifras de la pantalla de reportes.

                    savingsRate e incomeChangePercent pueden venir en null cuando
                    no hay base para calcularlos (sin ingresos, o sin periodo
                    anterior con movimientos). El cliente debe mostrar un guion,
                    no un cero.
                    """)
    public ReportDtos.SummaryResponse summary(
            @AuthenticationPrincipal AuthenticatedUser user,

            @Parameter(description = "MONTH, QUARTER o YEAR")
            @RequestParam(defaultValue = "MONTH") ReportPeriod period) {

        return reportService.summary(user.id(), period);
    }

    @GetMapping("/by-category")
    @Operation(summary = "Totales por categoria",
            description = "Datos del grafico de dona, con el porcentaje de cada categoria ya calculado.")
    public ReportDtos.ByCategoryResponse byCategory(
            @AuthenticationPrincipal AuthenticatedUser user,

            @Parameter(description = "MONTH, QUARTER o YEAR")
            @RequestParam(defaultValue = "MONTH") ReportPeriod period,

            @Parameter(description = "EXPENSE para gastos por categoria, INCOME para ingresos")
            @RequestParam(defaultValue = "EXPENSE") TransactionType type) {

        return reportService.byCategory(user.id(), period, type);
    }

    @GetMapping("/monthly-trend")
    @Operation(summary = "Tendencia mensual",
            description = """
                    Serie de los ultimos meses para el grafico de barras.

                    Los meses sin movimientos vienen en cero, no se omiten: asi el
                    grafico conserva la escala temporal real.
                    """)
    public ReportDtos.TrendResponse monthlyTrend(
            @AuthenticationPrincipal AuthenticatedUser user,

            @Parameter(description = "Cantidad de meses, entre 1 y 24. El prototipo mostraba 7.")
            @RequestParam(defaultValue = "7") int months) {

        return reportService.monthlyTrend(user.id(), months);
    }
}
