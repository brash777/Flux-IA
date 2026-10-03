package com.fluxia.backend.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Optional<Category> findBySlugAndUserIsNull(String slug);
}
