package com.fluxia.backend.transaction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Total gastado o ingresado en una categoria dentro de un periodo.
 * Alimenta el grafico de dona de la pantalla de reportes.
 *
 * <p>Trae icono y color desde la tabla de categorias para que el
 * cliente pueda pintar el grafico sin una segunda peticion.
 */
public record CategoryTotal(
        UUID categoryId,
        String slug,
        String name,
        String icon,
        String color,
        BigDecimal total,
        long movements
) {
}
