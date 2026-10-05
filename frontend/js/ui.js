/* =====================================================================
   Piezas compartidas por las pantallas: estados, filas de movimiento,
   formularios, hoja inferior, confirmación y avisos breves.

   Todo texto que viene de la API entra por h() o textContent.
   ===================================================================== */

import { h, icon } from './dom.js';
import * as motion from './motion.js';
import { relativeDay, signedMoney, time } from './format.js';

/* --- Estados: cargando, vacío y error ------------------------------ */

/** Tarjeta de estado: vacío, error o aviso, con una acción opcional. */
export function stateCard({ kind = 'empty', iconName = 'inbox', title, text, action }) {
  return h('div', { class: `glass glass--strong state${kind === 'error' ? ' state--error' : ''}`, role: kind === 'error' ? 'alert' : null },
    h('span', { class: 'state__icon' }, icon(iconName)),
    h('p', { class: 'state__title' }, title),
    text ? h('p', { class: 'state__text' }, text) : null,
    action ? h('button', { class: `btn ${action.primary ? 'btn--primary' : 'btn--glass'}`, type: 'button', onclick: action.onClick },
      action.icon ? icon(action.icon) : null, action.label) : null,
  );
}

/** Error al cargar: el mensaje de la API, o uno propio si fue la red. */
export function errorCard(error, onRetry) {
  const network = error?.code === 'NETWORK';
  return stateCard({
    kind: 'error',
    iconName: network ? 'cloud-off' : 'alert',
    title: network ? 'Sin conexión con el servidor' : 'No pudimos cargar esto',
    text: error?.message ?? 'Algo salió mal. Inténtalo de nuevo en un momento.',
    action: onRetry ? { label: 'Reintentar', icon: 'refresh', onClick: onRetry } : null,
  });
}

/** Lista de esqueleto con la forma de las filas que vienen. */
export function skeletonList(rows = 4, label = 'Cargando…') {
  const row = () => h('div', { class: 'skeleton-row', 'aria-hidden': 'true' },
    h('span', { class: 'skeleton skeleton--chip' }),
    h('span', {},
      h('span', { class: 'skeleton skeleton--line', style: { display: 'block', width: '70%' } }),
      h('span', { class: 'skeleton skeleton--line', style: { display: 'block', width: '42%' } })),
    h('span', { class: 'skeleton skeleton--line' }));
  return h('div', { class: 'glass glass--strong', 'aria-busy': 'true' },
    h('span', { class: 'sr-only' }, label),
    Array.from({ length: rows }, row));
}

export function skeletonBlock(height, label = 'Cargando…') {
  return h('div', { class: 'skeleton', style: { height: `${height}px`, borderRadius: 'var(--r-lg)' }, 'aria-busy': 'true' },
    h('span', { class: 'sr-only' }, label));
}

/* --- Movimientos ---------------------------------------------------- */

/** Chip con el emoji y el color de la categoría, tal como vienen de la API. */
export function categoryChip(category) {
  const chip = h('span', { class: 'cat-chip', 'aria-hidden': 'true' }, category?.icon ?? '•');
  if (category?.color) chip.style.setProperty('--cat', category.color);
  return chip;
}

/** Fila de un movimiento. `onOpen` recibe el movimiento al tocarla. */
export function transactionRow(tx, onOpen, { showDay = true } = {}) {
  const currency = tx.account?.currency;
  const amount = signedMoney(tx.amount, tx.type, currency);
  const when = showDay ? `${relativeDay(tx.occurredAt)}, ${time(tx.occurredAt)}` : time(tx.occurredAt);
  const button = h('button', { class: 'tx-row', type: 'button', onclick: () => onOpen?.(tx) },
    categoryChip(tx.category),
    h('span', { class: 'tx-row__main' },
      h('span', { class: 'tx-row__title' }, tx.description),
      h('span', { class: 'tx-row__meta' }, `${tx.category?.name ?? ''} · ${when}`)),
    h('span', { class: `tx-row__amount money${tx.type === 'INCOME' ? ' is-income' : ''}` }, amount));
  button.setAttribute('aria-label', `${tx.description}, ${tx.category?.name ?? ''}, ${amount}, ${when}`);
  return h('li', {}, button);
}

/* --- Formularios ---------------------------------------------------- */

/** Botón ocupado: no se puede enviar dos veces y dice qué pasa. */
export function setBusy(button, busy, busyLabel) {
  if (busy) {
    button.dataset.label = button.dataset.label ?? button.textContent.trim();
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.replaceChildren(h('span', { class: 'spinner', 'aria-hidden': 'true' }), busyLabel);
  } else {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    if (button.dataset.label) button.replaceChildren(button.dataset.label);
  }
}

/** Errores por campo (`fields` de la API) y mensaje general arriba. */
export function showFormErrors(form, error) {
  clearFormErrors(form);
  const alert = form.querySelector('[data-form-alert]');
  if (alert && error?.message) {
    alert.replaceChildren(icon('alert'), h('span', {}, error.message));
    alert.hidden = false;
  }
  for (const [name, message] of Object.entries(error?.fields ?? {})) {
    const input = form.querySelector(`[name="${CSS.escape(name)}"]`);
    if (!input) continue;
    const id = `${input.id}-error`;
    input.setAttribute('aria-invalid', 'true');
    input.setAttribute('aria-describedby', id);
    input.closest('.field')?.append(h('span', { class: 'field__error', id }, icon('alert', 'icon--sm'), message));
  }
  form.querySelector('[aria-invalid="true"]')?.focus();
}

