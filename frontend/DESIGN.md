# Sistema de diseño de Flux IA

Este documento manda sobre el aspecto y el movimiento de la app. Los valores
viven en [`css/tokens.css`](css/tokens.css) y los componentes en
[`css/app.css`](css/app.css). La página [`styleguide.html`](styleguide.html)
los muestra en vivo, con el mismo CSS que usa la app.

Si hace falta un valor nuevo, se agrega **primero aquí**, después en
`tokens.css` y recién entonces se usa. En `app.css` no se escriben colores,
tamaños ni duraciones sueltos.

---

## 1. De dónde sale el estilo

Las referencias están en `docs/referencias/`, solo en local: son capturas de
apps de terceros y no se suben al repositorio. Son fotos de una pantalla con un
prototipo de Figma llamado «Liquid Glass Style». **La principal** es la que
muestra la pantalla de inicio de un teléfono con un ícono morado y, a la
derecha, la pantalla de bienvenida (`IMG_3160`). Si otra referencia la
contradice, gana esa.

### Lo que se ve

| Aspecto | En la referencia | Cómo se traduce |
|---|---|---|
| **Fondo** | Casi negro con resplandores violeta e índigo; esferas violeta con volumen detrás de las tarjetas. | `--bg-base` #0A0614, tres resplandores radiales fijos y esferas decorativas (`.orb`). |
| **Paleta** | Violeta saturado, de lila claro a casi negro. Un único acento: lila muy claro en botones. | Rampa `--flux-50…900` y botón principal en `--flux-200` con texto oscuro. |
| **Tarjetas** | Vidrio violeta translúcido, inclinadas y superpuestas, con un borde fino que brilla arriba a la izquierda y un reflejo curvo. | `.glass` y `.balance-card`: desenfoque, base oscura, brillo diagonal y borde con degradado. |
| **Tipografía** | Sans geométrica muy gruesa en titulares de dos líneas, alineados a la izquierda. Cifras grandes, blancas y gruesas. | Sora 700 para títulos y Manrope 800 con cifras tabulares para montos. |
| **Espaciado** | Márgenes laterales generosos, ~20 px; mucho aire entre el titular y las tarjetas. | Base de 4 px y margen de pantalla de 20 px. |
| **Radios** | Tarjetas ~24 px; campos ~12 px; botón de avance totalmente redondo. | `--r-lg` 24, `--r-sm` 12 y `--r-full`. |
| **Sombras** | Poca sombra negra; la profundidad la dan el resplandor violeta y el brillo del borde. | Sombras teñidas de violeta y un brillo interno de 1 px arriba. |
| **Campos** | Contorno lila de ~1,5 px sobre fondo transparente; selector Login / Sign Up con la opción activa rellena de lila. | `.input` y `.segmented`. |
| **Navegación** | Indicador de página con un trazo largo para la activa y uno corto para las demás; botón redondo con flecha. | Indicador de la bienvenida y `.btn--fab`. |
| **Ícono** | Cuadrado redondeado violeta con un dibujo lila claro. | Fondo violeta propio y tres dibujos nuevos (sección 11). |
| **Movimiento** | La apertura: pantalla negra → el ícono aparece al centro → se vuelve un círculo violeta → el círculo crece hasta llenar la pantalla → entra la bienvenida. | Animación de apertura (sección 12). |

### Lo que no se toma

- Ningún texto, logo ni marca de las referencias: nada de «Manage Your
  Finances», nada de Visa ni de íconos del sistema operativo. La marca es
  **Flux IA**.
- **El saldo por tarjeta.** La referencia muestra un saldo en cada tarjeta,
  pero la API no tiene saldo por cuenta: solo el total de `/reports/summary`.
  Hay **una** tarjeta de saldo con ese total; las cuentas de `/accounts` se
  muestran con nombre, tipo y moneda, sin cifras.
- Los botones de redes sociales del login: el backend no los tiene.

---

## 2. Principios

