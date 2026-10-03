package com.fluxia.backend.category;

/**
 * Naturaleza de una categoria. Coincide con ck_categories_kind en la V1.
 *
 * <p>Sirve para impedir que un movimiento de gasto se clasifique en una
 * categoria de ingreso, que es como el prototipo acababa con cifras
 * incoherentes entre pantallas.
 */
public enum CategoryKind {
    INCOME,
    EXPENSE
}
