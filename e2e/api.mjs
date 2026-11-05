// =====================================================================
// Prueba de punta a punta de la API de Flux IA, contra el servidor
// corriendo con el perfil dev (usa la semilla y el devToken de
// recuperación).
//
// Uso: node api.mjs [http://localhost:8080]
//
// Además de que cada endpoint responda, comprueba la regla de oro: la
// misma cifra tiene que dar lo mismo en el resumen, la dona, la lista y
// la tendencia. Crea un usuario de prueba por corrida
// (prueba.e2e.<fecha>@fluxia.test); los movimientos que crea en el
// usuario demo los borra al terminar.
// =====================================================================

const BASE = process.argv[2] ?? 'http://localhost:8080';
const API = BASE + '/api/v1';
const DEMO = { email: 'diego.f@fluxia.app', password: 'Flux2026!' };

const results = [];
let section = '';

function ok(name, condition, detail = '') {
  results.push({ section, name, pass: Boolean(condition) });
  const why = !condition && detail ? `  -> ${typeof detail === 'string' ? detail : JSON.stringify(detail).slice(0, 300)}` : '';
  console.log(`${condition ? 'PASS' : 'FAIL'}  [${section}] ${name}${why}`);
}

async function call(method, path, { token, body, query, raw } = {}) {
  const url = new URL((raw ? BASE : API) + path);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null) url.searchParams.set(key, value);
  }
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = 'Bearer ' + token;
  const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), redirect: 'manual' });
  const text = await response.text();
  let data = text;
  try { data = JSON.parse(text); } catch { /* no es JSON */ }
  return { status: response.status, data };
}

// Las cifras se comparan en centavos enteros: la prueba no puede caer en
// el error de coma flotante que el backend evita.
const cents = (value) => (value === null || value === undefined ? null : Math.round(Number(value) * 100));
const userMessage = (r) => typeof r.data?.message === 'string' && r.data.message.length > 0 && !/Exception|SQL/.test(r.data.message);
const now = () => new Date().toISOString();

