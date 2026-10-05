# Flux IA — Frontend

App móvil de Flux IA en HTML, CSS y JavaScript puros: sin frameworks, sin
`npm install` y sin paso de build. Lo que se edita es lo que corre. Se ve como
una app de teléfono (390×844) y se puede instalar como PWA.

Volver al [README del proyecto](../README.md).

## Cómo abrirla

1. Levanta el backend (ver [backend/README.md](../backend/README.md)). Tiene que
   responder en `http://localhost:8080`.
2. En VS Code, abre `frontend/index.html` con la extensión **Live Server**
   («Open with Live Server»). Queda en
   `http://127.0.0.1:5500/frontend/index.html`.
3. Ingresa con el usuario de prueba: `diego.f@fluxia.app` / `Flux2026!`.

En el computador la app se ve dentro de un marco de teléfono. Para verla a
tamaño real: herramientas de desarrollador → modo dispositivo → 390×844.

- `?apertura` en la dirección repite la animación de apertura, que si no se
  ve una vez por pestaña.
- `styleguide.html` muestra el sistema de diseño en vivo.

> Hay que abrirla con un servidor (Live Server), no con doble clic: los
> módulos de JavaScript y los íconos no cargan con `file://`. El backend solo
> acepta los orígenes de `CORS_ALLOWED_ORIGINS` (por omisión, los del 5500).

## Pantallas

| Ruta | Pantalla |
|---|---|
| `#/bienvenida` | Presentación con scroll, antes del login |
| `#/ingresar`, `#/crear-cuenta`, `#/recuperar` | Ingreso, registro y recuperación de contraseña |
| `#/inicio` | Saldo, ingresos, gastos, tasa de ahorro y movimientos recientes |
| `#/movimientos` | Lista paginada con búsqueda y filtros; crear, editar y borrar |
| `#/reportes` | Resumen por período, dona por categoría y barras mensuales |
| `#/chat` | Asistente de IA |
| `#/perfil` | Datos, cambio de contraseña, cuentas y cierre de sesión |

## Estructura

```
frontend/
├── index.html            la app (una sola página)
├── manifest.webmanifest  app instalable
├── styleguide.html       el sistema de diseño en vivo
├── DESIGN.md             tokens, reglas y movimiento
├── css/
│   ├── tokens.css        las variables de DESIGN.md, y nada más
│   ├── app.css           componentes y pantallas
│   └── styleguide.css    maqueta de styleguide.html
├── js/
│   ├── main.js           arranque: rutas, barra inferior y sesión
│   ├── router.js         rutas por hash
│   ├── api.js            ÚNICA capa que llama al backend
│   ├── motion.js         toda la animación (GSAP + ScrollTrigger)
│   ├── format.js         cifras y fechas a texto (no calcula nada)
│   ├── ui.js, dom.js     piezas compartidas y construcción segura de nodos
│   └── screens/          una pantalla por archivo
└── assets/               fuentes (OFL), íconos e imágenes propias
```

## Reglas que conviene saber

- **Ninguna cifra se calcula aquí.** Saldo, totales y porcentajes vienen de la
  API. El frontend solo los formatea y dibuja.
- **Texto externo con `textContent`.** Lo que viene de la API o del usuario
  (incluidas las respuestas de la IA) nunca entra con `innerHTML`.
- **Sesión:** el `accessToken` vive en memoria y el `refreshToken` en
  `localStorage`. Ante un 401 se renueva una vez; si falla, vuelve al login.
- **La clave de la IA no está aquí:** el chat llama a `POST /api/v1/ai/chat`.
- **Movimiento:** GSAP desde cdnjs, con hash de integridad. Solo se anima
  `transform` y `opacity` (salvo los gráficos SVG). Con
  `prefers-reduced-motion`, todo pasa a fundidos y la bienvenida es una sola
  pantalla. Si GSAP no carga, la app funciona igual, sin animaciones.

Las convenciones completas están en [CLAUDE.md](../CLAUDE.md) y el sistema de
diseño en [DESIGN.md](DESIGN.md).

## Configuración

`js/config.js` tiene la dirección de la API (`http://localhost:8080/api/v1`) y
la zona horaria con la que se muestran las fechas. Esa zona tiene que ser la
misma que `flux.timezone` del backend, para que un gasto aparezca en el mismo
mes en la lista y en los reportes.
