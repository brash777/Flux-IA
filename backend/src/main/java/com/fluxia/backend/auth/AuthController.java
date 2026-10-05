package com.fluxia.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Endpoints de autenticacion. Son los unicos publicos de la API, por
 * definicion: no se puede exigir un token para obtener el primer token.
 *
 * <p>{@code @SecurityRequirements} vacio le indica a Swagger UI que
 * estas rutas no necesitan el boton Authorize.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacion", description = "Registro, inicio de sesion y recuperacion de contrasena")
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final boolean exposeResetToken;

    public AuthController(AuthService authService,
                          org.springframework.core.env.Environment environment) {
        this.authService = authService;
        // El token de recuperacion solo se devuelve en el perfil dev,
        // mientras no haya servicio de correo. En produccion la
        // respuesta no lo incluye nunca.
        this.exposeResetToken = environment.matchesProfiles("dev");
    }

    @PostMapping("/register")
    @Operation(summary = "Crear una cuenta nueva",
            description = "Crea el usuario, su cuenta de dinero inicial y abre sesion.")
    public ResponseEntity<AuthDtos.SessionResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion",
            description = "Devuelve un token de acceso de 15 minutos y uno de refresco de 30 dias.")
    public AuthDtos.SessionResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesion",
            description = "Cambia un token de refresco por un par nuevo. El token presentado queda revocado.")
    public AuthDtos.SessionResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesion",
            description = "Revoca el token de refresco. El token de acceso sigue valido hasta que vence.")
    public AuthDtos.MessageResponse logout(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        authService.logout(request.refreshToken());
        return new AuthDtos.MessageResponse("Sesion cerrada correctamente.");
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Pedir enlace de recuperacion",
            description = """
                    Genera un token de recuperacion valido por 30 minutos.

                    Responde siempre lo mismo, exista o no el correo: asi no se
                    puede usar este endpoint para averiguar quien tiene cuenta.

                    Mientras no haya servicio de correo, en el perfil dev el token
                    viene en la respuesta y tambien queda en el log del servidor.
                    """)
    public Map<String, Object> forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {

        Optional<String> token = authService.createPasswordResetToken(request);

        if (exposeResetToken && token.isPresent()) {
            return Map.of(
                    "message", "Si el correo esta registrado, recibiras un enlace para restablecerla.",
                    "devToken", token.get());
        }
        return Map.of(
                "message", "Si el correo esta registrado, recibiras un enlace para restablecerla.");
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Restablecer la contrasena",
            description = "Cambia la contrasena con un token de recuperacion y cierra todas las sesiones abiertas.")
    public AuthDtos.MessageResponse resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {

        authService.resetPassword(request);
        return new AuthDtos.MessageResponse(
                "Contrasena actualizada. Inicia sesion con la nueva.");
    }
}
