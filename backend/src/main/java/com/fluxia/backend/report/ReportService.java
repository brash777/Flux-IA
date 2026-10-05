package com.fluxia.backend.report;

import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.transaction.CategoryTotal;
import com.fluxia.backend.transaction.MonthlyTotalRow;
import com.fluxia.backend.transaction.TransactionRepository;
import com.fluxia.backend.transaction.TransactionType;
import com.fluxia.backend.transaction.TypeTotal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Calcula todos los reportes a partir de la tabla de transacciones.
 *
 * <p>Esta clase es la respuesta a la incoherencia del prototipo. Alli
 * la pantalla de inicio decia que el saldo era 45.230, la de reportes
 * que los gastos eran 12.840 y el chat que el ahorro era del 78 %,
 * mientras la suma real de los 12 movimientos daba 58.000 de ingresos,
 * 17.250 de gastos y 40.750 de saldo. Eran cuatro numeros escritos en
 * cuatro lugares distintos.
 *
 * <p>Aqui no hay ni una cifra escrita a mano: cada valor sale de una
 * suma sobre la base de datos, de modo que dos pantallas no pueden
 * discrepar.
 */
@Service
public class ReportService {

    /** Dos decimales en los porcentajes, suficiente para la interfaz. */
    private static final int PERCENT_SCALE = 2;

    /** Cuatro decimales en los pasos intermedios, para no arrastrar error. */
    private static final int INTERMEDIATE_SCALE = 6;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final TransactionRepository transactionRepository;
    private final FluxProperties properties;

    public ReportService(TransactionRepository transactionRepository,
                         FluxProperties properties) {
        this.transactionRepository = transactionRepository;
        this.properties = properties;
    }

    // -----------------------------------------------------------------
    // Resumen
    // -----------------------------------------------------------------

    /**
     * Saldo, ingresos, gastos, tasa de ahorro y comparacion con el
     * periodo anterior.
     *
     * <p>Distincion importante: {@code balance} es el saldo historico
     * acumulado (todo lo que entro menos todo lo que salio desde que la
     * cuenta existe), mientras {@code net} es solo el resultado del
     * periodo. El prototipo mezclaba ambos conceptos, y de ahi venia
     * parte del descuadre.
     */
    @Transactional(readOnly = true)
    public ReportDtos.SummaryResponse summary(UUID userId, ReportPeriod period) {
        ZoneId zone = properties.getTimezone();
        ReportPeriod.Range current = period.currentRange(zone);
        ReportPeriod.Range previous = period.previousRange(zone);

        Map<TransactionType, BigDecimal> currentSums =
                asMap(transactionRepository.sumByTypeBetween(userId, current.from(), current.to()));
        Map<TransactionType, BigDecimal> previousSums =
                asMap(transactionRepository.sumByTypeBetween(userId, previous.from(), previous.to()));
        Map<TransactionType, BigDecimal> historicSums =
                asMap(transactionRepository.sumByTypeUntil(userId, current.to()));

        BigDecimal income = get(currentSums, TransactionType.INCOME);
        BigDecimal expense = get(currentSums, TransactionType.EXPENSE);
        BigDecimal previousIncome = get(previousSums, TransactionType.INCOME);
        BigDecimal previousExpense = get(previousSums, TransactionType.EXPENSE);

        BigDecimal balance = get(historicSums, TransactionType.INCOME)
                .subtract(get(historicSums, TransactionType.EXPENSE));

        return new ReportDtos.SummaryResponse(
                period.name(),
                period.label(),
                current.from(),
                current.to(),
                balance,
                income,
                expense,
                income.subtract(expense),
                savingsRate(income, expense),
                new ReportDtos.Comparison(
                        previousIncome,
                        previousExpense,
                        percentChange(previousIncome, income),
                        percentChange(previousExpense, expense)));
    }

    // -----------------------------------------------------------------
    // Por categoria
    // -----------------------------------------------------------------

