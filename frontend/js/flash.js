/* =====================================================================
   Mensaje para la próxima pantalla: «Tu sesión venció», «Contraseña
   cambiada». Vive en memoria; se lee una vez y se borra.
   ===================================================================== */

let pending = null;

export function setFlash(message) {
  pending = message;
}

export function takeFlash() {
  const message = pending;
  pending = null;
  return message;
}
