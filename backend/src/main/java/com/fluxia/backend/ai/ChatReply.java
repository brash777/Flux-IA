package com.fluxia.backend.ai;

/**
 * Lo que devolvio el modelo.
 *
 * @param text         la respuesta; vacia si el modelo declino responder o
 *                     agoto el tope de tokens antes de escribir
 * @param stopReason   por que termino, tal como lo informa el proveedor; se
 *                     usa solo para el log
 */
public record ChatReply(String text, long inputTokens, long outputTokens, String stopReason) {
}
