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

/**
 * Chat con el asistente financiero.
 *
 * <p>Un solo endpoint, y a proposito: es la unica puerta por la que el
 * cliente puede llegar al modelo de IA. La clave de la API vive en el
 * servidor y nunca se expone, de modo que no existe forma de llamar al
 * modelo sin pasar por aqui, con un token de sesion valido.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "Asistente IA", description = "Chat con el asistente financiero")
public class AiController {

    private final AiChatService aiChatService;

    public AiController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Preguntarle al asistente",
            description = """
                    El servidor adjunta a la pregunta el resumen financiero real del
                    usuario (saldo, totales por categoria, tendencia mensual y
                    ultimos movimientos) calculado desde la base de datos, y despues
                    consulta al modelo.

                    El cliente no envia ni puede modificar ese contexto: se construye
                    a partir del usuario del token.

                    Responde 503 si el asistente esta desactivado (AI_ENABLED=false)
                    o sin clave configurada en el servidor.
                    """)
    public AiDtos.ChatResponse chat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AiDtos.ChatRequest request) {

        return aiChatService.answer(user.id(), request);
    }
}