async function main() {
  section = 'autenticación';
  const login = await call('POST', '/auth/login', { body: DEMO });
  ok('login del usuario demo -> 200 con tokens', login.status === 200 && login.data.accessToken && login.data.refreshToken, login);
  if (login.status !== 200) throw new Error('Sin login no se puede seguir. ¿Corre el backend con SPRING_PROFILES_ACTIVE=dev?');
  const T = login.data.accessToken;

  const badPassword = await call('POST', '/auth/login', { body: { email: DEMO.email, password: 'mala-clave' } });
  ok('contraseña incorrecta -> 401 con mensaje para el usuario', badPassword.status === 401 && userMessage(badPassword), badPassword);
  const badEmail = await call('POST', '/auth/login', { body: { email: 'no-es-correo', password: 'x' } });
  ok('correo inválido -> 400 con fields.email', badEmail.status === 400 && badEmail.data?.fields?.email, badEmail);
  ok('sin token -> 401', (await call('GET', '/me')).status === 401);
  ok('token falso -> 401', (await call('GET', '/me', { token: 'basura.basura.basura' })).status === 401);

  section = 'perfil, cuentas y categorías';
  const me = await call('GET', '/me', { token: T });
  ok('GET /me devuelve el usuario demo', me.status === 200 && me.data.email === DEMO.email, me);
  const accounts = await call('GET', '/accounts', { token: T });
  ok('GET /accounts -> al menos una cuenta', accounts.status === 200 && accounts.data.length >= 1, accounts);
  const categories = await call('GET', '/categories', { token: T });
  ok('GET /categories -> cada una con icon y color', categories.status === 200 && categories.data.length > 0 && categories.data.every((c) => c.icon && c.color), categories);
  const expenseCategory = categories.data.find((c) => c.kind === 'EXPENSE');
  const incomeCategory = categories.data.find((c) => c.kind === 'INCOME');

  section = 'movimientos';
  const page = await call('GET', '/transactions', { token: T, query: { size: 5 } });
  ok('GET /transactions?size=5 -> página con totales', page.status === 200 && page.data.items.length <= 5 && page.data.totals, page);
  const expenses = await call('GET', '/transactions', { token: T, query: { type: 'EXPENSE', size: 100 } });
  ok('filtro type=EXPENSE -> solo gastos', expenses.status === 200 && expenses.data.items.every((t) => t.type === 'EXPENSE'));
  ok('los montos llegan siempre positivos', expenses.data.items.every((t) => Number(t.amount) > 0));
  const word = page.data.items[0]?.description.split(/\s+/)[0];
  if (word) {
    const search = await call('GET', '/transactions', { token: T, query: { search: word } });
    ok(`búsqueda «${word}» -> solo resultados que la contienen`, search.status === 200 && search.data.items.length > 0 && search.data.items.every((t) => t.description.toLowerCase().includes(word.toLowerCase())));
  }
  const tooBig = await call('GET', '/transactions', { token: T, query: { size: 500 } });
  ok('size=500 se limita a 100', tooBig.status === 200 && tooBig.data.items.length <= 100, tooBig.status);
  const recent = await call('GET', '/transactions/recent', { token: T, query: { limit: 3 } });
  ok('GET /transactions/recent?limit=3 -> hasta 3', recent.status === 200 && recent.data.length <= 3, recent);

  section = 'reportes: una sola fuente de verdad';
  const summary = await call('GET', '/reports/summary', { token: T, query: { period: 'MONTH' } });
  ok('GET /reports/summary -> 200 con su rango', summary.status === 200 && summary.data.from && summary.data.to, summary);
  ok('net = income - expense', cents(summary.data.net) === cents(summary.data.income) - cents(summary.data.expense), summary.data);
  const byCategory = await call('GET', '/reports/by-category', { token: T, query: { period: 'MONTH' } });
  ok('GET /reports/by-category -> 200', byCategory.status === 200, byCategory);
  const sliceTotal = byCategory.data.slices.reduce((sum, s) => sum + cents(s.amount), 0);
  ok('la dona suma exactamente el gasto del resumen', sliceTotal === cents(summary.data.expense) && cents(byCategory.data.total) === cents(summary.data.expense), { sliceTotal, expense: summary.data.expense });
  const percentTotal = byCategory.data.slices.reduce((sum, s) => sum + Number(s.percent), 0);
  ok('los porcentajes de la dona suman 100', byCategory.data.slices.length === 0 || Math.abs(percentTotal - 100) < 0.5, percentTotal);
  const monthList = await call('GET', '/transactions', { token: T, query: { from: summary.data.from, to: summary.data.to, size: 1 } });
  ok('la lista del mes da los mismos totales que el resumen', cents(monthList.data.totals.income) === cents(summary.data.income) && cents(monthList.data.totals.expense) === cents(summary.data.expense), monthList.data.totals);
  const trend = await call('GET', '/reports/monthly-trend', { token: T, query: { months: 7 } });
  ok('GET /reports/monthly-trend?months=7 -> 7 meses', trend.status === 200 && trend.data.months.length === 7, trend);
  const current = trend.data.months.find((m) => m.current);
  ok('el mes actual de la tendencia coincide con el resumen', current && cents(current.income) === cents(summary.data.income) && cents(current.expense) === cents(summary.data.expense), current);
  for (const period of ['QUARTER', 'YEAR']) {
    ok(`summary ${period} -> 200`, (await call('GET', '/reports/summary', { token: T, query: { period } })).status === 200);
  }
  const badPeriod = await call('GET', '/reports/summary', { token: T, query: { period: 'SEMANA' } });
  ok('period inválido -> 400 con los valores admitidos', badPeriod.status === 400 && userMessage(badPeriod) && badPeriod.data.message.includes('MONTH'), badPeriod);

  section = 'crear, editar y borrar';
  const created = await call('POST', '/transactions', { token: T, body: { description: 'Prueba E2E café', amount: '123.45', type: 'EXPENSE', categoryId: expenseCategory.id, occurredAt: now() } });
  ok('POST /transactions -> 201', created.status === 201 && created.data.id, created);
  const id = created.data.id;
  const afterCreate = await call('GET', '/reports/summary', { token: T, query: { period: 'MONTH' } });
  ok('el gasto del mes sube exactamente 123,45', cents(afterCreate.data.expense) - cents(summary.data.expense) === 12345, [summary.data.expense, afterCreate.data.expense]);
  ok('el saldo baja exactamente 123,45', cents(summary.data.balance) - cents(afterCreate.data.balance) === 12345, [summary.data.balance, afterCreate.data.balance]);
  const patched = await call('PATCH', `/transactions/${id}`, { token: T, body: { amount: '200.00', description: 'Prueba E2E editada' } });
  ok('PATCH -> 200 con el monto nuevo', patched.status === 200 && cents(patched.data.amount) === 20000 && patched.data.description === 'Prueba E2E editada', patched);
  const fetched = await call('GET', `/transactions/${id}`, { token: T });
  ok('GET /transactions/{id} refleja la edición', fetched.status === 200 && cents(fetched.data.amount) === 20000, fetched);

  const negative = await call('POST', '/transactions', { token: T, body: { description: 'x', amount: '-5', type: 'EXPENSE', categoryId: expenseCategory.id, occurredAt: now() } });
  ok('monto negativo -> 400 con fields.amount', negative.status === 400 && negative.data?.fields?.amount, negative);
  const threeDecimals = await call('POST', '/transactions', { token: T, body: { description: 'x', amount: '1.001', type: 'EXPENSE', categoryId: expenseCategory.id, occurredAt: now() } });
  ok('monto con tres decimales -> 400', threeDecimals.status === 400, threeDecimals);
  const empty = await call('POST', '/transactions', { token: T, body: {} });
  ok('cuerpo vacío -> 400 con varios fields', empty.status === 400 && Object.keys(empty.data?.fields ?? {}).length >= 3, empty);
  const wrongKind = await call('POST', '/transactions', { token: T, body: { description: 'x', amount: '10', type: 'EXPENSE', categoryId: incomeCategory.id, occurredAt: now() } });
  ok('gasto en una categoría de ingresos -> 4xx', wrongKind.status >= 400 && wrongKind.status < 500, wrongKind);
  if (wrongKind.status === 201) await call('DELETE', `/transactions/${wrongKind.data.id}`, { token: T });
  const notANumber = await call('POST', '/transactions', { token: T, body: { description: 'x', amount: 'abc', type: 'EXPENSE', categoryId: expenseCategory.id, occurredAt: now() } });
  ok('monto que no es número -> 400 con fields.amount', notANumber.status === 400 && notANumber.data?.fields?.amount, notANumber);
  const broken = await fetch(API + '/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + T }, body: '{ roto' });
  ok('JSON roto -> 400, no 500', broken.status === 400, broken.status);

  const deleted = await call('DELETE', `/transactions/${id}`, { token: T });
  ok('DELETE -> 204', deleted.status === 204, deleted);
  ok('GET tras borrar -> 404', (await call('GET', `/transactions/${id}`, { token: T })).status === 404);
  const restored = await call('GET', '/reports/summary', { token: T, query: { period: 'MONTH' } });
  ok('el resumen vuelve al valor inicial', cents(restored.data.expense) === cents(summary.data.expense) && cents(restored.data.balance) === cents(summary.data.balance));
  const notUuid = await call('GET', '/transactions/no-es-uuid', { token: T });
  ok('id que no es UUID -> 400, no 500', notUuid.status === 400, notUuid);

  section = 'rutas y métodos';
  ok('ruta inexistente -> 404', (await call('GET', '/no-existe', { token: T })).status === 404);
  ok('método no admitido -> 405', (await call('PUT', '/transactions', { token: T, body: {} })).status === 405);

  section = 'aislamiento entre usuarios';
  const stamp = Date.now();
  const email = `prueba.e2e.${stamp}@fluxia.test`;
  const registered = await call('POST', '/auth/register', { body: { fullName: 'Prueba Automática', email, password: 'Clave1234!' } });
  ok('registro de usuario nuevo -> sesión abierta', (registered.status === 200 || registered.status === 201) && registered.data.accessToken, registered);
  const U = registered.data.accessToken;
  const duplicate = await call('POST', '/auth/register', { body: { fullName: 'Otra', email, password: 'Clave1234!' } });
  ok('correo repetido -> 409 con mensaje', duplicate.status === 409 && userMessage(duplicate), duplicate);
  const shortPassword = await call('POST', '/auth/register', { body: { fullName: 'Otra', email: `x${stamp}@fluxia.test`, password: '123' } });
  ok('contraseña corta -> 400 con fields.password', shortPassword.status === 400 && shortPassword.data?.fields?.password, shortPassword);
  const demoTransaction = page.data.items[0].id;
  ok('leer un movimiento ajeno -> 404, no 403', (await call('GET', `/transactions/${demoTransaction}`, { token: U })).status === 404);
  ok('editar un movimiento ajeno -> 404', (await call('PATCH', `/transactions/${demoTransaction}`, { token: U, body: { amount: '1.00' } })).status === 404);
  ok('borrar un movimiento ajeno -> 404', (await call('DELETE', `/transactions/${demoTransaction}`, { token: U })).status === 404);
  ok('el movimiento del demo sigue intacto', (await call('GET', `/transactions/${demoTransaction}`, { token: T })).status === 200);
  const newSummary = await call('GET', '/reports/summary', { token: U, query: { period: 'MONTH' } });
  ok('usuario nuevo: saldo 0 y tasa de ahorro null, no 0 %', newSummary.status === 200 && cents(newSummary.data.balance) === 0 && newSummary.data.savingsRate === null, newSummary.data);
  ok('usuario nuevo: sin movimientos', (await call('GET', '/transactions', { token: U })).data.totalItems === 0);
  const newAccounts = await call('GET', '/accounts', { token: U });
  ok('usuario nuevo: tiene su cuenta inicial', newAccounts.status === 200 && newAccounts.data.length === 1, newAccounts);

  section = 'categorías propias';
  const ownCategory = await call('POST', '/categories', { token: U, body: { name: 'Mascotas E2E', kind: 'EXPENSE', icon: '🐾', color: '#AA66FF' } });
  ok('crear una categoría propia -> 201', ownCategory.status === 201 || ownCategory.status === 200, ownCategory);
  ok('color inválido -> 400', (await call('POST', '/categories', { token: U, body: { name: 'Mal color', kind: 'EXPENSE', color: 'rojo' } })).status === 400);
  if (ownCategory.data?.id) {
    ok('borrar la categoría propia -> 204', (await call('DELETE', `/categories/${ownCategory.data.id}`, { token: U })).status === 204);
  }
  const systemDelete = await call('DELETE', `/categories/${expenseCategory.id}`, { token: U });
  ok('borrar una categoría del sistema -> 4xx', systemDelete.status >= 400 && systemDelete.status < 500, systemDelete);

  section = 'sesión';
  const firstRefresh = registered.data.refreshToken;
  const refreshed = await call('POST', '/auth/refresh', { body: { refreshToken: firstRefresh } });
  ok('refresh -> par nuevo', refreshed.status === 200 && refreshed.data.accessToken && refreshed.data.refreshToken !== firstRefresh, refreshed);
  ok('reusar el refresh viejo -> 401 (rotación)', (await call('POST', '/auth/refresh', { body: { refreshToken: firstRefresh } })).status === 401);
  const loggedOut = await call('POST', '/auth/logout', { body: { refreshToken: refreshed.data.refreshToken } });
  ok('logout -> 2xx', loggedOut.status >= 200 && loggedOut.status < 300, loggedOut);
  ok('refresh tras logout -> 401', (await call('POST', '/auth/refresh', { body: { refreshToken: refreshed.data.refreshToken } })).status === 401);

  section = 'contraseñas';
  const U2 = (await call('POST', '/auth/login', { body: { email, password: 'Clave1234!' } })).data.accessToken;
  const wrongCurrent = await call('POST', '/me/password', { token: U2, body: { currentPassword: 'equivocada', newPassword: 'Nueva1234!' } });
  ok('cambiar con la contraseña actual mal -> 4xx con mensaje', wrongCurrent.status >= 400 && wrongCurrent.status < 500 && userMessage(wrongCurrent), wrongCurrent);
  const changed = await call('POST', '/me/password', { token: U2, body: { currentPassword: 'Clave1234!', newPassword: 'Nueva1234!' } });
  ok('cambiar la contraseña -> 2xx', changed.status >= 200 && changed.status < 300, changed);
  const withNew = await call('POST', '/auth/login', { body: { email, password: 'Nueva1234!' } });
  ok('login con la contraseña nueva -> 200', withNew.status === 200);
  ok('login con la vieja -> 401', (await call('POST', '/auth/login', { body: { email, password: 'Clave1234!' } })).status === 401);

  const forgot = await call('POST', '/auth/forgot-password', { body: { email } });
  ok('forgot-password -> 200 con devToken (perfil dev)', forgot.status === 200 && forgot.data.devToken, forgot);
  const ghost = await call('POST', '/auth/forgot-password', { body: { email: `nadie.${stamp}@fluxia.test` } });
  ok('correo inexistente -> la misma respuesta, sin token', ghost.status === 200 && !ghost.data.devToken && ghost.data.message === forgot.data.message, ghost);
  const reset = await call('POST', '/auth/reset-password', { body: { token: forgot.data.devToken, newPassword: 'Reset1234!' } });
  ok('reset-password -> 2xx', reset.status >= 200 && reset.status < 300, reset);
  ok('reusar el código de recuperación -> 4xx', (await call('POST', '/auth/reset-password', { body: { token: forgot.data.devToken, newPassword: 'Otra1234!' } })).status >= 400);
  ok('el reset cierra las sesiones abiertas', (await call('POST', '/auth/refresh', { body: { refreshToken: withNew.data.refreshToken } })).status === 401);
  const afterReset = await call('POST', '/auth/login', { body: { email, password: 'Reset1234!' } });
  ok('login con la contraseña restablecida -> 200', afterReset.status === 200);
  const renamed = await call('PATCH', '/me', { token: afterReset.data.accessToken, body: { fullName: 'Prueba Renombrada' } });
  ok('PATCH /me -> nombre e iniciales nuevos', renamed.status === 200 && renamed.data.fullName === 'Prueba Renombrada' && renamed.data.initials === 'PR', renamed);

  section = 'asistente de IA';
  const chat = await call('POST', '/ai/chat', { token: T, body: { question: '¿En qué gasté más este mes?', history: [] } });
  console.log(`      /ai/chat -> ${chat.status} ${chat.data?.code ?? ''} ${(chat.data?.message ?? chat.data?.answer ?? '').slice(0, 160)}`);
  ok('el chat responde (IA activa) o avisa con 503 (IA apagada)', (chat.status === 200 && chat.data.answer) || (chat.status === 503 && userMessage(chat)), chat);
  ok('pregunta vacía -> 400', (await call('POST', '/ai/chat', { token: T, body: { question: '' } })).status === 400);

  section = 'app web, documentación y CORS';
  // Como la pide un navegador: sin Accept JSON, que con razón da 406.
  const app = await fetch(BASE + '/');
  ok('el backend entrega la app web en /', app.status === 200 && (await app.text()).includes('<title>Flux IA</title>'), app.status);
  ok('/docs responde', [200, 301, 302].includes((await call('GET', '/docs', { raw: true })).status));
  const spec = await call('GET', '/api-docs', { raw: true });
  ok('/api-docs -> especificación OpenAPI', spec.status === 200 && spec.data.openapi, spec.status);
  const liveServer = await fetch(API + '/auth/login', { method: 'OPTIONS', headers: { Origin: 'http://127.0.0.1:5500', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type' } });
  ok('CORS permite Live Server (127.0.0.1:5500)', liveServer.headers.get('access-control-allow-origin') === 'http://127.0.0.1:5500', liveServer.headers.get('access-control-allow-origin'));
  const foreign = await fetch(API + '/auth/login', { method: 'OPTIONS', headers: { Origin: 'http://malicioso.example', 'Access-Control-Request-Method': 'POST' } });
  ok('CORS rechaza un origen ajeno', !foreign.headers.get('access-control-allow-origin'), foreign.headers.get('access-control-allow-origin'));

  const failed = results.filter((r) => !r.pass);
  console.log(`\n${results.length - failed.length}/${results.length} pruebas de la API pasaron.`);
  if (failed.length) {
    failed.forEach((f) => console.log(`  - [${f.section}] ${f.name}`));
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error('La prueba se detuvo:', error.message);
  process.exitCode = 2;
});
