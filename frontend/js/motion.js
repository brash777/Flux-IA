/* =====================================================================
   Movimiento de Flux IA.

   Toda la coreografía con GSAP vive aquí. Las pantallas piden efectos
   («la bienvenida», «cambiar de vista») y nunca llaman a GSAP. Las reglas
   están en DESIGN.md, sección 12.

   Si GSAP no cargó (sin conexión, CDN caído), la app funciona igual: cada
   efecto deja el contenido en su estado final, sin animar.
   ===================================================================== */

const { gsap, ScrollTrigger } = window;
const ready = Boolean(gsap && ScrollTrigger);

if (ready) {
  gsap.registerPlugin(ScrollTrigger);
  // En el celular la barra del navegador cambia el alto al desplazar; sin
  // esto, cada cambio recalcularía todos los disparadores.
  ScrollTrigger.config({ ignoreMobileResize: true });
}

const reducedQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

export function prefersReducedMotion() {
  return reducedQuery.matches;
}

function animates() {
  return ready && !reducedQuery.matches;
}

/* Duraciones leídas de tokens.css: una sola fuente para CSS y JS. */
function token(name, fallback) {
  const value = parseFloat(getComputedStyle(document.documentElement).getPropertyValue(name));
  return Number.isFinite(value) ? value : fallback;
}

const DUR = {
  fast: token('--dur-fast', 180) / 1000,
  base: token('--dur-base', 280) / 1000,
  slow: token('--dur-slow', 480) / 1000,
};
const RISE = token('--rise', 16);
const EASE = { out: 'power3.out', inOut: 'power2.inOut', back: 'back.out(1.6)' };

/* GSAP avisa en consola cuando recibe una lista vacía; no todas las
   secciones tienen todo (barras, burbujas…), así que se filtra antes. */
function has(targets) {
  return Array.isArray(targets) ? targets.length > 0 : Boolean(targets);
}

function el(tag, className) {
  const node = document.createElement(tag);
  node.className = className;
  return node;
}

/* Recalcula los disparadores de scroll: hace falta cuando cambia el alto
   del contenido (llegaron datos, cargó una fuente, terminó una transición). */
export function refresh() {
  if (ready) ScrollTrigger.refresh();
}

/* --- Cambio de pantalla -------------------------------------------- */

export function enterView(view) {
  if (!ready) return Promise.resolve();
  const reduced = !animates();
  return gsap.fromTo(view,
    { opacity: 0, y: reduced ? 0 : RISE },
    { opacity: 1, y: 0, duration: reduced ? DUR.fast : DUR.base, ease: EASE.out, clearProps: 'transform,opacity' },
  ).then();
}

/* La entrante se puede tocar desde el primer cuadro; la saliente deja de
   recibir toques en cuanto empieza a irse. */
export function swapViews(from, to) {
  from.style.pointerEvents = 'none';
  if (!ready) return Promise.resolve();
  const reduced = !animates();
  return gsap.timeline()
    .to(from, { opacity: 0, y: reduced ? 0 : 8, duration: DUR.fast, ease: 'power1.in' }, 0)
    .fromTo(to,
      { opacity: 0, y: reduced ? 0 : RISE },
      { opacity: 1, y: 0, duration: DUR.base, ease: EASE.out, clearProps: 'transform,opacity' },
      reduced ? 0 : 0.06)
    .then();
}

/* --- Apertura ------------------------------------------------------- */

/* Dónde están las dos tarjetas dentro del ícono B (app-icon-b-tarjetas.svg),
   en unidades de su lienzo de 512 y ya con la escala de 1,1 del dibujo.
   Si el SVG cambia, estos números cambian con él. */
const ICON_CARDS = [
  { cx: 267.0, cy: 222.6, width: 259.6, tilt: -16 }, // la de atrás
  { cx: 249.4, cy: 293.4, width: 268.4, tilt: -6 },  // la de adelante
];

