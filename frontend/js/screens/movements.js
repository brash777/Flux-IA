/* =====================================================================
   Movimientos: lista paginada con búsqueda y filtros, y los totales de
   TODO el filtro (no de la página), que calcula la API.

   Los filtros de categoría son las categorías de /categories, con su
   emoji y su color: los mismos 7 del prototipo, sin un mapa propio.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon, render } from '../dom.js';
import { dayHeading, dayKey, monthRange, netMoney, signedMoney } from '../format.js';
import * as motion from '../motion.js';
import { errorCard, skeletonList, stateCard, transactionRow } from '../ui.js';
import { openMovementForm } from './movement-form.js';

const PAGE_SIZE = 20;

const PERIODS = [
  { id: 'month', label: 'Este mes', range: () => monthRange(0) },
  { id: 'previous', label: 'Mes pasado', range: () => monthRange(1) },
  { id: 'all', label: 'Todo', range: () => ({}) },
];

export function mount(view) {
  view.classList.add('app-bg');
  const fx = motion.scope(view);

  const filters = { period: 'month', category: '', search: '' };
  let page = 0;
  let loaded = 0;
  let request = 0;
  let alive = true;
  let currency;

  const add = h('button', { class: 'btn btn--icon btn--accent', type: 'button', 'aria-label': 'Nuevo movimiento' }, icon('plus'));
  add.addEventListener('click', () => openMovementForm({ onChange: reload }));

  // Búsqueda: espera a que el usuario deje de escribir para no pedir una
  // página por cada letra.
  const searchInput = h('input', { class: 'input', type: 'search', name: 'search', placeholder: 'Buscar por descripción', 'aria-label': 'Buscar movimientos', autocomplete: 'off', enterkeyhint: 'search' });
  let searchTimer;
  searchInput.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      filters.search = searchInput.value.trim();
      reload();
    }, 300);
  });

  const chip = (label, pressed, onClick, extra) => {
    const button = h('button', { class: 'chip', type: 'button', 'aria-pressed': String(pressed) }, extra, label);
    button.addEventListener('click', onClick);
    return button;
  };
  const press = (row, button) => row.querySelectorAll('.chip').forEach((c) => c.setAttribute('aria-pressed', String(c === button)));

  const periodRow = h('div', { class: 'chip-row', role: 'group', 'aria-label': 'Período' });
  PERIODS.forEach((period) => {
    const button = chip(period.label, filters.period === period.id, () => {
      filters.period = period.id;
      press(periodRow, button);
      reload();
    });
    periodRow.append(button);
  });

  const categoryRow = h('div', { class: 'chip-row', role: 'group', 'aria-label': 'Categoría' });
  const totals = h('div', { class: 'glass glass--strong totals', 'aria-live': 'polite' });
  const listArea = h('div', {});
  const more = h('button', { class: 'btn btn--glass btn--block load-more', type: 'button', hidden: true }, 'Cargar más');
  const status = h('p', { class: 'list-status', role: 'status' });
  more.addEventListener('click', () => loadPage(page + 1));

  view.append(h('main', { class: 'screen' },
    h('header', { class: 'screen-header' }, h('h1', { class: 'h1' }, 'Movimientos'), h('div', { class: 'screen-header__actions' }, add)),
    h('div', { class: 'search' }, icon('search'), searchInput),
    periodRow, categoryRow, totals, listArea, more, status));

  /* Las categorías llegan de la API; si fallan, la lista igual funciona. */
  api.categories().then((categories) => {
    if (!alive) return;
    const all = chip('Todas', true, () => { filters.category = ''; press(categoryRow, all); reload(); });
    categoryRow.append(all);
    categories.forEach((category) => {
      const button = chip(category.name, false, () => {
        filters.category = category.slug;
        press(categoryRow, button);
        reload();
      }, h('span', { 'aria-hidden': 'true' }, category.icon));
      categoryRow.append(button);
    });
  }).catch(() => { categoryRow.hidden = true; });

  api.accounts().then((accounts) => { currency = accounts[0]?.currency; }).catch(() => {});

  function query(pageNumber) {
    const range = PERIODS.find((p) => p.id === filters.period).range();
    return { ...range, category: filters.category, search: filters.search, page: pageNumber, size: PAGE_SIZE };
  }

  function reload() {
    loaded = 0;
    render(totals, h('span', { class: 'skeleton skeleton--line', style: { display: 'block', width: '60%' } }));
    render(listArea, h('div', { style: { marginTop: 'var(--space-5)' } }, skeletonList(5, 'Cargando movimientos…')));
    more.hidden = true;
    status.textContent = '';
    loadPage(0);
  }

  let lastKey = null;
  let lastList = null;

  async function loadPage(pageNumber) {
    const mine = ++request;
    if (pageNumber > 0) {
      more.disabled = true;
      more.textContent = 'Cargando…';
    }
    try {
      const result = await api.transactions(query(pageNumber));
      if (!alive || mine !== request) return; // llegó tarde: ganó otro filtro
      page = result.page;
      showTotals(result.totals);
      if (pageNumber === 0) {
        render(listArea);
        lastKey = null;
        lastList = null;
      }
      if (!result.items.length && pageNumber === 0) {
        showEmpty();
      } else {
        const rows = appendRows(result.items);
        loaded += result.items.length;
        fx.stagger(rows);
      }
      more.hidden = page + 1 >= result.totalPages;
      more.disabled = false;
      more.textContent = 'Cargar más';
      status.textContent = result.totalItems ? `Mostrando ${loaded} de ${result.totalItems}` : '';
      motion.refresh();
    } catch (error) {
      if (!alive || mine !== request || error.code === 'SESSION_EXPIRED') return;
      if (pageNumber === 0) {
        render(totals);
        render(listArea, h('div', { style: { marginTop: 'var(--space-5)' } }, errorCard(error, reload)));
      } else {
        more.disabled = false;
        more.textContent = 'Reintentar';
      }
    }
  }

  function showTotals(sum) {
    const row = (label, value) => h('div', { class: 'totals__row' }, h('span', {}, label), value);
    render(totals,
      row('Ingresos', h('span', { class: 'money is-income' }, signedMoney(sum.income, 'INCOME', currency))),
      row('Gastos', h('span', { class: 'money' }, signedMoney(sum.expense, 'EXPENSE', currency))),
      row('Neto', h('span', { class: 'money' }, netMoney(sum.net, currency))));
  }

  /* Agrupa por día. Una página nueva puede seguir el día de la anterior. */
  function appendRows(items) {
    const rows = [];
    for (const tx of items) {
      const key = dayKey(tx.occurredAt);
      if (key !== lastKey) {
        lastKey = key;
        lastList = h('ul', { class: 'tx-list' });
        listArea.append(h('section', { class: 'day-group' },
          h('h2', { class: 'day-heading' }, dayHeading(tx.occurredAt)),
          h('div', { class: 'glass glass--strong' }, lastList)));
      }
      const row = transactionRow(tx, (t) => openMovementForm({ transaction: t, onChange: reload }), { showDay: false });
      lastList.append(row);
      rows.push(row);
    }
    return rows;
  }

  function showEmpty() {
    const filtered = filters.category || filters.search || filters.period !== 'all';
    render(listArea, h('div', { style: { marginTop: 'var(--space-5)' } }, filtered
      ? stateCard({
        iconName: 'search',
        title: 'No hay movimientos con estos filtros',
        text: 'Prueba con otro período, otra categoría u otra búsqueda.',
        action: { label: 'Ver todos', icon: 'refresh', onClick: clearFilters },
      })
      : stateCard({
        iconName: 'inbox',
        title: 'Todavía no hay movimientos',
        text: 'Registra tu primer ingreso o gasto.',
        action: { label: 'Nuevo movimiento', icon: 'plus', primary: true, onClick: () => openMovementForm({ onChange: reload }) },
      })));
  }

  function clearFilters() {
    filters.period = 'all';
    filters.category = '';
    filters.search = '';
    searchInput.value = '';
    press(periodRow, periodRow.querySelectorAll('.chip')[2]);
    const firstCategory = categoryRow.querySelector('.chip');
    if (firstCategory) press(categoryRow, firstCategory);
    reload();
  }

  reload();

  return {
    unmount() {
      alive = false;
      clearTimeout(searchTimer);
      fx.revert();
    },
  };
}
