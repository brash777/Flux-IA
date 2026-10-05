/* =====================================================================
   Construcción de nodos.

   Todo texto que viene de la API o del usuario entra con textContent
   (CLAUDE.md): h() trata cada cadena como texto, nunca como HTML. Así una
   descripción de movimiento o una respuesta de la IA no puede inyectar
   un script que lea la sesión.
   ===================================================================== */

const SVG = 'http://www.w3.org/2000/svg';
const ICONS = 'assets/icons/ui.svg';

/**
 * h('p', { class: 'x', 'aria-label': '…' }, 'texto', otroNodo)
 * Las cadenas hijas siempre se insertan como texto.
 */
export function h(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs ?? {})) {
    if (value === false || value === null || value === undefined) continue;
    if (name === 'class') node.className = value;
    else if (name === 'style' && typeof value === 'object') setStyles(node, value);
    else if (name.startsWith('on') && typeof value === 'function') node.addEventListener(name.slice(2), value);
    else if (name === 'dataset') Object.assign(node.dataset, value);
    else node.setAttribute(name, value === true ? '' : value);
  }
  append(node, children);
  return node;
}

/* Las variables CSS (--algo) solo se pueden poner con setProperty. */
function setStyles(node, styles) {
  for (const [property, value] of Object.entries(styles)) {
    if (property.startsWith('--')) node.style.setProperty(property, value);
    else node.style[property] = value;
  }
}

function append(node, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/** Ícono del set de interfaz (assets/icons/ui.svg). */
export function icon(name, extraClass = '') {
  const svg = document.createElementNS(SVG, 'svg');
  svg.setAttribute('class', `icon ${extraClass}`.trim());
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG, 'use');
  use.setAttribute('href', `${ICONS}#i-${name}`);
  svg.append(use);
  return svg;
}

/** Reemplaza todo el contenido de un nodo. */
export function render(node, ...children) {
  node.replaceChildren();
  append(node, children);
  return node;
}

/** Nodo SVG con atributos (para los gráficos). */
export function svg(tag, attrs = {}, ...children) {
  const node = document.createElementNS(SVG, tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (value !== null && value !== undefined) node.setAttribute(name, value);
  }
  for (const child of children.flat()) if (child) node.append(child);
  return node;
}
