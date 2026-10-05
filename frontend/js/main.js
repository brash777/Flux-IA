/* =====================================================================
   Arranque de Flux IA: rutas, barra de navegación y sesión.
   ===================================================================== */

import { createRouter } from './router.js';
import { hasSession, onSessionExpired } from './api.js';
import * as motion from './motion.js';
import { setFlash } from './flash.js';

const LAUNCH_KEY = 'flux.launchShown';

/* La apertura se ve una vez por sesión del navegador: verla en cada
   recarga cansaría. Con ?apertura en la dirección se fuerza. */
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

const auth = () => import('./screens/auth.js');

/* access: 'guest' = solo sin sesión, 'user' = exige sesión.
   tab: el destino de la barra inferior que queda marcado. */
const routes = {
  bienvenida: { load: () => import('./screens/welcome.js'), access: 'guest' },
  ingresar: { load: auth, access: 'guest' },
  'crear-cuenta': { load: auth, access: 'guest' },
  recuperar: { load: auth, access: 'guest' },
  inicio: { load: () => import('./screens/home.js'), access: 'user', tab: 'inicio' },
  movimientos: { load: () => import('./screens/movements.js'), access: 'user', tab: 'movimientos' },
  reportes: { load: () => import('./screens/reports.js'), access: 'user', tab: 'reportes' },
  chat: { load: () => import('./screens/chat.js'), access: 'user', tab: 'chat' },
  perfil: { load: () => import('./screens/profile.js'), access: 'user', tab: 'perfil' },
};

function resolve(name) {
  const signedIn = hasSession();
  if (!Object.hasOwn(routes, name)) return signedIn ? 'inicio' : 'bienvenida';
  if (routes[name].access === 'user' && !signedIn) return 'ingresar';
  if (routes[name].access === 'guest' && signedIn) return 'inicio';
  return name;
}

const nav = document.getElementById('nav');

function updateNav(route) {
  const tab = routes[route].tab;
  motion.showNav(nav, Boolean(tab));
  nav.querySelectorAll('a').forEach((link) => {
    if (link.dataset.tab === tab) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
}

const router = createRouter({
  outlet: document.getElementById('outlet'),
  device: document.getElementById('device'),
  routes,
  resolve,
  onShow: updateNav,
});

// Si la sesión vence en cualquier pantalla, se vuelve al login con aviso.
onSessionExpired(() => {
  setFlash('Tu sesión venció. Ingresa de nuevo.');
  router.navigate('ingresar');
});

router.start({ launch: shouldPlayLaunch() });