    /**
     * Gastos agrupados por categoria, con su porcentaje sobre el total.
     *
     * <p>Los porcentajes se calculan aqui y no en el cliente por un
     * motivo concreto: el prototipo tenia un grafico cuyas porciones
     * (36 %, 23 %, 28 %, 13 %) no se correspondian con ningun monto
     * real. Calculandolos sobre la suma efectiva, siempre cierran.
     */
    @Transactional(readOnly = true)
    public ReportDtos.ByCategoryResponse byCategory(UUID userId,
                                                    ReportPeriod period,
                                                    TransactionType type) {
        ZoneId zone = properties.getTimezone();
        ReportPeriod.Range range = period.currentRange(zone);

        List<CategoryTotal> totals = transactionRepository
                .sumByCategoryBetween(userId, type, range.from(), range.to());

        BigDecimal total = totals.stream()
                .map(CategoryTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ReportDtos.CategorySlice> slices = totals.stream()
                .map(row -> new ReportDtos.CategorySlice(
                        row.categoryId(),
                        row.slug(),
                        row.name(),
                        row.icon(),
                        row.color(),
                        row.total(),
                        shareOf(row.total(), total),
                        row.movements()))
                .toList();

        return new ReportDtos.ByCategoryResponse(
                period.name(), range.from(), range.to(), total, slices);
    }

    // -----------------------------------------------------------------
    // Tendencia mensual
    // -----------------------------------------------------------------

    /**
     * Serie de los ultimos meses, incluido el actual.
     *
     * <p>Se rellenan los meses sin movimientos con ceros. Si no se
     * hiciera, el grafico de barras se comprimiria y mostraria una
     * tendencia que no existe: un mes vacio es informacion, no una
     * ausencia de datos.
     */
    @Transactional(readOnly = true)
    public ReportDtos.TrendResponse monthlyTrend(UUID userId, int months) {
        ZoneId zone = properties.getTimezone();
        int safeMonths = Math.clamp(months, 1, 24);

        LocalDate firstOfThisMonth = LocalDate.now(zone).withDayOfMonth(1);
        LocalDate firstMonth = firstOfThisMonth.minusMonths(safeMonths - 1L);

        OffsetDateTime from = firstMonth.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime to = firstOfThisMonth.plusMonths(1).atStartOfDay(zone).toOffsetDateTime();

        List<MonthlyTotalRow> rows = transactionRepository
                .sumByMonth(userId, from, to, zone.getId());

        // Se indexa por "ano*100 + mes" para poder buscar cada mes en
        // tiempo constante al recorrer la serie completa.
        Map<Integer, MonthlyTotalRow> byMonth = new java.util.HashMap<>();
        for (MonthlyTotalRow row : rows) {
            byMonth.put(row.getYear() * 100 + row.getMonth(), row);
        }

        List<ReportDtos.MonthPoint> points = new ArrayList<>(safeMonths);
        for (int i = 0; i < safeMonths; i++) {
            LocalDate monthStart = firstMonth.plusMonths(i);
            int key = monthStart.getYear() * 100 + monthStart.getMonthValue();
            MonthlyTotalRow row = byMonth.get(key);

            BigDecimal income = row != null ? row.getIncome() : BigDecimal.ZERO;
            BigDecimal expense = row != null ? row.getExpense() : BigDecimal.ZERO;

            points.add(new ReportDtos.MonthPoint(
                    monthStart.getYear(),
                    monthStart.getMonthValue(),
                    shortMonthLabel(monthStart.getMonth()),
                    income,
                    expense,
                    income.subtract(expense),
                    monthStart.equals(firstOfThisMonth)));
        }

        return new ReportDtos.TrendResponse(points);
    }

    // -----------------------------------------------------------------
    // Calculos auxiliares
    // -----------------------------------------------------------------

    /**
     * Tasa de ahorro: que porcion de los ingresos no se gasto.
     *
     * <p>Devuelve null si no hubo ingresos, en lugar de cero. Son cosas
     * distintas: "no ahorre nada" y "no tengo con que calcular el
     * ahorro". Mostrar 0 % en el segundo caso seria mentir.
     */
    private BigDecimal savingsRate(BigDecimal income, BigDecimal expense) {
        if (income.signum() <= 0) {
            return null;
        }
        return income.subtract(expense)
                .divide(income, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Variacion porcentual entre dos valores.
     *
     * <p>Devuelve null cuando el valor anterior es cero: pasar de 0 a
     * 100 no es "un aumento del infinito por ciento", simplemente no
     * hay base contra la que comparar. El cliente muestra un guion.
     */
    private BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Porcentaje de una parte sobre el total. */
    private BigDecimal shareOf(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return part.divide(total, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * "Ene", "Feb"... como los rotulos del grafico del prototipo.
     *
     * <p>Se recorta a tres letras porque en espanol septiembre abrevia
     * "sept." y un rotulo de cuatro caracteres descuadra el ancho de las
     * barras del grafico.
     */
    private String shortMonthLabel(Month month) {
        String label = month.getDisplayName(TextStyle.SHORT, SPANISH).replace(".", "");
        if (label.length() > 3) {
            label = label.substring(0, 3);
        }
        return label.substring(0, 1).toUpperCase(SPANISH) + label.substring(1);
    }

    private Map<TransactionType, BigDecimal> asMap(List<TypeTotal> totals) {
        Map<TransactionType, BigDecimal> map = new EnumMap<>(TransactionType.class);
        for (TypeTotal total : totals) {
            map.put(total.type(), total.total() != null ? total.total() : BigDecimal.ZERO);
        }
        return map;
    }

    private BigDecimal get(Map<TransactionType, BigDecimal> sums, TransactionType type) {
        return sums.getOrDefault(type, BigDecimal.ZERO);
    }
}