1. **La cifra manda.** En cada pantalla, el dato principal es lo más grande y
   lo más blanco. Nada compite con él: ni el color ni el movimiento.
2. **Vidrio con medida.** Pocas capas de desenfoque, nunca una dentro de otra.
   El vidrio se ve porque hay algo detrás, no porque haya muchas capas.
3. **El contraste se mide, no se estima.** Toda combinación de texto se probó
   contra el peor caso real (sección 4).
4. **Nada inventado.** Toda cifra viene de la API. Si un dato falta, se dice
   que falta.
5. **El movimiento acompaña, no hace esperar.** Se puede leer y tocar
   mientras algo se anima.

---

## 3. Color

### Fondo

| Token | Valor | Uso |
|---|---|---|
| `--bg-base` | `#0A0614` | Fondo de la app. |
| `--bg-outside` | `#05030B` | Alrededor del marco del teléfono, en el computador. |
| `--aurora-violet` | `rgb(109 43 239 / .55)` | Resplandor superior izquierdo. |
| `--aurora-magenta` | `rgb(178 59 255 / .35)` | Resplandor superior derecho. |
| `--aurora-indigo` | `rgb(42 26 168 / .45)` | Resplandor inferior. |
| `--flux-orb` | `#A274FF` | **Tope** de luminosidad de las esferas. |

Los resplandores son degradados radiales del fondo: no usan `filter: blur()`,
no se mueven al desplazar y no cuestan nada. Las esferas (`.orb`) tienen borde
definido a propósito: un fondo del todo difuso no deja ver el efecto del
vidrio. Ninguna esfera puede ser más clara que `--flux-orb`; si lo fuera, el
texto secundario sobre vidrio bajaría de 4,5:1.

### Marca

| Token | Valor | Uso |
|---|---|---|
| `--flux-50` | `#F4EEFF` | Brillo del botón redondo. |
| `--flux-100` | `#E6DAFF` | Parte alta del degradado del botón principal. |
| `--flux-200` | `#D6C5FF` | **Botón principal**, opción activa, ícono activo. |
| `--flux-300` | `#B795FF` | Enlaces y acentos sobre fondo oscuro. |
| `--flux-400` | `#9B6BFF` | Gráficos y detalles. |
| `--flux-500` | `#8243F5` | Violeta de la marca. |
| `--flux-600` | `#6326D6` | Arranque de la tarjeta de saldo. |
| `--flux-700` | `#4E1CB6` | Sombras de las esferas. |
| `--flux-800` | `#2C106E` | Final de la tarjeta de saldo. |
| `--flux-900` | `#1B0B45` | Texto sobre lila. |

### Texto

| Token | Valor | Uso |
|---|---|---|
| `--text-strong` | `#FFFFFF` | Cifras y títulos. |
| `--text` | `#EDE7FB` | Cuerpo. |
| `--text-muted` | `#C4B9E2` | Secundario: metadatos, etiquetas, ayudas. |
| `--text-subtle` | `#A99CD0` | Solo *placeholders*. |
| `--text-on-accent` | `#1B0B45` | Sobre el botón lila. |
| `--text-on-hero` | `#F1EAFF` | Etiquetas dentro de la tarjeta de saldo. |

### Significado

| Token | Valor | Uso |
|---|---|---|
| `--income` | `#4BE3A4` | Texto de ingresos y su ícono. |
| `--expense` | `#FF93A6` | Ícono de gastos. **No** se usa en las cifras de gasto (ver abajo). |
| `--danger` | `#FF8094` | Errores. |
| `--warning` | `#FFCB6B` | Avisos. |

**El color nunca va solo.** Un ingreso lleva «+» y va en verde; un gasto lleva
«−» y va en blanco. Que los gastos vayan en blanco es deliberado: una lista
donde casi todo está en rojo cansa y le quita fuerza a la señal. El signo dice
el sentido, y el verde destaca lo que entra.

### Colores de categoría (vienen de la API)

