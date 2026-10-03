package com.fluxia.backend.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a los movimientos y a todos los totales que se derivan de ellos.
 *
 * <p>Principio que rige este archivo: <b>las sumas se hacen en SQL</b>.
 * Traer las filas a Java para sumarlas ahi funcionaria con los 12
 * movimientos del prototipo, pero no con un ano de historia. PostgreSQL
 * suma sobre un indice sin mover los datos.
 */
public interface TransactionRepository extends
        JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction>,
        TransactionAggregationRepository {

    /**
     * Listado filtrado y paginado. Los filtros dinamicos se arman con
     * {@link TransactionSpecifications} en lugar de con condiciones
     * {@code (:param IS NULL OR ...)}, que obligan a pasar parametros
     * nulos con tipo ambiguo.
     *
     * <p>El {@code @EntityGraph} trae categoria y cuenta en la misma
     * consulta. Sin el, convertir 20 movimientos a DTO dispararia 40
     * consultas extra (el problema N+1).
     */
    @Override
    @EntityGraph(attributePaths = {"category", "account"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    /** Un movimiento propio. Si el id es de otro usuario, devuelve vacio. */
    @EntityGraph(attributePaths = {"category", "account"})
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByCategoryId(UUID categoryId);

    // -----------------------------------------------------------------
    // Totales
    // -----------------------------------------------------------------

    /**
     * Suma por tipo dentro de un periodo. Devuelve como maximo dos
     * filas (INCOME y EXPENSE); si un tipo no tiene movimientos, su
     * fila no aparece y quien llama lo interpreta como cero.
     *
     * <p>El rango es semiabierto, {@code [desde, hasta)}: asi un
     * movimiento a las 23:59:59.999 del ultimo dia no se pierde ni se
     * cuenta dos veces entre dos periodos contiguos.
     */
    @Query("""
            SELECT new com.fluxia.backend.transaction.TypeTotal(t.type, SUM(t.amount))
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            GROUP BY t.type
            """)
    List<TypeTotal> sumByTypeBetween(@Param("userId") UUID userId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to);

    /**
     * Suma por tipo sin limite inferior: es el saldo historico, que es
     * lo que la pantalla de inicio muestra como "saldo total disponible".
     */
    @Query("""
            SELECT new com.fluxia.backend.transaction.TypeTotal(t.type, SUM(t.amount))
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.occurredAt < :to
            GROUP BY t.type
            """)
    List<TypeTotal> sumByTypeUntil(@Param("userId") UUID userId,
                                   @Param("to") OffsetDateTime to);

    /** Totales por categoria dentro de un periodo, de mayor a menor. */
    @Query("""
            SELECT new com.fluxia.backend.transaction.CategoryTotal(
                       c.id, c.slug, c.name, c.icon, c.color, SUM(t.amount), COUNT(t))
            FROM Transaction t
            JOIN t.category c
            WHERE t.user.id = :userId
              AND t.type = :type
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            GROUP BY c.id, c.slug, c.name, c.icon, c.color
            ORDER BY SUM(t.amount) DESC
            """)
    List<CategoryTotal> sumByCategoryBetween(@Param("userId") UUID userId,
                                             @Param("type") TransactionType type,
                                             @Param("from") OffsetDateTime from,
                                             @Param("to") OffsetDateTime to);

    /**
     * Totales mes a mes, para el grafico de tendencia.
     *
     * <p>Es la unica consulta nativa del proyecto, por dos razones:
     * {@code date_trunc} es propio de PostgreSQL, y sobre todo el
     * {@code AT TIME ZONE}. Las fechas se guardan en UTC, asi que
     * agrupar por mes directamente pondria un gasto del 31 de diciembre
     * a las 21:00 de Buenos Aires en enero del ano siguiente. Convertir
     * a la zona del usuario antes de agrupar corrige ese desfase.
     */
    @Query(value = """
            SELECT CAST(EXTRACT(YEAR  FROM (t.occurred_at AT TIME ZONE :zone)) AS integer) AS year,
                   CAST(EXTRACT(MONTH FROM (t.occurred_at AT TIME ZONE :zone)) AS integer) AS month,
                   CAST(COALESCE(SUM(CASE WHEN t.type = 'INCOME'  THEN t.amount ELSE 0 END), 0) AS numeric) AS income,
                   CAST(COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS numeric) AS expense
            FROM transactions t
            WHERE t.user_id = :userId
              AND t.occurred_at >= :from
              AND t.occurred_at <  :to
            GROUP BY 1, 2
            ORDER BY 1, 2
            """, nativeQuery = true)
    List<MonthlyTotalRow> sumByMonth(@Param("userId") UUID userId,
                                     @Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("zone") String zone);

    /**
     * Los movimientos mas recientes, ya con categoria cargada.
     * Alimenta la tarjeta "Actividad reciente" de la pantalla de inicio
     * y el contexto que se le pasa a la IA.
     */
    @EntityGraph(attributePaths = {"category", "account"})
    List<Transaction> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
}
