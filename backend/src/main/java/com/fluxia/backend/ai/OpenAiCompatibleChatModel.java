package com.fluxia.backend.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente de las APIs compatibles con OpenAI (chat completions): Groq,
 * Gemini y cualquier otra que hable ese formato.
 *
 * <p>Se usa el RestClient de Spring y no un SDK por proveedor: es un solo
 * endpoint, y asi cambiar de proveedor no agrega dependencias. El
 * RestClient llega armado desde {@link AiConfig} con la URL base, la clave
 * y los tiempos de espera; esta clase solo arma el pedido y lee la
 * respuesta.
 */
public class OpenAiCompatibleChatModel implements ChatModel {

    private final RestClient restClient;
    private final int maxTokens;
    private final String reasoningEffort;

    public OpenAiCompatibleChatModel(RestClient restClient, int maxTokens, String reasoningEffort) {
        this.restClient = restClient;
        this.maxTokens = maxTokens;
        this.reasoningEffort = reasoningEffort;
    }

    @Override
    public ChatReply reply(String model, String systemPrompt, List<AiDtos.Turn> conversation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        for (AiDtos.Turn turn : conversation) {
            messages.add(new Message(turn.role(), turn.content()));
        }

        JsonNode response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CompletionRequest(model, messages, maxTokens, reasoningEffort))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw translate(model, ex);
        } catch (RestClientException ex) {
            // Sin respuesta util: tiempo de espera agotado, sin red o un
            // cuerpo que no es JSON.
            throw ChatModelException.unavailable(model + " no respondio: " + ex.getMessage(), ex);
        }
        return toReply(response);
    }

    private static ChatModelException translate(String model, RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        // El cuerpo del error puede traer datos de la cuenta: queda en el
        // mensaje de la excepcion, que va al log y nunca al cliente.
        String detail = model + " respondio " + status + ": " + abbreviate(ex.getResponseBodyAsString());
        if (status == 429) {
            return ChatModelException.rateLimited(detail, ex);
        }
        if (status == 401 || status == 403) {
            return ChatModelException.rejected(detail, ex);
        }
        return ChatModelException.unavailable(detail, ex);
    }

    /**
     * Solo se lee {@code content}: los modelos con razonamiento lo
     * devuelven aparte, y al usuario le llega unicamente la respuesta.
     */
    private static ChatReply toReply(JsonNode response) {
        JsonNode body = response == null ? MissingNode.getInstance() : response;
        JsonNode choice = body.path("choices").path(0);
        JsonNode usage = body.path("usage");
        return new ChatReply(
                choice.path("message").path("content").asText("").trim(),
                usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0),
                choice.path("finish_reason").asText("sin respuesta"));
    }

    private static String abbreviate(String text) {
        return text == null || text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    /**
     * El pedido en el formato de OpenAI. Los nulos no se envian: un
     * parametro presente con valor null es un error para varios proveedores.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CompletionRequest(
            String model,
            List<Message> messages,
            @JsonProperty("max_completion_tokens") int maxTokens,
            @JsonProperty("reasoning_effort") String reasoningEffort) {
    }

    record Message(String role, String content) {
    }
}
