package com.fluxia.backend.ai;

import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * El asistente financiero: el chat, el llenado de movimientos y la
 * propuesta de tope diario.
 *
 * <p>Son las unicas puertas por las que el cliente puede llegar al modelo
 * de IA. La clave de la API vive en el servidor y nunca se expone, de modo
 * que no existe forma de llamar al modelo sin pasar por aqui, con un token
 * de sesion valido.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "Asistente IA", description = "Chat con el asistente financiero")
public class AiController {

    private final AiChatService aiChatService;
    private final MovementDraftService movementDraftService;
    private final DailyLimitAdvisor dailyLimitAdvisor;

    public AiController(AiChatService aiChatService,
                        MovementDraftService movementDraftService,
                        DailyLimitAdvisor dailyLimitAdvisor) {
        this.aiChatService = aiChatService;
        this.movementDraftService = movementDraftService;
        this.dailyLimitAdvisor = dailyLimitAdvisor;
    }

    @PostMapping("/chat")
    @Operation(summary = "Preguntarle al asistente",
            description = """
                    El servidor adjunta a la pregunta el resumen financiero real del
                    usuario (saldo, totales por categoría, tendencia mensual y
                    últimos movimientos) calculado desde la base de datos, y después
                    consulta al modelo.

                    El cliente no envia ni puede modificar ese contexto: se construye
                    a partir del usuario del token.

                    Responde 503 si el asistente está desactivado (AI_ENABLED=false)
                    o sin clave configurada en el servidor.
                    """)
    public AiDtos.ChatResponse chat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AiDtos.ChatRequest request) {

        return aiChatService.answer(user.id(), request);
    }

    @PostMapping("/movement-draft")
    @Operation(summary = "Llenar un movimiento a partir de texto",
            description = """
                    Convierte un texto como «gasolina 20 mil» o «ayer me pagaron la
                    monitoría, 340 mil» en un movimiento: tipo, monto, descripción,
                    categoría y fecha.

                    No guarda nada. El cliente llena el formulario con la respuesta y
                    el usuario lo guarda con POST /transactions.

                    Responde 400 DRAFT_NO_AMOUNT si el texto no trae un monto.
                    """)
    public AiDtos.MovementDraft movementDraft(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AiDtos.DraftRequest request) {

        return movementDraftService.draft(user.id(), request.text());
    }

    @PostMapping("/daily-limit-proposal")
    @Operation(summary = "Proponer un tope diario",
            description = """
                    El servidor calcula el gasto promedio por día de los últimos 30
                    días, sin los gastos fijos, y la IA propone un tope que no lo
                    supere, con una explicación.

                    No guarda nada: el usuario lo acepta con PUT /spending/settings.

                    Responde 400 NOT_ENOUGH_DATA si no hay gastos en esos días.
                    """)
    public AiDtos.LimitProposal dailyLimitProposal(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody(required = false) AiDtos.ProposalRequest request) {

        List<UUID> excluded = request != null ? request.excludedCategoryIds() : null;
        return dailyLimitAdvisor.propose(user.id(), excluded);
    }
}
