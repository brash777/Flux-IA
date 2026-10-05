/* =====================================================================
   Perfil: datos del usuario, cambio de contraseña, cuentas y cierre de
   sesión.

   Las cuentas se muestran con nombre, tipo y moneda: la API no tiene saldo
   por cuenta, así que no se muestra ninguno.
   ===================================================================== */

import * as api from '../api.js';
import { h, icon, render } from '../dom.js';
import { setFlash } from '../flash.js';
import * as motion from '../motion.js';
import { clearFormErrors, errorCard, field, formAlert, passwordField, setBusy, showFormErrors, skeletonBlock, toast } from '../ui.js';

const ACCOUNT_TYPES = { BANK: 'Banco', CASH: 'Efectivo', CARD: 'Tarjeta' };

export function mount(view, { navigate }) {
  view.classList.add('app-bg');
  const fx = motion.scope(view);
  let alive = true;

  const content = h('div', {});
  view.append(h('main', { class: 'screen' },
    h('header', { class: 'screen-header' }, h('h1', { class: 'h1' }, 'Perfil')),
    content));

  async function load() {
    render(content, skeletonBlock(104, 'Cargando tu perfil…'), h('div', { style: { height: 'var(--space-4)' } }), skeletonBlock(220));
    try {
      const [user, accounts] = await Promise.all([api.me(), api.accounts()]);
      if (!alive) return;
      show(user, accounts);
    } catch (error) {
      if (!alive || error.code === 'SESSION_EXPIRED') return;
      render(content, errorCard(error, load));
    }
  }

  function show(user, accounts) {
    const avatar = h('span', { class: 'avatar', 'aria-hidden': 'true' }, user.initials);
    const name = h('p', { class: 'profile-name' }, user.fullName);
    const head = h('section', { class: 'glass glass--strong profile-head', 'aria-label': 'Tu cuenta' },
      avatar, h('div', { style: { minWidth: '0' } }, name, h('p', { class: 'profile-email' }, user.email)));

    /* Datos personales */
    const saveName = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Guardar');
    const nameForm = h('form', { class: 'glass glass--strong card form', novalidate: true },
      formAlert(),
      field({ label: 'Nombre', name: 'fullName', value: user.fullName, attrs: { autocomplete: 'name', maxlength: '120' } }),
      field({ label: 'Correo', name: 'email', type: 'email', value: user.email, hint: 'El correo no se puede cambiar.', attrs: { disabled: true } }),
      saveName);
    nameForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      clearFormErrors(nameForm);
      setBusy(saveName, true, 'Guardando…');
      try {
        const updated = await api.updateProfile(new FormData(nameForm).get('fullName').trim());
        name.textContent = updated.fullName;
        avatar.textContent = updated.initials;
        toast('Datos guardados');
      } catch (error) {
        showFormErrors(nameForm, error);
      } finally {
        setBusy(saveName, false);
      }
    });

    /* Contraseña: el backend cierra todas las sesiones al cambiarla, así
       que después se vuelve al login a propósito. */
    const savePassword = h('button', { class: 'btn btn--primary btn--block', type: 'submit' }, 'Cambiar contraseña');
    const passwordForm = h('form', { class: 'glass glass--strong card form', novalidate: true },
      formAlert(),
      // Para que el gestor de contraseñas sepa de qué cuenta es la clave.
      h('input', { type: 'text', name: 'username', autocomplete: 'username', value: user.email, readonly: true, hidden: true }),
      passwordField({ label: 'Contraseña actual', name: 'currentPassword', autocomplete: 'current-password' }),
      passwordField({ label: 'Contraseña nueva', name: 'newPassword', autocomplete: 'new-password' }),
      h('span', { class: 'field__hint' }, 'Entre 8 y 72 caracteres. Al cambiarla se cierran tus sesiones y vuelves a ingresar.'),
      savePassword);
    passwordForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      clearFormErrors(passwordForm);
      const data = Object.fromEntries(new FormData(passwordForm));
      setBusy(savePassword, true, 'Cambiando…');
      try {
        await api.changePassword(data.currentPassword, data.newPassword);
        await api.logout();
        setFlash('Tu contraseña cambió. Ingresa con la nueva.');
        navigate('ingresar');
      } catch (error) {
        setBusy(savePassword, false);
        showFormErrors(passwordForm, error);
      }
    });

    /* Cuentas de dinero: sin cifras, porque la API no tiene saldo por cuenta. */
    const accountList = h('div', { class: 'glass glass--strong' }, accounts.map((account) => h('div', { class: 'info-row' },
      h('span', { class: 'cat-chip', 'aria-hidden': 'true' }, icon('wallet')),
      h('span', { class: 'tx-row__main' },
        h('span', { class: 'tx-row__title' }, account.name),
        h('span', { class: 'tx-row__meta' }, `${ACCOUNT_TYPES[account.type] ?? account.type} · ${account.currency}`)))));

    const logout = h('button', { class: 'btn btn--glass btn--block', type: 'button' }, icon('logout'), 'Cerrar sesión');
    logout.addEventListener('click', async () => {
      setBusy(logout, true, 'Cerrando sesión…');
      await api.logout();
      setFlash('Cerraste sesión. Hasta pronto.');
      navigate('ingresar');
    });

    render(content, head,
      h('h2', { class: 'h2 section-title' }, 'Tus datos'), nameForm,
      h('h2', { class: 'h2 section-title' }, 'Contraseña'), passwordForm,
      h('h2', { class: 'h2 section-title' }, accounts.length === 1 ? 'Tu cuenta de dinero' : 'Tus cuentas de dinero'), accountList,
      h('div', { style: { marginTop: 'var(--space-8)' } }, logout));
    fx.stagger(content.children);
  }

  load();

  return {
    unmount() {
      alive = false;
      fx.revert();
    },
  };
}
