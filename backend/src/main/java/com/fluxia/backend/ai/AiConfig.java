package com.fluxia.backend.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fluxia.backend.config.FluxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

/**
 * Crea el cliente del modelo de IA segun el proveedor configurado
 * (AI_PROVIDER): Groq o Gemini con el cliente compatible con OpenAI, o
 * Anthropic con su SDK.
 *
 * <p>Se construye una sola vez, como bean, y no en cada peticion: el
 * cliente mantiene un pool de conexiones HTTP reutilizable.
 *
 * <p>{@code @ConditionalOnProperty} hace que el bean no exista si
 * {@code flux.ai.enabled} es false. Asi se puede desarrollar el resto
 * de la aplicacion sin clave de IA: el endpoint de chat responde 503 y
 * todo lo demas funciona igual.
 */
@Configuration
@ConditionalOnProperty(prefix = "flux.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public ChatModel chatModel(FluxProperties properties, RestClient.Builder restClientBuilder) {
        FluxProperties.Ai ai = properties.getAi();
        AiProvider provider = ai.getProvider();

        if (!hasText(ai.getApiKey())) {
            throw new IllegalStateException("""
                    Falta AI_API_KEY y flux.ai.enabled es true.

                    Opciones:
                      1. Poner la clave en el archivo .env. Para %s se obtiene en
                         %s
                      2. Desactivar la IA con AI_ENABLED=false mientras
                         trabajas en el resto de la aplicacion.
                    """.formatted(provider, provider.getKeyConsole()));
        }
        if (ai.modelChain().isEmpty()) {
            throw new IllegalStateException(
                    "Falta AI_MODEL: con AI_PROVIDER=" + provider + " no hay un modelo por omision.");
        }

        return provider.isOpenAiCompatible()
                ? openAiCompatible(ai, provider, restClientBuilder)
                : anthropic(ai);
    }

    private ChatModel openAiCompatible(FluxProperties.Ai ai, AiProvider provider, RestClient.Builder builder) {
        String baseUrl = hasText(ai.getBaseUrl()) ? ai.getBaseUrl() : provider.getDefaultBaseUrl();
        if (!hasText(baseUrl)) {
            throw new IllegalStateException(
                    "Falta AI_BASE_URL: con AI_PROVIDER=" + provider + " hay que indicar la URL de la API.");
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(ai.getTimeout());

        RestClient restClient = builder
                .baseUrl(baseUrl.trim().replaceAll("/+$", ""))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ai.getApiKey().trim())
                .requestFactory(requestFactory)
                .build();
        return new OpenAiCompatibleChatModel(restClient, ai.getMaxTokens(), reasoningEffort(ai.getEffort()));
    }

    private ChatModel anthropic(FluxProperties.Ai ai) {
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(ai.getApiKey().trim())
                .timeout(ai.getTimeout())
                // El SDK reintenta solo los errores que tiene sentido
                // reintentar: 429 y 5xx. No reintenta un 400.
                .maxRetries(2);

        // Solo se cambia la base cuando se apunta a un servidor
        // simulado; en condiciones normales queda la oficial.
        if (hasText(ai.getBaseUrl())) {
            builder.baseUrl(ai.getBaseUrl());
        }
        return new AnthropicChatModel(builder.build(), ai.getMaxTokens(), ai.getEffort());
    }

    /**
     * El esfuerzo de razonamiento en el formato de OpenAI. Groq y Gemini
     * aceptan low, medium y high; los niveles extra de Anthropic van al mas
     * alto. Un valor mal escrito falla al arrancar, no en una pregunta.
     */
    static String reasoningEffort(String effort) {
        String normalized = effort == null ? "LOW" : effort.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW" -> "low";
            case "MEDIUM" -> "medium";
            case "HIGH", "XHIGH", "MAX" -> "high";
            default -> throw new IllegalStateException(
                    "flux.ai.effort inválido: '" + effort
                            + "'. Valores admitidos: LOW, MEDIUM, HIGH, XHIGH, MAX.");
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
