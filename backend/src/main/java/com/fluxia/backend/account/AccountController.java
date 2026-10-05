package com.fluxia.backend.account;

import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cuentas de dinero del usuario.
 *
 * <p>Al registrarse se crea una "Cuenta principal" automaticamente, asi
 * que el cliente puede ignorar este recurso por completo y dejar que el
 * servidor elija la cuenta al registrar movimientos.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Cuentas", description = "Cuentas de dinero: efectivo, banco o tarjeta")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    @Operation(summary = "Listar mis cuentas")
    public List<AccountDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return accountService.list(user.id());
    }

    @PostMapping
    @Operation(summary = "Crear una cuenta")
    public ResponseEntity<AccountDtos.Response> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AccountDtos.CreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(accountService.create(user.id(), request));
    }
}
