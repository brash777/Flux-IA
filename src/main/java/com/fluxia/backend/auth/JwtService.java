package com.fluxia.backend.auth;

import com.fluxia.backend.config.FluxProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Emite y valida los tokens de acceso.
 *
 * <p>Que llevan y que no: el token guarda el id, el correo y el nombre
 * del usuario, para que cada peticion se resuelva sin consultar la base
 * de datos. No guarda nada sensible, porque <b>un JWT va firmado pero
 * no cifrado</b>: cualquiera que lo tenga puede leer su contenido. Lo
 * que la firma garantiza es que nadie pudo modificarlo.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";

    /** HS256 necesita al menos 256 bits de clave. */
    private static final int MIN_SECRET_BYTES = 32;

    private final FluxProperties properties;
    private SecretKey signingKey;

    public JwtService(FluxProperties properties) {
        this.properties = properties;
    }

    /**
     * Se valida la clave al arrancar, no al primer inicio de sesion.
     * Es preferible que el servidor no levante antes que descubrir en
     * produccion que la firma esta mal configurada.
     */
    @PostConstruct
    void initSigningKey() {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET. Genera uno con: openssl rand -base64 48");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corto (" + bytes.length + " bytes). "
                            + "Se necesitan al menos " + MIN_SECRET_BYTES
                            + ". Genera uno con: openssl rand -base64 48");
        }
        this.signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(bytes);
    }

    /** Emite un token de acceso para un usuario ya autenticado. */
    public IssuedToken issueAccessToken(UUID userId, String email, String fullName) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getJwt().getAccessTokenTtl());

        String token = Jwts.builder()
                .subject(userId.toString())
                .issuer(properties.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, fullName)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt);
    }

    /**
     * Valida la firma y la vigencia del token y devuelve la identidad.
     *
     * @throws TokenExpiredException si el token vencio (el cliente debe refrescar)
     * @throws TokenInvalidException si la firma o el formato no son validos
     */
    public AuthenticatedUser verify(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token);

            Claims claims = jws.getPayload();
            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class));

        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException ex) {
            // IllegalArgumentException cubre un subject que no es un UUID.
            throw new TokenInvalidException();
        }
    }

    /** Un token recien emitido y el instante en que deja de servir. */
    public record IssuedToken(String value, Instant expiresAt) {
    }

    /** El token es legitimo pero ya vencio. */
    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException() {
            super("El token de acceso vencio.");
        }
    }

    /** El token no se puede verificar: firma, emisor o formato invalidos. */
    public static class TokenInvalidException extends RuntimeException {
        public TokenInvalidException() {
            super("El token de acceso no es valido.");
        }
    }
}
