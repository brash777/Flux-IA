/* =====================================================================
   Hoja para crear, editar o borrar un movimiento. La usan Inicio y
   Movimientos.

   El monto viaja como texto («2450.50») para no pasar por coma flotante;
   el backend lo convierte a BigDecimal. El frontend no calcula nada: tras
   guardar, la pantalla vuelve a pedir sus cifras a la API.

   Un movimiento nuevo se puede escribir como se diría («gasolina 20
   mil»): la IA llena los campos y el usuario los revisa antes de guardar.
   La IA no guarda nada por su cuenta.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon } from '../dom.js';
import { amountForInput, fromLocalInput, parseAmount, toLocalInput } from '../format.js';
import * as motion from '../motion.js';
import { clearFormErrors, confirm, formAlert, setBusy, sheet, showFormErrors, toast } from '../ui.js';

let catalog = null;

/* Categorías y cuentas cambian poco: se piden una vez por sesión de la
   página. Si falla, se vuelve a intentar la próxima vez. */
async function loadCatalog() {
  if (!catalog) {
    catalog = Promise.all([api.categories(), api.accounts()])
      .then(([categories, accounts]) => ({ categories, accounts }))
      .catch((error) => {
        catalog = null;
        throw error;
      });
  }
  return catalog;
}

let ids = 0;

function selectField({ label, name, options }) {
  const id = `mf-${name}-${++ids}`;
  const select = h('select', { class: 'input select', id, name });
  options.forEach(([value, text]) => select.append(h('option', { value }, text)));
  return h('div', { class: 'field' },
    h('label', { class: 'field__label', for: id }, label),
    h('div', { class: 'field__control' }, select, icon('arrow-down', 'select-arrow icon--sm')));
}

function inputField({ label, name, value = '', attrs = {} }) {
  const id = `mf-${name}-${++ids}`;
  const input = h('input', { class: 'input', id, name, ...attrs });
  input.value = value;
  return h('div', { class: 'field' }, h('label', { class: 'field__label', for: id }, label), input);
}

/**
 * Abre la hoja. `transaction` = null para uno nuevo. `onChange` se llama
 * después de guardar o borrar, para que la pantalla recargue sus datos.
 */
