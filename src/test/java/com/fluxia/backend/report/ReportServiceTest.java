package com.fluxia.backend.report;

import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.transaction.CategoryTotal;
import com.fluxia.backend.transaction.TransactionRepository;
import com.fluxia.backend.transaction.TransactionType;
import com.fluxia.backend.transaction.TypeTotal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pruebas de los calculos de los reportes.
 *
 * <p>Estas son las pruebas que el prototipo no tenia y que habrian
 * detectado su descuadre: alli el saldo, los gastos y la tasa de ahorro
 * estaban escritos a mano en tres pantallas distintas y ninguno
 * coincidia con la suma de los movimientos.
 *
 * <p>Se usan dobles de prueba en lugar de una base de datos real: lo
 * que se verifica aqui es la aritmetica, no el SQL. El SQL se valida
 * levantando la aplicacion contra PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(transactionRepository, new FluxProperties());
    }

    @Test
    @DisplayName("Los totales del prototipo: 58.000 de ingresos y 17.250 de gastos dan 40.750 de saldo")
    void reproducesPrototypeAudit() {
        // Son las cifras reales del arreglo TXS del prototipo. La
        // aplicacion mostraba 45.230 de saldo y 12.840 de gastos.
        List<TypeTotal> totals = List.of(
                new TypeTotal(TransactionType.INCOME, new BigDecimal("58000.00")),
                new TypeTotal(TransactionType.EXPENSE, new BigDecimal("17250.00")));

        when(transactionRepository.sumByTypeBetween(eq(USER_ID), any(), any())).thenReturn(totals);
        when(transactionRepository.sumByTypeUntil(eq(USER_ID), any())).thenReturn(totals);

        ReportDtos.SummaryResponse summary = reportService.summary(USER_ID, ReportPeriod.MONTH);

        assertThat(summary.income()).isEqualByComparingTo("58000.00");
        assertThat(summary.expense()).isEqualByComparingTo("17250.00");
        assertThat(summary.net()).isEqualByComparingTo("40750.00");
        assertThat(summary.balance()).isEqualByComparingTo("40750.00");
        // 40.750 / 58.000 = 70,26 %. El prototipo afirmaba 78 %.
        assertThat(summary.savingsRate()).isEqualByComparingTo("70.26");
    }

    @Test
    @DisplayName("Sin movimientos, todos los totales son cero y no hay error")
    void handlesEmptyHistory() {
        when(transactionRepository.sumByTypeBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(transactionRepository.sumByTypeUntil(eq(USER_ID), any())).thenReturn(List.of());

        ReportDtos.SummaryResponse summary = reportService.summary(USER_ID, ReportPeriod.MONTH);

        assertThat(summary.income()).isEqualByComparingTo("0");
        assertThat(summary.expense()).isEqualByComparingTo("0");
        assertThat(summary.balance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Sin ingresos, la tasa de ahorro es null y no 0 %")
    void savingsRateIsNullWithoutIncome() {
        // Distincion deliberada: "no ahorre nada" y "no se puede
        // calcular el ahorro" no son lo mismo.
        when(transactionRepository.sumByTypeBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(new TypeTotal(TransactionType.EXPENSE, new BigDecimal("500.00"))));
        when(transactionRepository.sumByTypeUntil(eq(USER_ID), any()))
                .thenReturn(List.of(new TypeTotal(TransactionType.EXPENSE, new BigDecimal("500.00"))));

        ReportDtos.SummaryResponse summary = reportService.summary(USER_ID, ReportPeriod.MONTH);

        assertThat(summary.savingsRate()).isNull();
        assertThat(summary.net()).isEqualByComparingTo("-500.00");
    }

    @Test
    @DisplayName("Sin periodo anterior con datos, la variacion es null y no un aumento infinito")
    void percentChangeIsNullWithoutBaseline() {
        when(transactionRepository.sumByTypeBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(new TypeTotal(TransactionType.INCOME, new BigDecimal("1000.00"))))
                .thenReturn(List.of());
        when(transactionRepository.sumByTypeUntil(eq(USER_ID), any()))
                .thenReturn(List.of(new TypeTotal(TransactionType.INCOME, new BigDecimal("1000.00"))));

        ReportDtos.SummaryResponse summary = reportService.summary(USER_ID, ReportPeriod.MONTH);

        assertThat(summary.comparison().incomeChangePercent()).isNull();
        assertThat(summary.comparison().expenseChangePercent()).isNull();
    }

    @Test
    @DisplayName("Los porcentajes del grafico de dona suman 100")
    void categoryPercentagesAddUpToOneHundred() {
        // El grafico del prototipo repartia 36 + 23 + 28 + 13 sobre un
        // total que no correspondia a ningun monto real.
        List<CategoryTotal> totals = List.of(
                new CategoryTotal(UUID.randomUUID(), "supermercado", "Supermercado", "🛒", "#06b6d4",
                        new BigDecimal("5800.00"), 1),
                new CategoryTotal(UUID.randomUUID(), "servicios", "Servicios", "⚡", "#f59e0b",
                        new BigDecimal("4700.00"), 2),
                new CategoryTotal(UUID.randomUUID(), "comida", "Comida", "🍽️", "#6c63ff",
                        new BigDecimal("4570.00"), 3),
                new CategoryTotal(UUID.randomUUID(), "ocio", "Ocio", "🎮", "#ec4899",
                        new BigDecimal("1880.00"), 2),
                new CategoryTotal(UUID.randomUUID(), "transporte", "Transporte", "🚌", "#10b981",
                        new BigDecimal("300.00"), 2));

        when(transactionRepository.sumByCategoryBetween(
                eq(USER_ID), eq(TransactionType.EXPENSE), any(), any())).thenReturn(totals);

        ReportDtos.ByCategoryResponse result =
                reportService.byCategory(USER_ID, ReportPeriod.MONTH, TransactionType.EXPENSE);

        // 5800 + 4700 + 4570 + 1880 + 300 = 17.250, el gasto real del prototipo.
        assertThat(result.total()).isEqualByComparingTo("17250.00");

        BigDecimal sumOfPercentages = result.slices().stream()
                .map(ReportDtos.CategorySlice::percent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumOfPercentages).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Sin gastos, el grafico de dona queda vacio sin dividir por cero")
    void emptyDonutDoesNotDivideByZero() {
        when(transactionRepository.sumByCategoryBetween(
                eq(USER_ID), eq(TransactionType.EXPENSE), any(), any())).thenReturn(List.of());

        ReportDtos.ByCategoryResponse result =
                reportService.byCategory(USER_ID, ReportPeriod.MONTH, TransactionType.EXPENSE);

        assertThat(result.total()).isEqualByComparingTo("0");
        assertThat(result.slices()).isEmpty();
    }

    @Test
    @DisplayName("Los meses sin movimientos aparecen en cero, no se omiten")
    void trendFillsMissingMonthsWithZero() {
        // Omitirlos comprimiria el grafico de barras y mostraria una
        // tendencia que no existe.
        when(transactionRepository.sumByMonth(eq(USER_ID), any(OffsetDateTime.class),
                any(OffsetDateTime.class), any(String.class))).thenReturn(List.of());

        ReportDtos.TrendResponse trend = reportService.monthlyTrend(USER_ID, 7);

        assertThat(trend.months()).hasSize(7);
        assertThat(trend.months()).allSatisfy(month -> {
            assertThat(month.income()).isEqualByComparingTo("0");
            assertThat(month.expense()).isEqualByComparingTo("0");
        });
        // Solo el ultimo es el mes en curso.
        assertThat(trend.months().stream().filter(ReportDtos.MonthPoint::current)).hasSize(1);
        assertThat(trend.months().get(6).current()).isTrue();
    }

    @Test
    @DisplayName("La cantidad de meses pedidos se acota entre 1 y 24")
    void trendClampsMonthCount() {
        when(transactionRepository.sumByMonth(eq(USER_ID), any(OffsetDateTime.class),
                any(OffsetDateTime.class), any(String.class))).thenReturn(List.of());

        assertThat(reportService.monthlyTrend(USER_ID, 500).months()).hasSize(24);
        assertThat(reportService.monthlyTrend(USER_ID, 0).months()).hasSize(1);
        assertThat(reportService.monthlyTrend(USER_ID, -3).months()).hasSize(1);
    }
}
