/* =====================================================================
   Reportes: resumen del período, gasto por categoría (dona) y tendencia
   mensual (barras).

   Todas las cifras y porcentajes vienen de /reports/*. Lo único que se
   calcula aquí es geometría: qué parte del círculo ocupa una porción
   (su porcentaje de la API) y qué alto tiene una barra respecto de la
   más alta. Ningún número dibujado se muestra como texto sin venir de la
   API.
   ===================================================================== */

import * as api from '../api.js';
import { DEFAULT_CURRENCY } from '../config.js';
import { h, render, svg } from '../dom.js';
import { changePercent, money, netMoney, percent } from '../format.js';
import * as motion from '../motion.js';
import { categoryChip, errorCard, skeletonBlock, stateCard } from '../ui.js';

const PERIODS = [
  { id: 'MONTH', label: 'Mes', previous: 'el mes anterior' },
  { id: 'QUARTER', label: 'Trimestre', previous: 'el trimestre anterior' },
  { id: 'YEAR', label: 'Año', previous: 'el año anterior' },
];

const RADIUS = 80;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 2.4; // separación entre porciones, en unidades del dibujo

export function mount(view) {
  view.classList.add('app-bg');
  const fx = motion.scope(view);
  let period = 'MONTH';
  let currency = DEFAULT_CURRENCY;
  let alive = true;
  let request = 0;

  const tabs = h('div', { class: 'segmented segmented--3', role: 'group', 'aria-label': 'Período' });
  PERIODS.forEach((p) => {
    const button = h('button', { class: 'segmented__option', type: 'button', 'aria-pressed': String(p.id === period) }, p.label);
    button.addEventListener('click', () => {
      if (period === p.id) return;
      period = p.id;
      tabs.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(b === button)));
      loadPeriod();
    });
    tabs.append(button);
  });

  const summaryArea = h('div', {});
  const categoryArea = h('div', {});
  const trendArea = h('div', {});
  const scroller = h('main', { class: 'screen' },
    h('header', { class: 'screen-header' }, h('h1', { class: 'h1' }, 'Reportes')),
    tabs, summaryArea, categoryArea, trendArea);
  view.append(scroller);

  async function loadPeriod() {
    const mine = ++request;
    render(summaryArea, h('div', { style: { marginTop: 'var(--space-4)' } }, skeletonBlock(230, 'Cargando el resumen…')));
    render(categoryArea, h('div', { style: { marginTop: 'var(--space-4)' } }, skeletonBlock(420, 'Cargando el gasto por categoría…')));
    try {
      const [summary, categories] = await Promise.all([api.summary(period), api.byCategory(period, 'EXPENSE')]);
      if (!alive || mine !== request) return;
      showSummary(summary);
      showCategories(categories);
      motion.refresh();
    } catch (error) {
      if (!alive || mine !== request || error.code === 'SESSION_EXPIRED') return;
      render(summaryArea, h('div', { style: { marginTop: 'var(--space-4)' } }, errorCard(error, loadPeriod)));
      render(categoryArea);
    }
  }

  async function loadTrend() {
    render(trendArea, h('div', { style: { marginTop: 'var(--space-4)' } }, skeletonBlock(300, 'Cargando la tendencia…')));
    try {
      const trend = await api.monthlyTrend(7);
      if (!alive) return;
      showTrend(trend.months);
      motion.refresh();
    } catch (error) {
      if (!alive || error.code === 'SESSION_EXPIRED') return;
      render(trendArea, h('div', { style: { marginTop: 'var(--space-4)' } }, errorCard(error, loadTrend)));
    }
  }

  /* --- Resumen ------------------------------------------------------ */

  function showSummary(s) {
    const meta = PERIODS.find((p) => p.id === period);
    const fmt = (value) => money(value, currency);
    const tile = (label, value, note, extraClass = '') => h('div', { class: 'glass glass--strong card stat' },
      h('span', { class: 'stat__label' }, label),
      h('span', { class: `stat__value money ${extraClass}`.trim() }, value),
      note ? h('span', { class: 'stat__note' }, note) : null);
    const vs = (value) => {
      const text = changePercent(value);
      return text ? `${text} vs. ${meta.previous}` : `Sin datos para ${meta.previous}`;
    };
    const rate = percent(s.savingsRate);
    render(summaryArea, h('div', { class: 'stat-grid' },
      tile('Ingresos', fmt(s.income), vs(s.comparison?.incomeChangePercent)),
      tile('Gastos', fmt(s.expense), vs(s.comparison?.expenseChangePercent)),
      tile('Neto', netMoney(s.net, currency), 'Ingresos menos gastos'),
      // null = sin ingresos en el período: no se puede calcular, no es 0 %.
      tile('Tasa de ahorro', rate ?? '—', rate ? 'Del período' : 'Sin ingresos, no se puede calcular')));
    fx.stagger(summaryArea.querySelectorAll('.stat'));
  }

  /* --- Dona: gasto por categoría ------------------------------------ */

  function showCategories(data) {
    if (!data.slices.length) {
      render(categoryArea, h('div', { style: { marginTop: 'var(--space-4)' } }, stateCard({
        iconName: 'chart',
        title: 'Sin gastos en este período',
        text: 'Cuando registres gastos, aquí verás en qué categorías se va tu dinero.',
      })));
      return;
    }

    let start = 0;
    const segments = data.slices.map((slice, index) => {
      const length = Math.max((Number(slice.percent) / 100) * CIRCUMFERENCE - GAP, 0.5);
      const angle = -90 + (start / 100) * 360;
      start += Number(slice.percent);
      const circle = svg('circle', {
        cx: 100, cy: 100, r: RADIUS,
        stroke: slice.color,
        'stroke-dasharray': `${length} ${CIRCUMFERENCE}`,
        transform: `rotate(${angle} 100 100)`,
        'data-length': length,
        'data-index': index,
      });
      return circle;
    });

    const donut = svg('svg', { class: 'donut', viewBox: '0 0 200 200', 'aria-hidden': 'true' }, segments);
    const totalText = money(data.total, currency);

    // La lista es la leyenda y la vista en tabla: emoji, nombre, % y
    // monto, todo de la API. Tocar una fila resalta su porción.
    const legend = h('ul', { class: 'legend' }, data.slices.map((slice, index) => {
      const count = slice.movements === 1 ? '1 movimiento' : `${slice.movements} movimientos`;
      const button = h('button', { type: 'button', 'aria-pressed': 'false' },
        categoryChip(slice),
        h('span', {}, h('span', { class: 'legend__name' }, slice.name), h('span', { class: 'legend__meta' }, `${percent(slice.percent)} · ${count}`)),
        h('span', { class: 'legend__value money' }, money(slice.amount, currency)));
      button.addEventListener('click', () => highlight(index, button));
      return h('li', {}, button);
    }));

    function highlight(index, button) {
      const active = button.getAttribute('aria-pressed') !== 'true';
      legend.querySelectorAll('button').forEach((b) => b.setAttribute('aria-pressed', String(active && b === button)));
      segments.forEach((segment, i) => segment.classList.toggle('is-dim', active && i !== index));
    }

    const card = h('section', { class: 'glass glass--strong chart-card', 'aria-labelledby': 'cat-title' },
      h('h2', { class: 'h2 chart-card__title', id: 'cat-title' }, 'Gasto por categoría'),
      h('div', { class: 'donut-wrap' }, donut,
        h('div', { class: 'donut__center' }, h('span', {}, 'Total de gastos'), h('span', { class: 'money' }, totalText))),
      legend);
    render(categoryArea, card);
    fx.charts(scroller, card, { segments });
  }

  /* --- Barras: tendencia mensual ------------------------------------ */

  function showTrend(months) {
    const highest = Math.max(...months.flatMap((m) => [Number(m.income), Number(m.expense)]), 0);
    const height = (value) => (highest ? `${(Number(value) / highest) * 100}%` : '0%');
    const detail = h('div', { class: 'bar-detail', 'aria-live': 'polite' });

    const select = (month, column) => {
      columns.forEach((c) => c.setAttribute('aria-pressed', String(c === column)));
      const line = (label, value, cls = '') => h('div', { class: 'totals__row' }, h('span', {}, label), h('span', { class: `money ${cls}`.trim() }, value));
      render(detail,
        h('p', { class: 'eyebrow', style: { margin: '0 0 var(--space-1)' } }, `${month.label} ${month.year}${month.current ? ' · mes en curso' : ''}`),
        line('Ingresos', money(month.income, currency), 'is-income'),
        line('Gastos', money(month.expense, currency)),
        line('Neto', netMoney(month.net, currency)));
    };

    const bars = [];
    const columns = months.map((month) => {
      const income = h('span', { class: 'bar bar--income', style: { height: height(month.income) } });
      const expense = h('span', { class: 'bar bar--expense', style: { height: height(month.expense) } });
      bars.push(income, expense);
      const column = h('button', {
        class: `bar-col${month.current ? ' is-current' : ''}`,
        type: 'button',
        'aria-pressed': 'false',
        'aria-label': `${month.label} ${month.year}: ingresos ${money(month.income, currency)}, gastos ${money(month.expense, currency)}`,
      }, h('span', { class: 'bar-pair' }, income, expense), h('span', { class: 'bar-col__label' }, month.label));
      column.addEventListener('click', () => select(month, column));
      return column;
    });

    const chart = h('div', { class: 'bars', style: { '--months': months.length } }, columns);
    const card = h('section', { class: 'glass glass--strong chart-card', 'aria-labelledby': 'trend-title' },
      h('h2', { class: 'h2 chart-card__title', id: 'trend-title' }, `Últimos ${months.length} meses`),
      h('ul', { class: 'chart-legend' },
        h('li', {}, h('span', { class: 'swatch', style: { background: 'var(--chart-income)' } }), 'Ingresos'),
        h('li', {}, h('span', { class: 'swatch', style: { background: 'var(--chart-expense)' } }), 'Gastos')),
      chart, detail);
    render(trendArea, card);

    const current = months.findIndex((m) => m.current);
    const index = current >= 0 ? current : months.length - 1;
    if (months.length) select(months[index], columns[index]);
    fx.charts(scroller, chart, { bars });
  }

  loadPeriod();
  loadTrend();

  api.accounts().then((accounts) => { currency = accounts[0]?.currency ?? currency; }).catch(() => {});

  return {
    unmount() {
      alive = false;
      fx.revert();
    },
  };
}
