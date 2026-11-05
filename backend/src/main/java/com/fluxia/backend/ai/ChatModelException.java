package com.fluxia.backend.ai;

/**
 * El proveedor de IA no atendio el pedido.
 *
 * <p>Distingue lo unico que el asistente necesita para decidir: si vale la
 * pena probar con el modelo de respaldo (una cuota agotada o una caida, si;
 * una clave rechazada, no, porque fallaria igual con todos) y si fue por
 * exceso de pedidos, que al usuario se le explica distinto.
 *
 * <p>El mensaje es para el log del servidor: puede traer detalles del
 * proveedor y nunca llega al cliente.
 */
public class ChatModelException extends RuntimeException {

    private final boolean retryable;
    private final boolean rateLimited;

    private ChatModelException(String message, Throwable cause, boolean retryable, boolean rateLimited) {
        super(message, cause);
        this.retryable = retryable;
        this.rateLimited = rateLimited;
    }

    /** Cuota o limite de peticiones agotado: otro modelo puede tener cuota. */
    public static ChatModelException rateLimited(String message, Throwable cause) {
        return new ChatModelException(message, cause, true, true);
    }

    /** Caida, demora o error del proveedor: otro modelo puede responder. */
    public static ChatModelException unavailable(String message, Throwable cause) {
        return new ChatModelException(message, cause, true, false);
    }

    /** Clave invalida o sin permiso: ningun modelo del proveedor va a responder. */
    public static ChatModelException rejected(String message, Throwable cause) {
        return new ChatModelException(message, cause, false, false);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }
}
