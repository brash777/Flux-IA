// =====================================================================
// Crea la cuenta de JoAn con movimientos de ejemplo: un estudiante de
// universidad privada en Colombia, con sus gastos del día a día. Son
// datos inventados, para mostrar la app con una cuenta que se vea real.
//
// Uso: JOAN_PASSWORD=... node seed-joan.mjs [http://localhost:8080]
//
// Pasa por la API, con sus mismas validaciones, y no por SQL. Se puede
// correr de nuevo cuando empiece otro mes: agrega solo los movimientos
// que falten hasta hoy. Si se borró uno de los del plan, vuelve a crearlo.
// La contraseña llega por variable de entorno porque este repositorio es
// público.
// =====================================================================

const BASE = process.argv[2] ?? 'http://localhost:8080';
const API = `${BASE}/api/v1`;
const EMAIL = 'joan@fluxia.app';
const NAME = 'JoAn';
const PASSWORD = process.env.JOAN_PASSWORD;
const FIRST_MONTH = { year: 2026, month: 5 };
const BOGOTA_OFFSET = '-05:00';
const CONCURRENT_REQUESTS = 4;

if (!PASSWORD) {
  console.error('Falta la contraseña: JOAN_PASSWORD=... node seed-joan.mjs');
  process.exit(1);
}

let token = null;

async function call(method, path, body) {
  const response = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  return { status: response.status, data };
}

async function signIn() {
  const credentials = { email: EMAIL, password: PASSWORD };
  const login = await call('POST', '/auth/login', credentials);
  if (login.status === 200) return login.data.accessToken;
  const created = await call('POST', '/auth/register', { ...credentials, fullName: NAME });
  if (created.status >= 300) throw new Error(`No se pudo crear la cuenta: ${created.data?.message}`);
  console.log(`Cuenta creada: ${EMAIL}`);
  return created.data.accessToken;
}

/** Las categorías del sistema y las propias que la cuenta necesita. */
async function loadCategories() {
  const own = [
    { name: 'Universidad', kind: 'EXPENSE', icon: '🎓', color: '#0ea5e9' },
    { name: 'Snacks', kind: 'EXPENSE', icon: '🍿', color: '#f59e0b' },
  ];
  const { data: existing } = await call('GET', '/categories');
  for (const category of own) {
    if (!existing.some((c) => c.name === category.name)) {
      const created = await call('POST', '/categories', category);
      if (created.status >= 300) throw new Error(`Categoría ${category.name}: ${created.data?.message}`);
      existing.push(created.data);
    }
  }
  const ids = Object.fromEntries(existing.map((c) => [c.slug, c.id]));
  for (const c of existing) ids[c.name] = c.id;
  return ids;
}

