package com.fluxia.backend.auth;

import java.util.UUID;

/**
 * Identidad del usuario que hace la peticion, reconstruida a partir del
 * token JWT sin tocar la base de datos.
 *
 * <p>Los controladores la reciben con {@code @AuthenticationPrincipal}.
 * Nunca aceptan un {@code userId} por parametro: si lo hicieran,
 * cualquiera podria pedir los movimientos de otra persona cambiando un
 * numero en la URL.
 */
public record AuthenticatedUser(UUID id, String email, String fullName) {
}
