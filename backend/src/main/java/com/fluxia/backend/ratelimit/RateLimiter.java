package com.fluxia.backend.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta eventos por clave dentro de una ventana de tiempo.
 *
 * <p>Vive en memoria y no en la base de datos: comprobar el límite no debe
 * costar una consulta en cada petición, y perder las cuentas al reiniciar
 * no es grave. Alcanza con un servidor; con varios, cada uno llevaría su
 * propia cuenta y haría falta un almacén compartido.
 */
public class RateLimiter {

    /** Tope de claves en memoria, para que muchas IP no la agoten. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Verdadero si la clave ya gastó su cupo en la ventana vigente. */
    public boolean isExhausted(String key, int limit) {
        Window window = windows.get(key);
        return window != null && window.isActiveAt(clock.instant()) && window.count() >= limit;
    }

    /** Suma un evento. La ventana empieza con el primero y no se alarga. */
    public void record(String key, Duration length) {
        Instant now = clock.instant();
        if (windows.size() >= MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> !window.isActiveAt(now));
        }
        windows.compute(key, (ignored, current) ->
                current == null || !current.isActiveAt(now)
                        ? new Window(now.plus(length), 1)
                        : new Window(current.expiresAt(), current.count() + 1));
    }

    private record Window(Instant expiresAt, int count) {
        boolean isActiveAt(Instant moment) {
            return moment.isBefore(expiresAt);
        }
    }
}
