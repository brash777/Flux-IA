// =====================================================================
// Prueba de punta a punta de la interfaz de Flux IA, en Chrome y con
// tamaño de celular, contra el servidor corriendo con el perfil dev.
//
// Uso: node ui.mjs [http://localhost:8080] [ancho, 390 por omisión]
//
// Usa el Chrome instalado (no descarga navegadores). Para otro Chrome:
// CHROME_PATH=/ruta/al/chrome node ui.mjs. Deja capturas de cada pantalla
// en capturas/<ancho>/.
// =====================================================================

import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const BASE = process.argv[2] ?? 'http://localhost:8080';
const WIDTH = Number(process.argv[3] ?? 390);
// fileURLToPath y no .pathname: la carpeta del proyecto puede tener
// espacios, y .pathname los deja como %20.
const SHOTS = fileURLToPath(new URL(`./capturas/${WIDTH}/`, import.meta.url));
const DEMO = { email: 'diego.f@fluxia.app', password: 'Flux2026!' };
mkdirSync(SHOTS, { recursive: true });

const results = [];
function ok(name, condition, detail = '') {
  results.push({ name, pass: Boolean(condition) });
  console.log(`${condition ? 'PASS' : 'FAIL'}  ${name}${!condition && detail ? '  -> ' + String(detail).slice(0, 300) : ''}`);
}

const clean = (text) => (text ?? '').replace(/\s+/g, ' ').trim();
// El mismo formato que frontend/js/format.js, para comparar lo que se ve
// con lo que manda la API.
const ars = (n) => clean(new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(Number(n))));