export async function openMovementForm({ transaction = null, onChange }) {
  let data;
  try {
    data = await loadCatalog();
  } catch (error) {
    toast(error.message, 'error');
    return;
  }
  const { categories, accounts } = data;
  const editing = Boolean(transaction);
  let type = transaction?.type ?? 'EXPENSE';

  const dialog = sheet({ title: editing ? 'Editar movimiento' : 'Nuevo movimiento' });

  // Tipo: gasto o ingreso. Cambia las categorías que se ofrecen.
  function setType(value) {
    type = value;
    typeButtons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.type === value)));
    fillCategories();
  }
  const typeButton = (value, label) => {
    const button = h('button', { class: 'segmented__option', type: 'button', 'aria-pressed': String(type === value), dataset: { type: value } }, label);
    button.addEventListener('click', () => setType(value));
    return button;
  };
  const typeButtons = [typeButton('EXPENSE', 'Gasto'), typeButton('INCOME', 'Ingreso')];

  const categoryField = selectField({ label: 'Categoría', name: 'categoryId', options: [] });
  const categorySelect = categoryField.querySelector('select');
  function fillCategories() {
    const current = categorySelect.value || transaction?.category?.id;
    categorySelect.replaceChildren(...categories
      .filter((category) => category.kind === type)
      .map((category) => h('option', { value: category.id }, `${category.icon ?? ''} ${category.name}`.trim())));
    if ([...categorySelect.options].some((option) => option.value === current)) categorySelect.value = current;
  }
  fillCategories();

  const accountField = selectField({
    label: 'Cuenta',
    name: 'accountId',
    options: accounts.map((account) => [account.id, `${account.name} · ${account.currency}`]),
  });
  if (transaction?.account?.id) accountField.querySelector('select').value = transaction.account.id;
  // Con una sola cuenta no hay nada que elegir: el backend usa la principal.
  accountField.hidden = accounts.length < 2;

  const amountField = inputField({
    label: 'Monto',
    name: 'amount',
    value: editing ? amountForInput(transaction.amount) : '',
    attrs: { inputmode: 'decimal', autocomplete: 'off', placeholder: '0', required: true },
  });
  const descriptionField = inputField({
    label: 'Descripción',
    name: 'description',
    value: transaction?.description ?? '',
    attrs: { maxlength: '140', autocomplete: 'off', placeholder: 'Ej.: compra semanal', required: true },
  });
  const dateField = inputField({
    label: 'Fecha y hora',
    name: 'occurredAt',
    value: toLocalInput(transaction?.occurredAt ?? Date.now()),
    attrs: { type: 'datetime-local', required: true, max: toLocalInput(Date.now()) },
  });

  const submit = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, editing ? 'Guardar cambios' : 'Guardar');
  const form = h('form', { class: 'form', novalidate: true },
    formAlert(),
    editing ? null : aiFill(),
    h('div', { class: 'segmented', role: 'group', 'aria-label': 'Tipo de movimiento' }, typeButtons),
    amountField, descriptionField, categoryField, accountField, dateField,
    h('div', { class: 'sheet__actions' }, submit));

  /* «gasolina 20 mil» → los campos llenos. Enter llena; no guarda. */
  function aiFill() {
    const id = `mf-ai-${++ids}`;
    const input = h('input', { class: 'input', id, name: 'aiText', maxlength: '300', autocomplete: 'off', enterkeyhint: 'go', placeholder: 'Ej.: gasolina 20 mil', 'aria-describedby': `${id}-hint` });
    const button = h('button', { class: 'btn btn--primary', type: 'button' }, icon('sparkles'), 'Llenar');
    const hint = h('span', { class: 'field__hint', id: `${id}-hint` }, 'La IA llena los campos y tú los revisas antes de guardar.');

    async function fill() {
      clearFormErrors(form);
      const text = input.value.trim();
      if (!text) {
        showFormErrors(form, { fields: { aiText: 'Escribe algo como «gasolina 20 mil».' } });
        return;
      }
      setBusy(button, true, 'Llenando…');
      try {
        const draft = await api.movementDraft(text);
        setType(draft.type);
        categorySelect.value = draft.categoryId;
        amountField.querySelector('input').value = amountForInput(draft.amount);
        descriptionField.querySelector('input').value = draft.description;
        dateField.querySelector('input').value = toLocalInput(draft.occurredAt);
        hint.textContent = 'Listo. Revisa los datos y toca Guardar.';
        motion.reveal(hint);
      } catch (error) {
        showFormErrors(form, error);
      } finally {
        setBusy(button, false);
        button.replaceChildren(icon('sparkles'), 'Llenar');
      }
    }

    input.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      fill();
    });
    button.addEventListener('click', fill);
    return h('div', { class: 'field ai-fill' },
      h('label', { class: 'field__label', for: id }, icon('sparkles', 'icon--sm'), 'Escríbelo como lo dirías'),
      h('div', { class: 'ai-fill__row' }, input, button),
      hint);
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    clearFormErrors(form);
    const values = Object.fromEntries(new FormData(form));
    const amount = parseAmount(values.amount);
    if (amount === null || Number(amount) <= 0) {
      showFormErrors(form, { fields: { amount: 'Escribe un monto mayor que cero, por ejemplo 2.450,50.' } });
      return;
    }
    const body = {
      description: values.description.trim(),
      amount,
      type,
      categoryId: values.categoryId,
      accountId: accounts.length > 1 ? values.accountId : undefined,
      occurredAt: values.occurredAt ? fromLocalInput(values.occurredAt) : null,
    };
    setBusy(submit, true, 'Guardando…');
    try {
      if (editing) await api.updateTransaction(transaction.id, body);
      else await api.createTransaction(body);
      await dialog.close();
      toast(editing ? 'Movimiento actualizado' : 'Movimiento guardado');
      onChange?.();
    } catch (error) {
      setBusy(submit, false);
      showFormErrors(form, error);
    }
  });

  if (editing) {
    const remove = h('button', { class: 'btn btn--danger btn--block', type: 'button' }, icon('trash'), 'Eliminar movimiento');
    remove.addEventListener('click', async () => {
      const sure = await confirm({
        title: '¿Eliminar este movimiento?',
        text: `«${transaction.description}» se borra y tus totales se recalculan. No se puede deshacer.`,
        confirmLabel: 'Eliminar',
      });
      if (!sure) return;
      setBusy(remove, true, 'Eliminando…');
      try {
        await api.deleteTransaction(transaction.id);
        await dialog.close();
        toast('Movimiento eliminado');
        onChange?.();
      } catch (error) {
        setBusy(remove, false);
        showFormErrors(form, error);
      }
    });
    form.querySelector('.sheet__actions').append(remove);
  }

  dialog.body.append(form);
}