/**
 * El ícono aparece al centro, su fondo violeta crece hasta cubrir la
 * pantalla y, si se pasan `cards`, las tarjetas del ícono vuelan hasta
 * quedar exactamente donde están esas tarjetas reales.
 *
 * Lo que vuela son copias: las reales quedan ocultas debajo, en su sitio,
 * y aparecen cuando las copias se retiran. Así la pantalla de abajo nunca
 * se mueve ni se mide dos veces. Un toque salta la apertura.
 */
export function playLaunch({ device, cards = [], onReveal = () => {} }) {
  if (!ready) {
    onReveal();
    return Promise.resolve();
  }

  const layer = el('div', 'launch');
  layer.setAttribute('aria-hidden', 'true');
  const bg = el('div', 'launch__bg');
  const tile = el('div', 'launch__tile');
  layer.append(bg, tile);
  device.append(layer);

  return new Promise((resolve) => {
    let revealed = false;
    const reveal = () => {
      if (revealed) return;
      revealed = true;
      onReveal();
    };

    let timeline;
    const finish = () => {
      timeline?.kill();
      if (has(cards)) gsap.set(cards, { clearProps: 'opacity' });
      layer.remove();
      reveal();
      resolve();
    };
    layer.addEventListener('pointerdown', finish, { once: true });

    if (!animates()) {
      timeline = gsap.timeline({ onComplete: finish })
        .fromTo(tile, { opacity: 0 }, { opacity: 1, duration: DUR.fast, ease: 'none' })
        .add(reveal, '+=0.2')
        .to([bg, tile], { opacity: 0, duration: DUR.fast, ease: 'none' });
      return;
    }

    const box = device.getBoundingClientRect();
    const size = tile.offsetWidth;
    const unit = size / 512;

    if (has(cards)) gsap.set(cards, { opacity: 0 });
    const clones = [...cards].map((wrap, index) => {
      const rect = wrap.getBoundingClientRect();
      const left = rect.left - box.left;
      const top = rect.top - box.top;
      const clone = el('div', 'launch__card');
      Object.assign(clone.style, {
        left: `${left}px`,
        top: `${top}px`,
        width: `${rect.width}px`,
        height: `${rect.height}px`,
      });
      const card = wrap.firstElementChild;
      clone.append(card.cloneNode(true));
      layer.append(clone);

      // Punto de partida: la tarjeta tal como está dibujada en el ícono.
      const spec = ICON_CARDS[index] ?? ICON_CARDS[0];
      const tilt = parseFloat(getComputedStyle(card).getPropertyValue('--tilt')) || 0;
      gsap.set(clone, {
        x: box.width / 2 + (spec.cx - 256) * unit - (left + rect.width / 2),
        y: box.height / 2 + (spec.cy - 256) * unit - (top + rect.height / 2),
        scale: (spec.width * unit) / rect.width,
        rotation: spec.tilt - tilt,
        opacity: 0,
      });
      return clone;
    });

    // El cuadrado redondeado tiene que tapar también las esquinas: por eso
    // se divide por algo menos que su lado.
    const cover = Math.hypot(box.width, box.height) / (size * 0.8);

    timeline = gsap.timeline({ onComplete: finish })
      .fromTo(tile, { scale: 0.6, opacity: 0 }, { scale: 1, opacity: 1, duration: 0.35, ease: EASE.out }, 0.1)
      .to(tile, { scale: cover, duration: 0.65, ease: EASE.inOut }, 0.6)
      .add(reveal, 1.0)
      .to([bg, tile], { opacity: 0, duration: 0.35, ease: 'none' }, 1.05);
    if (has(clones)) {
      // Las tarjetas crecen a la par del violeta: con una curva más pareja
      // que la del fondo, para que no queden diminutas a mitad de camino.
      timeline
        .to(clones, { opacity: 1, duration: 0.15, ease: 'none' }, 0.32)
        .to(clones, { x: 0, y: 0, scale: 1, rotation: 0, duration: 0.75, ease: 'power2.inOut', stagger: 0.06 }, 0.6);
    }
  });
}

/* --- Bienvenida ----------------------------------------------------- */

