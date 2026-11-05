package com.fluxia.backend.ai;

import java.util.List;

/**
 * Proveedores de IA que admite el asistente, con sus valores por omision.
 *
 * <p>Groq y Gemini exponen su API en el formato de OpenAI (chat
 * completions): para ellos basta conocer la URL base y el modelo, y un
 * mismo cliente sirve para los dos. Por eso el proveedor se cambia con una
 * variable de entorno, sin tocar codigo. Anthropic usa su SDK oficial.
 *
 * <p>Groq es el proveedor por omision porque su plan gratuito no pide
 * tarjeta y alcanza para la demo: 1.000 consultas por dia y 8.000 tokens
 * por minuto, por modelo. Como cada modelo tiene su propia cuota, el de
 * respaldo responde cuando el principal la agota.
 */
public enum AiProvider {

    GROQ("https://api.groq.com/openai/v1",
            "openai/gpt-oss-120b",
            List.of("openai/gpt-oss-20b"),
            "https://console.groq.com/keys"),

    GEMINI("https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-3.8-flash",
            List.of("gemini-3.6-flash", "gemini-3.5-flash-lite"),
            "https://aistudio.google.com/apikey"),

    /** Cualquier otra API compatible con OpenAI: exige AI_BASE_URL y AI_MODEL. */
    OPENAI(null, null, List.of(), "la consola de tu proveedor"),

    ANTHROPIC(null, "claude-opus-5-5", List.of(), "https://console.anthropic.com");

    private final String defaultBaseUrl;
    private final String defaultModel;
    private final List<String> defaultFallbacks;
    private final String keyConsole;

    AiProvider(String defaultBaseUrl, String defaultModel, List<String> defaultFallbacks, String keyConsole) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.defaultFallbacks = defaultFallbacks;
        this.keyConsole = keyConsole;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public List<String> getDefaultFallbacks() {
        return defaultFallbacks;
    }

    /** Donde se saca la clave: va en el mensaje de error del arranque. */
    public String getKeyConsole() {
        return keyConsole;
    }

    /** Si se le habla con el cliente compatible con OpenAI. */
    public boolean isOpenAiCompatible() {
        return this != ANTHROPIC;
    }
}
