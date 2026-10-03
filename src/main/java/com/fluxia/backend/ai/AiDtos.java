package com.fluxia.backend.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

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

            @Schema(description = "Turnos previos de la conversacion, del mas antiguo al mas reciente. Opcional.")
            @Size(max = 20, message = "Se admiten como maximo 20 turnos previos.")
            List<Turn> history
    ) {
    }

    @Schema(description = "Un turno de la conversacion")
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
}
