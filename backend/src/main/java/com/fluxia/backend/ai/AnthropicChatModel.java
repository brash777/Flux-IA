package com.fluxia.backend.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import java.util.List;
import java.util.Locale;

/**
 * Cliente de Claude con el SDK oficial de Anthropic.
 *
 * <p>Es el proveedor de pago. Se conserva detras de la misma interfaz que
 * los gratuitos para poder volver a el con una variable de entorno
 * (AI_PROVIDER=anthropic), sin tocar el asistente.
 */
public class AnthropicChatModel implements ChatModel, AutoCloseable {

    private final AnthropicClient client;
    private final int maxTokens;
    private final OutputConfig.Effort effort;

    public AnthropicChatModel(AnthropicClient client, int maxTokens, String effort) {
        this.client = client;
        this.maxTokens = maxTokens;
        // Se traduce al construir: un valor mal escrito falla al arrancar y
        // no en la primera pregunta de un usuario.
        this.effort = effortFrom(effort);
    }

    @Override
    public ChatReply reply(String model, String systemPrompt, List<AiDtos.Turn> conversation) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(systemPrompt)
                .outputConfig(OutputConfig.builder().effort(effort).build());

        for (AiDtos.Turn turn : conversation) {
            if ("assistant".equals(turn.role())) {
                params.addAssistantMessage(turn.content());
            } else {
                params.addUserMessage(turn.content());
            }
        }

        try {
            Message response = client.messages().create(params.build());
            return new ChatReply(
                    extractText(response),
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    stopReasonOf(response));

        } catch (RateLimitException ex) {
            throw ChatModelException.rateLimited("Limite de peticiones de Anthropic con " + model, ex);
        } catch (UnauthorizedException | PermissionDeniedException ex) {
            throw ChatModelException.rejected("Anthropic rechazo la clave", ex);
        } catch (AnthropicException ex) {
            // Errores HTTP del servicio y tambien los de red o de tiempo de
            // espera, que no son AnthropicServiceException.
            throw ChatModelException.unavailable("Error de Anthropic con " + model, ex);
        }
    }

    /** Spring lo llama al apagar: el cliente del SDK tiene su pool de conexiones. */
    @Override
    public void close() {
        client.close();
    }

    /** Concatena los bloques de texto; se ignoran los de razonamiento. */
    private static String extractText(Message response) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(textBlock -> text.append(textBlock.text()));
        }
        return text.toString().trim();
    }

    /** El motivo de corte; si el modelo declino, con su categoria. */
    private static String stopReasonOf(Message response) {
        String reason = response.stopReason().map(Object::toString).orElse("desconocido");
        if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            reason += response.stopDetails()
                    .map(details -> " (" + details.category() + ")")
                    .orElse("");
        }
        return reason;
    }

    /**
     * Traduce el nivel de esfuerzo configurado a la constante del SDK.
     *
     * <p>Se usa un switch y no una conversion automatica para que un
     * valor mal escrito en la configuracion falle aqui, con un mensaje
     * claro, en lugar de llegar a la API y volver como un 400 opaco.
     */
    static OutputConfig.Effort effortFrom(String effort) {
        String normalized = effort == null ? "LOW" : effort.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> OutputConfig.Effort.LOW;
            case "MEDIUM" -> OutputConfig.Effort.MEDIUM;
            case "HIGH" -> OutputConfig.Effort.HIGH;
            case "XHIGH" -> OutputConfig.Effort.XHIGH;
            case "MAX" -> OutputConfig.Effort.MAX;
            default -> throw new IllegalStateException(
                    "flux.ai.effort inválido: '" + effort
                            + "'. Valores admitidos: LOW, MEDIUM, HIGH, XHIGH, MAX.");
        };
    }
}
