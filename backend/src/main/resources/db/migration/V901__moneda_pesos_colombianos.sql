-- La aplicación pasa a usar pesos colombianos.
--
-- Va como V901 y no como V3 porque las bases de desarrollo ya aplicaron la
-- semilla V900, y Flyway rechaza una versión menor que la última aplicada.
-- En producción, sin la semilla, corre después de V2 sin problema.
--
-- Solo cambia la moneda de las cuentas: los montos no se convierten. Hasta
-- ahora solo hay datos de prueba, sin una tasa de cambio que respetar.
ALTER TABLE accounts ALTER COLUMN currency SET DEFAULT 'COP';

UPDATE accounts SET currency = 'COP' WHERE currency = 'ARS';
