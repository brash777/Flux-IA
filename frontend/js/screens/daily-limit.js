/* =====================================================================
   Tope diario y modo salida: la tarjeta «Tope de hoy» de Inicio y la
   hoja para elegir el tope.

   Las cifras salen de /spending/today: lo gastado, lo que queda y la
   barra ya vienen calculados (CLAUDE.md, el frontend no calcula). La IA
   propone el tope en /ai/daily-limit-proposal; aquí solo se muestra y el
   usuario decide si lo usa.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon } from '../dom.js';
import { amountForInput, money, parseAmount, untilText } from '../format.js';
import * as motion from '../motion.js';
import { clearFormErrors, confirm, field, formAlert, setBusy, sheet, showFormErrors, toast } from '../ui.js';
import { openCardSheet } from './flux-card.js';

/** «Transporte y Salud». */
export function categoryNames(categories = []) {
  const names = categories.map((category) => category.name);
  return names.length > 1 ? `${names.slice(0, -1).join(', ')} y ${names.at(-1)}` : names.join('');
}

/* setBusy devuelve solo el texto: el ícono se vuelve a poner aquí. */
function idle(button, iconName, label) {
  setBusy(button, false);
  button.replaceChildren(icon(iconName), label);
}

/**
 * Tarjeta de Inicio. `onChange` recarga Inicio después de guardar el
 * tope, activar el modo salida o pagar con la tarjeta.
 */
export function todayCard(today, { currency, onChange }) {
  const fmt = (value) => money(value, currency);
  const hasLimit = today.dailyLimit !== null && today.dailyLimit !== undefined;
  const over = hasLimit && Number(today.overBy) > 0;

  const adjust = h('button', { class: 'btn btn--icon', type: 'button', 'aria-label': hasLimit ? 'Cambiar el tope diario' : 'Elegir un tope diario' }, icon('sliders'));
  adjust.addEventListener('click', () => openLimitSheet({ today, currency, onChange }));

  let body;
  if (hasLimit) {
    const shown = fmt(over ? today.overBy : today.remaining);
    const caption = over ? `te pasaste del tope de ${fmt(today.dailyLimit)}` : `te quedan de ${fmt(today.dailyLimit)}`;
    body = [
      h('p', { class: `limit-card__amount money${over ? ' is-over' : ''}` }, shown),
      h('p', { class: 'limit-card__caption' }, caption),
      h('div', {
        class: `meter${over ? ' is-over' : ''}`,
        role: 'progressbar',
        'aria-label': 'Parte del tope ya gastada',
        'aria-valuemin': '0',
        'aria-valuemax': '100',
        'aria-valuenow': today.progressPercent,
      }, h('span', { class: 'meter__fill', style: { width: `${today.progressPercent}%` } })),
      h('p', { class: 'limit-card__note' },
        `Gastado hoy: ${fmt(today.spentToday)}`,
        today.excludedCategories.length ? ` · No cuentan: ${categoryNames(today.excludedCategories)}` : ''),
    ];
  } else {
    const propose = h('button', { class: 'btn btn--glass btn--block', type: 'button' }, icon('sparkles'), 'Proponer un tope con IA');
    propose.addEventListener('click', () => openLimitSheet({ today, currency, onChange, propose: true }));
    body = [h('p', { class: 'limit-card__text' }, 'Ponte un límite de gasto por día. La IA te propone uno con tus movimientos, y tú decides.'), propose];
  }

  const goingOut = today.goingOut?.active
    ? h('p', { class: 'going-out', role: 'status' }, icon('moon', 'icon--sm'),
      h('span', {}, `Modo salida hasta ${untilText(today.goingOut.until)}. La tarjeta solo aprueba ${categoryNames(today.alwaysAllowedCategories)}.`))
    : null;

  const actions = h('div', { class: 'limit-card__actions' });
  if (!today.goingOut?.active) {
    const out = h('button', { class: 'btn btn--glass', type: 'button' }, icon('moon'), 'Hoy salgo');
    out.addEventListener('click', () => startGoingOut(today, onChange));
    actions.append(out);
  }
  const pay = h('button', { class: 'btn btn--glass', type: 'button' }, icon('card'), 'Pagar');
  pay.addEventListener('click', () => openCardSheet({ currency, onChange }));
  actions.append(pay);

  return h('article', { class: 'glass glass--strong card limit-card', 'aria-label': 'Tope de hoy' },
    h('div', { class: 'limit-card__head' }, h('h2', { class: 'limit-card__label' }, 'Tope de hoy'), adjust),
    body, goingOut, actions);
}

