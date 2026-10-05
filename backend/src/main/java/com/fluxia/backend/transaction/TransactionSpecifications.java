package com.fluxia.backend.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros componibles para el listado de movimientos.
 *
 * <p>Son los mismos filtros que el prototipo resolvia en memoria con
 * {@code TXS.filter(...)}, pero traducidos a condiciones SQL: aqui el
 * filtrado lo hace PostgreSQL y el servidor solo devuelve la pagina
 * pedida, no la tabla entera.
 *
 * <p>Se usa la API de Specification en lugar de una consulta con
 * {@code (:param IS NULL OR ...)} porque esta construye solo las
 * condiciones que el usuario realmente pidio, y evita tener que pasar
 * parametros nulos cuyo tipo Hibernate no puede deducir.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Condicion obligatoria en TODA consulta de movimientos: solo los
     * del usuario autenticado. Es el equivalente en la capa de
     * aplicacion a lo que en Supabase hace Row Level Security.
     */
    public static Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    /** Desde esta fecha, inclusive. */
    public static Specification<Transaction> occurredFrom(OffsetDateTime from) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("occurredAt"), from);
    }

    /** Hasta esta fecha, exclusive: evita solapes entre periodos. */
    public static Specification<Transaction> occurredBefore(OffsetDateTime to) {
        return (root, query, cb) ->
                cb.lessThan(root.<OffsetDateTime>get("occurredAt"), to);
    }

    public static Specification<Transaction> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /** Busqueda por texto en la descripcion, sin distinguir mayusculas. */
    public static Specification<Transaction> descriptionContains(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("description")), pattern);
    }
}
