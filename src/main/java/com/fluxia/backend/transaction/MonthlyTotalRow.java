package com.fluxia.backend.transaction;

import java.math.BigDecimal;

/**
 * Una fila del grafico de tendencia mensual: un mes con sus totales.
 *
 * <p>Es una proyeccion de Spring Data: los nombres de los getters se
 * corresponden con los alias de la consulta nativa que la produce.
 * Se usan enteros para ano y mes, en lugar de una fecha, porque asi la
 * conversion de tipos entre PostgreSQL y Java no deja margen de duda.
 */
public interface MonthlyTotalRow {

    Integer getYear();

    Integer getMonth();

    BigDecimal getIncome();

    BigDecimal getExpense();
}
