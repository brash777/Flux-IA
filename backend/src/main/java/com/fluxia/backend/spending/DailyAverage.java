package com.fluxia.backend.spending;

import com.fluxia.backend.transaction.CategoryTotal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cuánto se gastó por día en las últimas semanas, sin los gastos fijos.
 *
 * @param days          cuántos días se miraron (hasta 30, menos si la
 *                      cuenta es más nueva)
 * @param total         la suma de esos días, desde {@code transactions}
 * @param perDay        el total dividido por los días
 * @param categories    lo gastado en cada categoría que cuenta para el tope
 * @param excludedNames los gastos fijos que quedaron fuera
 */
public record DailyAverage(int days,
                           BigDecimal total,
                           BigDecimal perDay,
                           List<CategoryTotal> categories,
                           List<String> excludedNames) {
}
