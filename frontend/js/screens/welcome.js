/* =====================================================================
   Pantalla de bienvenida: qué es Flux IA, antes del login.

   No muestra datos: es anterior a la sesión y la API exige token. Por eso
   las ilustraciones son solo formas, sin cifras. Todo el texto está fijo
   en este archivo, así que las plantillas se insertan con innerHTML sin
   riesgo: nada viene de la API ni del usuario.

   Con movimiento reducido se muestra una versión estática en una sola
   pantalla. El movimiento lo dirige motion.welcome().
   ===================================================================== */

import * as motion from '../motion.js';

const ICONS = 'assets/icons/ui.svg';
const APP_ICON = 'assets/icons/app-icon-b-tarjetas.svg';

const icon = (name) => `<svg class="icon" aria-hidden="true"><use href="${ICONS}#i-${name}"/></svg>`;

/* El dibujo de la tarjeta de adelante del ícono: una barra y una onda,
   en el mismo sistema de coordenadas que el SVG del ícono. */
const CARD_ART = `
  <svg class="hero-card__art" viewBox="0 0 244 156" aria-hidden="true">
    <path d="M34 40h54" stroke-width="10" opacity="0.85"/>
    <path d="M34 110c34-26 64 22 100-4s58-22 80-10" stroke-width="11"/>
  </svg>`;

/* Las dos tarjetas del ícono. data-launch-card: la apertura las hace
   volar desde el ícono hasta aquí (en este orden: atrás, adelante). */
function heroCards(withDepth) {
  const depth = (value) => (withDepth ? ` data-depth="${value}"` : '');
  return `
    <div class="welcome-cards" data-hero-cards aria-hidden="true">
      <div class="hero-card-wrap hero-card-wrap--back" data-launch-card${depth(-0.12)}>
        <div class="hero-card hero-card--back"></div>
      </div>
      <div class="hero-card-wrap hero-card-wrap--front" data-launch-card${depth(-0.28)}>
        <div class="hero-card hero-card--front">${CARD_ART}<span class="hero-card__mark">Flux IA</span></div>
      </div>
    </div>`;
}

const TITLE = ['Tu dinero,', 'claro y en', 'movimiento.'];
const LEAD = 'Registra lo que entra y lo que sale, mira a dónde va y pregúntale a tu asistente lo que quieras saber.';

const lines = (texts) => texts.map((text) => `<span class="line"><span>${text}</span></span>`).join('');

/* --- Versión con scroll --------------------------------------------- */

