/* =====================================================================
   Chat con el asistente de IA.

   La clave de la IA nunca está aquí: se llama a POST /ai/chat y el
   backend habla con el modelo, con el contexto financiero del usuario.

   Las respuestas del modelo se insertan como TEXTO (CLAUDE.md): son la
   salida de un modelo y podrían traer algo que pareciera HTML. Solo se
   reconoce un formato mínimo (párrafos, listas y **negrita**),
   construyendo nodos, nunca con innerHTML.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon } from '../dom.js';
import * as motion from '../motion.js';

const APP_ICON = 'assets/icons/app-icon-b-tarjetas.svg';
const MAX_TURNS = 20; // el backend acepta hasta 20 turnos previos
// Si quien lee está a menos de esto del final, la conversación lo sigue;
// si subió más, se lo deja leer tranquilo.
const FOLLOW_MARGIN_PX = 80;

const SUGGESTIONS = ['¿En qué gasté más este mes?', '¿Cuánto ahorré este mes?', 'Dame un resumen del mes'];

/* Avisos propios para cuando el asistente no puede responder: más claros
   que un 503. El resto de los errores muestra el mensaje de la API. */
const UNAVAILABLE = {
  AI_DISABLED: {
    title: 'El asistente está apagado',
    text: 'La IA está desactivada en este servidor, así que no puedo responder preguntas. El resto de Flux IA funciona con normalidad.',
  },
  AI_NOT_CONFIGURED: {
    title: 'Falta configurar el asistente',
    text: 'El servidor todavía no tiene una clave de IA. Cuando la tenga, podrás preguntarme lo que quieras sobre tus finanzas.',
  },
  AI_RATE_LIMITED: {
    title: 'Demasiadas preguntas seguidas',
    text: 'Espera unos segundos y vuelve a intentarlo.',
  },
  // Pasa, por ejemplo, si la clave configurada no es válida.
  AI_UNAVAILABLE: {
    title: 'El asistente no está disponible',
    text: 'No pude conectarme con el servicio de IA. Inténtalo de nuevo más tarde.',
  },
};

const BULLET = /^[-*•]\s+(.+)$/;
const NUMBERED = /^(\d{1,3})[.)]\s+(.+)$/;
const HEADING = /^#{1,6}\s+(.+)$/;
const RULE = /^([-*_])\1{2,}$/;

/** **negrita** → <strong>, el resto como texto. */
function inline(text) {
  return text.split(/(\*\*[^*]+\*\*)/g).filter(Boolean).map((part) => (part.length > 4 && part.startsWith('**') && part.endsWith('**')
    ? h('strong', {}, part.slice(2, -2))
    : part));
}

/**
 * La respuesta en bloques: cada línea es un párrafo; las que empiezan con
 * «-», «*» o «1.» forman una lista, y un título de markdown queda como un
 * párrafo en negrita. Los modelos escriben así, y los asteriscos o
 * numerales sueltos se verían como basura en la burbuja.
 */
function formatAnswer(text) {
  const blocks = [];
  let list = null;
  for (const raw of text.split('\n')) {
    const line = raw.trim();
    // Una línea vacía no corta la lista: los modelos separan así los ítems.
    if (!line || RULE.test(line)) continue;
    const bullet = BULLET.exec(line);
    const numbered = NUMBERED.exec(line);
    if (bullet || numbered) {
      const tag = bullet ? 'ul' : 'ol';
      if (list?.localName !== tag) {
        list = h(tag, { start: numbered?.[1] });
        blocks.push(list);
      }
      list.append(h('li', {}, inline(bullet ? bullet[1] : numbered[2])));
    } else {
      list = null;
      const heading = HEADING.exec(line);
      blocks.push(h('p', {}, heading ? h('strong', {}, heading[1].replaceAll('**', '')) : inline(line)));
    }
  }
  return h('div', { class: 'answer' }, blocks);
}

