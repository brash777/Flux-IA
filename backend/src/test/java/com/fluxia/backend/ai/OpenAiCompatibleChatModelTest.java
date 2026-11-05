package com.fluxia.backend.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pruebas del cliente compatible con OpenAI (Groq, Gemini) contra un
 * servidor simulado: que pedido arma y como traduce cada respuesta. No
 * usan la red ni gastan cuota.
 */
class OpenAiCompatibleChatModelTest {

    private static final String BASE = "https://ia.prueba/v1";
    private static final List<AiDtos.Turn> CONVERSATION = List.of(
            new AiDtos.Turn("user", "Hola"),
            new AiDtos.Turn("assistant", "¿En qué te ayudo?"),
            new AiDtos.Turn("user", "DATOS\n\nPREGUNTA DEL USUARIO\n¿Cuánto ahorré?"));

    private MockRestServiceServer server;
    private OpenAiCompatibleChatModel model;

    @BeforeEach
    void setUp() {
        model = modelWithEffort("low");
    }

    private OpenAiCompatibleChatModel modelWithEffort(String effort) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenAiCompatibleChatModel(builder.build(), 1024, effort);
    }

    @Test
    @DisplayName("Arma el pedido en formato OpenAI: las reglas primero, la conversación en orden, el tope y el esfuerzo")
    void buildsTheRequest() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("REGLAS"))
                .andExpect(jsonPath("$.messages[1].content").value("Hola"))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(jsonPath("$.max_completion_tokens").value(1024))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant",
                           "content":" Ahorraste **$ 40.750,00**. ","reasoning":"Resto 58000 - 17250..."},
                           "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":812,"completion_tokens":64}}
                        """, MediaType.APPLICATION_JSON));

        ChatReply reply = model.reply("openai/gpt-oss-120b", "REGLAS", CONVERSATION);

        // Solo el contenido: el razonamiento interno no llega al usuario.
        assertThat(reply.text()).isEqualTo("Ahorraste **$ 40.750,00**.");
        assertThat(reply.inputTokens()).isEqualTo(812);
        assertThat(reply.outputTokens()).isEqualTo(64);
        server.verify();
    }

    @Test
    @DisplayName("Sin esfuerzo configurado, el parámetro no se envía, ni siquiera como null")
    void omitsMissingReasoningEffort() {
        model = modelWithEffort(null);
        server.expect(requestTo(BASE + "/chat/completions"))
                .andExpect(jsonPath("$.reasoning_effort").doesNotExist())
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"Listo"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(model.reply("m", "REGLAS", CONVERSATION).text()).isEqualTo("Listo");
        server.verify();
    }

    @Test
    @DisplayName("Un 429 es cuota agotada: se puede probar con otro modelo")
    void tooManyRequestsIsRateLimited() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":{\"message\":\"Rate limit reached\"}}"));

        assertThatThrownBy(() -> model.reply("m", "REGLAS", CONVERSATION))
                .isInstanceOfSatisfying(ChatModelException.class, ex -> {
                    assertThat(ex.isRateLimited()).isTrue();
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    @DisplayName("Un 401 es la clave rechazada: no tiene sentido probar otro modelo")
    void unauthorizedIsNotRetryable() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":{\"message\":\"Invalid API Key\"}}"));

        assertThatThrownBy(() -> model.reply("m", "REGLAS", CONVERSATION))
                .isInstanceOfSatisfying(ChatModelException.class,
                        ex -> assertThat(ex.isRetryable()).isFalse());
    }

    @Test
    @DisplayName("Un error 500 del proveedor se puede reintentar con otro modelo")
    void serverErrorIsRetryable() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> model.reply("m", "REGLAS", CONVERSATION))
                .isInstanceOfSatisfying(ChatModelException.class, ex -> {
                    assertThat(ex.isRetryable()).isTrue();
                    assertThat(ex.isRateLimited()).isFalse();
                });
    }

    @Test
    @DisplayName("Si el modelo no escribe nada, la respuesta llega vacía y no como error")
    void nullContentIsEmptyText() {
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"role":"assistant","content":null},"finish_reason":"length"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":1024}}
                        """, MediaType.APPLICATION_JSON));

        ChatReply reply = model.reply("m", "REGLAS", CONVERSATION);

        assertThat(reply.text()).isEmpty();
        assertThat(reply.stopReason()).isEqualTo("length");
    }

    @Test
    @DisplayName("Los niveles de esfuerzo pasan al formato de OpenAI, y uno mal escrito falla al arrancar")
    void reasoningEffortMapping() {
        assertThat(AiConfig.reasoningEffort("LOW")).isEqualTo("low");
        assertThat(AiConfig.reasoningEffort("medium")).isEqualTo("medium");
        assertThat(AiConfig.reasoningEffort("XHIGH")).isEqualTo("high");
        assertThatThrownBy(() -> AiConfig.reasoningEffort("RAPIDO"))
                .isInstanceOf(IllegalStateException.class);
    }
}