async function demoSummary() {
  const login = await fetch(`${BASE}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(DEMO) });
  const { accessToken } = await login.json();
  const summary = await fetch(`${BASE}/api/v1/reports/summary?period=MONTH`, { headers: { Authorization: 'Bearer ' + accessToken } });
  return summary.json();
}

const browser = await chromium.launch(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : { channel: 'chrome' });
const context = await browser.newContext({ viewport: { width: WIDTH, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true, locale: 'es-AR' });
const page = await context.newPage();

// --- Qué cuenta como error ----------------------------------------------
// Se ignora solo lo provocado a propósito: el 401 de la contraseña mala,
// el 503 del chat sin IA y el corte de red simulado.
const consoleErrors = [];
const serverErrors = [];
const answered = new Set();
let expected401 = false;
let simulatingOffline = false;
page.on('response', (r) => {
  answered.add(r.request().method() + r.url());
  if (r.status() >= 500 && !(r.status() === 503 && r.url().endsWith('/ai/chat'))) serverErrors.push(`${r.status()} ${r.url()}`);
});
page.on('console', (m) => {
  const text = m.text();
  if (m.type() !== 'error') return;
  if ((expected401 && text.includes('401')) || text.includes('status of 503') || text.includes('ERR_INTERNET_DISCONNECTED')) return;
  consoleErrors.push(text);
});
page.on('pageerror', (e) => consoleErrors.push('pageerror: ' + e.message));
// Chrome marca como abortada una petición ya respondida (un 204, por
// ejemplo); solo cuenta la que no tuvo respuesta.
page.on('requestfailed', (r) => {
  if (!simulatingOffline && !answered.has(r.method() + r.url())) consoleErrors.push(`sin respuesta: ${r.method()} ${r.url()} ${r.failure()?.errorText}`);
});

// --- Ayudas ----------------------------------------------------------------
const hash = () => page.evaluate(() => location.hash);
// Durante una transición conviven la pantalla que sale y la que entra; se
// espera a que quede una sola.
const settle = () => page.waitForFunction(() => document.querySelectorAll('#outlet > .view').length === 1, null, { timeout: 10000 });
async function go(route) {
  await page.goto(`${BASE}/#/${route}`);
  await settle();
}
async function waitRoute(route) {
  await page.waitForFunction((h) => location.hash === h, `#/${route}`, { timeout: 15000 });
  await settle();
}
const tab = async (name) => {
  await page.locator(`.nav-bar a[data-tab=${name}]`).click();
  await waitRoute(name);
};
const shot = (name) => page.screenshot({ path: `${SHOTS}${name}.png` });
const toast = async () => clean(await page.locator('.toast').last().textContent({ timeout: 8000 }));
async function noHorizontalScroll(label) {
  const overflow = await page.evaluate(() => {
    const doc = document.documentElement;
    const screen = document.querySelector('.screen');
    // Lo recortado con overflow-x hidden no se puede deslizar: no cuenta.
    const clipped = screen && ['hidden', 'clip'].includes(getComputedStyle(screen).overflowX);
    return { page: doc.scrollWidth - doc.clientWidth, screen: screen && !clipped ? screen.scrollWidth - screen.clientWidth : 0 };
  });
  ok(`${label}: sin scroll horizontal a ${WIDTH} px`, overflow.page <= 0 && overflow.screen <= 0, JSON.stringify(overflow));
}

try {
  // --- Bienvenida e ingreso --------------------------------------------
  await page.goto(BASE + '/', { waitUntil: 'networkidle' });
  ok('sin sesión abre la bienvenida', (await hash()) === '#/bienvenida', await hash());
  await page.waitForTimeout(6000); // animación de apertura
  await shot('01-bienvenida');
  await noHorizontalScroll('bienvenida');

  await go('ingresar');
  expected401 = true;
  await page.locator('input[name=email]').fill(DEMO.email);
  await page.locator('input[name=password]').fill('incorrecta');
  await page.locator('form button[type=submit]').click();
  const alert = page.locator('[data-form-alert]');
  await alert.waitFor({ state: 'visible', timeout: 8000 });
  ok('contraseña mala: se ve el mensaje del backend, con tildes', clean(await alert.textContent()).includes('El correo o la contraseña no son correctos'), await alert.textContent());
  await page.locator('input[name=password]').fill(DEMO.password);
  await page.locator('form button[type=submit]').click();
  await waitRoute('inicio');
  expected401 = false;
  ok('el login lleva a inicio', true);

  // --- Inicio: lo que se ve es lo que dice la API ------------------------
  const before = await demoSummary();
  await page.locator('article.balance-card .sr-only').first().waitFor({ timeout: 10000 });
  await page.waitForTimeout(2500); // conteo de las cifras
  ok('el saldo de inicio es el de la API', clean(await page.locator('article.balance-card .sr-only').first().textContent()) === ars(before.balance));
  ok('la cifra animada termina exactamente en el valor de la API', clean(await page.locator('article.balance-card .balance-card__amount').textContent()) === ars(before.balance));
  await shot('02-inicio');
  await noHorizontalScroll('inicio');

  await page.locator('[aria-label="Nuevo movimiento"]').first().click();
  await page.locator('.sheet input[name=amount]').waitFor();
  await page.locator('.sheet input[name=amount]').fill('123,45');
  await page.locator('.sheet input[name=description]').fill('Prueba UI E2E');
  await shot('03-nuevo-movimiento');
  await page.locator('.sheet button[type=submit]').click();
  ok('crear: aviso «Movimiento guardado»', (await toast()).includes('Movimiento guardado'));
  await page.locator('.tx-list', { hasText: 'Prueba UI E2E' }).waitFor({ timeout: 10000 });
  ok('el movimiento nuevo aparece en Recientes', true);
  await page.waitForTimeout(2500);
  const after = await demoSummary();
  ok('la API bajó el saldo exactamente 123,45', Math.round((before.balance - after.balance) * 100) === 12345, `${before.balance} -> ${after.balance}`);
  ok('inicio muestra el saldo nuevo', clean(await page.locator('article.balance-card .sr-only').first().textContent()) === ars(after.balance));

  // --- Movimientos: buscar, editar y borrar ------------------------------
  await tab('movimientos');
  await page.locator('.tx-list').first().waitFor({ timeout: 10000 });
  await page.waitForTimeout(1200);
  await shot('04-movimientos');
  await noHorizontalScroll('movimientos');
  await page.locator('input[name=search]').fill('Prueba UI');
  await page.waitForTimeout(1500);
  ok('la búsqueda encuentra exactamente uno', (await page.locator('.tx-list li').count()) === 1);
  await page.locator('.tx-list li', { hasText: 'Prueba UI E2E' }).first().click();
  await page.locator('.sheet input[name=amount]').waitFor();
  await page.locator('.sheet input[name=amount]').fill('200');
  await page.locator('.sheet button[type=submit]').click();
  ok('editar: aviso «Movimiento actualizado»', (await toast()).includes('Movimiento actualizado'));
  await page.waitForTimeout(1500);
  ok('los totales del filtro muestran el monto editado', clean(await page.locator('.totals').textContent()).includes(ars(200)));
  await page.locator('.tx-list li', { hasText: 'Prueba UI E2E' }).first().click();
  await page.getByRole('button', { name: 'Eliminar movimiento' }).click();
  await page.locator('.sheet').last().getByRole('button', { name: 'Eliminar', exact: true }).click();
  ok('borrar: aviso «Movimiento eliminado»', (await toast()).includes('Movimiento eliminado'));
  await page.waitForTimeout(1500);
  ok('tras borrar se ve el estado vacío del filtro', (await page.locator('.tx-list li').count()) === 0 && (await page.getByText('No hay movimientos con estos filtros').count()) === 1);
  ok('el saldo vuelve al original', (await demoSummary()).balance === before.balance);

  // --- Reportes, chat y perfil ---------------------------------------------
  await tab('reportes');
  await page.locator('.chart-card svg').first().waitFor({ timeout: 10000 });
  await page.waitForTimeout(2500);
  await shot('05-reportes');
  await noHorizontalScroll('reportes');
  for (const period of ['Trimestre', 'Año', 'Mes']) {
    await page.getByRole('button', { name: period, exact: true }).click();
    await page.waitForTimeout(1500);
    ok(`reportes: la pestaña ${period} carga sin error`, (await page.getByRole('button', { name: /Reintentar/ }).count()) === 0);
  }
  await page.locator('.screen').evaluate((el) => el.scrollTo(0, el.scrollHeight));
  await page.waitForTimeout(1500);
  await shot('06-reportes-tendencia');

  await tab('chat');
  await page.locator('input[name=question]').fill('¿En qué gasté más este mes?');
  ok('chat: al escribir en el celular se oculta la barra inferior', !(await page.locator('#nav').isVisible()));
  const composer = await page.locator('.chat-composer').boundingBox();
  ok('chat: al escribir, el campo baja hasta el borde', composer && 844 - (composer.y + composer.height) <= 12, JSON.stringify(composer));
  await page.locator('[aria-label=Enviar]').click();
  // La respuesta se escribe palabra por palabra: se espera a que termine.
  await page.waitForFunction(() => !document.querySelector('.chat-log .typing')
    && !document.querySelector('.chat-log[aria-busy=true]')
    && document.querySelectorAll('.bubble--ai').length >= 2, null, { timeout: 60000 });
  ok('chat: al enviar se cierra el teclado y vuelve la barra', await page.locator('#nav').isVisible());
  const chatText = clean(await page.locator('.chat-log').textContent());
  ok('el chat responde o explica que el asistente está apagado', chatText.includes('El asistente está apagado') || chatText.length > 160, chatText.slice(-200));
  ok('chat: la respuesta se ve con formato, sin asteriscos sueltos', !chatText.includes('**'), chatText.slice(-200));
  const tail = await page.evaluate(() => ({
    answer: document.querySelector('.chat-log > .bubble-row:last-child').getBoundingClientRect().bottom,
    composer: document.querySelector('.chat-composer').getBoundingClientRect().top,
  }));
  ok('chat: el final de la respuesta queda a la vista, sobre el campo', tail.answer <= tail.composer + 1, JSON.stringify(tail));
  await shot('07-chat');
  await noHorizontalScroll('chat');

  // --- Teclado de iPhone, simulado ------------------------------------------
  // iOS no achica la página al abrir el teclado: achica visualViewport. Se
  // reemplaza por uno falso para comprobar que la app se ajusta a la parte
  // que se ve (frontend/js/keyboard.js).
  const KEYBOARD = 336;
  const phone = await context.newPage();
  await phone.addInitScript(() => {
    sessionStorage.setItem('flux.launchShown', '1');
    const fake = new EventTarget();
    Object.assign(fake, { width: innerWidth, height: innerHeight, offsetTop: 0, offsetLeft: 0, pageTop: 0, pageLeft: 0, scale: 1 });
    Object.defineProperty(window, 'visualViewport', { configurable: true, get: () => fake });
    window.fakeKeyboard = (height) => {
      fake.height = height;
      fake.dispatchEvent(new Event('resize'));
    };
  });
  await phone.goto(`${BASE}/#/chat`);
  await phone.waitForFunction(() => location.hash === '#/chat' && document.querySelectorAll('#outlet > .view').length === 1, null, { timeout: 15000 });
  // Al terminar de entrar, la pantalla lleva el foco a su título
  // (router.js): se espera a eso, o le quitaría el foco al campo.
  await phone.waitForFunction(() => document.activeElement?.localName === 'h1', null, { timeout: 5000 });
  await phone.bringToFront();
  await phone.locator('input[name=question]').focus();
  await phone.waitForFunction(() => document.documentElement.classList.contains('is-typing'), null, { timeout: 5000 });
  await phone.evaluate((height) => window.fakeKeyboard(height), 844 - KEYBOARD);
  await phone.waitForFunction(() => document.documentElement.classList.contains('keyboard-open'), null, { timeout: 5000 });
  const open = await phone.evaluate(() => {
    const device = document.getElementById('device').getBoundingClientRect();
    return { top: device.top, height: device.height, composer: document.querySelector('.chat-composer').getBoundingClientRect().bottom };
  });
  ok('teclado: la app se ajusta a la parte que se ve, sin correrse hacia arriba', open.top === 0 && Math.round(open.height) === 844 - KEYBOARD, JSON.stringify(open));
  ok('teclado: el campo queda justo encima del teclado', open.composer <= 844 - KEYBOARD && open.composer >= 844 - KEYBOARD - 16, JSON.stringify(open));
  await phone.screenshot({ path: `${SHOTS}07b-chat-teclado.png` });
  await phone.evaluate(() => window.fakeKeyboard(innerHeight));
  await phone.locator('input[name=question]').blur();
  await phone.waitForFunction(() => !document.documentElement.classList.contains('keyboard-open'), null, { timeout: 5000 });
  ok('teclado: al cerrarse, la app vuelve a la pantalla completa', Math.round(await phone.evaluate(() => document.getElementById('device').getBoundingClientRect().height)) === 844);
  await phone.close();
  await page.bringToFront();

  await tab('perfil');
  await page.getByRole('button', { name: 'Cerrar sesión' }).waitFor({ timeout: 10000 });
  await page.waitForTimeout(1500);
  ok('el perfil muestra el correo del usuario', (await page.getByText(DEMO.email).count()) > 0);
  await shot('08-perfil');
  await noHorizontalScroll('perfil');
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await waitRoute('ingresar');
  ok('cerrar sesión vuelve a ingresar con aviso', (await page.getByText('Cerraste sesión').count()) === 1);

  // --- Registro, recarga y recuperación ------------------------------------
  const email = `ui.e2e.${Date.now()}@fluxia.test`;
  await go('crear-cuenta');
  await page.locator('input[name=fullName]').fill('Usuaria Prueba');
  await page.locator('input[name=email]').fill(email);
  await page.locator('input[name=password]').fill('Clave1234!');
  await page.locator('form button[type=submit]').click();
  await waitRoute('inicio');
  await page.getByText('Todavía no hay movimientos').first().waitFor({ timeout: 10000 });
  ok('registro: entra a inicio con el estado vacío', true);
  await page.waitForTimeout(1500);
  await shot('09-inicio-vacio');
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  ok('recargar no cierra la sesión', (await hash()) === '#/inicio', await hash());
  await tab('perfil');
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await waitRoute('ingresar');
  await go('recuperar');
  await page.locator('input[name=email]').fill(email);
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await page.locator('input[name=newPassword]').waitFor({ state: 'visible', timeout: 8000 });
  ok('recuperar: el código de desarrollo se carga solo', (await page.locator('input[name=token]').inputValue()).length > 10);
  await page.locator('input[name=newPassword]').fill('Nueva1234!');
  await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
  await waitRoute('ingresar');
  ok('recuperar: vuelve a ingresar con aviso', (await page.getByText('tu contraseña cambió').count()) === 1);
  await page.locator('input[name=email]').fill(email);
  await page.locator('input[name=password]').fill('Nueva1234!');
  await page.locator('form button[type=submit]').click();
  await waitRoute('inicio');
  ok('ingresa con la contraseña nueva', true);

  // --- Sin conexión: error con reintento -----------------------------------
  simulatingOffline = true;
  await page.route('**/api/v1/reports/summary**', (route) => route.abort('internetdisconnected'));
  await go('movimientos');
  await go('inicio');
  const retry = page.getByRole('button', { name: /Reintentar/ });
  await retry.waitFor({ timeout: 10000 });
  ok('sin conexión, inicio muestra el error con «Reintentar»', true);
  await shot('10-sin-conexion');
  await page.unroute('**/api/v1/reports/summary**');
  simulatingOffline = false;
  await retry.click();
  await page.locator('article.balance-card').waitFor({ timeout: 10000 });
  ok('«Reintentar» recupera la pantalla', true);
} catch (error) {
  ok('el recorrido terminó sin interrupciones', false, error.message);
  await shot('zz-fallo').catch(() => {});
} finally {
  ok('sin errores de consola ni peticiones sin respuesta', consoleErrors.length === 0, consoleErrors.join(' | '));
  ok('sin respuestas 5xx del servidor', serverErrors.length === 0, serverErrors.join(' | '));
  await browser.close();
  const failed = results.filter((r) => !r.pass);
  console.log(`\n${results.length - failed.length}/${results.length} pruebas de interfaz pasaron a ${WIDTH} px. Capturas en ${SHOTS}`);
  process.exitCode = failed.length ? 1 : 0;
}