function scrollTemplate() {
  return `
    <main class="screen screen--bare">
      <section class="welcome-section welcome-hero" data-hero data-welcome-section>
        <div class="welcome-orb-layer" data-depth="0.45" aria-hidden="true">
          <div class="orb" style="width:230px; height:230px; top:380px; right:-90px"></div>
          <div class="orb" style="width:130px; height:130px; top:560px; left:-60px; opacity:.85"></div>
        </div>
        <div data-hero-text>
          <p class="welcome-brand" data-hero-fade><img src="${APP_ICON}" alt="">Flux IA</p>
          <h1 class="display welcome-title">${lines(TITLE)}</h1>
          <p class="welcome-lead" data-hero-fade>${LEAD}</p>
        </div>
        ${heroCards(true)}
      </section>

      <section class="welcome-section welcome-feature" data-feature data-welcome-section>
        <div class="welcome-art" aria-hidden="true">
          <div class="orb" data-depth="0.5" style="width:170px; height:170px; top:0; right:-40px"></div>
          <div class="art-card" data-grow data-depth="-0.35">
            <div data-stagger>
              <div class="art-row">
                <span class="art-chip art-chip--income">${icon('arrow-up')}</span>
                <span><span class="art-line"></span><span class="art-line art-line--short"></span></span>
                <span class="art-line art-line--income"></span>
              </div>
              <div class="art-row">
                <span class="art-chip art-chip--expense">${icon('arrow-down')}</span>
                <span><span class="art-line" style="width:80%"></span><span class="art-line art-line--short"></span></span>
                <span class="art-line"></span>
              </div>
              <div class="art-row">
                <span class="art-chip art-chip--brand">${icon('wallet')}</span>
                <span><span class="art-line" style="width:65%"></span><span class="art-line art-line--short"></span></span>
                <span class="art-line"></span>
              </div>
            </div>
            <span class="art-fab">${icon('plus')}</span>
          </div>
        </div>
        <div>
          <p class="eyebrow">Movimientos</p>
          <h2 class="h1">${lines(['Cada peso,', 'en su lugar.'])}</h2>
          <p class="welcome-copy" data-reveal>Anota un gasto o un ingreso en segundos. Flux IA lo ordena por categoría y fecha, y tu saldo se actualiza solo.</p>
        </div>
      </section>

      <section class="welcome-section welcome-feature" data-feature data-welcome-section>
        <div class="welcome-art" aria-hidden="true">
          <div class="orb" data-depth="0.5" style="width:160px; height:160px; top:10px; left:-50px"></div>
          <div class="art-card art-report" data-grow data-depth="-0.35">
            <svg class="art-donut" viewBox="0 0 120 120">
              <circle class="seg-1" cx="60" cy="60" r="46" stroke-dasharray="112.6 289.03" data-draw="112.6" transform="rotate(-90 60 60)"/>
              <circle class="seg-2" cx="60" cy="60" r="46" stroke-dasharray="75 289.03" data-draw="75" transform="rotate(54 60 60)"/>
              <circle class="seg-3" cx="60" cy="60" r="46" stroke-dasharray="54.8 289.03" data-draw="54.8" transform="rotate(151.1 60 60)"/>
              <circle class="seg-4" cx="60" cy="60" r="46" stroke-dasharray="34.6 289.03" data-draw="34.6" transform="rotate(223.1 60 60)"/>
            </svg>
            <div class="art-bars">
              <span class="art-bar" data-bar style="height:45%"></span>
              <span class="art-bar" data-bar style="height:62%"></span>
              <span class="art-bar" data-bar style="height:50%"></span>
              <span class="art-bar" data-bar style="height:78%"></span>
              <span class="art-bar" data-bar style="height:58%"></span>
              <span class="art-bar art-bar--current" data-bar style="height:92%"></span>
            </div>
          </div>
        </div>
        <div>
          <p class="eyebrow">Reportes</p>
          <h2 class="h1">${lines(['Mira a dónde', 'se va.'])}</h2>
          <p class="welcome-copy" data-reveal>Elige el mes, el trimestre o el año y ve cuánto entró, cuánto salió, cuánto ahorraste y en qué gastas más.</p>
        </div>
      </section>

      <section class="welcome-section welcome-feature" data-feature data-welcome-section>
        <div class="welcome-art" aria-hidden="true">
          <div class="orb" data-depth="0.5" style="width:170px; height:170px; top:0; right:-50px"></div>
          <div class="art-chat" data-grow data-depth="-0.35">
            <p class="art-bubble art-bubble--user" data-bubble>¿En qué gasté más este mes?</p>
            <div class="art-bubble art-bubble--ai" data-bubble>
              <img src="${APP_ICON}" alt="">
              <span class="art-answer"><span class="art-line"></span><span class="art-line" style="width:80%"></span><span class="art-line art-line--short"></span></span>
            </div>
            <div class="art-bubble art-bubble--ai" data-bubble>
              <img src="${APP_ICON}" alt="">
              <span class="typing" data-typing><span></span><span></span><span></span></span>
            </div>
          </div>
        </div>
        <div>
          <p class="eyebrow">Asistente de IA</p>
          <h2 class="h1">${lines(['Pregunta', 'como hablas.'])}</h2>
          <p class="welcome-copy" data-reveal>«¿En qué gasté más este mes?». El asistente te responde con tus movimientos reales, no con suposiciones.</p>
        </div>
      </section>

      <section class="welcome-final" data-final data-welcome-section>
        <div class="welcome-final__stage" data-final-stage>
          <div class="welcome-final__card" data-final-card aria-hidden="true">${CARD_ART}</div>
          <div class="welcome-final__content" data-final-content>
            <h2 class="display">Empecemos.</h2>
            <p>Crea tu cuenta o ingresa con la que ya tienes.</p>
          </div>
        </div>
      </section>
    </main>

    <div class="welcome-bar" data-welcome-bar>
      <div class="welcome-dots" aria-hidden="true">
        <span data-dot></span><span data-dot></span><span data-dot></span><span data-dot></span><span data-dot></span>
      </div>
      <button class="btn btn--primary welcome-start" type="button" data-start>Empezar ${icon('arrow-right')}</button>
    </div>`;
}

/* --- Versión estática (movimiento reducido) ------------------------ */

function staticTemplate() {
  return `
    <main class="screen screen--bare welcome-static">
      <p class="welcome-brand"><img src="${APP_ICON}" alt="">Flux IA</p>
      <div>
        <h1 class="display welcome-title">${TITLE.join(' ')}</h1>
        <p class="welcome-lead">${LEAD}</p>
      </div>
      ${heroCards(false)}
      <ul class="feature-list">
        <li><span class="feature-list__icon">${icon('swap')}</span><p style="margin:0"><strong>Movimientos</strong><span>Anota gastos e ingresos; tu saldo se actualiza solo.</span></p></li>
        <li><span class="feature-list__icon">${icon('chart')}</span><p style="margin:0"><strong>Reportes</strong><span>Mira cuánto entró, cuánto salió y en qué gastas más.</span></p></li>
        <li><span class="feature-list__icon">${icon('chat')}</span><p style="margin:0"><strong>Asistente de IA</strong><span>Pregunta como hablas; responde con tus datos reales.</span></p></li>
      </ul>
    </main>

    <div class="welcome-bar" data-welcome-bar>
      <button class="btn btn--primary btn--block" type="button" data-start>Empezar ${icon('arrow-right')}</button>
    </div>`;
}

/* --- Montaje -------------------------------------------------------- */

export function mount(view, { navigate, device, launch }) {
  view.classList.add('app-bg');
  view.innerHTML = motion.prefersReducedMotion() ? staticTemplate() : scrollTemplate();

  const scroller = view.querySelector('.screen');
  const start = view.querySelector('[data-start]');
  const goToLogin = () => navigate('ingresar');
  start.addEventListener('click', goToLogin);

  const stopMotion = motion.welcome({ root: view, scroller, device, launch });

  return {
    ownsLaunch: true,
    unmount() {
      start.removeEventListener('click', goToLogin);
      stopMotion();
    },
  };
}
