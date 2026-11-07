package com.fluxia.backend.ai;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Cómo se escriben las cifras en lo que lee el modelo.
 *
 * <p>Igual que en la app, sin centavos cuando no los hay: así el modelo
 * repite las cifras tal como el usuario las ve en la pantalla.
 */
final class AiFormat {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");

    private AiFormat() {
    }

    static String money(BigDecimal amount) {
        if (amount == null) {
            return "$0";
        }
        String pattern = amount.stripTrailingZeros().scale() <= 0 ? "$%,.0f" : "$%,.2f";
        return String.format(SPANISH, pattern, amount);
    }
}
