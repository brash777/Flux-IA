package com.fluxia.backend.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.spending.DailyAverage;
import com.fluxia.backend.spending.SpendingService;
import com.fluxia.backend.transaction.CategoryTotal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la propuesta de tope: el promedio lo pone el servidor y el
 * modelo no puede proponer gastar más que eso.
 */
@ExtendWith(MockitoExtension.class)
class DailyLimitAdvisorTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private AiChatService aiChatService;

    @Mock
    private SpendingService spendingService;

    private DailyLimitAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new DailyLimitAdvisor(aiChatService, spendingService, new ObjectMapper());
    }

    private void averageIs(String total, String perDay) {
        when(spendingService.dailyAverage(eq(USER_ID), any())).thenReturn(new DailyAverage(
                30, new BigDecimal(total), new BigDecimal(perDay),
                List.of(new CategoryTotal(UUID.randomUUID(), "comida", "Comida", "", "#000000", new BigDecimal("300000"), 20)),
                List.of("Universidad")));
    }

    private void modelAnswers(String text) {
        when(aiChatService.complete(eq(USER_ID), eq(DailyLimitAdvisor.SYSTEM_PROMPT), anyList()))
                .thenReturn(new ChatReply(text, 300, 40, "stop"));
    }

    @Test
    @DisplayName("Un tope por debajo del promedio se acepta, con su explicación")
    void acceptsLimitBelowAverage() {
        averageIs("703700.00", "23456.67");
        modelAnswers("""
                {"dailyLimit": 20000, "reason": "Gastas **$23.456,67** al día, sobre todo en Comida."}
                """);

        AiDtos.LimitProposal proposal = advisor.propose(USER_ID, null);

        assertThat(proposal.dailyLimit()).isEqualByComparingTo("20000");
        assertThat(proposal.averagePerDay()).isEqualByComparingTo("23456.67");
        assertThat(proposal.reason()).doesNotContain("**");
    }

    @Test
    @DisplayName("Si el modelo propone gastar más que el promedio, se descarta")
    void rejectsLimitAboveAverage() {
        averageIs("703700.00", "23456.67");
        modelAnswers("{\"dailyLimit\": 50000, \"reason\": \"Te alcanza.\"}");

        assertThatThrownBy(() -> advisor.propose(USER_ID, null))
                .extracting("code").isEqualTo("AI_BAD_ANSWER");
    }

    @Test
    @DisplayName("Redondear el promedio a los miles de arriba no cuenta como proponer de más")
    void roundingUpToThousandsIsAllowed() {
        BigDecimal perDay = new BigDecimal("23456.67");

        assertThat(DailyLimitAdvisor.isAcceptable(new BigDecimal("24000"), perDay)).isTrue();
        assertThat(DailyLimitAdvisor.isAcceptable(new BigDecimal("25000"), perDay)).isFalse();
        assertThat(DailyLimitAdvisor.isAcceptable(BigDecimal.ZERO, perDay)).isFalse();
    }

    @Test
    @DisplayName("Sin gastos no se consulta al modelo: no hay nada que proponer")
    void noExpensesDoesNotCallTheModel() {
        averageIs("0", "0");

        assertThatThrownBy(() -> advisor.propose(USER_ID, List.of()))
                .extracting("code").isEqualTo("NOT_ENOUGH_DATA");
        verify(aiChatService, never()).complete(any(), anyString(), anyList());
    }

    @Test
    @DisplayName("El modelo recibe el promedio y los gastos fijos tal como se ven en la app")
    void dataUsesAppFormat() {
        String data = DailyLimitAdvisor.data(new DailyAverage(30, new BigDecimal("703700.00"),
                new BigDecimal("23456.67"), List.of(), List.of("Universidad", "Servicios")));

        assertThat(data).contains("Promedio por día: $23.456,67")
                .contains("No cuentan (gastos fijos): Universidad, Servicios");
    }
}
