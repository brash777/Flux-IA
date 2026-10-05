package com.fluxia.backend.shared;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Forma unica de todos los errores que devuelve la API.
 *
 * <p>Tener un solo formato permite que los tres clientes (movil,
 * escritorio, reloj) escriban el manejo de errores una sola vez.
 *
 * @param code    identificador estable del error, p. ej. EMAIL_TAKEN
 * @param message texto en espanol, apto para mostrar al usuario
 * @param fields  errores por campo cuando falla la validacion; null si no aplica
 * @param path    ruta que se estaba pidiendo
 */
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> fields,
        String path,
        OffsetDateTime timestamp
) {
    public static ApiErrorResponse of(String code, String message, String path) {
        return new ApiErrorResponse(code, message, null, path, OffsetDateTime.now());
    }

    public static ApiErrorResponse ofFields(String message, Map<String, String> fields, String path) {
        return new ApiErrorResponse("VALIDATION_ERROR", message, fields, path, OffsetDateTime.now());
    }
}