/**
 * Toda la coreografía de la bienvenida: la apertura (si toca), la entrada
 * del titular y las escenas atadas al scroll. Devuelve la función que la
 * deshace al salir de la pantalla.
 *
 * La pantalla marca lo que se mueve con atributos:
 *   data-hero, data-hero-fade, data-launch-card, data-depth (velocidad),
 *   data-feature, data-grow, data-stagger, data-draw, data-bar,
 *   data-bubble, data-typing, data-final…, data-dot, data-welcome-bar.
 */
export function welcome({ root, scroller, device, launch }) {
  const cards = [...root.querySelectorAll('[data-launch-card]')];

  if (!animates()) {
    // Versión estática: sin escenas; como mucho, la apertura reducida.
    if (launch) playLaunch({ device });
    return () => {};
  }

  const q = (selector, scope = root) => [...scope.querySelectorAll(selector)];
  let intro;

  const ctx = gsap.context(() => {
    const hero = root.querySelector('[data-hero]');
    const heroCards = root.querySelector('[data-hero-cards]');
    const bar = root.querySelector('[data-welcome-bar]');

    // Entrada del titular, por líneas. Se prepara ya, para que nada
    // aparezca un instante antes de animarse.
    const heroLines = q('.line > span', hero);
    const heroFade = q('[data-hero-fade]', hero);
    gsap.set(heroLines, { yPercent: 110 });
    gsap.set([...heroFade, bar], { opacity: 0, y: RISE });

    intro = gsap.timeline({ paused: true })
      .to(heroLines, { yPercent: 0, duration: 0.7, ease: EASE.out, stagger: 0.08 }, 0)
      .to(heroFade, { opacity: 1, y: 0, duration: DUR.slow, ease: EASE.out, stagger: 0.06 }, 0.1)
      .to(bar, { opacity: 1, y: 0, duration: DUR.slow, ease: EASE.out }, 0.25);
    if (!launch) {
      // Sin apertura (se vuelve desde el login), las tarjetas suben solas.
      intro.fromTo(heroCards, { opacity: 0, y: 40 }, { opacity: 1, y: 0, duration: DUR.slow, ease: EASE.out }, 0.15);
    }

    // Hero: capas a distinta velocidad. data-depth positivo baja mientras
    // se sube (va más lento: el fondo); negativo sube más (el frente).
    q('[data-depth]', hero).forEach((layer) => {
      const depth = parseFloat(layer.dataset.depth);
      gsap.to(layer, {
        y: () => depth * hero.offsetHeight,
        ease: 'none',
        scrollTrigger: { trigger: hero, scroller, start: 'top top', end: 'bottom top', scrub: true, invalidateOnRefresh: true },
      });
    });

    // El texto del inicio se aparta mientras las tarjetas, más rápidas, le
    // pasan por encima: así nunca quedan a medias sobre la letra.
    gsap.to(hero.querySelector('[data-hero-text]'), {
      opacity: 0,
      y: -48,
      ease: 'none',
      scrollTrigger: { trigger: hero, scroller, start: 'top top', end: '40% top', scrub: true },
    });

    q('[data-feature]').forEach((section) => featureScene(section, scroller, q));
    finalScene(root.querySelector('[data-final]'), scroller);
    dotsScene(q('[data-welcome-section]'), q('[data-dot]'), scroller);
  }, root);

  if (launch) playLaunch({ device, cards, onReveal: () => intro.play() });
  else intro.play();

  // Las fuentes cambian el alto de los titulares: hay que volver a medir.
  document.fonts?.ready.then(refresh);

  return () => ctx.revert();
}

