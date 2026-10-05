package com.fluxia.backend.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contratos de entrada y salida de los endpoints de movimientos. */
public final class TransactionDtos {

    private TransactionDtos() {
    }

    @Schema(description = "Datos de un movimiento nuevo")
    public record CreateRequest(

            @NotBlank(message = "La descripcion es obligatoria.")
            @Size(max = 140, message = "La descripcion es demasiado larga.")
            String description,

            /*
             * El monto va positivo siempre. El sentido lo da "type".
             * Digits limita a 12 enteros y 2 decimales, que es
             * exactamente NUMERIC(14,2) en la base de datos: validarlo
             * aqui da un error claro en lugar de un fallo de SQL.
             */
            @NotNull(message = "El monto es obligatorio.")
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 2,
                    message = "El monto admite hasta 12 enteros y 2 decimales.")
            BigDecimal amount,

            @NotNull(message = "El tipo es obligatorio (INCOME o EXPENSE).")
            TransactionType type,

            @NotNull(message = "La categoria es obligatoria.")
            UUID categoryId,

            @Schema(description = "Opcional: si se omite, se usa la cuenta principal del usuario.")
            UUID accountId,

            @NotNull(message = "La fecha del movimiento es obligatoria.")
            OffsetDateTime occurredAt
    ) {
    }

    @Schema(description = "Campos a modificar. Los que se omitan quedan como estan.")
    public record UpdateRequest(

            @Size(max = 140, message = "La descripcion es demasiado larga.")
            String description,

            @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 2,
                    message = "El monto admite hasta 12 enteros y 2 decimales.")
            BigDecimal amount,

            TransactionType type,

            UUID categoryId,

            UUID accountId,

            OffsetDateTime occurredAt
    ) {
    }

    @Schema(description = "Un movimiento tal como lo consume el cliente")
    public record Response(
            UUID id,
            String description,
            BigDecimal amount,
            TransactionType type,
            /**
             * El monto con signo, ya calculado por el servidor. Se envia
             * para que movil, escritorio y reloj no tengan que repetir
             * cada uno la misma regla de signos.
             */
            BigDecimal signedAmount,
            OffsetDateTime occurredAt,
            CategoryRef category,
            AccountRef account
    ) {
        public static Response from(Transaction transaction) {
            return new Response(
                    transaction.getId(),
                    transaction.getDescription(),
                    transaction.getAmount(),
                    transaction.getType(),
                    transaction.signedAmount(),
                    transaction.getOccurredAt(),
                    new CategoryRef(
                            transaction.getCategory().getId(),
                            transaction.getCategory().getSlug(),
                            transaction.getCategory().getName(),
                            transaction.getCategory().getIcon(),
                            transaction.getCategory().getColor()),
                    new AccountRef(
                            transaction.getAccount().getId(),
                            transaction.getAccount().getName(),
                            transaction.getAccount().getCurrency()));
        }
    }

    /** Categoria embebida: evita una segunda peticion para pintar la fila. */
    public record CategoryRef(UUID id, String slug, String name, String icon, String color) {
    }

    public record AccountRef(UUID id, String name, String currency) {
    }

    @Schema(description = "Pagina de movimientos con sus totales")
    public record PageResponse(
            java.util.List<Response> items,
            int page,
            int size,
            long totalItems,
            int totalPages,
            /**
             * Totales del filtro completo, no solo de esta pagina.
             * Es lo que permite que la pantalla muestre el total del
             * filtro sin descargar todos los movimientos.
             */
            Totals totals
    ) {
    }

    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal net) {
    }
}
