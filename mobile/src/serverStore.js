/* =====================================================================
   Dónde está el servidor: se guarda en el celular y se comprueba antes
   de usarlo.
   ===================================================================== */

import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import { devComputerHost, devServerCandidates, normalizeServerUrl } from './serverUrl';

const KEY = 'flux.serverUrl';
const CHECK_TIMEOUT_MS = 8000;
// La app acaba de llegar desde ese computador: si no contesta enseguida,
// el backend no está ahí.
const PROBE_TIMEOUT_MS = 3000;

/**
 * La dirección con la que arranca la app.
 *
 * En Expo Go, la app se carga desde el computador que corre el servidor de
 * Expo, que es también el del backend (mobile/README.md). Si la IP de ese
 * computador cambió —otra red Wi-Fi, otra concesión del router—, la
 * dirección guardada quedó vieja y el celular no llegaría al backend: se
 * busca Flux IA en la IP de ahora y, si responde, se guarda. En el APK no
 * hay servidor de Expo y se usa la dirección guardada.
 */
export async function resolveServerUrl() {
  const saved = await loadServerUrl();
  const candidates = devServerCandidates(saved, devComputerHost(Constants.expoConfig?.hostUri));
  // Todas a la vez, para no sumar esperas; gana la primera que sea Flux IA
  // en orden de preferencia.
  const checks = candidates.map((url) => checkServer(url, PROBE_TIMEOUT_MS));
  for (const [index, check] of checks.entries()) {
    if ((await check) === null) {
      await saveServerUrl(candidates[index]);
      return candidates[index];
    }
  }
  return saved;
}

/* La dirección elegida por el usuario manda. Si no eligió ninguna, se usa
   la que vino al compilar (EXPO_PUBLIC_SERVER_URL, ver .env.example); si
   tampoco hay, la app la pide. */
async function loadServerUrl() {
  try {
    const saved = await AsyncStorage.getItem(KEY);
    if (saved) return saved;
  } catch {
    // Sin almacenamiento la app pide la dirección cada vez: es aceptable.
  }
  return normalizeServerUrl(process.env.EXPO_PUBLIC_SERVER_URL);
}

export async function saveServerUrl(url) {
  try {
    await AsyncStorage.setItem(KEY, url);
  } catch {
    // Ídem: la dirección vale para esta sesión.
  }
}

/**
 * Comprueba que en esa dirección responda Flux IA y no otro servidor: en
 * el mismo computador puede haber otra app escuchando en el 8080. Se pide
 * el manifiesto de la app web porque es público, pequeño y trae el nombre.
 *
 * Devuelve null si todo está bien, o el mensaje para el usuario.
 */
export async function checkServer(url, timeoutMs = CHECK_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  let response;
  try {
    response = await fetch(`${url}/manifest.webmanifest`, { signal: controller.signal });
  } catch {
    return controller.signal.aborted
      ? 'El servidor no respondió a tiempo. Revisa que el celular y el computador estén en la misma red Wi-Fi.'
      : 'No pudimos conectar con esa dirección. Revisa que el backend esté corriendo y que la IP y el puerto sean los correctos.';
  } finally {
    clearTimeout(timer);
  }

  let manifest = null;
  try {
    manifest = JSON.parse(await response.text());
  } catch {
    // No es JSON: responde otra cosa.
  }
  return response.ok && manifest?.name === 'Flux IA'
    ? null
    : 'En esa dirección responde otra aplicación, no Flux IA. Revisa el puerto.';
}
