/* =====================================================================
   Configuración del frontend.
   ===================================================================== */

/* Dirección de la API. El backend corre en el 8080 (ver README). */
export const API_BASE = 'http://localhost:8080/api/v1';

/* Zona en la que se muestran las fechas. Tiene que ser la misma que
   flux.timezone del backend: es la que decide en qué mes cae cada
   movimiento, y la pantalla no puede decir «30 sep» de un gasto que el
   reporte cuenta en octubre. */
export const APP_TIMEZONE = 'America/Argentina/Buenos_Aires';

/* Moneda cuando la API todavía no dijo cuál usar. Es la misma que asigna
   el backend a una cuenta nueva. */
export const DEFAULT_CURRENCY = 'ARS';
