package com.fluxia.backend.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Contratos de las categorias. */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    @Schema(description = "Categoria propia nueva")
    public record CreateRequest(

            @NotBlank(message = "El nombre es obligatorio.")
            @Size(max = 80, message = "El nombre es demasiado largo.")
            String name,

            @NotNull(message = "El tipo es obligatorio (INCOME o EXPENSE).")
            CategoryKind kind,

            @Size(max = 16, message = "El icono es demasiado largo.")
            String icon,

            @Pattern(regexp = "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$",
                    message = "El color debe ser hexadecimal, por ejemplo #6c63ff.")
            String color
    ) {
    }

    @Schema(description = "Una categoria visible para el usuario")
    public record Response(
            UUID id,
            String slug,
            String name,
            String icon,
            String color,
            CategoryKind kind,
            @Schema(description = "true si es del sistema y por lo tanto no se puede editar ni borrar")
            boolean system
    ) {
        public static Response from(Category category) {
            return new Response(
                    category.getId(),
                    category.getSlug(),
                    category.getName(),
                    category.getIcon(),
                    category.getColor(),
                    category.getKind(),
                    category.isSystemCategory());
        }
    }
}