export function clearFormErrors(form) {
  form.querySelectorAll('.field__error').forEach((node) => node.remove());
  form.querySelectorAll('[aria-invalid]').forEach((node) => {
    node.removeAttribute('aria-invalid');
    node.removeAttribute('aria-describedby');
  });
  const alert = form.querySelector('[data-form-alert]');
  if (alert) alert.hidden = true;
}

let fieldCount = 0;

/** Campo con etiqueta siempre visible. */
export function field({ label, name, type = 'text', value = '', hint, attrs = {} }) {
  const id = `f-${name}-${++fieldCount}`;
  const input = h('input', { class: 'input', id, name, type, ...attrs });
  input.value = value ?? '';
  return h('div', { class: 'field' },
    h('label', { class: 'field__label', for: id }, label),
    input,
    hint ? h('span', { class: 'field__hint' }, hint) : null);
}

/** Campo de contraseña con el botón para mostrarla. */
export function passwordField({ label, name, autocomplete }) {
  const id = `f-${name}-${++fieldCount}`;
  const input = h('input', { class: 'input input--with-action', id, name, type: 'password', autocomplete, required: true });
  const toggle = h('button', { class: 'field__action', type: 'button', 'aria-label': 'Mostrar contraseña', 'aria-pressed': 'false' }, icon('eye'));
  toggle.addEventListener('click', () => {
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(show));
    toggle.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggle.replaceChildren(icon(show ? 'eye-off' : 'eye'));
  });
  return h('div', { class: 'field' },
    h('label', { class: 'field__label', for: id }, label),
    h('div', { class: 'field__control' }, input, toggle));
}

export const formAlert = () => h('div', { class: 'alert', role: 'alert', 'data-form-alert': true, hidden: true });

/* --- Hoja inferior, confirmación y avisos --------------------------- */

function device() {
  return document.getElementById('device');
}

/**
 * Hoja que sube desde abajo (crear o editar un movimiento, filtros…).
 * Devuelve { body, close }. Escape y el fondo la cierran; el foco vuelve
 * a donde estaba.
 */
export function sheet({ title, onClose }) {
  const previousFocus = document.activeElement;
  const titleId = `sheet-title-${++fieldCount}`;
  const backdrop = h('div', { class: 'sheet-backdrop' });
  const body = h('div', { class: 'sheet__body' });
  const closeButton = h('button', { class: 'btn btn--icon', type: 'button', 'aria-label': 'Cerrar' }, icon('close'));
  const panel = h('section', { class: 'sheet', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId },
    h('div', { class: 'sheet__head' }, h('h2', { class: 'h2', id: titleId }, title), closeButton),
    body);
  device().append(backdrop, panel);

  let closed = false;
  const close = async () => {
    if (closed) return;
    closed = true;
    document.removeEventListener('keydown', onKey);
    await motion.closeSheet(backdrop, panel);
    backdrop.remove();
    panel.remove();
    previousFocus?.focus?.({ preventScroll: true });
    onClose?.();
  };
  const onKey = (event) => { if (event.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  backdrop.addEventListener('click', close);
  closeButton.addEventListener('click', close);

  motion.openSheet(backdrop, panel);
  requestAnimationFrame(() => panel.querySelector('input, select, button:not(.btn--icon)')?.focus({ preventScroll: true }));
  return { body, close, panel };
}

/** Pregunta antes de algo que no se puede deshacer. Resuelve true o false. */
export function confirm({ title, text, confirmLabel, danger = true }) {
  return new Promise((resolve) => {
    let answer = false;
    const dialog = sheet({ title, onClose: () => resolve(answer) });
    const yes = h('button', { class: `btn btn--block ${danger ? 'btn--danger' : 'btn--primary'}`, type: 'button' },
      danger ? icon('trash') : null, confirmLabel);
    const no = h('button', { class: 'btn btn--glass btn--block', type: 'button' }, 'Cancelar');
    yes.addEventListener('click', () => { answer = true; dialog.close(); });
    no.addEventListener('click', () => dialog.close());
    dialog.body.append(h('p', { class: 'text-muted', style: { margin: '0' } }, text), h('div', { class: 'sheet__actions' }, yes, no));
  });
}

/** Aviso breve abajo de la pantalla: «Movimiento guardado». */
export function toast(message, kind = 'ok') {
  const node = h('div', { class: `toast${kind === 'error' ? ' toast--error' : ''}`, role: 'status' },
    icon(kind === 'error' ? 'alert' : 'check'), h('span', {}, message));
  device().append(node);
  motion.toast(node);
}

/** Encabezado de pantalla: título y acciones a la derecha. */
export function screenHeader(title, ...actions) {
  return h('header', { class: 'screen-header' },
    h('h1', { class: 'h1' }, title),
    actions.length ? h('div', { class: 'screen-header__actions' }, actions) : null);
}
