package com.fluxia.backend.ai;

import com.fluxia.backend.transaction.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contratos del chat con el asistente de IA. */
public final class AiDtos {

    private AiDtos() {
    }

    @Schema(description = "Pregunta del usuario al asistente")
    public record ChatRequest(

            /*
             * Se limita el largo por dos razones: una pregunta de 50 000
             * caracteres costaria dinero real en tokens, y es la forma
             * mas facil de intentar saturar el servicio.
             */
            @NotBlank(message = "La pregunta es obligatoria.")
            @Size(max = 2000, message = "La pregunta no puede exceder 2000 caracteres.")
            String question,

            @Schema(description = "Turnos previos de la conversación, del más antiguo al más reciente. Opcional.")
            @Size(max = 20, message = "Se admiten como máximo 20 turnos previos.")
            List<Turn> history
    ) {
    }

    @Schema(description = "Un turno de la conversación")
    public record Turn(

            @Schema(description = "user o assistant")
            @NotBlank(message = "El rol es obligatorio.")
            String role,

            @NotBlank(message = "El contenido es obligatorio.")
            @Size(max = 4000, message = "El contenido es demasiado largo.")
            String content
    ) {
    }

    @Schema(description = "Respuesta del asistente")
    public record ChatResponse(
            String answer,
            OffsetDateTime answeredAt,
            @Schema(description = "Tokens consumidos. Se devuelve para poder medir el costo del servicio.")
            TokenUsage usage
    ) {
    }

    @Schema(description = "Consumo de tokens de la llamada")
    public record TokenUsage(long inputTokens, long outputTokens) {
    }

    @Schema(description = "Un movimiento escrito como lo diría el usuario")
    public record DraftRequest(

            @NotBlank(message = "Escribe el movimiento, por ejemplo «gasolina 20 mil».")
            @Size(max = 300, message = "El texto no puede exceder 300 caracteres.")
            String text
    ) {
    }

    @Schema(description = """
            Movimiento que propone la IA. No se guarda: el usuario lo revisa en el
            formulario y lo guarda con POST /transactions, con sus validaciones.
            """)
    public record MovementDraft(
            TransactionType type,
            BigDecimal amount,
            String description,
            UUID categoryId,
            OffsetDateTime occurredAt
    ) {
    }

    @Schema(description = "Datos para proponer un tope diario")
    public record ProposalRequest(

            @Schema(description = """
                    Gastos fijos que no cuentan para el tope. Si se omite, se usan los
                    que el usuario ya guardó.
                    """)
            @Size(max = 50, message = "Se admiten como máximo 50 categorías.")
            List<UUID> excludedCategoryIds
    ) {
    }

    @Schema(description = "Tope diario que propone la IA. No se guarda hasta que el usuario lo acepta.")
    public record LimitProposal(
            BigDecimal dailyLimit,
            @Schema(description = "Por qué ese tope, en una o dos frases")
            String reason,
            @Schema(description = "Cuántos días se miraron")
            int days,
            @Schema(description = "Gasto promedio por día de esos días, sin los gastos fijos")
            BigDecimal averagePerDay
    ) {
    }
}