export function mount(view) {
  view.classList.add('app-bg');
  const fx = motion.scope(view);
  const history = [];
  const touch = window.matchMedia('(pointer: coarse)');
  let alive = true;
  let waiting = false;
  let finishWriting = () => {};

  const log = h('div', { class: 'chat-log', role: 'log', 'aria-live': 'polite', 'aria-label': 'Conversación' });
  const scroller = h('main', { class: 'screen screen--chat' },
    h('header', { class: 'screen-header' },
      h('div', {}, h('p', { class: 'eyebrow' }, 'Asistente de IA'), h('h1', { class: 'h1' }, 'Chat'))),
    log);

  const input = h('input', { class: 'input', name: 'question', type: 'text', maxlength: '2000', autocomplete: 'off', enterkeyhint: 'send', placeholder: 'Pregunta sobre tus finanzas', 'aria-label': 'Tu pregunta' });
  const send = h('button', { class: 'btn btn--icon btn--accent', type: 'submit', 'aria-label': 'Enviar' }, icon('send'));
  // El botón no se lleva el foco: si el campo lo perdiera al tocarlo, la
  // barra inferior volvería y el botón se movería antes de que llegue el
  // clic.
  send.addEventListener('mousedown', (event) => event.preventDefault());
  const composer = h('form', { class: 'chat-composer glass glass--strong' }, input, send);
  composer.addEventListener('submit', (event) => {
    event.preventDefault();
    ask(input.value);
  });
  view.append(scroller, composer);

  const nearBottom = () => scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight < FOLLOW_MARGIN_PX;
  const toBottom = (smooth = true) => scroller.scrollTo({ top: scroller.scrollHeight, behavior: smooth && !motion.prefersReducedMotion() ? 'smooth' : 'auto' });

  // Al abrirse o cerrarse el teclado cambia el alto visible: si se estaba
  // viendo el final de la conversación, se sigue viendo.
  let atBottom = true;
  let height = scroller.clientHeight;
  scroller.addEventListener('scroll', () => { atBottom = nearBottom(); }, { passive: true });
  const resizes = new ResizeObserver(() => {
    if (scroller.clientHeight !== height && atBottom) toBottom(false);
    height = scroller.clientHeight;
  });
  resizes.observe(scroller);

  function aiRow(...content) {
    const bubble = h('div', { class: 'bubble bubble--ai', dataset: { origin: '0% 100%' } }, content);
    const row = h('div', { class: 'bubble-row' }, h('img', { src: APP_ICON, alt: '' }), bubble);
    log.append(row);
    fx.bubble(bubble);
    toBottom();
    return row;
  }

  function userRow(text) {
    const bubble = h('p', { class: 'bubble bubble--user', dataset: { origin: '100% 100%' } }, text);
    log.append(h('div', { class: 'bubble-row bubble-row--user' }, bubble));
    fx.bubble(bubble);
    toBottom();
  }

  /* La respuesta se escribe palabra por palabra y la pantalla sigue el
     final. Mientras tanto la conversación queda «ocupada» (aria-busy): un
     lector de pantalla la lee entera al terminar, no a pedazos. */
  function answerRow(text) {
    const answer = formatAnswer(text);
    aiRow(answer);
    log.setAttribute('aria-busy', 'true');
    finishWriting = fx.write(answer, {
      onStep: () => {
        if (nearBottom()) toBottom(false);
      },
      onDone: () => {
        log.removeAttribute('aria-busy');
        finishWriting = () => {};
      },
    });
  }

  /* Bienvenida del asistente y sugerencias para empezar. */
  aiRow(h('span', {}, 'Hola, soy tu asistente. Respondo con tus movimientos reales: pregúntame en qué gastas, cuánto ahorraste o cómo vas este mes.'));
  const chips = h('div', { class: 'suggestions' }, SUGGESTIONS.map((text) => {
    const chip = h('button', { class: 'chip', type: 'button' }, text);
    chip.addEventListener('click', () => ask(text));
    return chip;
  }));
  log.append(chips);

  async function ask(raw) {
    const question = raw.trim();
    if (!question || waiting) return;
    // En el celular, enviar cierra el teclado: la respuesta se lee con la
    // pantalla entera, como en los chats de IA.
    if (touch.matches) input.blur();
    finishWriting();
    waiting = true;
    chips.remove();
    input.value = '';
    send.disabled = true;
    userRow(question);

    const dots = h('span', { class: 'typing', 'aria-label': 'El asistente está escribiendo' }, h('span'), h('span'), h('span'));
    const typingRow = aiRow(dots);
    const stopTyping = fx.typing([...dots.children]);

    try {
      const response = await api.chat(question, history.slice(-MAX_TURNS));
      if (!alive) return;
      stopTyping();
      typingRow.remove();
      answerRow(response.answer);
      history.push({ role: 'user', content: question }, { role: 'assistant', content: response.answer });
    } catch (error) {
      if (!alive) return;
      stopTyping();
      typingRow.remove();
      if (error.code === 'SESSION_EXPIRED') return;
      const known = UNAVAILABLE[error.code];
      const retry = h('button', { class: 'btn btn--glass', type: 'button', style: { marginTop: 'var(--space-3)' } }, icon('refresh'), 'Reintentar');
      retry.addEventListener('click', () => {
        retry.closest('.bubble-row').remove();
        ask(question);
      });
      aiRow(
        h('strong', { style: { display: 'block', color: 'var(--text-strong)' } }, known?.title ?? 'No pude responder'),
        h('span', { style: { display: 'block', marginTop: 'var(--space-1)', color: 'var(--text-muted)' } }, known?.text ?? error.message),
        error.code === 'AI_DISABLED' || error.code === 'AI_NOT_CONFIGURED' ? null : retry);
    } finally {
      waiting = false;
      send.disabled = false;
    }
  }

  return {
    unmount() {
      alive = false;
      resizes.disconnect();
      fx.revert();
    },
  };
}
