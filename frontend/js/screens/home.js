/* =====================================================================
   Inicio: saldo, ingresos, gastos y tasa de ahorro del mes, y los
   movimientos más recientes. Todas las cifras salen de
   /reports/summary y /transactions/recent.
   ===================================================================== */

import * as api from '../api.js';
import { DEFAULT_CURRENCY } from '../config.js';
import { h, icon, render } from '../dom.js';
import { changePercent, currentMonthName, lengthClass, money, percent } from '../format.js';
import * as motion from '../motion.js';
import { errorCard, skeletonBlock, skeletonList, stateCard, transactionRow } from '../ui.js';
import { openMovementForm } from './movement-form.js';

export function mount(view, { navigate }) {
  view.classList.add('app-bg');
  const fx = motion.scope(view);

  const greeting = h('h1', { class: 'h1' }, 'Hola');
  const content = h('div', {});
  const scroller = h('main', { class: 'screen' },
    h('header', { class: 'screen-header' },
      h('div', {}, h('p', { class: 'eyebrow' }, currentMonthName()), greeting),
      h('div', { class: 'screen-header__actions' },
        // «Nuevo» va en el encabezado y no flotando: un botón flotante tapa
        // la columna de montos, y la cifra manda (DESIGN.md).
        h('button', { class: 'btn btn--icon btn--accent', type: 'button', 'aria-label': 'Nuevo movimiento', onclick: () => openMovementForm({ onChange: load }) }, icon('plus')),
        h('a', { class: 'btn btn--icon', href: '#/perfil', 'aria-label': 'Ir a tu perfil' }, icon('user')))),
    content);

  const sticky = h('div', { class: 'balance-card balance-card--compact balance-sticky', 'aria-hidden': 'true' });
  view.append(h('div', { class: 'orb', style: { width: '220px', height: '220px', top: '60px', right: '-90px' }, 'aria-hidden': 'true' }), scroller, sticky);

  let alive = true;
  let stopSticky = () => {};

  async function load() {
    render(content, skeletonBlock(214, 'Cargando tu resumen…'),
      h('div', { class: 'section-title' }, h('h2', { class: 'h2' }, 'Recientes')),
      skeletonList(4, 'Cargando movimientos…'));
    try {
      const [user, summary, recent, accounts] = await Promise.all([
        api.me(), api.summary('MONTH'), api.recentTransactions(5), api.accounts(),
      ]);
      if (!alive) return;
      greeting.textContent = `Hola, ${user.fullName.split(/\s+/)[0]}`;
      show(summary, recent, accounts[0]?.currency ?? DEFAULT_CURRENCY);
    } catch (error) {
      if (!alive || error.code === 'SESSION_EXPIRED') return;
      render(content, errorCard(error, load));
    }
  }

  function show(summary, recent, currency) {
    const fmt = (value) => money(value, currency);
    const balanceText = fmt(summary.balance);
    const amount = h('span', { class: `balance-card__amount money ${lengthClass(balanceText)}`, 'aria-hidden': 'true' });
    const income = h('span', { class: 'flow__value money', 'aria-hidden': 'true' });
    const expense = h('span', { class: 'flow__value money', 'aria-hidden': 'true' });
    const rate = percent(summary.savingsRate);

    const card = h('article', { class: 'balance-card', 'aria-label': 'Resumen del mes' },
      h('p', { class: 'balance-card__label' }, icon('wallet', 'icon--sm'), 'Saldo total'),
      amount, h('span', { class: 'sr-only' }, balanceText),
      h('div', { class: 'balance-card__rows' },
        h('div', { class: 'flow flow--income' },
          h('span', { class: 'flow__icon' }, icon('arrow-up', 'icon--sm')),
          h('span', { class: 'flow__label' }, 'Ingresos del mes'),
          income, h('span', { class: 'sr-only' }, fmt(summary.income))),
        h('div', { class: 'flow flow--expense' },
          h('span', { class: 'flow__icon' }, icon('arrow-down', 'icon--sm')),
          h('span', { class: 'flow__label' }, 'Gastos del mes'),
          expense, h('span', { class: 'sr-only' }, fmt(summary.expense)))),
      h('div', { class: 'balance-card__footer' },
        h('span', {}, 'Tasa de ahorro del mes'),
        // null significa «no hubo ingresos»: no es 0 %.
        h('span', { class: 'money' }, rate ?? '—')),
      rate ? null : h('p', { class: 'flow__label', style: { margin: 'var(--space-2) 0 0' } }, 'Sin ingresos este mes, no se puede calcular.'));

    const change = changePercent(summary.comparison?.expenseChangePercent);
    // El neto puede ser negativo: el signo sale del valor de la API.
    const net = h('span', { class: 'stat__value money' },
      `${Number(summary.net) < 0 ? '−' : '+'}${fmt(summary.net)}`);
    const stats = h('div', { class: 'stat-grid' },
      h('div', { class: 'glass glass--strong card stat' },
        h('span', { class: 'stat__label' }, 'Neto del mes'), net,
        h('span', { class: 'stat__note' }, 'Ingresos menos gastos')),
      h('div', { class: 'glass glass--strong card stat' },
        h('span', { class: 'stat__label' }, 'Gastos vs. mes anterior'),
        h('span', { class: 'stat__value money' }, change ?? '—'),
        h('span', { class: 'stat__note' }, change ? `Antes: ${fmt(summary.comparison.previousExpense)}` : 'Sin gastos el mes anterior')));

    const list = recent.length
      ? h('div', { class: 'glass glass--strong' },
        h('ul', { class: 'tx-list' }, recent.map((tx) => transactionRow(tx, (t) => openMovementForm({ transaction: t, onChange: load })))))
      : stateCard({
        iconName: 'inbox',
        title: 'Todavía no hay movimientos',
        text: 'Registra tu primer ingreso o gasto y aquí verás cómo se mueve tu dinero.',
        action: { label: 'Nuevo movimiento', icon: 'plus', primary: true, onClick: () => openMovementForm({ onChange: load }) },
      });

    const seeAll = h('a', { class: 'btn btn--ghost', href: '#/movimientos' }, 'Ver todos');
    render(content, card, stats,
      h('div', { class: 'section-title' }, h('h2', { class: 'h2' }, 'Recientes'), recent.length ? seeAll : null),
      list);

    render(sticky, h('span', { class: 'balance-card__label' }, 'Saldo total'), h('span', { class: 'balance-card__amount money' }, balanceText));

    // Las cifras cuentan de 0 al valor exacto de la API.
    fx.countUp(amount, summary.balance, fmt);
    fx.countUp(income, summary.income, fmt);
    fx.countUp(expense, summary.expense, fmt);
    fx.stagger([...stats.children, ...content.querySelectorAll('.tx-list > li')]);
    stopSticky();
    stopSticky = fx.sticky(scroller, card, sticky);
    motion.refresh();
  }

  load();

  return {
    unmount() {
      alive = false;
      fx.revert();
    },
  };
}