Cada categoría trae `icon` (un emoji) y `color` (hexadecimal). **Se usan esos**,
no un mapa propio. Pero fueron pensados para el prototipo claro y, medidos
sobre este fondo, fallan: «Otros» (`#374151`) queda a 1,72:1 de la superficie,
y verde y rosa casi no se distinguen con deuteranopia (ΔE 4,9). Por eso:

- El color de la categoría **nunca colorea texto**.
- En el chip de la fila, el color se mezcla al 28 % sobre una base clara
  propia y lleva un anillo, así hasta «Otros» se distingue del fondo. El emoji
  carga la identidad.
- En los gráficos, cada segmento lleva su emoji y su nombre al lado
  (sección 10).

> Pendiente para el backend: ajustar esos colores para fondo oscuro con una
> migración nueva. Se plantea junto con la primera `V3`.

---

## 4. Contraste medido

Mínimo **4,5:1** para todo texto y 3:1 para lo que no es texto (bordes de
campo, íconos con significado). Calculado con la fórmula de WCAG sobre la
superficie compuesta, es decir, el color que de verdad queda detrás de la
letra.

**Peor caso:** vidrio encima del punto más brillante de una esfera
(`--flux-orb`, `#A274FF`).

| Texto | Vidrio fuerte, peor caso | Vidrio sobre fondo | Sin desenfoque | Tarjeta de saldo, zona más clara |
|---|---:|---:|---:|---:|
| `--text-strong` | 11,41 | 18,78 | 17,68 | 6,53 |
| `--text` | 9,46 | 15,59 | 14,67 | no se usa |
| `--text-on-hero` | — | — | — | 5,58 |
| `--text-muted` | 6,18 | 10,18 | 9,59 | no se usa |
| `--text-subtle` | 4,54 | 7,47 | 7,03 | no se usa |
| `--income` | 6,97 | 11,47 | 10,80 | no se usa |
| `--expense` | 5,42 | 8,92 | 8,40 | no se usa |
| `--flux-300` (enlaces) | 4,76 | 7,83 | 7,37 | no se usa |
| `--field-border` (no texto, mínimo 3) | 3,20 | 5,26 | 4,96 | — |

Texto oscuro `--text-on-accent` sobre el botón `--flux-200`: 11,26.

De esta tabla salen tres reglas:

1. **Vidrio normal (55 %) solo con texto principal.** En el peor caso deja a
   `--text-subtle` en 3,32 y a `--expense` en 3,96. Todo vidrio que lleve texto
   secundario o cifras de color es **vidrio fuerte** (72 %).
2. **En la tarjeta de saldo, solo blanco y `--text-on-hero`.** Verde y rosa
   sobre el violeta dan 4,0 y 3,1. Por eso el color de ingresos y gastos va en
   el círculo del ícono (no es texto: basta 3:1) y la cifra va en blanco.
3. **`--text-subtle` solo para *placeholders*.** Sobre vidrio fuerte apenas
   llega a 4,54.

---

## 5. Tipografía

Dos familias, alojadas en `assets/fonts/` (licencia OFL). Van en el proyecto y
no se cargan de Google Fonts: la PWA tiene que verse igual sin conexión.
Son fuentes variables, así que un archivo por familia cubre todos los pesos.

- **Sora** para títulos: geométrica y algo ancha, como los titulares de la
  referencia, pero con más carácter que la sans de sistema.
- **Manrope** para texto y cifras: muy legible en tamaño chico, con cifras
  tabulares (`tnum`).

| Rol | Clase | Familia | Peso | Tamaño / interlínea |
|---|---|---|---|---|
| Display | `.display` | Sora | 700 | 38 / 42, −2 % |
| Título 1 | `.h1` | Sora | 700 | 28 / 34 |
| Título 2 | `.h2` | Sora | 600 | 20 / 26 |
| Cuerpo | — | Manrope | 500 | 16 / 24 |
| Pequeño | — | Manrope | 500 | 14 / 20 |
| Rótulo | `.eyebrow` | Manrope | 700 | 12 / 16, mayúsculas, +8 % |
| Cifra XL (saldo) | `.money` | Manrope | 800 | 40 / 44 |
| Cifra L | `.money` | Manrope | 800 | 24 |
| Cifra M (filas) | `.money` | Manrope | 800 | 17 |