/** Azar con semilla: el mismo mes da siempre el mismo plan. */
function seededRandom(text) {
  let state = [...text].reduce((hash, ch) => (hash * 31 + ch.charCodeAt(0)) >>> 0, 7);
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const TUITION_MONTHS = new Set([2, 3, 4, 5, 8, 9, 10, 11]);

function hasClasses(month, day) {
  if (month >= 2 && month <= 5) return true;
  if (month === 6) return day <= 6;
  if (month === 8) return day >= 3;
  if (month === 9 || month === 10) return true;
  return month === 11 && day <= 27;
}

const SNACKS = [
  ['Empanada y gaseosa', 7000], ['Papas y gaseosa', 6500], ['Galletas y café', 5000],
  ['Tinto y pandebono', 4500], ['Paquete de papitas', 3500], ['Chocolatina', 3000],
  ['Arepa de queso', 6000], ['Jugo natural', 5500], ['Helado', 4000], ['Buñuelo y tinto', 3500],
];
const LUNCHES = ['Almuerzo en la U', 'Corrientazo cerca de la U', 'Almuerzo en la cafetería'];
const OUTINGS = [
  ['Salida con amigos', [42000, 58000, 65000]], ['Hamburguesa con amigos', [26000, 32000]],
  ['Cine', [18000, 22000]], ['Pizza con amigos', [24000, 30000]],
  ['Domicilio por Rappi', [29000, 35000, 41000]], ['Cervezas con los del grupo', [28000, 36000]],
];
const ONE_OFFS = {
  '6-22': ['Paseo a Santa Marta con amigos', 280000, 'ocio'],
  '8-10': ['Libros del semestre', 185000, 'Universidad'],
  '9-12': ['Inscripción al congreso de la U', 60000, 'Universidad'],
  '10-30': ['Disfraz de Halloween', 55000, 'ocio'],
};

/** Todos los movimientos de un mes, aunque algunos aún no hayan ocurrido. */
function planMonth(year, month) {
  const random = seededRandom(`${year}-${month}`);
  const pick = (list) => list[Math.floor(random() * list.length)];
  const chance = (probability) => random() < probability;
  const between = (low, high) => low + Math.floor(random() * (high - low + 1));
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const moves = [];

  const add = (day, hour, minute, description, amount, category, type = 'EXPENSE') => {
    if (day > lastDay) return;
    const pad = (n) => String(n).padStart(2, '0');
    moves.push({
      description,
      amount,
      type,
      category,
      occurredAt: `${year}-${pad(month)}-${pad(day)}T${pad(hour)}:${pad(minute)}:00${BOGOTA_OFFSET}`,
    });
  };
  const income = (day, hour, description, amount) => add(day, hour, 5, description, amount, 'ingreso', 'INCOME');

  if (year === FIRST_MONTH.year && month === FIRST_MONTH.month) income(1, 8, 'Ahorros que traía', 620000);
  income(1, 9, 'Mesada de mis papás', 950000);
  add(2, 18, 20, 'Plan del celular', 45000, 'servicios');
  add(6, 7, 0, 'Spotify', 9900, 'servicios');
  add(8, between(15, 18), 30, 'Barbería', 20000, 'otros');
  add(11, 7, 0, 'Netflix compartido', 13000, 'servicios');
  add(9, 17, between(10, 50), 'Mercado en D1', pick([38500, 46200, 52900, 44700]), 'supermercado');
  add(23, 16, between(10, 50), 'Mercado en D1', pick([41300, 56800, 61200, 47500]), 'supermercado');
  if (month % 2 === 0) add(16, 18, 15, 'Aseo personal', 27500, 'supermercado');

  if (TUITION_MONTHS.has(month)) {
    income(3, 10, 'Apoyo de mis papás para la matrícula', 1850000);
    add(4, 11, 20, 'Cuota de la matrícula', 1850000, 'Universidad');
    income(15, 14, 'Monitoría en la U', 340000);
  }
  if (month === 7) income(18, 16, 'Trabajo de vacaciones', 820000);

  for (const [key, [description, amount, category]] of Object.entries(ONE_OFFS)) {
    const [oneOffMonth, day] = key.split('-').map(Number);
    if (oneOffMonth === month) add(day, between(10, 19), between(0, 59), description, amount, category);
  }

  // Gasolina: 20 mil cada tanto, más seguido cuando hay que ir a la U.
  let fuelDay = between(1, 3);
  while (fuelDay <= lastDay) {
    add(fuelDay, between(6, 8), between(0, 59), 'Gasolina', 20000, 'transporte');
    fuelDay += hasClasses(month, fuelDay) ? between(3, 5) : between(6, 8);
  }

  for (let day = 1; day <= lastDay; day += 1) {
    const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
    const isWeekday = weekday >= 1 && weekday <= 5;

    if (isWeekday && hasClasses(month, day)) {
      if (chance(0.6)) add(day, 7, between(0, 40), 'Parqueadero de la U', 3000, 'transporte');
      if (chance(0.65)) add(day, between(12, 13), between(5, 55), pick(LUNCHES), pick([13000, 14000, 15000, 16000, 18000]), 'comida');
      if (chance(0.6)) {
        const [snack, price] = pick(SNACKS);
        add(day, pick([9, 10, 15, 16]), between(5, 55), snack, price, 'Snacks');
      }
      if (chance(0.2)) {
        const [snack, price] = pick(SNACKS);
        add(day, 17, between(5, 55), snack, price, 'Snacks');
      }
      if (chance(0.15)) add(day, 11, between(5, 55), 'Fotocopias e impresiones', pick([1500, 2500, 4000, 6500, 8500]), 'Universidad');
    } else if (isWeekday && chance(0.2)) {
      const [snack, price] = pick(SNACKS);
      add(day, 16, between(5, 55), snack, price, 'Snacks');
    }

    if ((weekday === 5 || weekday === 6) && chance(0.45)) {
      const [outing, prices] = pick(OUTINGS);
      add(day, between(19, 21), between(0, 59), outing, pick(prices), 'ocio');
    }
    if (weekday === 0 && chance(0.25)) add(day, 16, between(0, 59), 'Postre y café', pick([12000, 14000, 16500]), 'comida');
    if (chance(0.04)) add(day, 22, between(0, 45), 'Uber a la casa', pick([12500, 14000, 17800, 19200]), 'transporte');
    if (chance(0.02)) add(day, 18, between(0, 59), 'Regalo de cumpleaños', pick([35000, 45000]), 'otros');
  }
  return moves;
}

function planUntil(now) {
  const moves = [];
  const today = new Date(now.getTime() - 5 * 3600 * 1000);
  let { year, month } = FIRST_MONTH;
  while (year < today.getUTCFullYear() || (year === today.getUTCFullYear() && month <= today.getUTCMonth() + 1)) {
    moves.push(...planMonth(year, month));
    month += 1;
    if (month > 12) { month = 1; year += 1; }
  }
  return moves.filter((move) => new Date(move.occurredAt) <= now);
}

/**
 * La matrícula, los servicios y el mercado no son compras por impulso: no
 * cuentan para el tope diario. Solo se marcan si JoAn todavía no configuró
 * nada, para no pisar lo que haya elegido en la app. El tope en sí no se
 * pone: en la demostración lo propone la IA.
 */
async function markFixedExpenses(categories) {
  const { data: today } = await call('GET', '/spending/today');
  if (today.dailyLimit !== null || today.excludedCategories.length > 0) return;
  const fixed = ['Universidad', 'servicios', 'supermercado'].map((key) => categories[key]);
  const saved = await call('PUT', '/spending/settings', { dailyLimit: null, excludedCategoryIds: fixed });
  if (saved.status >= 300) throw new Error(`Gastos fijos: ${saved.data?.message}`);
  console.log('Gastos fijos marcados: Universidad, Servicios y Supermercado');
}

const keyOf = (description, amount, occurredAt) => `${description}|${Number(amount)}|${new Date(occurredAt).getTime()}`;

async function existingKeys() {
  const keys = new Set();
  for (let page = 0; ; page += 1) {
    const { data } = await call('GET', `/transactions?page=${page}&size=100`);
    for (const tx of data.items) keys.add(keyOf(tx.description, tx.amount, tx.occurredAt));
    if (page + 1 >= data.totalPages) return keys;
  }
}

async function main() {
  token = await signIn();
  const categories = await loadCategories();
  const existing = await existingKeys();
  const pending = planUntil(new Date()).filter((m) => !existing.has(keyOf(m.description, m.amount, m.occurredAt)));

  let index = 0;
  const failures = [];
  const worker = async () => {
    while (index < pending.length) {
      const { category, ...move } = pending[index];
      index += 1;
      const saved = await call('POST', '/transactions', { ...move, categoryId: categories[category] });
      if (saved.status >= 300) failures.push(`${move.description}: ${saved.data?.message}`);
    }
  };
  await Promise.all(Array.from({ length: CONCURRENT_REQUESTS }, worker));

  await markFixedExpenses(categories);

  const { data: summary } = await call('GET', '/reports/summary?period=MONTH');
  console.log(`Movimientos nuevos: ${pending.length - failures.length}; ya estaban: ${existing.size}`);
  console.log(`Saldo: ${summary.balance}; este mes: +${summary.income} / -${summary.expense}`);
  if (failures.length) {
    console.error(`Fallaron ${failures.length}:\n${failures.slice(0, 5).join('\n')}`);
    process.exitCode = 1;
  }
}

await main();
