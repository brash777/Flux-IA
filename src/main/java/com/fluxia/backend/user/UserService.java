package com.fluxia.backend.user;

import com.fluxia.backend.auth.AuthDtos;
import com.fluxia.backend.auth.RefreshTokenRepository;
import com.fluxia.backend.shared.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Consulta y modificacion del perfil del usuario autenticado. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse me(UUID userId) {
        return AuthDtos.UserResponse.from(require(userId));
    }

    @Transactional
    public AuthDtos.UserResponse updateProfile(UUID userId, UserDtos.UpdateProfileRequest request) {
        User user = require(userId);

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }

        return AuthDtos.UserResponse.from(userRepository.save(user));
    }

    /**
     * Cambia la contrasena de un usuario con sesion abierta.
     *
     * <p>Se exige la contrasena actual aunque ya tenga un token valido:
     * si alguien le dejara el telefono desbloqueado a otra persona, sin
     * esta comprobacion podria cambiarle la contrasena y quedarse con
     * la cuenta.
     *
     * <p>Al terminar se revocan las demas sesiones, por el mismo motivo
     * que al restablecerla por correo.
     */
    @Transactional
    public void changePassword(UUID userId, UserDtos.ChangePasswordRequest request) {
        User user = require(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("CURRENT_PASSWORD_INVALID",
                    "La contrasena actual no es correcta.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("El usuario no existe."));
    }
}
