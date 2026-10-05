package com.fluxia.backend.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Contratos de las cuentas de dinero. */
public final class AccountDtos {

    private AccountDtos() {
    }

    @Schema(description = "Cuenta de dinero nueva")
    public record CreateRequest(

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 80, message = "El nombre es demasiado largo.")
            String name,

            @NotNull(message = "El tipo es obligatorio (CASH, BANK o CARD).")
            AccountType type,

            @Pattern(regexp = "^[A-Z]{3}$",
                    message = "La moneda debe ser un codigo de 3 letras, por ejemplo ARS.")
            String currency
    ) {
    }

    @Schema(description = "Una cuenta del usuario")
    public record Response(UUID id, String name, AccountType type, String currency) {

        public static Response from(Account account) {
            return new Response(
                    account.getId(),
                    account.getName(),
                    account.getType(),
                    account.getCurrency());
        }
    }
}
