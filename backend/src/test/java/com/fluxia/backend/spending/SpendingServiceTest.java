package com.fluxia.backend.spending;

import com.fluxia.backend.category.Category;
import com.fluxia.backend.category.CategoryKind;
import com.fluxia.backend.category.CategoryRepository;
import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.transaction.TransactionRepository;
import com.fluxia.backend.transaction.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.fluxia.backend.spending.SpendingDtos.Decision.APPROVED;
import static com.fluxia.backend.spending.SpendingDtos.Decision.DECLINED_DAILY_LIMIT;
import static com.fluxia.backend.spending.SpendingDtos.Decision.DECLINED_GOING_OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del tope diario, el modo salida y la tarjeta simulada.
 *
 * <p>El reloj está fijo en un viernes a las 21:00 de Bogotá, que es cuando
 * esta función tiene que funcionar. La base de datos es un doble de prueba:
 * lo que se verifica es la regla, no el SQL.
 */
@ExtendWith(MockitoExtension.class)
class SpendingServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID USER_ID = UUID.randomUUID();
    /** Viernes 6 de noviembre de 2026, 21:00 en Bogotá. */
    private static final Instant FRIDAY_NIGHT = Instant.parse("2026-11-07T02:00:00Z");

    @Mock
    private SpendingSettingsRepository settingsRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionService transactionService;

    private SpendingService service;

    @BeforeEach
    void setUp() {
        service = new SpendingService(settingsRepository, transactionRepository, categoryRepository,
                transactionService, new FluxProperties(), Clock.fixed(FRIDAY_NIGHT, BOGOTA));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static Category category(String slug, CategoryKind kind) {
        Category category = new Category(null, slug, slug, "", "#000000", kind);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private static SpendingSettings settings(String limit, OffsetDateTime goingOutUntil, UUID... excluded) {
        SpendingSettings settings = new SpendingSettings(USER_ID);
        settings.setDailyLimit(limit == null ? null : money(limit));
        settings.setGoingOutUntil(goingOutUntil);
        settings.setExcludedCategoryIds(Set.of(excluded));
        return settings;
    }

    private void spentToday(String amount) {
        when(transactionRepository.sumExpensesBetween(eq(USER_ID), any(), any(), anyCollection()))
                .thenReturn(money(amount));
    }

    // -----------------------------------------------------------------
    // La regla de la tarjeta
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Con el modo salida, la tarjeta rechaza una compra en un bar")
    void goingOutDeclines() {
        assertThat(SpendingService.decide(false, true, null, true, BigDecimal.ZERO, money("80000")))
                .isEqualTo(DECLINED_GOING_OUT);
    }

    @Test
    @DisplayName("Transporte y salud pasan aunque esté el modo salida y no quede tope")
    void essentialAlwaysPasses() {
        assertThat(SpendingService.decide(true, true, money("50000"), true, money("50000"), money("18000")))
                .isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("Una compra que haría pasar el tope se rechaza")
    void overLimitDeclines() {
        assertThat(SpendingService.decide(false, false, money("50000"), true, money("30000"), money("20000.01")))
                .isEqualTo(DECLINED_DAILY_LIMIT);
    }

    @Test
    @DisplayName("Gastar exactamente el tope está permitido: el tope es lo que se puede gastar")
    void exactlyTheLimitPasses() {
        assertThat(SpendingService.decide(false, false, money("50000"), true, money("30000"), money("20000")))
                .isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("Un gasto fijo no cuenta para el tope y pasa aunque no quede nada")
    void excludedCategoryPasses() {
        assertThat(SpendingService.decide(false, false, money("50000"), false, money("50000"), money("1850000")))
                .isEqualTo(APPROVED);
    }

    @Test
    @DisplayName("Sin tope y sin modo salida, la tarjeta aprueba todo")
    void noRulesApproves() {
        assertThat(SpendingService.decide(false, false, null, true, BigDecimal.ZERO, money("999999")))
                .isEqualTo(APPROVED);
    }

    // -----------------------------------------------------------------
    // Hasta cuándo dura el modo salida
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Activado el viernes a las 21:00, el modo salida dura hasta el sábado a las 8:00")
    void goingOutLastsUntilNextMorning() {
        ZonedDateTime friday = ZonedDateTime.of(2026, 11, 6, 21, 0, 0, 0, BOGOTA);
        assertThat(SpendingService.goingOutEnd(friday))
                .isEqualTo(ZonedDateTime.of(2026, 11, 7, 8, 0, 0, 0, BOGOTA).toOffsetDateTime());
    }

    @Test
    @DisplayName("Activado a la 1:00, termina a las 8:00 de ese mismo día y no del siguiente")
    void goingOutAfterMidnightEndsThatMorning() {
        ZonedDateTime night = ZonedDateTime.of(2026, 11, 7, 1, 0, 0, 0, BOGOTA);
        assertThat(SpendingService.goingOutEnd(night))
                .isEqualTo(ZonedDateTime.of(2026, 11, 7, 8, 0, 0, 0, BOGOTA).toOffsetDateTime());
    }

    @Test
    @DisplayName("Activado justo a las 8:00, dura hasta las 8:00 del día siguiente")
    void goingOutAtEightLastsADay() {
        ZonedDateTime eight = ZonedDateTime.of(2026, 11, 7, 8, 0, 0, 0, BOGOTA);
        assertThat(SpendingService.goingOutEnd(eight))
                .isEqualTo(ZonedDateTime.of(2026, 11, 8, 8, 0, 0, 0, BOGOTA).toOffsetDateTime());
    }

    // -----------------------------------------------------------------
    // Cómo va el día
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Con tope de 50.000 y 20.000 gastados, quedan 30.000 y la barra va en 40 %")
    void todayWithRoomLeft() {
        when(settingsRepository.findWithExclusionsByUserId(USER_ID))
                .thenReturn(Optional.of(settings("50000", null)));
        spentToday("20000.00");

        SpendingDtos.TodayResponse today = service.today(USER_ID);

        assertThat(today.remaining()).isEqualByComparingTo("30000");
        assertThat(today.overBy()).isEqualByComparingTo("0");
        assertThat(today.progressPercent()).isEqualByComparingTo("40.00");
        assertThat(today.goingOut().active()).isFalse();
    }

    @Test
    @DisplayName("Pasado el tope, lo que queda es 0 y no negativo, y la barra no pasa de 100 %")
    void todayOverTheLimit() {
        when(settingsRepository.findWithExclusionsByUserId(USER_ID))
                .thenReturn(Optional.of(settings("50000", null)));
        spentToday("65000.00");

        SpendingDtos.TodayResponse today = service.today(USER_ID);

        assertThat(today.remaining()).isEqualByComparingTo("0");
        assertThat(today.overBy()).isEqualByComparingTo("15000");
        assertThat(today.progressPercent()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Sin ajustes, no hay tope: lo que queda es null y no 0")
    void todayWithoutSettings() {
        when(settingsRepository.findWithExclusionsByUserId(USER_ID)).thenReturn(Optional.empty());
        spentToday("12000.00");

        SpendingDtos.TodayResponse today = service.today(USER_ID);

        assertThat(today.dailyLimit()).isNull();
        assertThat(today.remaining()).isNull();
        assertThat(today.spentToday()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("El día empieza a la medianoche de Bogotá, no a la de UTC")
    void dayStartsAtBogotaMidnight() {
        when(settingsRepository.findWithExclusionsByUserId(USER_ID)).thenReturn(Optional.empty());
        spentToday("0");

        SpendingDtos.TodayResponse today = service.today(USER_ID);

        // A las 21:00 del viernes en Bogotá ya es sábado en UTC.
        assertThat(today.from()).isEqualTo(ZonedDateTime.of(2026, 11, 6, 0, 0, 0, 0, BOGOTA).toOffsetDateTime());
        assertThat(today.to()).isEqualTo(ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, BOGOTA).toOffsetDateTime());
    }

    // -----------------------------------------------------------------
    // La compra completa
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Una compra rechazada no crea ningún movimiento")
    void declinedPurchaseSavesNothing() {
        Category bar = category("ocio", CategoryKind.EXPENSE);
        when(categoryRepository.findByIdVisibleTo(bar.getId(), USER_ID)).thenReturn(Optional.of(bar));
        OffsetDateTime tomorrowMorning = OffsetDateTime.parse("2026-11-07T08:00:00-05:00");
        when(settingsRepository.findForUpdate(USER_ID))
                .thenReturn(Optional.of(settings(null, tomorrowMorning)));
        spentToday("0");

        SpendingDtos.PurchaseResponse response = service.purchase(USER_ID,
                new SpendingDtos.PurchaseRequest("Bar La 85", money("80000"), bar.getId()));

        assertThat(response.approved()).isFalse();
        assertThat(response.decision()).isEqualTo(DECLINED_GOING_OUT);
        assertThat(response.transaction()).isNull();
        verify(transactionService, never()).create(any(), any());
    }

    @Test
    @DisplayName("Activar el modo salida dos veces no lo alarga")
    void goingOutTwiceKeepsTheSameEnd() {
        OffsetDateTime alreadyUntil = OffsetDateTime.parse("2026-11-07T08:00:00-05:00");
        when(settingsRepository.findForUpdate(USER_ID))
                .thenReturn(Optional.of(settings(null, alreadyUntil)));
        when(settingsRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        spentToday("0");

        SpendingDtos.TodayResponse today = service.startGoingOut(USER_ID);

        assertThat(today.goingOut().until()).isEqualTo(alreadyUntil);
    }

    // -----------------------------------------------------------------
    // Promedio para la propuesta
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Con una cuenta de 10 días, el promedio se divide por 10 y no por 30")
    void averageOfANewAccount() {
        when(settingsRepository.findWithExclusionsByUserId(USER_ID)).thenReturn(Optional.empty());
        when(transactionRepository.firstOccurredAt(USER_ID))
                .thenReturn(OffsetDateTime.parse("2026-10-27T10:00:00-05:00"));
        spentToday("230000.00");
        when(transactionRepository.sumByCategoryBetween(eq(USER_ID), any(), any(), any())).thenReturn(List.of());

        DailyAverage average = service.dailyAverage(USER_ID, null);

        assertThat(average.days()).isEqualTo(10);
        assertThat(average.perDay()).isEqualByComparingTo("23000.00");
    }

    @Test
    @DisplayName("Sin movimientos, el promedio es 0 y no se consulta nada más")
    void averageWithoutMovements() {
        when(transactionRepository.firstOccurredAt(USER_ID)).thenReturn(null);

        DailyAverage average = service.dailyAverage(USER_ID, List.of());

        assertThat(average.total()).isEqualByComparingTo("0");
        verify(transactionRepository, never()).sumExpensesBetween(any(), any(), any(), anyCollection());
    }
}
