package com.fluxia.backend.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
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
     * Todas las sumas del resumen en una sola consulta: las del periodo, las
     * del periodo anterior y el saldo historico hasta el fin del periodo.
     *
     * <p>Son una sola porque cada ida y vuelta a la base cuesta unos 150 ms:
     * con tres consultas separadas, el resumen de la pantalla de inicio
     * tardaba mas de un segundo.
     *
     * <p>Los rangos son semiabiertos, {@code [desde, hasta)}: asi un
     * movimiento a las 23:59:59.999 del ultimo dia no se pierde ni se
     * cuenta dos veces entre dos periodos contiguos.
     */
    @Query("""
            SELECT new com.fluxia.backend.transaction.SummaryTotals(
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.INCOME
                          AND t.occurredAt >= :from THEN t.amount END),
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.EXPENSE
                          AND t.occurredAt >= :from THEN t.amount END),
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.INCOME
                          AND t.occurredAt >= :previousFrom AND t.occurredAt < :previousTo THEN t.amount END),
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.EXPENSE
                          AND t.occurredAt >= :previousFrom AND t.occurredAt < :previousTo THEN t.amount END),
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.INCOME THEN t.amount END),
                SUM(CASE WHEN t.type = com.fluxia.backend.transaction.TransactionType.EXPENSE THEN t.amount END))
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.occurredAt < :to
            """)
    SummaryTotals summaryTotals(@Param("userId") UUID userId,
                                @Param("from") OffsetDateTime from,
                                @Param("to") OffsetDateTime to,
                                @Param("previousFrom") OffsetDateTime previousFrom,
                                @Param("previousTo") OffsetDateTime previousTo);

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
     * a las 21:00 de Bogotá en enero del ano siguiente. Convertir
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
     * Gastos de un rango sin contar ciertas categorías: lo que cuenta para
     * el tope diario, que deja fuera los gastos fijos del usuario.
     *
     * <p>Conviene llamarla con {@link #sumExpensesBetween}, que nunca le pasa
     * una lista vacía: {@code NOT IN ()} no es SQL válido.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.user.id = :userId
              AND t.type = com.fluxia.backend.transaction.TransactionType.EXPENSE
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
              AND t.category.id NOT IN :excluded
            """)
    BigDecimal sumExpensesExcluding(@Param("userId") UUID userId,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    @Param("excluded") Collection<UUID> excluded);

    /** Un id que ninguna categoría tiene: ocupa el lugar de una lista vacía. */
    UUID NO_CATEGORY = new UUID(0, 0);

    default BigDecimal sumExpensesBetween(UUID userId,
                                          OffsetDateTime from,
                                          OffsetDateTime to,
                                          Collection<UUID> excluded) {
        return sumExpensesExcluding(userId, from, to, excluded.isEmpty() ? List.of(NO_CATEGORY) : excluded);
    }

    /** El primer movimiento del usuario, o null si no tiene ninguno. */
    @Query("SELECT MIN(t.occurredAt) FROM Transaction t WHERE t.user.id = :userId")
    OffsetDateTime firstOccurredAt(@Param("userId") UUID userId);

    /**
     * Los movimientos mas recientes, ya con categoria cargada.
     * Alimenta la tarjeta "Actividad reciente" de la pantalla de inicio
     * y el contexto que se le pasa a la IA.
     */
    @EntityGraph(attributePaths = {"category", "account"})
    List<Transaction> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);
}
