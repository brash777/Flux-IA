/* =====================================================================
   Teclado en pantalla: que no tape ni desarme la app.

   En iPhone, el teclado no achica la página: iOS la corre hacia arriba
   para mostrar el campo y la cabecera queda fuera de la pantalla (igual
   en Safari que dentro de la app de Expo). visualViewport dice qué parte
   se ve; mientras el teclado está abierto, la app se ajusta a esa parte
   (css/app.css, «Teclado»): la cabecera queda arriba y el campo, justo
   encima del teclado.

   Además, mientras se escribe en un celular, la barra inferior se oculta:
   el teclado ya ocupa media pantalla.
   ===================================================================== */

// Más que lo que se esconde la barra de direcciones de Safari y menos que
// cualquier teclado.
const KEYBOARD_MIN_PX = 120;
const NOT_TEXT = new Set(['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit']);

const root = document.documentElement;

function isTextField(element) {
  if (!element) return false;
  if (element.localName === 'textarea' || element.isContentEditable) return true;
  return element.localName === 'input' && !NOT_TEXT.has(element.type);
}

export function watchKeyboard() {
  const touch = window.matchMedia('(pointer: coarse)');
  const syncTyping = () => {
    root.classList.toggle('is-typing', touch.matches && isTextField(document.activeElement));
  };
  document.addEventListener('focusin', syncTyping);
  // Al pasar de un campo a otro, el foco sale antes de entrar: se mira
  // cuando ya llegó a destino, para que la barra no parpadee.
  document.addEventListener('focusout', () => setTimeout(syncTyping));

  const viewport = window.visualViewport;
  if (!viewport) return;
  let frame = 0;
  let fitted = 0;

  const fit = () => {
    frame = 0;
    const open = root.clientHeight - viewport.height > KEYBOARD_MIN_PX;
    if (open && (window.scrollY !== 0 || viewport.offsetTop !== 0)) {
      // La página nunca se desplaza (cada pantalla tiene su scroll): se
      // deshace el corrimiento que hizo iOS para mostrar el campo.
      window.scrollTo(0, 0);
    }
    root.classList.toggle('keyboard-open', open);
    if (!open) {
      root.style.removeProperty('--viewport-h');
      root.style.removeProperty('--viewport-top');
      fitted = 0;
      return;
    }
    root.style.setProperty('--viewport-h', `${viewport.height}px`);
    root.style.setProperty('--viewport-top', `${viewport.offsetTop}px`);
    if (viewport.height === fitted) return;
    fitted = viewport.height;
    // Con la app ya achicada, el campo activo puede quedar bajo el borde de
    // su pantalla: se trae a la vista dentro de su propio scroll.
    requestAnimationFrame(() => {
      const field = document.activeElement;
      if (isTextField(field)) field.scrollIntoView({ block: 'nearest' });
    });
  };
  const schedule = () => {
    if (!frame) frame = requestAnimationFrame(fit);
  };
  viewport.addEventListener('resize', schedule);
  viewport.addEventListener('scroll', schedule);
}
