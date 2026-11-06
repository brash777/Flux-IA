package com.fluxia.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Borra una vez al día los tokens vencidos.
 *
 * <p>Un token vencido ya no sirve para entrar, pero cada sesión y cada pedido
 * de recuperación deja una fila: sin limpieza, esas tablas solo crecen. Se
 * corre de madrugada porque nadie necesita el resultado al instante.
 */
@Component
public class ExpiredTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTokenCleanup.class);

    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository passwordResetTokens;

    public ExpiredTokenCleanup(RefreshTokenRepository refreshTokens,
                               PasswordResetTokenRepository passwordResetTokens) {
        this.refreshTokens = refreshTokens;
        this.passwordResetTokens = passwordResetTokens;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "${flux.timezone}")
    @Transactional
    public void purgeExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        int sessions = refreshTokens.deleteExpiredBefore(now);
        int resets = passwordResetTokens.deleteExpiredBefore(now);
        log.info("Limpieza de tokens vencidos: {} de sesión y {} de recuperación", sessions, resets);
    }
}
