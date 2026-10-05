/* =====================================================================
   Arranque de Flux IA.
   ===================================================================== */

import { createRouter } from './router.js';

const LAUNCH_KEY = 'flux.launchShown';

/* La apertura se ve una vez por sesión: volver a verla en cada recarga
   cansaría. Con ?apertura en la dirección se fuerza, para mostrarla. */
function shouldPlayLaunch() {
  if (new URLSearchParams(location.search).has('apertura')) return true;
  try {
    if (sessionStorage.getItem(LAUNCH_KEY)) return false;
    sessionStorage.setItem(LAUNCH_KEY, '1');
  } catch {
    // Sin almacenamiento (navegación privada estricta): se muestra igual.
  }
  return true;
}

const router = createRouter({
  outlet: document.getElementById('outlet'),
  device: document.getElementById('device'),
  routes: {
    bienvenida: () => import('./screens/welcome.js'),
    ingresar: () => import('./screens/login.js'),
  },
  fallback: 'bienvenida',
});

router.start({ launch: shouldPlayLaunch() });
