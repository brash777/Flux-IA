package com.fluxia.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Contratos de entrada y salida de los endpoints de autenticacion.
 *
 * <p>Van en un solo archivo porque son records cortos que solo tienen
 * sentido juntos. Se usan records, y no las entidades, para que la
 * forma de la API no quede atada a la forma de las tablas: cambiar una
 * columna no deberia romper a los tres clientes.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // -----------------------------------------------------------------
    // Entradas
    // -----------------------------------------------------------------

    @Schema(description = "Datos para crear una cuenta nueva")
    public record RegisterRequest(

            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "Ingresa un correo valido.")
            @Size(max = 255, message = "El correo es demasiado largo.")
            String email,

            /*
             * Ocho caracteres como minimo. El prototipo aceptaba "1234";
             * una contrasena de cuatro digitos se agota por fuerza bruta
             * en 10 000 intentos.
             */
            @NotBlank(message = "La contrasena es obligatoria.")
            @Size(min = 8, max = 72,
                    message = "La contrasena debe tener entre 8 y 72 caracteres.")
            String password,

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 120, message = "El nombre es demasiado largo.")
            String fullName
    ) {
    }

    @Schema(description = "Credenciales de inicio de sesion")
    public record LoginRequest(

            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "Ingresa un correo valido.")
            String email,

            @NotBlank(message = "La contrasena es obligatoria.")
            String password
    ) {
    }

    @Schema(description = "Token de refresco obtenido al iniciar sesion")
    public record RefreshRequest(

            @NotBlank(message = "El token de refresco es obligatorio.")
            String refreshToken
    ) {
    }

    @Schema(description = "Correo al que enviar el enlace de recuperacion")
    public record ForgotPasswordRequest(

            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "Ingresa un correo valido.")
            String email
    ) {
    }

    @Schema(description = "Token del enlace de recuperacion y contrasena nueva")
    public record ResetPasswordRequest(

            @NotBlank(message = "El token es obligatorio.")
            String token,

            @NotBlank(message = "La contrasena es obligatoria.")
            @Size(min = 8, max = 72,
                    message = "La contrasena debe tener entre 8 y 72 caracteres.")
            String newPassword
    ) {
    }

    // -----------------------------------------------------------------
    // Salidas
    // -----------------------------------------------------------------

    @Schema(description = "Sesion abierta: tokens y datos del usuario")
    public record SessionResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserResponse user
    ) {
    }

    @Schema(description = "Datos publicos del usuario")
    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            /** Iniciales para el avatar, como el "DF" del prototipo. */
            String initials
    ) {
        public static UserResponse from(com.fluxia.backend.user.User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    initialsOf(user.getFullName()));
        }

        private static String initialsOf(String fullName) {
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, 1).toUpperCase();
            }
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                    .toUpperCase();
        }
    }

    @Schema(description = "Confirmacion simple de una operacion")
    public record MessageResponse(String message) {
    }
}
