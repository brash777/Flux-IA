package com.fluxia.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalida los pedidos anteriores del usuario. Si pide el enlace
     * tres veces, solo el ultimo correo funciona.
     */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken prt
            SET prt.usedAt = :now
            WHERE prt.user.id = :userId AND prt.usedAt IS NULL
            """)
    int invalidatePendingForUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