**Nada por debajo de 12 px.** Los campos usan 16 px: con menos, Safari en
iPhone hace zoom al enfocar.

### Cifras

- **Siempre tabulares** (`font-variant-numeric: tabular-nums`). Todos los
  dígitos miden lo mismo: un monto que cuenta no «baila» y las columnas quedan
  alineadas.
- **Formato:** `Intl.NumberFormat('es-AR', { style: 'currency', currency })`
  en una sola función de `js/`. La moneda sale de la cuenta (`currency`,
  `ARS` por omisión en el backend). Ejemplo: `$ 56.410,00`.
- **Signo pegado al símbolo:** `+$ 43.000,00` y `−$ 5.800,00`, con el signo
  menos tipográfico (U+2212), no el guion. El monto llega siempre positivo de
  la API; el signo sale de `type`.
- **Nunca se corta una cifra** ni se reemplaza por «…». Si no entra, baja un
  escalón de tamaño: 40 → 32 → 26 (`.is-long`, `.is-very-long`).
- **`null` no es cero.** Si la API manda `savingsRate: null` (no hubo
  ingresos), se muestra «—» con la explicación, nunca «0 %».
- **Cada cifra dice su período:** «Ingresos del mes», no «Ingresos». El saldo
  (`balance`) es el acumulado hasta el cierre del período; se rotula «Saldo
  total».

---

## 6. Espacio, radios y tamaños

| Token | Valor | | Token | Valor | Uso |
|---|---|---|---|---|---|
| `--space-1` | 4 | | `--r-sm` | 12 | Campos, chips |
| `--space-2` | 8 | | `--r-md` | 16 | Botones, filas |
| `--space-3` | 12 | | `--r-lg` | 24 | Tarjetas, barra |
| `--space-4` | 16 | | `--r-xl` | 32 | Hojas inferiores |
| `--space-5` | 20 | | `--r-full` | 999 | Botón redondo |
| `--space-6` | 24 | | `--r-device` | 52 | Marco en el computador |
| `--space-8` | 32 | | | | |
| `--space-10` | 40 | | | | |
| `--space-14` | 56 | | | | |

- **Margen lateral de pantalla:** `--gutter`, 20 px.
- **Área táctil mínima:** `--touch`, 44×44 px. Botones de ícono, opciones del
  selector, el ojo de la contraseña y cada destino de la barra. Las filas de
  movimiento miden al menos 64 px de alto.
- **Campos:** 52 px de alto. **Barra inferior:** 64 px.

---

## 7. Vidrio

Receta de `.glass`:

1. `backdrop-filter: blur(22px) saturate(140%)`. La saturación evita que el
   desenfoque deje el fondo lavado y gris.
2. Relleno con **base oscura** translúcida (`--glass-fill`, 55 %, o
   `--glass-fill-strong`, 72 %), no solo blanco translúcido. Es lo que mantiene
   el contraste con una esfera detrás.
3. Brillo diagonal (`--glass-sheen`): blanco al 12 % arriba a la izquierda,
   que se desvanece antes de la mitad.
4. Borde de 1 px con degradado (`--glass-edge`), más claro arriba a la
   izquierda, donde «pega» la luz. Se dibuja con una máscara en `::before`,
   porque un `border` normal no admite degradado con esquinas redondeadas.
5. Sombra violeta difusa y un brillo interno de 1 px en el borde superior.

### Reglas

- **Normal o fuerte.** Normal solo con texto principal; fuerte con texto
  secundario, listas, formularios y barra inferior (sección 4).
- **Pocas capas a la vez:** como máximo **tres** superficies con desenfoque
  visibles en una pantalla (por ejemplo: tarjeta de saldo, lista y barra).
  Para un celular de gama media, cada capa es un recálculo del fondo en cada
  cuadro.
