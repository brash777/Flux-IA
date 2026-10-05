package com.fluxia.backend.category;

import com.fluxia.backend.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Categorias visibles para el usuario: las del sistema mas las suyas. */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categorias", description = "Categorias del sistema y propias del usuario")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "Listar categorias",
            description = "Las del sistema mas las propias. Alimenta los chips de filtro de la pantalla de movimientos.")
    public List<CategoryDtos.Response> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return categoryService.list(user.id());
    }

    @PostMapping
    @Operation(summary = "Crear una categoria propia",
            description = "El slug se genera a partir del nombre, sin acentos ni espacios.")
    public ResponseEntity<CategoryDtos.Response> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CategoryDtos.CreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(categoryService.create(user.id(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una categoria propia",
            description = "Falla si es del sistema o si tiene movimientos asociados.")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {

        categoryService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
