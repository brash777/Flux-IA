/* =====================================================================
   Login: provisional. Existe para que «Empezar» tenga a dónde llevar
   mientras se construye la pantalla real, conectada al backend, en el
   paso siguiente.
   ===================================================================== */

export function mount(view, { navigate }) {
  view.classList.add('app-bg');
  view.innerHTML = `
    <main class="screen">
      <h1 class="h1">Ingresar</h1>
      <div class="glass glass--strong state" style="margin-top: var(--space-6)">
        <span class="state__icon"><svg class="icon" aria-hidden="true"><use href="assets/icons/ui.svg#i-lock"/></svg></span>
        <p class="state__title">Pantalla en construcción</p>
        <p class="state__text">El login conectado al backend llega en el siguiente paso.</p>
        <button class="btn btn--glass" type="button" data-back>
          <svg class="icon" aria-hidden="true"><use href="assets/icons/ui.svg#i-arrow-left"/></svg>Volver a la bienvenida
        </button>
      </div>
    </main>`;

  const back = view.querySelector('[data-back]');
  const goBack = () => navigate('bienvenida');
  back.addEventListener('click', goBack);

  return { unmount: () => back.removeEventListener('click', goBack) };
}