- **Nunca vidrio dentro de vidrio.** Una lista es **un** contenedor de vidrio;
  sus filas no llevan desenfoque propio.
- **Nunca se anima el desenfoque**, y un elemento con `backdrop-filter` solo
  se anima con `opacity`. Si tiene que moverse o cambiar de tamaño, se anima
  otro elemento (sección 12).
- **Alternativa sólida.** Sin soporte de `backdrop-filter`
  (`@supports not`), el vidrio pasa a `--glass-solid` y la tarjeta de saldo a
  `--hero-solid`. La clase `.glass--solid` fuerza lo mismo si un dispositivo
  no da abasto.

---

## 8. Tarjeta de saldo

La pieza principal de Inicio y la única con vidrio violeta. Hay **una sola** por
pantalla.

- Fondo `--hero-fill`: violeta de `#6326D6` a `#2C106E` al 90 %, casi opaco.
  Más transparente, el blanco perdería contraste en la zona clara (medido: con
  el arranque en `#7A3CF0` y 14 % de brillo, el blanco quedaba en 4,56).
- Arco fino arriba a la derecha: el reflejo curvo de la referencia.
- Contenido: «Saldo total» (`balance`), ingresos y gastos del período con su
  ícono de color, y la tasa de ahorro al pie.
- **Variante compacta** (`.balance-card--compact`): una línea con el saldo, la
  que queda fija arriba al bajar en Inicio.

---

## 9. Componentes

### Botones

| Clase | Aspecto | Para qué |
|---|---|---|
| `.btn--primary` | Lila claro, texto oscuro (11,3:1), 52 px | Una acción principal por pantalla. |
| `.btn--glass` | Translúcido con contorno | Acción secundaria. |
| `.btn--ghost` | Solo texto `--flux-300` | Enlaces: «¿Olvidaste tu contraseña?», «Ver todos». |
| `.btn--danger` | Fondo rojo suave, texto `--danger` | Borrar. Siempre con confirmación. |
| `.btn--icon` | Redondo de 44 px | Buscar, filtrar, volver. Siempre con `aria-label`. |
| `.btn--fab` | Redondo de 60 px, lila con brillo | Avanzar en la bienvenida; nuevo movimiento. |

- **Ocupado** (`aria-busy="true"` + `disabled`): indicador que gira y texto que
  dice qué pasa («Ingresando…»). Opacidad 85 %, para que no parezca
  deshabilitado.
- **Deshabilitado:** opacidad 45 %.

### Campos

- Etiqueta **siempre visible arriba**; el *placeholder* es solo un ejemplo.
- Contorno `--field-border` de 1,5 px; al enfocar, `--focus` con un halo.
  El cambio es instantáneo, sin transición de color.
- Error: `aria-invalid="true"`, contorno `--danger` y el mensaje **escrito**
  debajo, con ícono (`.field__error`), enlazado con `aria-describedby`.
- Error general (`.alert`): el `message` de la API, tal cual llega.
- Contraseña: botón de ojo de 44 px dentro del campo.

### Fila de movimiento

- Chip de categoría (emoji y color de la API), descripción en hasta dos líneas,
  categoría y fecha, y monto.
- **La descripción se corta con «…»; la cifra, nunca.**
- Fecha relativa solo cerca: «Hoy», «Ayer»; después, fecha corta («3 oct»), en
  la zona del usuario.
- Toda la fila se toca y abre el detalle.

### Navegación inferior

- Barra de vidrio fuerte **flotante**, separada 8 px de los bordes y por encima
  de la zona segura inferior.
- Cinco destinos, **siempre con texto**: Inicio, Movimientos, Reportes,
  Asistente y Perfil.
- Activa: fondo lila al 16 %, ícono `--flux-200` y texto blanco.

### Estados

Toda pantalla resuelve los tres:

- **Cargando:** esqueleto con la forma del contenido que viene; nada salta
  cuando llegan los datos. Lleva `aria-busy` y un texto oculto para lectores
  de pantalla.