function featureScene(section, scroller, q) {
  const range = 140; // recorrido máximo del parallax en px

  q('[data-depth]', section).forEach((layer) => {
    const depth = parseFloat(layer.dataset.depth);
    gsap.fromTo(layer, { y: -depth * range }, {
      y: depth * range,
      ease: 'none',
      scrollTrigger: { trigger: section, scroller, start: 'top bottom', end: 'bottom top', scrub: true },
    });
  });

  // La ilustración empieza chica y crece siguiendo el dedo.
  const art = section.querySelector('[data-grow]');
  if (art) {
    gsap.fromTo(art, { scale: 0.55, opacity: 0.35 }, {
      scale: 1,
      opacity: 1,
      ease: 'none',
      scrollTrigger: { trigger: section, scroller, start: 'top bottom', end: 'top 20%', scrub: true },
    });
  }

  // Titular por líneas y texto, una sola vez al entrar.
  const lines = q('.line > span', section);
  const copy = q('[data-reveal]', section);
  const items = q('[data-stagger] > *', section);
  const segments = q('[data-draw]', section);
  const bars = q('[data-bar]', section);
  const bubbles = q('[data-bubble]', section);

  // Cada pieza: estado inicial y cómo llega. Se arma solo lo que existe.
  // Los segmentos usan la excepción de los gráficos (DESIGN.md, sección
  // 12): la dona se traza con stroke-dashoffset.
  const steps = [
    [lines, { yPercent: 110 }, { yPercent: 0, duration: 0.7, ease: EASE.out, stagger: 0.08 }, 0],
    [copy, { opacity: 0, y: RISE }, { opacity: 1, y: 0, duration: DUR.slow, ease: EASE.out }, 0.15],
    [items, { opacity: 0, x: 12 }, { opacity: 1, x: 0, duration: DUR.base, ease: EASE.out, stagger: 0.08 }, 0.1],
    [segments, { strokeDashoffset: (i, segment) => segment.dataset.draw }, { strokeDashoffset: 0, duration: DUR.slow, ease: EASE.out, stagger: 0.12 }, 0.1],
    [bars, { scaleY: 0 }, { scaleY: 1, duration: DUR.slow, ease: EASE.out, stagger: 0.05 }, 0.15],
    [bubbles, { opacity: 0, y: 8, scale: 0.92 }, { opacity: 1, y: 0, scale: 1, duration: DUR.slow, ease: EASE.back, stagger: 0.3 }, 0.1],
  ];
  const reveal = gsap.timeline({ paused: true });
  steps.filter(([targets]) => has(targets)).forEach(([targets, from, to, at]) => {
    gsap.set(targets, from);
    reveal.to(targets, to, at);
  });

  ScrollTrigger.create({ trigger: section, scroller, start: 'top 70%', once: true, onEnter: () => reveal.play() });

  // «Escribiendo»: corre solo mientras la sección está a la vista.
  const dots = q('[data-typing] > span', section);
  if (dots.length) {
    const typing = gsap.to(dots, { y: -4, duration: 0.3, ease: 'sine.inOut', yoyo: true, repeat: -1, stagger: 0.12, paused: true });
    ScrollTrigger.create({
      trigger: section,
      scroller,
      start: 'top bottom',
      end: 'bottom top',
      onToggle: (self) => (self.isActive ? typing.play() : typing.pause()),
    });
  }
}

/* La tarjeta violeta crece hasta tapar toda la pantalla; recién entonces
   aparece el texto, que no va dentro de ella para no agrandarse. */
function finalScene(section, scroller) {
  if (!section) return;
  const card = section.querySelector('[data-final-card]');
  const art = card.querySelector('svg');
  const content = section.querySelector('[data-final-content]');
  const stage = section.querySelector('[data-final-stage]');

  const cover = () => {
    const width = card.offsetWidth;
    const height = card.offsetHeight;
    // Con margen, para que las esquinas redondeadas también salgan.
    return Math.max(stage.offsetWidth / width, stage.offsetHeight / height) * 1.35;
  };

  gsap.set(content, { opacity: 0, y: RISE * 2 });
  gsap.timeline({
    scrollTrigger: { trigger: section, scroller, start: 'top top', end: 'bottom bottom', scrub: true, invalidateOnRefresh: true },
  })
    .fromTo(card, { scale: 1, rotation: -5 }, { scale: cover, rotation: 0, ease: 'power1.in', duration: 1 }, 0)
    // El dibujo crecería con la tarjeta hasta ser enorme: se va antes.
    .to(art, { opacity: 0, ease: 'none', duration: 0.3 }, 0.35)
    .to(content, { opacity: 1, y: 0, ease: 'none', duration: 0.25 }, 0.75);
}

