-- =====================================================================
-- Categorias del sistema (user_id NULL = compartidas por todos).
--
-- Los slugs, los iconos y los colores salen del prototipo movil: son
-- exactamente los 7 filtros de la pantalla "Movimientos", de modo que
-- el frontend no necesita traducir nada.
-- =====================================================================
INSERT INTO categories (user_id, slug, name, icon, color, kind) VALUES
    (NULL, 'comida',        'Comida',        '🍽️', '#6c63ff', 'EXPENSE'),
    (NULL, 'servicios',     'Servicios',     '⚡',  '#f59e0b', 'EXPENSE'),
    (NULL, 'ocio',          'Ocio',          '🎮', '#ec4899', 'EXPENSE'),
    (NULL, 'transporte',    'Transporte',    '🚌', '#10b981', 'EXPENSE'),
    (NULL, 'supermercado',  'Supermercado',  '🛒', '#06b6d4', 'EXPENSE'),
    (NULL, 'otros',         'Otros',         '📦', '#374151', 'EXPENSE'),
    (NULL, 'ingreso',       'Ingresos',      '💰', '#10b981', 'INCOME');