- **Vacío:** dice qué falta y ofrece la acción que lo resuelve.
- **Error:** el mensaje de la API si existe; si es un fallo de red, un texto
  propio. Siempre con «Reintentar». Nunca el detalle técnico.

---

## 10. Gráficos

Se siguen las reglas de visualización de datos del proyecto: una sola escala
por gráfico, marcas finas, el color identifica y el texto va en tokens de
texto.

### Dona: gasto por categoría

- Datos de `/reports/by-category`: cada porción trae `name`, `icon`, `color`,
  `amount` y `percent`. **Los porcentajes vienen de la API**; el frontend no
  los calcula.
- Color de cada porción: el `color` de la categoría. Como esos colores fallan
  las comprobaciones sobre fondo oscuro (sección 3), la **codificación
  secundaria es obligatoria**:
  - 2 px de separación (`--chart-gap`) entre porciones, del color de la
    superficie;
  - debajo de la dona, la lista con emoji, nombre, monto y porcentaje de cada
    categoría, que hace de leyenda **y** de vista en tabla.
- Al centro, el total del período (`total`), en blanco.
- Tocar una porción resalta su fila en la lista, y al revés.

### Barras: tendencia mensual

- Datos de `/reports/monthly-trend`: ingresos y gastos por mes.
- Colores `--chart-income` `#25AD7C` y `--chart-expense` `#BF4D8C`. **No** son
  `--income` / `--expense`: esos son para texto. Validados como par para
  barras lado a lado: pasan luminosidad, croma, contraste y separación con
  daltonismo (ΔE 8,6 en deuteranopia; con los tonos de texto daba 3,1).
- Barras con extremo redondeado de 4 px, apoyadas en la base, y 2 px entre la
  barra de ingresos y la de gastos de un mismo mes.
- Leyenda arriba («Ingresos», «Gastos»), grilla muy tenue (`--chart-grid`) y
  rótulo del mes (`label`) debajo.
- El mes en curso (`current: true`) se marca con su rótulo en negrita y una
  banda de fondo tenue, **sin cambiar el color** de sus barras.
- Tocar un mes muestra sus dos cifras exactas.

### Dónde van

Los gráficos van sobre vidrio fuerte **sin una esfera detrás**. Medido: con
una esfera, la barra de gastos baja a 2,53:1 contra la superficie, por debajo
del 3:1 que necesita una marca.

---

## 11. Íconos

### Interfaz

[`assets/icons/ui.svg`](assets/icons/ui.svg): dibujados para el proyecto.
Grilla de 24, trazo de 1,75 y puntas redondeadas. Toman `currentColor`, así
que nunca quedan con un color propio sin contraste.

```html
<svg class="icon" aria-hidden="true"><use href="assets/icons/ui.svg#i-home"/></svg>
```

Un ícono sin texto al lado va dentro de un botón con `aria-label`.

### Categorías

El emoji que manda la API. No se reemplaza por un ícono propio.

### Ícono de la app

Tres propuestas en `assets/icons/app-icon-*.svg`, todas con el fondo violeta
del ícono de la referencia y un dibujo propio:

| Opción | Dibujo | Lectura |
|---|---|---|
| A · Flujo | Una F hecha de dos cintas onduladas | El dinero que fluye; la más tipográfica. |
| **B · Tarjetas de vidrio** (recomendada) | Dos tarjetas de vidrio inclinadas con una onda | Repite las tarjetas de la bienvenida: en la apertura, el ícono se convierte en la pantalla. |
| C · Onda | Una lente de vidrio atravesada por una onda | La más abstracta. |

Todas están dibujadas a sangre en 512×512, sin esquinas: las pone el sistema.
El dibujo cabe en el círculo seguro de los íconos *maskable* (radio de 205
desde el centro), así que sobrevive al recorte circular de Android. Con la
elegida se generan los PNG de 192 y 512, la versión *maskable* y el
`apple-touch-icon` de 180.

---

## 12. Movimiento

