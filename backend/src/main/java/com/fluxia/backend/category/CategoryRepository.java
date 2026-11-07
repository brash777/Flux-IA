package com.fluxia.backend.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Las categorias visibles para un usuario: las del sistema mas las
     * suyas. Es la consulta que alimenta los filtros de la pantalla de
     * movimientos.
     */
    @Query("""
            SELECT c FROM Category c
            WHERE c.user IS NULL OR c.user.id = :userId
            ORDER BY c.kind DESC, c.name ASC
            """)
    List<Category> findVisibleTo(@Param("userId") UUID userId);

    /**
     * Resuelve un slug a categoria. Si el usuario definio una categoria
     * propia con el mismo slug que una del sistema, la suya tiene
     * prioridad: por eso NULLS LAST deja la del sistema al final.
     */
    @Query("""
            SELECT c FROM Category c
            WHERE c.slug = :slug AND (c.user IS NULL OR c.user.id = :userId)
            ORDER BY c.user.id NULLS LAST
            """)
    List<Category> findBySlugVisibleTo(@Param("slug") String slug, @Param("userId") UUID userId);

    @Query("""
            SELECT c FROM Category c
            WHERE c.id = :id AND (c.user IS NULL OR c.user.id = :userId)
            """)
    Optional<Category> findByIdVisibleTo(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * De una sola vez, las categorías que muestra la tarjeta del tope: las
     * excluidas por el usuario (si las ve) y las del sistema que pasan
     * siempre. Llamarla con una lista de ids no vacía: {@code IN ()} no es
     * SQL válido.
     */
    @Query("""
            SELECT c FROM Category c
            WHERE (c.id IN :ids AND (c.user IS NULL OR c.user.id = :userId))
               OR (c.user IS NULL AND c.slug IN :systemSlugs)
            ORDER BY c.name ASC
            """)
    List<Category> findByIdsOrSystemSlugs(@Param("userId") UUID userId,
                                          @Param("ids") Collection<UUID> ids,
                                          @Param("systemSlugs") Collection<String> systemSlugs);

    Optional<Category> findBySlugAndUserIsNull(String slug);
}
