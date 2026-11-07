package com.fluxia.backend.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxia.backend.shared.ApiException;

import java.math.BigDecimal;

/**
 * Lee la respuesta del modelo cuando se le pidió un JSON.
 *
 * <p>No se usa el «modo JSON» de ningún proveedor: cada uno lo pide
 * distinto y Anthropic no lo tiene. Se le pide JSON en las instrucciones y
 * aquí se toma el primer objeto que aparezca, aunque venga entre texto o
 * dentro de un bloque de código. Lo que no se pueda leer es un error, nunca
 * un dato inventado.
 */
final class AiJson {

    private AiJson() {
    }

    /** El primer objeto JSON del texto. */
    static JsonNode object(ObjectMapper mapper, String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw unusable();
        }
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            if (!node.isObject()) {
                throw unusable();
            }
            return node;
        } catch (JsonProcessingException ex) {
            throw unusable();
        }
    }

    /**
     * Un monto: un número, o un texto con solo dígitos y a lo sumo un punto
     * decimal. Devuelve null si no es eso: «20.000» es ambiguo y no se
     * adivina.
     */
    static BigDecimal amount(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String text = node.asText("").replaceAll("[\\s$]", "");
        return text.matches("\\d+(\\.\\d{1,2})?") ? new BigDecimal(text) : null;
    }

    /** Un texto, o null si el campo falta o no es texto. */
    static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText().trim() : null;
    }

    static ApiException unusable() {
        return ApiException.unavailable("AI_BAD_ANSWER",
                "El asistente respondió algo que no pude usar. Inténtalo de nuevo.");
    }
}