GSAP 3 con ScrollTrigger, cargados desde cdnjs. **Toda la coreografía vive en
`js/motion.js`**; las pantallas le piden efectos («entrada de la lista»,
«contar esta cifra») y no llaman a GSAP directamente.

Dos excepciones, en CSS, porque tienen que ser instantáneas y funcionar
aunque el JavaScript no haya cargado:

- la respuesta al tocar un botón (escala a 97 % en 100 ms);
- los indicadores continuos: el que gira mientras se espera y el brillo del
  esqueleto.

### Duraciones y curvas

`motion.js` lee las duraciones de `tokens.css` con `getComputedStyle`: hay una
sola fuente.

| Token | Valor | Para qué |
|---|---|---|
| `--dur-press` | 100 ms | Respuesta al tocar. |
| `--dur-fast` | 180 ms | Fundidos, aparición de errores. |
| `--dur-base` | 280 ms | Entradas de elementos, cambio de pantalla. |
| `--dur-slow` | 480 ms | Gráficos, burbujas del chat. |
| `--dur-count` | 900 ms | Conteo de cifras. |
| `--dur-launch` | 1400 ms | Apertura completa (tope). |
| `--stagger` | 50 ms | Escalonado entre elementos. |
| `--stagger-max` | 400 ms | Tope del escalonado completo. |
| `--rise` | 16 px | Distancia de entrada. |

| Curva | CSS | GSAP | Para qué |
|---|---|---|---|
| Salida | `--ease-out` | `power3.out` | Entradas: llega rápido y frena suave. |
| Ida y vuelta | `--ease-in-out` | `power2.inOut` | Cambio de pantalla, apertura. |
| Rebote corto | `--ease-back` | `back.out(1.6)` | Burbujas del chat. |
| Seguir el dedo | — | `none` con `scrub` | Todo lo que va atado al scroll. |

### Qué se anima

| Pantalla | Efecto |
|---|---|
| **Apertura** | Fondo negro → el ícono aparece al centro (escala 0,6 → 1) → se funde en un círculo violeta del mismo tamaño → el círculo crece (`scale`) hasta cubrir la pantalla → entra la bienvenida. Con el ícono B, las tarjetas del ícono crecen y se ubican donde están las de la bienvenida. Un toque la salta. Solo en el primer arranque de la sesión. |
| **Bienvenida** | El efecto principal. Fondo y esferas con *parallax* (se mueven a 0,4–0,6 de la velocidad del contenido). Imágenes que empiezan en escala ~0,55 y crecen hasta ocupar la pantalla con `scrub`. Titulares que entran por líneas (cada línea sube 16 px y aparece). Indicador de página: el trazo activo se alarga con `scaleX`. |
| **Inicio** | La tarjeta de saldo se va con el scroll y, al salir, aparece fija arriba la variante compacta (solo `opacity` y `translateY`). Las cifras cuentan de 0 a su valor. Las tarjetas entran escalonadas. |
| **Movimientos** | Las filas entran escalonadas al cargar y al paginar: solo las nuevas. |
| **Reportes** | Al entrar en pantalla: las barras crecen desde la base (`scaleY`, origen abajo) y la dona gira de −90° a 0 mientras sus porciones aparecen una tras otra (`rotate` + `opacity`). |
| **Chat** | Cada burbuja entra con un rebote corto (escala 0,92 → 1 y sube 8 px). Indicador de «escribiendo»: tres puntos que suben y bajan en secuencia. |
| **Cambio de pantalla** | La saliente se funde y baja 8 px; la entrante sube 16 px y aparece. 280 ms en total. |
| **Botones** | Escala 97 % al tocar (CSS). |

### Qué no se anima

- Nada que no sea `transform` u `opacity`: ni tamaños, ni márgenes, ni
  colores, ni `backdrop-filter`, ni `clip-path`, ni `stroke-dashoffset`. Por eso
  la dona «se dibuja» girando y no trazando, y las imágenes crecen con `scale`
  y no con `width`.
