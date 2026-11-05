/* =====================================================================
   Dirección del servidor: de lo que escribe el usuario a una dirección
   usable («192.168.1.20:8080» → «http://192.168.1.20:8080»), y dónde
   buscar el backend cuando la app corre en Expo Go.

   No importa nada de React Native, para poder probarla con Node.
   ===================================================================== */

const HAS_SCHEME = /^[a-z][a-z0-9+.-]*:\/\//i;
const HTTP_URL = /^(https?):\/\/([^\s/?#]+)(\/[^\s?#]*)?$/i;
const IPV4_HOST = /^(\d{1,3}(?:\.\d{1,3}){3})(?::\d+)?(?:\/.*)?$/;
const SCHEME_HOST_PORT = /^(https?):\/\/([^/:]+)(?::(\d+))?/i;

// El puerto por omisión del backend y el que se le pone cuando otra app
// ya ocupa el 8080 en el computador.
const BACKEND_PORTS = ['8080', '8081'];

/** Devuelve la dirección normalizada, o null si no se puede usar. */
export function normalizeServerUrl(input) {
  const text = String(input ?? '').trim().replace(/[?#].*$/, '');
  if (!text) return null;
  // Sin esquema se asume http: un servidor en la red local no tiene HTTPS.
  const withScheme = HAS_SCHEME.test(text) ? text : `http://${text}`;
  const match = HTTP_URL.exec(withScheme);
  if (!match) return null;
  const [, scheme, host, path = ''] = match;
  return `${scheme.toLowerCase()}://${host.toLowerCase()}${path.replace(/\/+$/, '')}`;
}

/**
 * IP del computador desde el que Expo Go cargó la app
 * («192.168.1.20:8090» → «192.168.1.20»). null en el APK, que no tiene
 * servidor de Expo, y con un túnel de Expo, cuyo host no es el computador.
 */
export function devComputerHost(hostUri) {
  const match = IPV4_HOST.exec(String(hostUri ?? '').trim());
  return match ? match[1] : null;
}

/**
 * Dónde buscar el backend en ese computador, en orden de preferencia: el
 * puerto de la dirección guardada y después los habituales. Vacía si la
 * guardada ya apunta a ese computador, porque no hay nada que corregir, o
 * si es HTTPS: un túnel o un servidor publicado no dependen de la IP local.
 */
export function devServerCandidates(savedUrl, host) {
  if (!host) return [];
  let ports = BACKEND_PORTS;
  if (savedUrl) {
    const match = SCHEME_HOST_PORT.exec(savedUrl);
    if (!match || match[1].toLowerCase() === 'https' || match[2] === host) return [];
    const savedPort = match[3] ?? '80';
    ports = [savedPort, ...BACKEND_PORTS.filter((port) => port !== savedPort)];
  }
  return ports.map((port) => `http://${host}:${port}`);
}
