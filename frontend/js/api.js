/* =====================================================================
   Única capa que habla con el backend (CLAUDE.md). Ninguna pantalla
   llama a fetch.

   Sesión: el accessToken vive solo en memoria y el refreshToken en
   localStorage, para que recargar no cierre la sesión (decisión y riesgo
   explicados en CLAUDE.md). Ante un 401 se pide un token nuevo UNA vez y
   se reintenta; si el refresco también falla, la sesión se da por
   terminada y la app vuelve al login.
   ===================================================================== */

import { API_BASE } from './config.js';

const REFRESH_KEY = 'flux.refreshToken';

let accessToken = null;
let currentUser = null;
let refreshing = null;
const expiredListeners = new Set();

/** Error con el mensaje listo para mostrar al usuario. */
export class ApiError extends Error {
  constructor({ status = 0, code = 'UNKNOWN', message, fields = null }) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

const NETWORK_MESSAGE = 'No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';
const UNEXPECTED_MESSAGE = 'Algo salió mal. Inténtalo de nuevo en un momento.';

/* --- Almacenamiento: puede fallar (navegación privada estricta) ----- */

function readRefresh() {
  try { return localStorage.getItem(REFRESH_KEY); } catch { return null; }
}

function writeRefresh(token) {
  try {
    if (token) localStorage.setItem(REFRESH_KEY, token);
    else localStorage.removeItem(REFRESH_KEY);
  } catch {
    // Sin almacenamiento la sesión dura lo que la pestaña: es aceptable.
  }
}

function startSession(session) {
  accessToken = session.accessToken;
  currentUser = session.user;
  writeRefresh(session.refreshToken);
  return session.user;
}

function endSession() {
  accessToken = null;
  currentUser = null;
  writeRefresh(null);
}

export function hasSession() {
  return Boolean(accessToken || readRefresh());
}

export function cachedUser() {
  return currentUser;
}

/** La app se suscribe para volver al login cuando la sesión vence. */
export function onSessionExpired(listener) {
  expiredListeners.add(listener);
}

/* --- Peticiones ----------------------------------------------------- */

async function send(method, path, { body, auth = true, query } = {}) {
  const url = new URL(API_BASE + path);
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
  }
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError({ code: 'NETWORK', message: NETWORK_MESSAGE });
  }
  if (response.status === 204) return null;

  let data = null;
  try { data = await response.json(); } catch { /* respuesta sin JSON */ }

  if (!response.ok) {
    // El backend ya escribe sus mensajes para el usuario; si no hay uno
    // (un 500 o un proxy caído), se muestra un texto propio, nunca el
    // detalle técnico.
    throw new ApiError({
      status: response.status,
      code: data?.code ?? 'HTTP_' + response.status,
      message: data?.message && data.code !== 'INTERNAL_ERROR' ? data.message : UNEXPECTED_MESSAGE,
      fields: data?.fields ?? null,
    });
  }
  return data;
}

/* Un solo refresco a la vez: si tres pantallas reciben 401 juntas, las
   tres esperan el mismo token nuevo. */
function refreshSession() {
  if (!refreshing) {
    const refreshToken = readRefresh();
    refreshing = (refreshToken
      ? send('POST', '/auth/refresh', { body: { refreshToken }, auth: false }).then(startSession)
      : Promise.reject(new ApiError({ status: 401, code: 'NO_SESSION', message: 'Tu sesión terminó. Ingresa de nuevo.' })))
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

function expire() {
  endSession();
  expiredListeners.forEach((listener) => listener());
  return new ApiError({ status: 401, code: 'SESSION_EXPIRED', message: 'Tu sesión venció. Ingresa de nuevo.' });
}

/* Renueva la sesión; si el servidor la rechaza, la da por terminada. Un
   fallo de red no cierra la sesión: se puede reintentar. */
async function renewOrExpire() {
  try {
    await refreshSession();
  } catch (error) {
    if (error.status === 401) throw expire();
    throw error;
  }
}

async function request(method, path, options = {}) {
  let renewed = false;
  if (!accessToken && readRefresh()) {
    await renewOrExpire(); // recién recargada la página: solo hay refreshToken
    renewed = true;
  }
  try {
    return await send(method, path, options);
  } catch (error) {
    if (error.status !== 401) throw error;
    if (renewed) throw expire(); // el token recién emitido ya no sirve
    await renewOrExpire();
    try {
      return await send(method, path, options);
    } catch (retryError) {
      if (retryError.status === 401) throw expire();
      throw retryError;
    }
  }
}

/* --- Autenticación -------------------------------------------------- */

export async function login(email, password) {
  return startSession(await send('POST', '/auth/login', { body: { email, password }, auth: false }));
}

export async function register({ fullName, email, password }) {
  return startSession(await send('POST', '/auth/register', { body: { fullName, email, password }, auth: false }));
}

export async function logout() {
  const refreshToken = readRefresh();
  endSession();
  if (!refreshToken) return;
  try {
    await send('POST', '/auth/logout', { body: { refreshToken }, auth: false });
  } catch {
    // La sesión local ya se cerró; si el servidor no respondió, el token
    // vence solo.
  }
}

/** En el perfil dev la respuesta trae devToken: no hay correo todavía. */
export function forgotPassword(email) {
  return send('POST', '/auth/forgot-password', { body: { email }, auth: false });
}

export function resetPassword(token, newPassword) {
  return send('POST', '/auth/reset-password', { body: { token, newPassword }, auth: false });
}

/* --- Perfil, cuentas y categorías ---------------------------------- */

export async function me() {
  currentUser = await request('GET', '/me');
  return currentUser;
}

export async function updateProfile(fullName) {
  currentUser = await request('PATCH', '/me', { body: { fullName } });
  return currentUser;
}

export const changePassword = (currentPassword, newPassword) =>
  request('POST', '/me/password', { body: { currentPassword, newPassword } });

export const accounts = () => request('GET', '/accounts');
export const categories = () => request('GET', '/categories');

/* --- Movimientos ---------------------------------------------------- */

export const transactions = (filters) => request('GET', '/transactions', { query: filters });
export const recentTransactions = (limit = 5) => request('GET', '/transactions/recent', { query: { limit } });
export const createTransaction = (data) => request('POST', '/transactions', { body: data });
export const updateTransaction = (id, data) => request('PATCH', `/transactions/${encodeURIComponent(id)}`, { body: data });
export const deleteTransaction = (id) => request('DELETE', `/transactions/${encodeURIComponent(id)}`);

/* --- Reportes ------------------------------------------------------- */

export const summary = (period = 'MONTH') => request('GET', '/reports/summary', { query: { period } });
export const byCategory = (period = 'MONTH', type = 'EXPENSE') => request('GET', '/reports/by-category', { query: { period, type } });
export const monthlyTrend = (months = 7) => request('GET', '/reports/monthly-trend', { query: { months } });

/* --- Asistente ------------------------------------------------------ */

export const chat = (question, history) => request('POST', '/ai/chat', { body: { question, history } });
