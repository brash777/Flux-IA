package com.fluxia.backend.auth;

import com.fluxia.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Token de refresco guardado en base de datos.
 *
 * <p>Por que hace falta la tabla: un JWT firmado no se puede "apagar",
 * sirve hasta que vence. Con esta tabla, cerrar sesion es marcar la
 * fila como revocada, y el token deja de servir al instante.
 *
 * <p>Se guarda {@code token_hash} (SHA-256), no el token. Si alguien
 * consigue leer la tabla, no obtiene credenciales utilizables.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {
        // Constructor exigido por JPA.
    }

    public RefreshToken(User user, String tokenHash, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    /** Sirve si no fue revocado y todavia no vencio. */
    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(OffsetDateTime.now());
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
