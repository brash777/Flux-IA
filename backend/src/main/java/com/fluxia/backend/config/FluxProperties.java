package com.fluxia.backend.config;

import com.fluxia.backend.ai.AiProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Todos los ajustes propios de la aplicacion, tipados.
 *
 * <p>La ventaja de tiparlos frente a leerlos con {@code @Value} suelto:
 * si falta un valor o tiene un formato invalido, la aplicacion falla al
 * arrancar con un mensaje claro, en lugar de reventar mas tarde en
 * medio de una peticion.
 */
@ConfigurationProperties(prefix = "flux")
public class FluxProperties {

    /**
     * Zona horaria con la que se delimitan los periodos de los reportes.
     *
     * <p>Importa mas de lo que parece: las fechas se guardan en UTC, y
     * sin esta conversion un gasto del 31 de diciembre a las 21:00 de
     * Bogotá caeria en enero del ano siguiente.
     */
    private ZoneId timezone = ZoneId.of("America/Bogota");

    private final Jwt jwt = new Jwt();
    private final Ai ai = new Ai();
    private final Cors cors = new Cors();
    private final PasswordReset passwordReset = new PasswordReset();

    public ZoneId getTimezone() {
        return timezone;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Ai getAi() {
        return ai;
    }

    public Cors getCors() {
        return cors;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    /** Ajustes de firma y vigencia de los tokens. */
    public static class Jwt {

        /** Clave de firma HMAC. Viene de la variable JWT_SECRET. */
        private String secret;

        private String issuer = "flux-ia";

        /** Vigencia del token de acceso: corta a proposito. */
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        /** Vigencia del token de refresco, revocable en base de datos. */
        private Duration refreshTokenTtl = Duration.ofDays(30);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    /** Ajustes del asistente de IA. La clave jamas sale del servidor. */
    public static class Ai {

        private boolean enabled = true;

        /** Groq, Gemini, Anthropic o cualquier API compatible con OpenAI. */
        private AiProvider provider = AiProvider.GROQ;

        private String apiKey;

        /**
         * Base alternativa de la API. Vacia, se usa la del proveedor; hace
         * falta solo con {@code openai} o para apuntar a un servidor
         * simulado.
         */
        private String baseUrl;

        /** Modelo principal. Vacio, se usa el del proveedor. */
        private String model;

        /**
         * Modelos que se prueban, en orden, si el principal no responde.
         * Vacio, se usan los del proveedor.
         */
        private List<String> fallbackModels = new ArrayList<>();

        /**
         * Tope de tokens de la respuesta. En los modelos con razonamiento,
         * el razonamiento tambien consume de este tope, de modo que un
         * valor muy ajustado puede truncar la respuesta.
         */
        private int maxTokens = 4096;

        /**
         * Cuantos turnos previos de la conversacion se envian al modelo.
         * Alcanzan para las repreguntas y mantienen el pedido chico.
         */
        private int maxHistoryTurns = 8;

        /**
         * Profundidad de razonamiento: LOW, MEDIUM, HIGH, XHIGH o MAX.
         * Para respuestas de chat alcanza LOW, que es la opcion mas
         * economica. Se declara explicito porque en Opus 5.5 el valor
         * por omision es MEDIUM.
         */
        private String effort = "LOW";

        private Duration timeout = Duration.ofSeconds(60);

        /**
         * Los modelos en el orden en que se prueban: el principal y los de
         * respaldo. Lo que no se configuro sale del proveedor.
         */
        public List<String> modelChain() {
            boolean customModel = model != null && !model.isBlank();
            List<String> chain = new ArrayList<>();
            chain.add(customModel ? model : provider.getDefaultModel());
            // Con un modelo elegido a mano no se suman los respaldos del
            // proveedor: podrian ser de otra familia y responder distinto.
            if (!fallbackModels.isEmpty()) {
                chain.addAll(fallbackModels);
            } else if (!customModel) {
                chain.addAll(provider.getDefaultFallbacks());
            }
            return chain.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public AiProvider getProvider() {
            return provider;
        }

        public void setProvider(AiProvider provider) {
            this.provider = provider;
        }

        public List<String> getFallbackModels() {
            return fallbackModels;
        }

        public void setFallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels == null ? new ArrayList<>() : fallbackModels;
        }

        public int getMaxHistoryTurns() {
            return maxHistoryTurns;
        }

        public void setMaxHistoryTurns(int maxHistoryTurns) {
            this.maxHistoryTurns = maxHistoryTurns;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public String getEffort() {
            return effort;
        }

        public void setEffort(String effort) {
            this.effort = effort;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /** Origenes del frontend autorizados a llamar a la API. */
    public static class Cors {

        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** Vigencia del enlace de recuperacion de contrasena. */
    public static class PasswordReset {

        private Duration tokenTtl = Duration.ofMinutes(30);

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }
    }
}
