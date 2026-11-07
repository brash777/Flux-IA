package com.fluxia.backend.spending;

import com.fluxia.backend.transaction.TransactionDtos;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contratos del tope diario, el modo salida y la tarjeta simulada. */
public final class SpendingDtos {

    private SpendingDtos() {
    }

    @Schema(description = "Cómo va el día frente al tope, y si el modo salida está activo")
    public record TodayResponse(

            @Schema(description = "Inicio del día en la zona de la app")
            OffsetDateTime from,

            @Schema(description = "Fin del día (exclusivo)")
            OffsetDateTime to,

            @Schema(description = "Tope diario elegido por el usuario. Null si no tiene.")
            BigDecimal dailyLimit,

            @Schema(description = "Gastos de hoy que cuentan para el tope: todos menos los de las categorías excluidas")
            BigDecimal spentToday,

            @Schema(description = "Lo que queda del tope hoy; nunca negativo. Null sin tope.")
            BigDecimal remaining,

            @Schema(description = "Cuánto se pasó del tope; 0 si no se pasó. Null sin tope.")
            BigDecimal overBy,

            @Schema(description = "Porción del tope ya gastada, entre 0 y 100, para la barra. Null sin tope.")
            BigDecimal progressPercent,

            GoingOut goingOut,

            @Schema(description = "Gastos fijos que no cuentan para el tope")
            List<TransactionDtos.CategoryRef> excludedCategories,

            @Schema(description = "Categorías que la tarjeta aprueba siempre, aun con el modo salida o sin tope disponible")
            List<TransactionDtos.CategoryRef> alwaysAllowedCategories
    ) {
    }

    @Schema(description = "Modo salida: mientras está activo, la tarjeta rechaza lo que no sea esencial")
    public record GoingOut(boolean active, OffsetDateTime until) {
    }

    @Schema(description = "Tope diario y gastos fijos. Reemplaza los ajustes anteriores.")
    public record SettingsRequest(

            @Schema(description = "Null para quitar el tope")
            @DecimalMin(value = "0.01", message = "El tope debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 2, message = "El tope admite hasta 12 enteros y 2 decimales.")
            BigDecimal dailyLimit,

            @Schema(description = "Categorías de gasto que no cuentan para el tope")
            @NotNull(message = "Indica las categorías que no cuentan para el tope (puede ser una lista vacía).")
            @Size(max = 50, message = "Se admiten como máximo 50 categorías.")
            List<@NotNull(message = "Hay una categoría vacía en la lista.") UUID> excludedCategoryIds
    ) {
    }

    @Schema(description = "Una compra con la tarjeta simulada")
    public record PurchaseRequest(

            @NotBlank(message = "Escribe dónde compraste.")
            @Size(max = 140, message = "El comercio es demasiado largo.")
            String description,

            @NotNull(message = "El monto es obligatorio.")
            @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero.")
            @Digits(integer = 12, fraction = 2, message = "El monto admite hasta 12 enteros y 2 decimales.")
            BigDecimal amount,

            @NotNull(message = "La categoría es obligatoria.")
            UUID categoryId
    ) {
    }

    /** Por qué se aprobó o se rechazó una compra. */
    public enum Decision {
        APPROVED,
        DECLINED_GOING_OUT,
        DECLINED_DAILY_LIMIT
    }

    @Schema(description = "Resultado de la compra. Un rechazo no es un error: responde 200 con approved en false.")
    public record PurchaseResponse(
            boolean approved,
            Decision decision,
            String message,
            @Schema(description = "El movimiento creado, si se aprobó")
            TransactionDtos.Response transaction,
            @Schema(description = "Cómo queda el día después de la compra")
            TodayResponse today
    ) {
    }
}
