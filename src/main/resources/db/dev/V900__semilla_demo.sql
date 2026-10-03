-- =====================================================================
-- Semilla de DESARROLLO. No se aplica en produccion: solo el perfil
-- "dev" agrega classpath:db/dev a las rutas de Flyway (ver
-- application-dev.yml). Por eso va numerada 900, lejos del esquema.
--
-- Reproduce los 12 movimientos del arreglo TXS del prototipo, pero con
-- fechas reales y relativas a hoy, para que los reportes de "este mes"
-- siempre tengan datos. Suma seis meses de historia para que el grafico
-- de tendencia mensual tenga valores de verdad.
--
-- Usuario demo:  diego.f@fluxia.app  /  Flux2026!
-- =====================================================================

-- ---------------------------------------------------------------------
-- Usuario y cuenta
-- ---------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'diego.f@fluxia.app',
    -- BCrypt (coste 10) de 'Flux2026!'
    '$2y$10$jB2/aq4PogNP7dw1mmk1B.g6V1wK.HGMM27GA2WgWhUTAGPgmGR0O',
    'Diego Fernández'
)
ON CONFLICT DO NOTHING;

INSERT INTO accounts (id, user_id, name, type, currency)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Cuenta principal',
    'BANK',
    'ARS'
)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Los 12 movimientos del prototipo
--
-- Los dias del prototipo ('Hoy', 'Ayer', 'Lun 16', 'Dom 15', 'Sab 14')
-- se traducen a desplazamientos de 0 a -4 dias respecto de hoy.
-- Los montos van positivos: la direccion la da la columna type.
-- ---------------------------------------------------------------------
INSERT INTO transactions (user_id, account_id, category_id, description, amount, type, occurred_at)
SELECT
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    c.id,
    s.description,
    s.amount,
    s.type,
    -- Se ancla a la hora local de la zona de la aplicacion y no a now()
    -- directo: asi las horas de la semilla salen iguales a las del
    -- prototipo (14:30, 08:15...) sea cual sea la zona del servidor.
    (date_trunc('day', now() AT TIME ZONE 'America/Argentina/Buenos_Aires')
        - make_interval(days => s.days_ago)
        + s.time_of_day) AT TIME ZONE 'America/Argentina/Buenos_Aires'
FROM (VALUES
    -- descripcion,              categoria,       monto,     tipo,      dias atras, hora
    ('Restaurante Central',      'comida',        2450.00,   'EXPENSE', 0, TIME '14:30'),
    ('Transporte Público',       'transporte',     150.00,   'EXPENSE', 0, TIME '08:15'),
    ('Transferencia Recibida',   'ingreso',      15000.00,   'INCOME',  0, TIME '06:00'),
    ('Netflix',                  'ocio',          1290.00,   'EXPENSE', 1, TIME '12:00'),
    ('Transporte Público',       'transporte',     150.00,   'EXPENSE', 1, TIME '07:30'),
    ('Supermercado Día',         'supermercado',  5800.00,   'EXPENSE', 1, TIME '19:00'),
    ('Spotify',                  'ocio',           590.00,   'EXPENSE', 2, TIME '10:00'),
    ('Luz y Gas',                'servicios',     3200.00,   'EXPENSE', 2, TIME '09:00'),
    ('Sushi Express',            'comida',        1800.00,   'EXPENSE', 3, TIME '20:30'),
    ('Salario',                  'ingreso',      43000.00,   'INCOME',  3, TIME '08:00'),
    ('Café del Centro',          'comida',         320.00,   'EXPENSE', 4, TIME '09:00'),
    ('Internet Hogar',           'servicios',     1500.00,   'EXPENSE', 4, TIME '11:00')
) AS s (description, category_slug, amount, type, days_ago, time_of_day)
JOIN categories c ON c.slug = s.category_slug AND c.user_id IS NULL;

-- ---------------------------------------------------------------------
-- Historia de los seis meses anteriores
--
-- Un salario fijo mas gastos recurrentes, para que el grafico de
-- tendencia mensual y la comparacion "vs periodo anterior" tengan con
-- que calcular. Se generan con generate_series en lugar de escribir
-- 60 filas a mano.
-- ---------------------------------------------------------------------
INSERT INTO transactions (user_id, account_id, category_id, description, amount, type, occurred_at)
SELECT
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    c.id,
    p.description,
    p.amount,
    p.type,
    ((date_trunc('month', now() AT TIME ZONE 'America/Argentina/Buenos_Aires')
        - make_interval(months => m))
        + make_interval(days => p.day_of_month - 1)
        + p.time_of_day) AT TIME ZONE 'America/Argentina/Buenos_Aires'
FROM generate_series(1, 6) AS m
CROSS JOIN (VALUES
    ('Salario',             'ingreso',      43000.00, 'INCOME',   1, TIME '08:00'),
    ('Alquiler',            'servicios',    18000.00, 'EXPENSE',  3, TIME '10:00'),
    ('Supermercado Día',    'supermercado',  5600.00, 'EXPENSE',  5, TIME '19:00'),
    ('Supermercado Día',    'supermercado',  5900.00, 'EXPENSE', 19, TIME '19:00'),
    ('Luz y Gas',           'servicios',     3100.00, 'EXPENSE',  8, TIME '09:00'),
    ('Internet Hogar',      'servicios',     1500.00, 'EXPENSE',  8, TIME '11:00'),
    ('Netflix',             'ocio',          1290.00, 'EXPENSE', 12, TIME '12:00'),
    ('Spotify',             'ocio',           590.00, 'EXPENSE', 12, TIME '10:00'),
    ('Restaurante Central', 'comida',        2300.00, 'EXPENSE', 15, TIME '14:00'),
    ('Café del Centro',     'comida',         310.00, 'EXPENSE', 22, TIME '09:00'),
    ('Transporte Público',  'transporte',    1800.00, 'EXPENSE', 25, TIME '08:00')
) AS p (description, category_slug, amount, type, day_of_month, time_of_day)
JOIN categories c ON c.slug = p.category_slug AND c.user_id IS NULL;