/* Indicador de sección: el trazo activo se alarga. */
function dotsScene(sections, dots, scroller) {
  if (!dots.length) return;
  const show = (active) => {
    dots.forEach((dot, index) => {
      gsap.to(dot, {
        scaleX: index === active ? 1 : 0.4,
        opacity: index === active ? 1 : 0.4,
        duration: DUR.base,
        ease: EASE.out,
        overwrite: true,
      });
    });
  };
  sections.forEach((section, index) => {
    ScrollTrigger.create({
      trigger: section,
      scroller,
      start: 'top 55%',
      end: 'bottom 55%',
      onToggle: (self) => self.isActive && show(index),
    });
  });
}

/* --- Piezas de las pantallas de la app ----------------------------- */

/**
 * Ámbito de animación de una pantalla: lo que se crea con él se deshace
 * junto al salir (animaciones y disparadores de scroll). Sin GSAP, cada
 * efecto deja el contenido en su estado final.
 */
export function scope(root) {
  const ctx = ready ? gsap.context(() => {}, root) : null;
  const run = (fn) => (ctx ? ctx.add(fn) : undefined);
  return {
    /** Elementos que entran escalonados (tarjetas, filas nuevas). */
    stagger(elements) {
      const list = [...elements];
      if (!list.length || !ready) return;
      run(() => {
        if (!animates()) {
          gsap.fromTo(list, { opacity: 0 }, { opacity: 1, duration: DUR.fast, ease: 'none', clearProps: 'opacity' });
          return;
        }
        // El escalonado total no pasa del tope, por larga que sea la lista.
        const each = Math.min(0.05, 0.4 / list.length);
        gsap.fromTo(list, { opacity: 0, y: RISE }, {
          opacity: 1, y: 0, duration: DUR.base, ease: EASE.out, stagger: each, clearProps: 'transform,opacity',
        });
      });
    },

    /**
     * Cuenta de 0 al valor de la API. El texto final es exactamente el que
     * da `format(value)`: el conteo es solo visual y al terminar se
     * reemplaza por la cadena exacta.
     */
    countUp(element, value, format) {
      const finalText = format(value);
      if (!animates()) {
        element.textContent = finalText;
        return;
      }
      run(() => {
        const state = { n: 0 };
        const target = Number(value);
        element.textContent = format(0);
        gsap.to(state, {
          n: target,
          duration: token('--dur-count', 900) / 1000,
          ease: 'power2.out',
          onUpdate: () => { element.textContent = format(state.n); },
          onComplete: () => { element.textContent = finalText; },
        });
      });
    },

    /**
     * Tarjeta compacta fija: aparece cuando la grande sale por arriba.
     * Tiene desenfoque, así que solo se anima su opacidad (DESIGN.md).
     */
    sticky(scroller, big, compact) {
      if (!ready) return () => {};
      let trigger;
      run(() => {
        gsap.set(compact, { autoAlpha: 0 });
        trigger = ScrollTrigger.create({
          trigger: big,
          scroller,
          start: 'center top+=60',
          onEnter: () => gsap.to(compact, { autoAlpha: 1, duration: DUR.fast, ease: 'none', overwrite: true }),
          onLeaveBack: () => gsap.to(compact, { autoAlpha: 0, duration: DUR.fast, ease: 'none', overwrite: true }),
        });
      });
      // Al recargar los datos la tarjeta grande se reemplaza: quien llama
      // apaga este disparador antes de crear el nuevo.
      return () => trigger?.kill();
    },

    /**
     * Gráficos al entrar en pantalla: la dona se traza con
     * stroke-dashoffset y las barras crecen desde la base (excepción de
     * los gráficos, DESIGN.md sección 12).
     */
    charts(scroller, trigger, { segments = [], bars = [] }) {
      if (!animates()) return;
      run(() => {
        segments.forEach((segment) => gsap.set(segment, { strokeDashoffset: segment.dataset.length }));
        if (bars.length) gsap.set(bars, { scaleY: 0, transformOrigin: '50% 100%' });
        const play = () => {
          if (segments.length) gsap.to(segments, { strokeDashoffset: 0, duration: DUR.slow, ease: EASE.out, stagger: 0.1 });
          if (bars.length) gsap.to(bars, { scaleY: 1, duration: DUR.slow, ease: EASE.out, stagger: 0.03 });
        };
        ScrollTrigger.create({ trigger, scroller, start: 'top 90%', once: true, onEnter: play });
      });
    },

    /** Burbuja de chat: entra con un rebote corto. */
    bubble(element) {
      if (!ready) return;
      run(() => {
        if (!animates()) {
          gsap.fromTo(element, { opacity: 0 }, { opacity: 1, duration: DUR.fast, ease: 'none' });
          return;
        }
        gsap.fromTo(element, { opacity: 0, y: 8, scale: 0.92 }, {
          opacity: 1, y: 0, scale: 1, duration: DUR.slow, ease: EASE.back, transformOrigin: element.dataset.origin ?? '50% 100%',
        });
      });
    },

    /** «Escribiendo…»: tres puntos que suben y bajan. Devuelve cómo pararlo. */
    typing(dots) {
      if (!animates()) return () => {};
      let tween;
      run(() => {
        tween = gsap.to(dots, { y: -4, duration: 0.3, ease: 'sine.inOut', yoyo: true, repeat: -1, stagger: 0.12 });
      });
      return () => tween?.kill();
    },

    revert() {
      ctx?.revert();
    },
  };
}

