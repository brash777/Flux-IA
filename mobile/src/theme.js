/* =====================================================================
   Colores y medidas de frontend/css/tokens.css (DESIGN.md).

   Las pantallas nativas son pocas (conectar con el servidor, cargando,
   sin conexión), pero tienen que verse como el resto de Flux IA. Si un
   valor cambia en tokens.css, cambia aquí también.
   ===================================================================== */

export const colors = {
  bg: '#0A0614', // --bg-base
  surface: '#170F2C', // --glass-solid-strong
  line: 'rgba(255, 255, 255, 0.10)', // --line
  glassButton: 'rgba(255, 255, 255, 0.08)',
  textStrong: '#FFFFFF', // --text-strong
  text: '#EDE7FB', // --text
  textMuted: '#C4B9E2', // --text-muted
  textSubtle: '#A99CD0', // --text-subtle
  accent: '#B795FF', // --flux-300
  primary: '#D6C5FF', // --flux-200, relleno del botón principal
  onPrimary: '#1B0B45', // --text-on-accent
  danger: '#FF8094', // --danger
  focus: '#E2D4FF', // --focus
};

export const space = { 1: 4, 2: 8, 3: 12, 4: 16, 5: 20, 6: 24, 8: 32, 10: 40 };

export const radius = { sm: 12, md: 16, lg: 24 };

/* Área táctil mínima (CLAUDE.md): 44 × 44. */
export const TOUCH = 44;
