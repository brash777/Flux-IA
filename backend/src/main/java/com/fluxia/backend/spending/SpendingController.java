package com.fluxia.backend.spending;

import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tope diario, modo salida y tarjeta simulada del usuario autenticado.
 *
 * <p>No hay un endpoint para apagar el modo salida: termina solo a las 8:00.
 * Es a propósito (ver {@link SpendingService}).
 */
@RestController
@RequestMapping("/api/v1/spending")
@Tag(name = "Tope diario", description = "Tope de gasto por día, modo salida y tarjeta simulada")
public class SpendingController {

    private final SpendingService spendingService;

    public SpendingController(SpendingService spendingService) {
        this.spendingService = spendingService;
    }

    @GetMapping("/today")
    @Operation(summary = "Cómo va el día",
            description = """
                    Lo gastado hoy que cuenta para el tope, lo que queda y si el modo
                    salida está activo. Lo gastado se suma desde los movimientos en
                    cada consulta: no se guarda en ningún lado.
                    """)
    public SpendingDtos.TodayResponse today(@AuthenticationPrincipal AuthenticatedUser user) {
        return spendingService.today(user.id());
    }

    @PutMapping("/settings")
    @Operation(summary = "Guardar el tope y los gastos fijos",
            description = "Reemplaza los ajustes. dailyLimit en null quita el tope.")
    public SpendingDtos.TodayResponse updateSettings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SpendingDtos.SettingsRequest request) {

        return spendingService.updateSettings(user.id(), request);
    }

    @PostMapping("/going-out")
    @Operation(summary = "Activar el modo salida",
            description = """
                    Hasta las 8:00 siguientes, la tarjeta simulada rechaza todo menos
                    transporte y salud. No se puede apagar antes.
                    """)
    public SpendingDtos.TodayResponse startGoingOut(@AuthenticationPrincipal AuthenticatedUser user) {
        return spendingService.startGoingOut(user.id());
    }

    @PostMapping("/card-purchases")
    @Operation(summary = "Pagar con la tarjeta simulada",
            description = """
                    Aprueba o rechaza la compra con el tope y el modo salida. Si se
                    aprueba, queda como un gasto. Un rechazo responde 200 con
                    approved en false: no es un error.
                    """)
    public SpendingDtos.PurchaseResponse purchase(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SpendingDtos.PurchaseRequest request) {

        return spendingService.purchase(user.id(), request);
    }
}
