package com.fluxia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.shared.ApiException;
import com.fluxia.backend.spending.DailyAverage;
import com.fluxia.backend.spending.SpendingService;
import com.fluxia.backend.transaction.CategoryTotal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * La IA propone un tope diario a partir de lo que el usuario gasta.
 *
 * <p>El reparto de tareas es el que se puede sustentar: el promedio lo
 * calcula el servidor con una suma en SQL, y el modelo solo elige un tope
 * por debajo de él y explica por qué. Si el modelo propone algo por encima
 * del promedio, o que no es un número, se descarta en lugar de mostrarlo.
 *
 * <p>El tope no se guarda aquí: el usuario lo ve, lo cambia si quiere y lo
 * acepta con {@code PUT /spending/settings}.
 */
@Service
public class DailyLimitAdvisor {

    static final String SYSTEM_PROMPT = """
            Eres el asistente de Flux IA. La persona quiere un tope de gasto diario
            para dejar de comprar por impulso. Propón uno con sus datos.

            Responde SOLO con un objeto JSON, sin texto antes ni después:
            {"dailyLimit": número en pesos sin separadores de miles, "reason": "texto"}

            Reglas:
            1. El tope no puede superar el promedio por día de los datos: la idea
               es gastar un poco menos que ahora. Redondéalo hacia abajo a miles.
            2. reason son dos frases cortas, tuteando, en español de Colombia: cuál
               es su promedio por día y qué categorías pesan más. Usa solo cifras
               que aparezcan en los datos, escritas igual, además del tope que
               propones, que va con el mismo formato ($25.000). Texto plano, sin
               listas ni negritas.
            """;

    private static final int MAX_REASON = 400;

    private final AiChatService aiChatService;
    private final SpendingService spendingService;
    private final ObjectMapper objectMapper;

    public DailyLimitAdvisor(AiChatService aiChatService,
                             SpendingService spendingService,
                             ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.spendingService = spendingService;
        this.objectMapper = objectMapper;
    }

    public AiDtos.LimitProposal propose(UUID userId, List<UUID> excludedCategoryIds) {
        DailyAverage average = spendingService.dailyAverage(userId, excludedCategoryIds);
        if (average.total().signum() <= 0) {
            // Sin gastos no hay nada que proponer, y no se gasta una
            // consulta al modelo para decirlo.
            throw ApiException.badRequest("NOT_ENOUGH_DATA",
                    "Todavía no tienes gastos del día a día para proponerte un tope. "
                            + "Escríbelo tú, y cuando registres algunos te propongo uno.");
        }

        ChatReply reply = aiChatService.complete(userId, SYSTEM_PROMPT,
                List.of(new AiDtos.Turn("user", data(average))));
        JsonNode json = AiJson.object(objectMapper, reply.text());

        BigDecimal limit = AiJson.amount(json.get("dailyLimit"));
        if (!isAcceptable(limit, average.perDay())) {
            throw ApiException.unavailable("AI_BAD_ANSWER",
                    "El asistente propuso un tope que no cuadra con tus datos. "
                            + "Inténtalo de nuevo o escríbelo tú.");
        }

        return new AiDtos.LimitProposal(
                limit.setScale(2, RoundingMode.HALF_UP),
                reason(AiJson.text(json.get("reason"))),
                average.days(),
                average.perDay());
    }

    /**
     * Mayor que cero y no más que el promedio. Se admite hasta el promedio
     * redondeado hacia arriba a miles: si el promedio es $23.400, un tope de
     * $24.000 es el modelo redondeando, no proponiendo gastar más.
     */
    static boolean isAcceptable(BigDecimal limit, BigDecimal perDay) {
        return limit != null
                && limit.signum() > 0
                && limit.compareTo(perDay.setScale(-3, RoundingMode.CEILING)) <= 0;
    }

    /** Lo que lee el modelo. Todas las cifras salen de {@link DailyAverage}. */
    static String data(DailyAverage average) {
        StringBuilder data = new StringBuilder(512);
        data.append("DATOS DE LOS ÚLTIMOS ").append(average.days()).append(" DÍAS (sin contar hoy)\n");
        data.append("  Gasto que cuenta para el tope: ").append(AiFormat.money(average.total())).append('\n');
        data.append("  Promedio por día: ").append(AiFormat.money(average.perDay())).append('\n');
        data.append("  No cuentan (gastos fijos): ")
                .append(average.excludedNames().isEmpty() ? "ninguno" : String.join(", ", average.excludedNames()))
                .append("\n\nPor categoría\n");
        for (CategoryTotal category : average.categories()) {
            data.append("  ").append(category.name()).append(": ").append(AiFormat.money(category.total()))
                    .append(" (").append(category.movements())
                    .append(category.movements() == 1 ? " movimiento)\n" : " movimientos)\n");
        }
        return data.toString();
    }

    /** Sin las marcas de formato que a veces agrega el modelo. */
    private static String reason(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace("**", "").strip();
        return clean.length() > MAX_REASON ? clean.substring(0, MAX_REASON).strip() + "…" : clean;
    }
}
