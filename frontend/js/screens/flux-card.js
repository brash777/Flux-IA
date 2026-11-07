/* =====================================================================
   Tarjeta Flux: una tarjeta simulada para mostrar el tope y el modo
   salida en acción.

   No mueve plata de verdad. El servidor decide como lo haría un banco que
   le pregunta por cada compra: aprueba (y la compra queda como un gasto)
   o rechaza (y no se guarda nada). Aquí solo se muestra la respuesta.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon } from '../dom.js';
import { money, parseAmount, untilText } from '../format.js';
import * as motion from '../motion.js';
import { clearFormErrors, field, formAlert, setBusy, sheet, showFormErrors, toast } from '../ui.js';

let ids = 0;

function categorySelect(categories) {
  const id = `card-category-${++ids}`;
  const select = h('select', { class: 'input select', id, name: 'categoryId' },
    categories.map((category) => h('option', { value: category.id }, `${category.icon ?? ''} ${category.name}`.trim())));
  return h('div', { class: 'field' },
    h('label', { class: 'field__label', for: id }, 'Categoría'),
    h('div', { class: 'field__control' }, select, icon('arrow-down', 'select-arrow icon--sm')));
}

/* Lo que queda del día después de la compra, si hay tope o modo salida. */
function todayLine(today, fmt) {
  if (today.goingOut?.active) return `Modo salida hasta ${untilText(today.goingOut.until)}.`;
  if (today.dailyLimit === null) return null;
  return `Te quedan ${fmt(today.remaining)} de ${fmt(today.dailyLimit)} hoy.`;
}

export async function openCardSheet({ currency, onChange }) {
  let categories;
  try {
    categories = (await api.categories()).filter((category) => category.kind === 'EXPENSE');
  } catch (error) {
    toast(error.message, 'error');
    return;
  }
  const fmt = (value) => money(value, currency);
  const dialog = sheet({ title: 'Tarjeta Flux' });

  const card = h('div', { class: 'flux-card', 'aria-hidden': 'true' },
    h('span', { class: 'flux-card__brand' }, 'Flux IA'),
    h('span', { class: 'flux-card__chip' }),
    h('span', { class: 'flux-card__tag' }, 'Simulada'));

  const amountField = field({ label: 'Monto', name: 'amount', attrs: { inputmode: 'decimal', autocomplete: 'off', placeholder: '0', required: true } });
  const placeField = field({ label: 'Dónde compras', name: 'description', attrs: { maxlength: '140', autocomplete: 'off', placeholder: 'Ej.: Bar La 85', required: true } });
  const result = h('div', { class: 'purchase-result', role: 'status', hidden: true });
  const pay = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Pagar');

  const form = h('form', { class: 'form', novalidate: true },
    card,
    h('p', { class: 'flux-card__note' }, 'Es de demostración: aprueba o rechaza con tu tope y el modo salida, como lo haría un banco.'),
    formAlert(), amountField, placeField, categorySelect(categories), result,
    h('div', { class: 'sheet__actions' }, pay));

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearFormErrors(form);
    const values = Object.fromEntries(new FormData(form));
    const amount = parseAmount(values.amount);
    if (amount === null || Number(amount) <= 0) {
      showFormErrors(form, { fields: { amount: 'Escribe un monto mayor que cero, por ejemplo 80.000.' } });
      return;
    }
    setBusy(pay, true, 'Pagando…');
    try {
      const response = await api.cardPurchase({ description: values.description.trim(), amount, categoryId: values.categoryId });
      const line = todayLine(response.today, fmt);
      result.className = `purchase-result ${response.approved ? 'is-approved' : 'is-declined'}`;
      result.replaceChildren(
        h('p', { class: 'purchase-result__title' }, icon(response.approved ? 'check' : 'close'), response.approved ? 'Aprobada' : 'Rechazada'),
        h('p', { class: 'purchase-result__text' }, response.message),
        line ? h('p', { class: 'purchase-result__text' }, line) : null);
      result.hidden = false;
      motion.reveal(result);
      if (response.approved) {
        form.querySelector('[name="amount"]').value = '';
        form.querySelector('[name="description"]').value = '';
        onChange?.();
      }
    } catch (error) {
      showFormErrors(form, error);
    } finally {
      setBusy(pay, false);
    }
  });

  dialog.body.append(form);
}
