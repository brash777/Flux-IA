package com.fluxia.backend.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implementacion del fragmento de agregacion.
 *
 * <p>Spring Data lo descubre por convencion de nombre: la clase debe
 * llamarse igual que la interfaz mas el sufijo {@code Impl}. No lleva
 * anotacion alguna.
 */
public class TransactionAggregationRepositoryImpl implements TransactionAggregationRepository {

    private final EntityManager entityManager;

    public TransactionAggregationRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Map<TransactionType, BigDecimal> sumByType(Specification<Transaction> specification) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        // Se reutiliza el mismo Specification que arma el listado: una
        // sola definicion del filtro para las dos consultas.
        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        query.multiselect(
                root.get("type"),
                cb.sum(root.<BigDecimal>get("amount")));
        query.groupBy(root.get("type"));

        List<Tuple> rows = entityManager.createQuery(query).getResultList();

        Map<TransactionType, BigDecimal> totals = new EnumMap<>(TransactionType.class);
        for (Tuple row : rows) {
            TransactionType type = row.get(0, TransactionType.class);
            BigDecimal sum = row.get(1, BigDecimal.class);
            totals.put(type, sum != null ? sum : BigDecimal.ZERO);
        }
        return totals;
    }
}
