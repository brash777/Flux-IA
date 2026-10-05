/* =====================================================================
   Formato de cifras y fechas: el único lugar donde se convierten en
   texto. Aquí se presenta lo que manda la API; no se calcula ningún
   total, porcentaje ni saldo (CLAUDE.md, regla de oro).
   ===================================================================== */

import { APP_TIMEZONE, DEFAULT_CURRENCY } from './config.js';

const MINUS = '−'; // signo menos tipográfico, no el guion

const moneyFormats = new Map();

function moneyFormat(currency) {
  if (!moneyFormats.has(currency)) {
    moneyFormats.set(currency, new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }));
  }
  return moneyFormats.get(currency);
}

/** «$ 56.410,00». El monto llega positivo; el signo lo pone signedMoney. */
export function money(amount, currency = DEFAULT_CURRENCY) {
  return moneyFormat(currency).format(Math.abs(Number(amount)));
}

/** «+$ 43.000,00» o «−$ 5.800,00», según el tipo del movimiento. */
export function signedMoney(amount, type, currency = DEFAULT_CURRENCY) {
  return `${type === 'INCOME' ? '+' : MINUS}${money(amount, currency)}`;
}

const percentFormat = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Variación porcentual con signo: «+34,88 %» / «−57,29 %». */
export function changePercent(value) {
  if (value === null || value === undefined) return null;
  const number = Number(value);
  const text = percentFormat.format(Math.abs(number));
  if (number > 0) return `+${text} %`;
  if (number < 0) return `${MINUS}${text} %`;
  return `${text} %`;
}

/** «70,26 %». null no es cero: quien llama decide qué mostrar. */
export function percent(value) {
  if (value === null || value === undefined) return null;
  return `${percentFormat.format(Number(value))} %`;
}

/** Escalón de tamaño para cifras largas (DESIGN.md: nunca se cortan). */
export function lengthClass(text) {
  if (text.length > 16) return 'is-very-long';
  if (text.length > 13) return 'is-long';
  return '';
}

/* --- Fechas --------------------------------------------------------- */

const dayKeyFormat = new Intl.DateTimeFormat('en-CA', { timeZone: APP_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit' });
const shortDate = new Intl.DateTimeFormat('es-AR', { timeZone: APP_TIMEZONE, day: 'numeric', month: 'short' });
const longDate = new Intl.DateTimeFormat('es-AR', { timeZone: APP_TIMEZONE, weekday: 'long', day: 'numeric', month: 'long' });
const timeFormat = new Intl.DateTimeFormat('es-AR', { timeZone: APP_TIMEZONE, hour: '2-digit', minute: '2-digit', hour12: false });
const monthName = new Intl.DateTimeFormat('es-AR', { timeZone: APP_TIMEZONE, month: 'long' });

/** «2026-10-05» en la zona de la app: sirve para agrupar por día. */
export function dayKey(date) {
  return dayKeyFormat.format(new Date(date));
}

/** «Hoy», «Ayer» o «3 oct.». */
export function relativeDay(date) {
  const key = dayKey(date);
  const today = dayKey(Date.now());
  const yesterday = dayKey(Date.now() - 86_400_000);
  if (key === today) return 'Hoy';
  if (key === yesterday) return 'Ayer';
  return shortDate.format(new Date(date));
}

/** Encabezado de grupo: «Hoy», «Ayer» o «sábado 3 de octubre». */
export function dayHeading(date) {
  const relative = relativeDay(date);
  if (relative === 'Hoy' || relative === 'Ayer') return relative;
  const text = longDate.format(new Date(date));
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function time(date) {
  return timeFormat.format(new Date(date));
}

/** «Octubre», con mayúscula, para rótulos de período. */
export function currentMonthName() {
  const text = monthName.format(new Date());
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/* Desfase de la zona de la app en una fecha dada, como «-03:00». */
function offsetAt(date) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: APP_TIMEZONE, timeZoneName: 'longOffset' })
    .formatToParts(date);
  const name = parts.find((part) => part.type === 'timeZoneName')?.value ?? 'GMT';
  const match = name.match(/GMT([+-]\d{2}):?(\d{2})?/);
  return match ? `${match[1]}:${match[2] ?? '00'}` : 'Z';
}

/** Valor para un <input type="datetime-local">, en la zona de la app. */
export function toLocalInput(date) {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: APP_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(new Date(date)).map((part) => [part.type, part.value]));
  const hour = parts.hour === '24' ? '00' : parts.hour;
  return `${parts.year}-${parts.month}-${parts.day}T${hour}:${parts.minute}`;
}

/** De «2026-10-05T14:30» (hora de la app) a ISO-8601 con desfase. */
export function fromLocalInput(value) {
  const guess = new Date(`${value}:00Z`);
  return `${value}:00${offsetAt(guess)}`;
}

/** Mes actual (0) o anteriores (1, 2…) como rango semiabierto [desde, hasta). */
export function monthRange(monthsBack = 0) {
  const [year, month] = dayKey(Date.now()).split('-').map(Number);
  const start = new Date(Date.UTC(year, month - 1 - monthsBack, 1));
  const end = new Date(Date.UTC(year, month - monthsBack, 1));
  const iso = (d) => {
    const text = `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}-01T00:00:00`;
    return `${text}${offsetAt(d)}`;
  };
  return { from: iso(start), to: iso(end) };
}

/* --- Montos que escribe el usuario --------------------------------- */

/**
 * Interpreta «2.450,50», «2450,5» o «2450.50» y devuelve «2450.50» como
 * texto, para que viaje a la API sin pasar por coma flotante. null si no
 * es un monto válido.
 */
export function parseAmount(input) {
  let text = String(input).trim().replace(/[\s$]/g, '');
  if (!text) return null;
  if (text.includes(',')) {
    text = text.replace(/\./g, '').replace(',', '.');
  } else if (/^\d{1,3}(\.\d{3})+$/.test(text)) {
    text = text.replace(/\./g, ''); // «2.450» es dos mil cuatrocientos cincuenta
  }
  if (!/^\d+(\.\d{1,2})?$/.test(text)) return null;
  const [whole, fraction = ''] = text.split('.');
  return `${whole.replace(/^0+(?=\d)/, '')}.${fraction.padEnd(2, '0')}`;
}

/** Monto de la API para un campo editable: «2450,50». */
export function amountForInput(amount) {
  return Number(amount).toFixed(2).replace('.', ',');
}
