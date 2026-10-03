package com.fluxia.backend.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fluxia.backend.config.FluxProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea el cliente del modelo de IA.
 *
 * <p>Se construye una sola vez, como bean, y no en cada peticion: el
 * cliente mantiene un pool de conexiones HTTP reutilizable.
 *
 * <p>{@code @ConditionalOnProperty} hace que el bean no exista si
 * {@code flux.ai.enabled} es false. Asi se puede desarrollar el resto
 * de la aplicacion sin clave de IA y sin gastar credito: el endpoint de
 * chat responde 503 y todo lo demas funciona igual.
 */
@Configuration
@ConditionalOnProperty(prefix = "flux.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiConfig {

    @Bean
    public AnthropicClient anthropicClient(FluxProperties properties) {
        FluxProperties.Ai ai = properties.getAi();

        if (ai.getApiKey() == null || ai.getApiKey().isBlank()) {
            throw new IllegalStateException("""
                    Falta ANTHROPIC_API_KEY y flux.ai.enabled es true.

                    Opciones:
                      1. Poner la clave en el archivo .env (se obtiene en
                         https://console.anthropic.com -> API Keys).
                      2. Desactivar la IA con AI_ENABLED=false mientras
                         trabajas en el resto de la aplicacion.
                    """);
        }

        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(ai.getApiKey())
                .timeout(ai.getTimeout())
                // El SDK reintenta solo los errores que tiene sentido
                // reintentar: 429 y 5xx. No reintenta un 400.
                .maxRetries(2);

        // Solo se cambia la base cuando se apunta a un servidor
        // simulado; en condiciones normales queda la oficial.
        if (ai.getBaseUrl() != null && !ai.getBaseUrl().isBlank()) {
            builder.baseUrl(ai.getBaseUrl());
        }

        return builder.build();
    }
}
