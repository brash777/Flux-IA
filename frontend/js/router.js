/* =====================================================================
   Enrutador de Flux IA.

   Rutas por hash (#/inicio, #/movimientos…): funcionan con cualquier
   servidor de archivos estáticos, Live Server incluido, sin configurar
   nada. Los nombres de ruta están en español porque el usuario los ve en
   la barra de direcciones.

   Cada pantalla es un módulo con mount(view, ctx) que arma su contenido
   dentro de `view` y devuelve { unmount } para limpiarse al salir:
   escuchas, animaciones y disparadores de scroll.

   `resolve` decide a dónde se va de verdad (por ejemplo, al login si una
   ruta exige sesión y no la hay). `onShow` avisa qué ruta quedó a la
   vista, para la barra de navegación.
   ===================================================================== */

import * as motion from './motion.js';

export function createRouter({ outlet, device, routes, resolve, onShow }) {
  let current = null;
  let lastRequest = 0;

  const requested = () => location.hash.replace(/^#\/?/, '');

  function navigate(name) {
    const hash = `#/${name}`;
    if (location.hash === hash) return;
    location.hash = hash;
  }

  async function show(name, { launch = false } = {}) {
    const target = resolve(name);
    if (target !== name) history.replaceState(null, '', `#/${target}`);

    // Si el usuario toca dos destinos seguidos, gana el último.
    const request = ++lastRequest;
    const screen = await routes[target].load();
    if (request !== lastRequest) return;

    const view = document.createElement('section');
    view.className = 'view';
    view.dataset.view = target;
    outlet.append(view);
    onShow?.(target);

    const mounted = screen.mount(view, { navigate, device, launch, route: target }) ?? {};
    const previous = current;
    current = { view, unmount: mounted.unmount ?? (() => {}) };

    // La apertura la dirige la pantalla si sabe hacerla (la bienvenida);
    // si no, se muestra la versión simple.
    if (launch && !mounted.ownsLaunch) motion.playLaunch({ device });

    if (previous) {
      await motion.swapViews(previous.view, view);
      previous.unmount();
      previous.view.remove();
    } else if (!launch) {
      await motion.enterView(view);
    }

    motion.refresh();
    focusTitle(view);
  }

  /* Al cambiar de pantalla, el foco va al título: un lector de pantalla
     anuncia dónde se está, y el teclado sigue desde ahí. */
  function focusTitle(view) {
    const title = view.querySelector('h1');
    if (!title) return;
    title.setAttribute('tabindex', '-1');
    title.focus({ preventScroll: true });
  }

  function start({ launch }) {
    window.addEventListener('hashchange', () => show(requested()));
    show(requested(), { launch });
  }

  return { start, navigate };
}
