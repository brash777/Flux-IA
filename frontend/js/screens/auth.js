/* =====================================================================
   Ingresar, crear cuenta y recuperar la contraseña.

   Tres rutas, una pantalla: #/ingresar, #/crear-cuenta y #/recuperar.
   La validación la hace el backend; sus mensajes se muestran tal cual,
   arriba el general y debajo de cada campo el suyo.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon } from '../dom.js';
import { takeFlash, setFlash } from '../flash.js';
import { clearFormErrors, field, formAlert, passwordField, setBusy, showFormErrors } from '../ui.js';

const APP_ICON = 'assets/icons/app-icon-b-tarjetas.svg';

const COPY = {
  ingresar: { title: 'Hola de nuevo', text: 'Ingresa para ver tus finanzas.' },
  'crear-cuenta': { title: 'Crea tu cuenta', text: 'Solo necesitas tu nombre, tu correo y una contraseña.' },
  recuperar: { title: 'Recupera tu contraseña', text: 'Te enviamos un código para elegir una nueva.' },
};

export function mount(view, { navigate, route }) {
  view.classList.add('app-bg');
  const cleanups = [];
  const on = (node, event, handler) => {
    node.addEventListener(event, handler);
    cleanups.push(() => node.removeEventListener(event, handler));
  };

  const back = h('button', { class: 'btn btn--icon', type: 'button', 'aria-label': route === 'recuperar' ? 'Volver a ingresar' : 'Volver a la bienvenida' }, icon('arrow-left'));
  on(back, 'click', () => navigate(route === 'recuperar' ? 'ingresar' : 'bienvenida'));

  const flash = takeFlash();
  const content = route === 'recuperar' ? recoverForms(navigate, on) : signInForm(route, navigate, on);

  view.append(h('main', { class: 'screen screen--auth' },
    h('div', { class: 'screen-header' },
      back,
      h('p', { class: 'welcome-brand' }, h('img', { src: APP_ICON, alt: '' }), 'Flux IA')),
    h('div', { class: 'auth-head' },
      h('h1', { class: 'display' }, COPY[route].title),
      h('p', {}, COPY[route].text)),
    flash ? h('p', { class: 'note', role: 'status', style: { marginBottom: 'var(--space-4)' } }, flash) : null,
    content));

  return { unmount: () => cleanups.forEach((fn) => fn()) };
}

/* --- Ingresar / crear cuenta --------------------------------------- */

function signInForm(route, navigate, on) {
  const isLogin = route === 'ingresar';

  const tab = (name, label) => {
    const button = h('button', { class: 'segmented__option', type: 'button', role: 'tab', 'aria-selected': String(route === name) }, label);
    on(button, 'click', () => navigate(name));
    return button;
  };

  const submit = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, isLogin ? 'Ingresar' : 'Crear cuenta');
  const form = h('form', { class: 'form', novalidate: true },
    formAlert(),
    isLogin ? null : field({ label: 'Nombre', name: 'fullName', attrs: { autocomplete: 'name', required: true } }),
    field({ label: 'Correo', name: 'email', type: 'email', attrs: { autocomplete: 'email', inputmode: 'email', required: true, placeholder: 'nombre@correo.com' } }),
    passwordField({ label: 'Contraseña', name: 'password', autocomplete: isLogin ? 'current-password' : 'new-password' }),
    isLogin ? null : h('span', { class: 'field__hint' }, 'Entre 8 y 72 caracteres.'),
    submit);

  on(form, 'submit', async (event) => {
    event.preventDefault();
    clearFormErrors(form);
    const data = Object.fromEntries(new FormData(form));
    setBusy(submit, true, isLogin ? 'Ingresando…' : 'Creando tu cuenta…');
    try {
      if (isLogin) await api.login(data.email.trim(), data.password);
      else await api.register({ fullName: data.fullName.trim(), email: data.email.trim(), password: data.password });
      navigate('inicio');
    } catch (error) {
      setBusy(submit, false);
      showFormErrors(form, error);
    }
  });

  const forgot = h('button', { class: 'btn btn--ghost', type: 'button' }, '¿Olvidaste tu contraseña?');
  on(forgot, 'click', () => navigate('recuperar'));

  return h('div', {},
    h('div', { class: 'glass glass--strong card form' },
      h('div', { class: 'segmented', role: 'tablist', 'aria-label': 'Acceso' }, tab('ingresar', 'Ingresar'), tab('crear-cuenta', 'Crear cuenta')),
      form),
    isLogin ? h('div', { class: 'auth-links' }, forgot) : null);
}