- Un elemento con `backdrop-filter`, salvo con `opacity`. Por eso la tarjeta
  compacta de Inicio es un elemento aparte que aparece, y no la grande
  encogiéndose.
- Lo que el usuario escribe, los mensajes de error (aparecen al instante y se
  funden en 180 ms como mucho) y el foco de los campos.
- La barra de navegación.

### Reglas técnicas

1. **El scroll ocurre dentro de `.screen`, no en `window`.** Cada ScrollTrigger
   recibe ese contenedor como `scroller`; si no, no se dispara nada.
2. **Al cambiar de pantalla** se matan los ScrollTrigger y las animaciones de
   la anterior (`gsap.context()` y `revert()` al desmontar).
3. **Cuando llegan los datos** se llama a `ScrollTrigger.refresh()`: el
   contenido cambió de alto y las posiciones calculadas ya no sirven.
4. **Una cifra animada termina exactamente en el valor de la API.** El conteo
   es solo visual: al terminar, el texto se reemplaza por la cadena formateada
   del valor recibido, sin redondeos del conteo. El valor final está en el DOM
   desde el principio para los lectores de pantalla (`aria-label`).
5. **El movimiento nunca retrasa.** Nada desactiva los toques mientras anima;
   el contenido se puede leer desde el primer cuadro; si los datos llegan a
   mitad de una entrada, se muestran ya. El escalonado completo no pasa de
   400 ms, aunque la lista sea larga.
6. **`prefers-reduced-motion: reduce`:** sin *parallax*, sin `scrub`, sin
   conteos (la cifra aparece ya en su valor), sin escalas ni desplazamientos.
   Solo fundidos de 180 ms. La apertura pasa a un fundido del ícono a la
   bienvenida.

---

## 13. App instalable (PWA) y marco

- `manifest.webmanifest`: `display: standalone`, `theme_color` y
  `background_color` `#0A0614`, íconos de 192 y 512 (`any` y `maskable`).
  Además, `apple-touch-icon` de 180.
- `viewport-fit=cover` en el viewport, y márgenes con
  `env(safe-area-inset-*)` (tokens `--safe-*`) para la muesca y la barra
  inferior del sistema.
- **En el celular** la app ocupa toda la pantalla (`100dvh`).
- **En el computador** (≥ 600 px de ancho) se ve centrada dentro de un marco
  de teléfono de 390×844 (`.device`). Si la ventana es más baja, el marco se
  acorta en lugar de obligar a desplazar la página.
- **Service worker:** al final del proyecto. Solo guarda en caché los archivos
  de la app (HTML, CSS, JS, fuentes, íconos, GSAP); **nunca** respuestas de la
  API. **No se registra con Live Server** (orígenes `localhost:5500` y
  `127.0.0.1:5500`), para que durante el desarrollo no aparezcan versiones
  viejas.

---

## 14. Voz y textos

- Español, tratando de **tú**: «Revisa tu conexión», «Registra tu primer
  movimiento».
- Frases cortas. Un botón dice lo que hace: «Guardar», no «Aceptar».
- Los errores dicen qué pasó y qué hacer, sin culpar ni dar detalles técnicos.
- Los mensajes de la API ya vienen escritos para el usuario: se muestran tal
  cual.

---

## 15. Lo que no se hace

- Copiar textos, logos, fotos o marcas de las referencias.
- Mostrar saldo por cuenta o cualquier cifra que la API no devuelva.
- Mostrar «0 %» cuando la API manda `null`.
- Cortar una cifra con «…».
- Poner vidrio dentro de vidrio, o más de tres capas con desenfoque a la vez.
- Usar el color de una categoría como color de texto.
- Usar el color solo, sin signo, ícono o palabra.
- Animar algo que no sea `transform` u `opacity`.
- Insertar con `innerHTML` texto que venga de la API o del usuario
  (ver CLAUDE.md).
- Degradados violeta-rosa en todas partes: el violeta está en el fondo, en la
  tarjeta de saldo y en el ícono. El resto es oscuro, blanco y lila.