/* El modo salida no se puede apagar antes de tiempo: por eso se pregunta. */
async function startGoingOut(today, onChange) {
  const sure = await confirm({
    title: '¿Activar el modo salida?',
    text: `Hasta la mañana, la tarjeta rechaza todo menos ${categoryNames(today.alwaysAllowedCategories)}. No se puede apagar antes: así no lo quitas en plena salida.`,
    confirmLabel: 'Activar',
    danger: false,
  });
  if (!sure) return;
  try {
    await api.startGoingOut();
    toast('Modo salida activado');
    onChange?.();
  } catch (error) {
    toast(error.message, 'error');
  }
}

/** Hoja del tope. Con `propose`, le pide a la IA una propuesta al abrir. */
export async function openLimitSheet({ today, currency, onChange, propose = false }) {
  let categories;
  try {
    categories = (await api.categories()).filter((category) => category.kind === 'EXPENSE');
  } catch (error) {
    toast(error.message, 'error');
    return;
  }
  const fmt = (value) => money(value, currency);
  const excluded = new Set(today.excludedCategories.map((category) => category.id));
  const dialog = sheet({ title: 'Tope diario' });

  const limitField = field({
    label: 'Tope por día',
    name: 'dailyLimit',
    value: today.dailyLimit !== null ? amountForInput(today.dailyLimit) : '',
    attrs: { inputmode: 'decimal', autocomplete: 'off', placeholder: '0' },
  });
  const limitInput = limitField.querySelector('input');

  const chips = categories.map((category) => {
    const chip = h('button', { class: 'chip', type: 'button', 'aria-pressed': String(excluded.has(category.id)) },
      `${category.icon ?? ''} ${category.name}`.trim());
    chip.addEventListener('click', () => {
      if (excluded.has(category.id)) excluded.delete(category.id);
      else excluded.add(category.id);
      chip.setAttribute('aria-pressed', String(excluded.has(category.id)));
    });
    return chip;
  });
  const fixed = h('fieldset', { class: 'fieldset' },
    h('legend', { class: 'field__label' }, 'Gastos fijos: no cuentan para el tope'),
    h('div', { class: 'chip-wrap' }, chips),
    h('span', { class: 'field__hint' }, 'Marca lo que pagas sí o sí, como la matrícula, los servicios o el mercado.'));

  const proposal = h('div', { class: 'proposal', role: 'status', hidden: true });
  const proposeButton = h('button', { class: 'btn btn--glass btn--block', type: 'button' }, icon('sparkles'), 'Proponer con IA');
  proposeButton.addEventListener('click', () => askProposal());

  const save = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Guardar');
  const actions = h('div', { class: 'sheet__actions' }, save);
  const form = h('form', { class: 'form', novalidate: true }, formAlert(), proposeButton, proposal, limitField, fixed, actions);

  async function askProposal() {
    clearFormErrors(form);
    setBusy(proposeButton, true, 'Pensando…');
    try {
      const result = await api.limitProposal([...excluded]);
      const use = h('button', { class: 'btn btn--glass', type: 'button' }, 'Usar este tope');
      use.addEventListener('click', () => {
        limitInput.value = amountForInput(result.dailyLimit);
        limitInput.focus();
      });
      proposal.replaceChildren(
        h('p', { class: 'proposal__title' }, icon('sparkles', 'icon--sm'), 'La IA te propone ',
          h('strong', { class: 'money' }, fmt(result.dailyLimit)), ' al día'),
        result.reason ? h('p', { class: 'proposal__reason' }, result.reason) : null,
        use);
      proposal.hidden = false;
      motion.reveal(proposal);
    } catch (error) {
      showFormErrors(form, error);
    } finally {
      idle(proposeButton, 'sparkles', 'Proponer con IA');
    }
  }

  async function store(dailyLimit, button, busyLabel, message) {
    setBusy(button, true, busyLabel);
    try {
      await api.saveSpendingSettings(dailyLimit, [...excluded]);
      await dialog.close();
      toast(message);
      onChange?.();
    } catch (error) {
      setBusy(button, false);
      showFormErrors(form, error);
    }
  }

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    clearFormErrors(form);
    const amount = parseAmount(limitInput.value);
    if (amount === null || Number(amount) <= 0) {
      showFormErrors(form, { fields: { dailyLimit: 'Escribe un tope mayor que cero, por ejemplo 45.000.' } });
      return;
    }
    store(amount, save, 'Guardando…', 'Tope guardado');
  });

  if (today.dailyLimit !== null) {
    const remove = h('button', { class: 'btn btn--glass btn--block', type: 'button' }, 'Quitar el tope');
    remove.addEventListener('click', () => store(null, remove, 'Quitando…', 'Quitaste el tope'));
    actions.append(remove);
  }

  dialog.body.append(form);
  if (propose) askProposal();
}
