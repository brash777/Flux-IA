package com.fluxia.backend.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Suma montos aplicando exactamente los mismos filtros que el listado.
 *
 * <p>Por que existe: Spring Data sabe paginar con Specification, pero
 * no sabe agregar. Sin esto habria que escribir las condiciones del
 * filtro dos veces (una para la lista y otra para los totales), y dos
 * copias de la misma regla terminan desincronizandose. Que fue,
 * exactamente, lo que le paso al prototipo.
 */
public interface TransactionAggregationRepository {

    /**
     * Total por tipo para los movimientos que cumplen la condicion.
     * Los tipos sin movimientos no aparecen en el mapa.
     */
    Map<TransactionType, BigDecimal> sumByType(Specification<Transaction> specification);
}
