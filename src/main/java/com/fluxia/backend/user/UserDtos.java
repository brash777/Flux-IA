package com.fluxia.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos del perfil del usuario. */
public final class UserDtos {

    private UserDtos() {
    }

    @Schema(description = "Datos a modificar del perfil")
    public record UpdateProfileRequest(

            @Size(max = 120, message = "El nombre es demasiado largo.")
            String fullName
    ) {
    }

    @Schema(description = "Cambio de contrasena de un usuario con sesion abierta")
    public record ChangePasswordRequest(

            @NotBlank(message = "La contrasena actual es obligatoria.")
            String currentPassword,

            @NotBlank(message = "La contrasena nueva es obligatoria.")
            @Size(min = 8, max = 72,
                    message = "La contrasena debe tener entre 8 y 72 caracteres.")
            String newPassword
    ) {
    }
}
