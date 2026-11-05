/* =====================================================================
   Configuración del frontend.
   ===================================================================== */

/* Dirección de la API.
   - Si la app la entrega el propio backend (http://localhost:8080/, la IP
     del computador desde un celular, o la app de Expo), la API está en el
     mismo origen: no hace falta CORS ni escribir ninguna IP aquí.
   - Con Live Server (puertos 5500 y 5501), la API está en el mismo equipo,
     en el puerto del backend. */
const LIVE_SERVER_PORTS = new Set(['5500', '5501']);
const BACKEND_PORT = '8080';

export const API_BASE = LIVE_SERVER_PORTS.has(location.port)
  ? `${location.protocol}//${location.hostname}:${BACKEND_PORT}/api/v1`
  : `${location.origin}/api/v1`;

/* Zona en la que se muestran las fechas. Tiene que ser la misma que
   flux.timezone del backend: es la que decide en qué mes cae cada
   movimiento, y la pantalla no puede decir «30 sep» de un gasto que el
   reporte cuenta en octubre. */
export const APP_TIMEZONE = 'America/Argentina/Buenos_Aires';

/* Moneda cuando la API todavía no dijo cuál usar. Es la misma que asigna
   el backend a una cuenta nueva. */
export const DEFAULT_CURRENCY = 'ARS';
