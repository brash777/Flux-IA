package com.fluxia.backend.transaction;

import java.math.BigDecimal;

/**
 * Total acumulado de un tipo de movimiento dentro de un periodo.
 * La base de datos devuelve una fila por tipo presente.
 */
public record TypeTotal(TransactionType type, BigDecimal total) {
}
