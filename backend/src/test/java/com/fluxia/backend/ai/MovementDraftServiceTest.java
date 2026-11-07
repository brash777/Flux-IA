package com.fluxia.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.category.Category;
import com.fluxia.backend.category.CategoryKind;
import com.fluxia.backend.category.CategoryRepository;
import com.fluxia.backend.config.FluxProperties;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pruebas del llenado de movimientos con IA.
 *
 * <p>El modelo es un doble que devuelve el JSON de cada caso. Lo que se
 * prueba es que el servidor no se fíe de él: categorías que no existen,
 * fechas futuras o respuestas sin monto.
 */
@ExtendWith(MockitoExtension.class)
class MovementDraftServiceTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final UUID USER_ID = UUID.randomUUID();
    /** Sábado 7 de noviembre de 2026, 15:30 en Bogotá. */
    private static final Instant SATURDAY = Instant.parse("2026-11-07T20:30:00Z");

    @Mock
    private AiChatService aiChatService;

    @Mock
    private CategoryRepository categoryRepository;

    private final Category transporte = category("transporte", CategoryKind.EXPENSE);
    private final Category otros = category("otros", CategoryKind.EXPENSE);
    private final Category ingreso = category("ingreso", CategoryKind.INCOME);

    private MovementDraftService service;

    @BeforeEach
    void setUp() {
        service = new MovementDraftService(aiChatService, categoryRepository, new ObjectMapper(),
                new FluxProperties(), Clock.fixed(SATURDAY, BOGOTA));
        // lenient: las pruebas de fechas no piden categorías.
        lenient().when(categoryRepository.findVisibleTo(USER_ID)).thenReturn(List.of(transporte, otros, ingreso));
    }

    private static Category category(String slug, CategoryKind kind) {
        Category category = new Category(null, slug, slug, "", "#000000", kind);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        return category;
    }

    private void modelAnswers(String text) {
        when(aiChatService.complete(eq(USER_ID), eq(MovementDraftService.SYSTEM_PROMPT), anyList()))
                .thenReturn(new ChatReply(text, 300, 40, "stop"));
    }

    @Test
    @DisplayName("«gasolina 20 mil» queda como un gasto de 20.000 en Transporte, ahora")
    void fuelTwentyThousand() {
        modelAnswers("""
                {"type":"EXPENSE","amount":20000,"description":"gasolina","category":"transporte","date":null,"time":null}
                """);

        AiDtos.MovementDraft draft = service.draft(USER_ID, "gasolina 20 mil");

        assertThat(draft.type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(draft.amount()).isEqualByComparingTo("20000.00");
        assertThat(draft.description()).isEqualTo("Gasolina");
        assertThat(draft.categoryId()).isEqualTo(transporte.getId());
        assertThat(draft.occurredAt().toInstant()).isEqualTo(SATURDAY);
    }

    @Test
    @DisplayName("El JSON se lee aunque el modelo lo envuelva en un bloque de código")
    void readsJsonInsideCodeFence() {
        modelAnswers("""
                Claro:
                ```json
                {"type":"EXPENSE","amount":"7000","description":"Empanada","category":"otros"}
                ```
                """);

        assertThat(service.draft(USER_ID, "empanada 7 lucas").amount()).isEqualByComparingTo("7000");
    }

    @Test
    @DisplayName("Una categoría que no existe termina en Otros, no en un error")
    void unknownCategoryFallsBackToOthers() {
        modelAnswers("""
                {"type":"EXPENSE","amount":15000,"description":"Almuerzo","category":"restaurantes"}
                """);

        assertThat(service.draft(USER_ID, "almuerzo 15 mil").categoryId()).isEqualTo(otros.getId());
    }

    @Test
    @DisplayName("Un ingreso nunca queda en una categoría de gastos")
    void incomeGetsAnIncomeCategory() {
        modelAnswers("""
                {"type":"INCOME","amount":340000,"description":"Monitoría","category":"transporte"}
                """);

        AiDtos.MovementDraft draft = service.draft(USER_ID, "me pagaron la monitoría 340 mil");

        assertThat(draft.type()).isEqualTo(TransactionType.INCOME);
        assertThat(draft.categoryId()).isEqualTo(ingreso.getId());
    }

    @Test
    @DisplayName("Sin monto, responde un error para el usuario y no un movimiento en cero")
    void withoutAmountIsAnError() {
        modelAnswers("{\"error\":\"sin monto\"}");

        assertThatThrownBy(() -> service.draft(USER_ID, "fui al cine"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("DRAFT_NO_AMOUNT");
    }

    @Test
    @DisplayName("Un monto negativo no se acepta: el sentido del dinero lo da el tipo")
    void negativeAmountIsAnError() {
        modelAnswers("""
                {"type":"EXPENSE","amount":-20000,"description":"Gasolina","category":"transporte"}
                """);

        assertThatThrownBy(() -> service.draft(USER_ID, "gasolina menos 20 mil"))
                .extracting("code").isEqualTo("DRAFT_NO_AMOUNT");
    }

    @Test
    @DisplayName("Una respuesta que no es JSON es un error, nunca un dato inventado")
    void notJsonIsAnError() {
        modelAnswers("No entendí qué quieres registrar.");

        assertThatThrownBy(() -> service.draft(USER_ID, "hola"))
                .extracting("code").isEqualTo("AI_BAD_ANSWER");
    }

    @Test
    @DisplayName("«Ayer» sin hora queda al mediodía de ayer en Bogotá")
    void yesterdayWithoutTimeIsNoon() {
        OffsetDateTime now = OffsetDateTime.ofInstant(SATURDAY, BOGOTA);

        assertThat(MovementDraftService.occurredAt("2026-11-06", null, now, BOGOTA))
                .isEqualTo(OffsetDateTime.parse("2026-11-06T12:00:00-05:00"));
    }

    @Test
    @DisplayName("Una fecha futura se cambia por ahora: no se registra algo que no pasó")
    void futureDateBecomesNow() {
        OffsetDateTime now = OffsetDateTime.ofInstant(SATURDAY, BOGOTA);

        assertThat(MovementDraftService.occurredAt("2026-11-07", "22:00", now, BOGOTA)).isEqualTo(now);
        assertThat(MovementDraftService.occurredAt("2027-01-01", null, now, BOGOTA)).isEqualTo(now);
    }

    @Test
    @DisplayName("Una fecha mal escrita por el modelo se ignora y queda ahora")
    void badDateBecomesNow() {
        OffsetDateTime now = OffsetDateTime.ofInstant(SATURDAY, BOGOTA);

        assertThat(MovementDraftService.occurredAt("ayer", "tarde", now, BOGOTA)).isEqualTo(now);
    }
}