/* --- Recuperar: pedir el código y elegir la contraseña nueva -------- */

function recoverForms(navigate, on) {
  const container = h('div', { class: 'stack' });

  const requestSubmit = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Enviar código');
  const requestForm = h('form', { class: 'glass glass--strong card form', novalidate: true },
    formAlert(),
    field({ label: 'Correo de tu cuenta', name: 'email', type: 'email', attrs: { autocomplete: 'email', inputmode: 'email', required: true, placeholder: 'nombre@correo.com' } }),
    requestSubmit);

  const resetSubmit = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Cambiar contraseña');
  const resetForm = h('form', { class: 'glass glass--strong card form', novalidate: true, hidden: true },
    h('p', { class: 'h2', style: { margin: '0' } }, 'Elige una contraseña nueva'),
    formAlert(),
    field({ label: 'Código', name: 'token', attrs: { autocomplete: 'one-time-code', required: true, spellcheck: 'false' } }),
    passwordField({ label: 'Contraseña nueva', name: 'newPassword', autocomplete: 'new-password' }),
    h('span', { class: 'field__hint' }, 'Entre 8 y 72 caracteres. Al cambiarla se cierran todas tus sesiones.'),
    resetSubmit);

  const sent = h('p', { class: 'note', role: 'status', hidden: true });
  const haveCode = h('button', { class: 'btn btn--ghost', type: 'button' }, 'Ya tengo un código');
  on(haveCode, 'click', () => {
    resetForm.hidden = false;
    haveCode.hidden = true;
    resetForm.querySelector('[name="token"]').focus();
  });

  on(requestForm, 'submit', async (event) => {
    event.preventDefault();
    clearFormErrors(requestForm);
    setBusy(requestSubmit, true, 'Enviando…');
    try {
      const email = new FormData(requestForm).get('email').trim();
      const response = await api.forgotPassword(email);
      // Mientras no haya servicio de correo, el perfil dev devuelve el
      // código en la respuesta: se muestra y se completa solo.
      const devToken = response?.devToken;
      sent.replaceChildren(
        h('span', {}, response?.message ?? 'Si el correo está registrado, recibirás un código.'),
        devToken ? h('span', { style: { display: 'block', marginTop: 'var(--space-2)' } },
          'Modo desarrollo: todavía no hay servicio de correo, así que el servidor devolvió el código y ya está cargado abajo.') : null);
      sent.hidden = false;
      resetForm.hidden = false;
      haveCode.hidden = true;
      if (devToken) resetForm.querySelector('[name="token"]').value = devToken;
      resetForm.querySelector(devToken ? '[name="newPassword"]' : '[name="token"]').focus();
    } catch (error) {
      showFormErrors(requestForm, error);
    } finally {
      setBusy(requestSubmit, false);
    }
  });

  on(resetForm, 'submit', async (event) => {
    event.preventDefault();
    clearFormErrors(resetForm);
    const data = Object.fromEntries(new FormData(resetForm));
    setBusy(resetSubmit, true, 'Cambiando…');
    try {
      await api.resetPassword(data.token.trim(), data.newPassword);
      setFlash('Listo: tu contraseña cambió. Ingresa con la nueva.');
      navigate('ingresar');
    } catch (error) {
      setBusy(resetSubmit, false);
      showFormErrors(resetForm, error);
    }
  });

  container.append(requestForm, sent, resetForm, h('div', { class: 'auth-links' }, haveCode));
  return container;
}