/* --- Piezas globales: hoja inferior y avisos ----------------------- */

export function openSheet(backdrop, sheet) {
  if (!ready) return Promise.resolve();
  if (!animates()) {
    return gsap.fromTo([backdrop, sheet], { opacity: 0 }, { opacity: 1, duration: DUR.fast, ease: 'none' }).then();
  }
  gsap.fromTo(backdrop, { opacity: 0 }, { opacity: 1, duration: DUR.base, ease: 'none' });
  return gsap.fromTo(sheet, { yPercent: 100 }, { yPercent: 0, duration: DUR.base + 0.06, ease: EASE.out }).then();
}

export function closeSheet(backdrop, sheet) {
  if (!ready) return Promise.resolve();
  if (!animates()) {
    return gsap.to([backdrop, sheet], { opacity: 0, duration: DUR.fast, ease: 'none' }).then();
  }
  gsap.to(backdrop, { opacity: 0, duration: DUR.fast, ease: 'none' });
  return gsap.to(sheet, { yPercent: 100, duration: DUR.fast + 0.04, ease: 'power2.in' }).then();
}

export function toast(element, visibleFor = 2600) {
  if (!ready) {
    setTimeout(() => element.remove(), visibleFor);
    return;
  }
  const reduced = !animates();
  gsap.timeline({ onComplete: () => element.remove() })
    .fromTo(element, { opacity: 0, y: reduced ? 0 : 12 }, { opacity: 1, y: 0, duration: DUR.base, ease: EASE.out })
    .to(element, { opacity: 0, y: reduced ? 0 : 8, duration: DUR.fast, ease: 'power1.in' }, `+=${visibleFor / 1000}`);
}

export function showNav(nav, visible) {
  if (!ready) {
    nav.hidden = !visible;
    return;
  }
  if (visible !== nav.hidden) return; // ya está como se pide
  if (visible) {
    nav.hidden = false;
    gsap.fromTo(nav, { opacity: 0, y: animates() ? 16 : 0 }, { opacity: 1, y: 0, duration: DUR.base, ease: EASE.out, clearProps: 'transform' });
  } else {
    gsap.to(nav, { opacity: 0, duration: DUR.fast, ease: 'none', onComplete: () => { nav.hidden = true; } });
  }
}
