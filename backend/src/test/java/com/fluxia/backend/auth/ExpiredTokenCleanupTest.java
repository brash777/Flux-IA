package com.fluxia.backend.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpiredTokenCleanupTest {

    @Mock
    private RefreshTokenRepository refreshTokens;

    @Mock
    private PasswordResetTokenRepository passwordResetTokens;

    @InjectMocks
    private ExpiredTokenCleanup cleanup;

    @Test
    @DisplayName("La limpieza borra los tokens vencidos de sesión y de recuperación")
    void purgesBothKinds() {
        cleanup.purgeExpired();

        verify(refreshTokens).deleteExpiredBefore(any());
        verify(passwordResetTokens).deleteExpiredBefore(any());
    }
}
