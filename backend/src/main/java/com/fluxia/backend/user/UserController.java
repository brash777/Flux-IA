package com.fluxia.backend.user;

import com.fluxia.backend.auth.AuthDtos;
import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario autenticado: la pantalla de perfil del prototipo. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Perfil", description = "Datos del usuario con sesion abierta")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Ver mi perfil",
            description = "Devuelve nombre, correo e iniciales para el avatar.")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.me(user.id());
    }

    @PatchMapping
    @Operation(summary = "Modificar mi perfil")
    public AuthDtos.UserResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserDtos.UpdateProfileRequest request) {

        return userService.updateProfile(user.id(), request);
    }

    @PostMapping("/password")
    @Operation(summary = "Cambiar mi contrasena",
            description = "Exige la contrasena actual y cierra las demas sesiones abiertas.")
    public AuthDtos.MessageResponse changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UserDtos.ChangePasswordRequest request) {

        userService.changePassword(user.id(), request);
        return new AuthDtos.MessageResponse(
                "Contrasena actualizada. Las demas sesiones fueron cerradas.");
    }
}
