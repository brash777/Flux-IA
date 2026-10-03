package com.fluxia.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoca de golpe todas las sesiones de un usuario. Se usa al
     * cambiar la contrasena: si alguien habia entrado con la vieja,
     * queda fuera.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
            SET rt.revokedAt = :now
            WHERE rt.user.id = :userId AND rt.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    /** Limpieza de tokens que ya no sirven para nada. */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") OffsetDateTime before);
}
