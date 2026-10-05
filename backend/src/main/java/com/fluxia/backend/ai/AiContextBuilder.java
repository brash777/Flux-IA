package com.fluxia.backend.ai;

import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.report.ReportDtos;
import com.fluxia.backend.report.ReportPeriod;
import com.fluxia.backend.report.ReportService;
import com.fluxia.backend.transaction.TransactionDtos;
import com.fluxia.backend.transaction.TransactionService;
import com.fluxia.backend.transaction.TransactionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Arma el resumen financiero que acompana a cada pregunta del usuario.
 *
 * <p>Esta clase es la diferencia entre el chat del prototipo y uno de
 * verdad. Alli las respuestas estaban escritas a mano en el arreglo
 * {@code AI_R} y afirmaban cosas que los datos no respaldaban: decia
 * que el mayor gasto era Comida con $4.570 cuando Supermercado tenia
 * $5.800. Aqui el modelo recibe las cifras reales calculadas desde la
 * base de datos, de modo que no tiene que inventar ninguna.
 *
 * <p>Se manda un resumen agregado y no la tabla completa de
 * movimientos: con un ano de historia el contexto costaria una
 * fortuna en tokens, y los totales ya responden la mayoria de las
 * preguntas.
 */
@Component
public class AiContextBuilder {

    /** Cuantos movimientos recientes se adjuntan al detalle. */
    private static final int RECENT_LIMIT = 15;

    /** Cuantos meses de tendencia se adjuntan. */
    private static final int TREND_MONTHS = 6;

    /**
     * Las fechas se guardan en UTC. Se convierten a la zona configurada
     * antes de mostrarlas: si no, un gasto de las 14:30 de Buenos Aires
     * le llegaria al modelo como si hubiera ocurrido a las 17:30, y
     * cualquier conclusion sobre horarios seria falsa.
     */
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");

    private final ReportService reportService;
    private final TransactionService transactionService;
    private final FluxProperties properties;

    public AiContextBuilder(ReportService reportService,
                            TransactionService transactionService,
                            FluxProperties properties) {
        this.reportService = reportService;
        this.transactionService = transactionService;
        this.properties = properties;
    }

    /**
     * Devuelve el contexto financiero del usuario como texto.
     *
     * <p>Todos los numeros salen de las mismas consultas que alimentan
     * las pantallas, asi que el asistente nunca puede contradecir lo
     * que el usuario esta viendo.
     */
    @Transactional(readOnly = true)
    public String build(UUID userId) {
        ReportDtos.SummaryResponse summary =
                reportService.summary(userId, ReportPeriod.MONTH);
        ReportDtos.ByCategoryResponse expenses =
                reportService.byCategory(userId, ReportPeriod.MONTH, TransactionType.EXPENSE);
        ReportDtos.TrendResponse trend =
                reportService.monthlyTrend(userId, TREND_MONTHS);
        List<TransactionDtos.Response> recent =
                transactionService.recent(userId, RECENT_LIMIT);

        StringBuilder context = new StringBuilder(1024);

        context.append("DATOS FINANCIEROS DEL USUARIO\n");
        context.append("=============================\n\n");

        context.append("Mes en curso\n");
        context.append("  Saldo total disponible: ").append(money(summary.balance())).append('\n');
        context.append("  Ingresos del mes:       ").append(money(summary.income())).append('\n');
        context.append("  Gastos del mes:         ").append(money(summary.expense())).append('\n');
        context.append("  Resultado del mes:      ").append(money(summary.net())).append('\n');
        context.append("  Tasa de ahorro:         ")
                .append(summary.savingsRate() != null
                        ? summary.savingsRate() + " %"
                        : "sin datos (no hubo ingresos este mes)")
                .append('\n');

        if (summary.comparison() != null) {
            context.append("  Vs mes anterior: ingresos ")
                    .append(percent(summary.comparison().incomeChangePercent()))
                    .append(", gastos ")
                    .append(percent(summary.comparison().expenseChangePercent()))
                    .append('\n');
        }

        context.append("\nGastos por categoria (mes en curso)\n");
        if (expenses.slices().isEmpty()) {
            context.append("  Sin gastos registrados este mes.\n");
        } else {
            for (ReportDtos.CategorySlice slice : expenses.slices()) {
                context.append("  ")
                        .append(slice.name())
                        .append(": ")
                        .append(money(slice.amount()))
                        .append(" (")
                        .append(slice.percent())
                        .append(" % del gasto, ")
                        .append(slice.movements())
                        .append(slice.movements() == 1 ? " movimiento)\n" : " movimientos)\n");
            }
            context.append("  Total de gastos: ").append(money(expenses.total())).append('\n');
        }

        context.append("\nTendencia de los ultimos ").append(TREND_MONTHS).append(" meses\n");
        for (ReportDtos.MonthPoint month : trend.months()) {
            context.append("  ")
                    .append(month.label()).append(' ').append(month.year())
                    .append(": ingresos ").append(money(month.income()))
                    .append(", gastos ").append(money(month.expense()))
                    .append(month.current() ? "  (mes en curso, aun incompleto)" : "")
                    .append('\n');
        }

        context.append("\nUltimos ").append(recent.size()).append(" movimientos\n");
        if (recent.isEmpty()) {
            context.append("  Sin movimientos registrados.\n");
        } else {
            for (TransactionDtos.Response transaction : recent) {
                context.append("  ")
                        .append(transaction.occurredAt()
                                .atZoneSameInstant(properties.getTimezone())
                                .format(DAY_FORMAT))
                        .append("  ")
                        .append(transaction.type() == TransactionType.INCOME ? "+" : "-")
                        .append(money(transaction.amount()))
                        .append("  ")
                        .append(transaction.description())
                        .append(" [").append(transaction.category().name()).append("]\n");
            }
        }

        return context.toString();
    }

    private String money(BigDecimal amount) {
        if (amount == null) {
            return "$0";
        }
        return String.format(SPANISH, "$%,.2f", amount);
    }

    /** Null se traduce a texto, no a "0 %", que seria enganoso. */
    private String percent(BigDecimal value) {
        return value != null ? value + " %" : "sin comparacion disponible";
    }
}
