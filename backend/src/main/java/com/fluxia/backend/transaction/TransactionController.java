package com.fluxia.backend.transaction;

import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Movimientos del usuario autenticado.
 *
 * <p>Ningun endpoint recibe un {@code userId}: se toma siempre del
 * token. Si se aceptara por parametro, bastaria cambiar un id en la URL
 * para leer los movimientos de otra persona.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Movimientos", description = "Alta, consulta, modificacion y baja de movimientos")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "Listar movimientos",
            description = """
                    Listado paginado con filtros opcionales, equivalente a los chips
                    de la pantalla de movimientos.

                    Devuelve ademas los totales de TODO el filtro, no solo de la
                    pagina actual.

                    El rango de fechas es semiabierto [from, to): un movimiento
                    nunca se cuenta en dos periodos a la vez.
                    """)
    public TransactionDtos.PageResponse list(
            @AuthenticationPrincipal AuthenticatedUser user,

            @Parameter(description = "Desde (inclusive), formato ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,

            @Parameter(description = "Hasta (exclusive), formato ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,

            @Parameter(description = "Slug de categoria: comida, transporte, ocio, servicios, supermercado, ingreso. 'todos' no filtra.")
            @RequestParam(required = false) String category,

            @Parameter(description = "INCOME o EXPENSE")
            @RequestParam(required = false) TransactionType type,

            @Parameter(description = "Texto a buscar en la descripcion")
            @RequestParam(required = false) String search,

            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Maximo 100")
            @RequestParam(defaultValue = "20") int size) {

        return transactionService.list(user.id(), from, to, category, type, search, page, size);
    }

    @GetMapping("/recent")
    @Operation(summary = "Movimientos mas recientes",
            description = "Alimenta la tarjeta de actividad reciente de la pantalla de inicio.")
    public List<TransactionDtos.Response> recent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "5") int limit) {

        return transactionService.recent(user.id(), limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un movimiento")
    public TransactionDtos.Response getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {

        return transactionService.getById(user.id(), id);
    }

    @PostMapping
    @Operation(summary = "Registrar un movimiento",
            description = """
                    El monto va siempre positivo; el sentido lo indica el campo
                    type (INCOME o EXPENSE).

                    Se valida que la categoria sea coherente con el tipo: un gasto
                    no puede ir en una categoria de ingresos.
                    """)
    public ResponseEntity<TransactionDtos.Response> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody TransactionDtos.CreateRequest request) {

        TransactionDtos.Response created = transactionService.create(user.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modificar un movimiento",
            description = "Modificacion parcial: los campos omitidos quedan como estaban.")
    public TransactionDtos.Response update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionDtos.UpdateRequest request) {

        return transactionService.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un movimiento")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {

        transactionService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
