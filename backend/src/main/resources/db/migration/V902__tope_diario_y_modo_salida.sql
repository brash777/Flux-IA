-- =====================================================================
-- Tope diario, modo salida y la categoría Salud.
--
-- El tope y el modo salida son ajustes que elige el usuario, no cifras
-- calculadas: lo gastado en el día se sigue sumando desde transactions en
-- cada consulta (regla de oro, CLAUDE.md). Por eso aquí no hay ninguna
-- columna de «gastado hoy» ni de «disponible».
-- =====================================================================

CREATE TABLE spending_settings (
    user_id          UUID           PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- NULL significa «sin tope».
    daily_limit      NUMERIC(14, 2) NULL,
    -- Hasta cuándo la tarjeta simulada rechaza lo que no sea esencial.
    -- NULL o una fecha pasada: el modo salida está apagado.
    going_out_until  TIMESTAMPTZ    NULL,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_spending_settings_limit CHECK (daily_limit IS NULL OR daily_limit > 0)
);

-- Gastos fijos que el usuario deja fuera del tope (la matrícula, los
-- servicios, el mercado): no son compras por impulso.
CREATE TABLE spending_excluded_categories (
    user_id      UUID NOT NULL REFERENCES spending_settings (user_id) ON DELETE CASCADE,
    category_id  UUID NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_id)
);

-- Sostiene el ON DELETE CASCADE al borrar una categoría propia: sin este
-- índice, PostgreSQL recorrería la tabla entera para encontrar sus filas.
CREATE INDEX ix_spending_excluded_category ON spending_excluded_categories (category_id);

-- Farmacia y médico. Con el modo salida activo siguen pasando, igual que
-- el transporte: bloquearlas podría dejar a alguien sin un remedio.
INSERT INTO categories (user_id, slug, name, icon, color, kind) VALUES
    (NULL, 'salud', 'Salud', '💊', '#ef4444', 'EXPENSE');
