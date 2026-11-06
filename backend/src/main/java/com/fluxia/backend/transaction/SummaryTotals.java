package com.fluxia.backend.transaction;

import java.math.BigDecimal;

/**
 * Las sumas que necesita el resumen, resueltas en una sola consulta.
 *
 * <p>Un total es {@code null} cuando no hay movimientos de ese tipo: SUM de
 * nada no es cero en SQL. Quien la usa lo interpreta como cero.
 */
public record SummaryTotals(BigDecimal currentIncome,
                            BigDecimal currentExpense,
                            BigDecimal previousIncome,
                            BigDecimal previousExpense,
                            BigDecimal historicIncome,
                            BigDecimal historicExpense) {
}
