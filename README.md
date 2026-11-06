# Flux IA

**Flux IA** es una app de finanzas personales con un asistente de IA. Registras
tus ingresos y gastos, ves a dónde va tu dinero en reportes y le preguntas al
asistente cosas como «¿en qué gasté más este mes?». El asistente responde con
tus movimientos reales, calculados en el servidor.

El repositorio tiene todas las partes del proyecto:

| Carpeta | Qué contiene |
|---|---|
| [`backend/`](backend/) | API REST en Java 21 + Spring Boot + PostgreSQL. Toda cifra se calcula aquí. También entrega la app web. |
| [`frontend/`](frontend/) | App móvil en HTML, CSS y JavaScript puros, instalable como PWA. |
| [`mobile/`](mobile/) | La misma app para Expo Go y como APK de Android, dentro de una WebView. |
| [`e2e/`](e2e/) | Pruebas de punta a punta de la API y de la interfaz, contra el servidor corriendo. |
| [`docs/`](docs/) | Arquitectura, auditoría del prototipo, capturas y el prototipo original. |

## Capturas

Datos de la semilla de prueba, a 390×844.

<table>
  <tr>
    <td align="center"><img src="docs/capturas/01-bienvenida.png" width="200" alt="Bienvenida"><br>Bienvenida</td>
    <td align="center"><img src="docs/capturas/02-ingresar.png" width="200" alt="Ingresar"><br>Ingresar</td>
    <td align="center"><img src="docs/capturas/03-inicio.png" width="200" alt="Inicio"><br>Inicio</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/capturas/04-movimientos.png" width="200" alt="Movimientos"><br>Movimientos</td>
    <td align="center"><img src="docs/capturas/05-nuevo-movimiento.png" width="200" alt="Nuevo movimiento"><br>Nuevo movimiento</td>
    <td align="center"><img src="docs/capturas/06-reportes.png" width="200" alt="Reportes"><br>Reportes</td>
  </tr>
  <tr>
    <td align="center"><img src="docs/capturas/07-reportes-tendencia.png" width="200" alt="Tendencia mensual"><br>Tendencia mensual</td>
    <td align="center"><img src="docs/capturas/08-chat.png" width="200" alt="Chat con el asistente"><br>Chat</td>
    <td align="center"><img src="docs/capturas/09-perfil.png" width="200" alt="Perfil"><br>Perfil</td>
  </tr>
</table>

## Tecnologías

**Backend**

| Pieza | Versión | Para qué |
|---|---|---|
| Java | 21 (LTS) | Lenguaje |
| Spring Boot | 3.5 | Web, seguridad e inyección de dependencias |
| PostgreSQL | 13 o superior | Base de datos (local o Supabase) |
| Flyway | 11 | Migraciones versionadas del esquema |
| Spring Security + jjwt | 6.5 / 0.13 | Autenticación con JWT |
| springdoc-openapi | 2.9 | Documentación navegable en `/docs` (perfil `dev`) |
| Groq (API compatible con OpenAI) | `gpt-oss-120b` | Asistente de IA, plan gratuito |
| anthropic-java | 2.68 | Alternativa de pago para el asistente (`AI_PROVIDER=anthropic`) |

**Frontend**

| Pieza | Para qué |
|---|---|
| HTML, CSS y JavaScript (módulos ES) | Sin frameworks ni paso de build |
| GSAP 3 + ScrollTrigger (cdnjs) | Animación de apertura, *parallax* y gráficos |
| Sora y Manrope (OFL, incluidas) | Tipografía, también sin conexión |
| Web App Manifest | App instalable con ícono propio |

## Estructura

```
Flux IA/
├── backend/        API REST (Spring Boot)        → backend/README.md
│   ├── src/main/java/com/fluxia/backend/   un paquete por concepto
│   ├── src/main/resources/db/              migraciones y semilla de prueba
│   └── .env.example                        plantilla de configuración
├── frontend/       app móvil (HTML/CSS/JS)       → frontend/README.md
│   ├── index.html, js/, css/, assets/
│   ├── DESIGN.md                           sistema de diseño
│   └── styleguide.html                     el sistema de diseño en vivo
├── mobile/         Expo Go y APK (WebView)       → mobile/README.md
├── e2e/            pruebas de punta a punta      → e2e/README.md
├── docs/
│   ├── arquitectura.md
│   ├── auditoria-prototipo.md
│   ├── capturas/
│   └── prototipo-movil.html                prototipo original (ver abajo)
├── CLAUDE.md       convenciones del proyecto
└── README.md
```

## Cómo ejecutarlo

### Requisitos

- **JDK 21.** No hace falta instalar Maven: el proyecto trae el *wrapper*.
- **PostgreSQL**, local o en Supabase.
- Para el celular: **Node 20 o superior** y la app **Expo Go**.

### 1. Configurar el backend

```bash
cd backend
cp .env.example .env
```

Edita `backend/.env` con los datos de tu base (`DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`) y una clave de firma propia:

```bash
openssl rand -base64 48   # pega el resultado en JWT_SECRET
```

Para el asistente, saca una clave gratuita de Groq en
https://console.groq.com/keys (no pide tarjeta) y pégala en `AI_API_KEY`. Sin
clave, deja `AI_ENABLED=false`: todo funciona salvo el chat, que lo avisa con un
mensaje claro. Gemini y Anthropic también sirven: ver `backend/.env.example`.

> **Si usas Supabase:** la conexión directa solo tiene IPv6. En una red sin
> IPv6 el backend no arranca (`No route to host`) y hay que usar el *pooler*
> de Supabase, que sí tiene IPv4. Está explicado en `backend/.env.example`.

### 2. Levantar el backend con datos de prueba

Desde `backend/`:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

El perfil `dev` crea las tablas y carga la semilla con el usuario de prueba.
Cuando el log diga `Started FluxIaApplication`, la API responde en
`http://localhost:8080` y su documentación en
**http://localhost:8080/docs**, solo con el perfil `dev`. (Si `backend/.env` tiene otro `PORT`, usa ese
en todas las direcciones.)

### 3. Abrir la app

El backend también entrega la app web: abre **http://localhost:8080/**. La
app y la API comparten origen, así que no hay nada más que configurar.

Para verla con tamaño de teléfono: herramientas de desarrollador → modo
dispositivo → 390×844. En una ventana normal se ve dentro de un marco de
teléfono.

Para editar el frontend con recarga automática también sirve Live Server
(clic derecho sobre `frontend/index.html` → **Open with Live Server**): en
los puertos 5500 y 5501 la app busca la API en el 8080.

### 4. Ingresar

**Usuario de prueba:** `diego.f@fluxia.app` / `Flux2026!`

### 5. En el celular (Expo Go o APK)

Con el backend corriendo, desde `mobile/`:

```bash
npm install
npm start
```

Escanea el QR con Expo Go: la app busca el backend sola en el computador que
corre el servidor de Expo y, si no lo encuentra, pide la dirección
(`http://192.168.x.x:8080`). El celular y el computador tienen que estar en
la misma red. Cómo generar el APK y qué hacer
si la red aísla los dispositivos: [mobile/README.md](mobile/README.md).

## API

Base: `http://localhost:8080/api/v1`. Todos los endpoints exigen
`Authorization: Bearer <token>`, salvo los de `/auth`. La referencia completa,
con ejemplos, está en **http://localhost:8080/docs** y en
[backend/README.md](backend/README.md).

| Grupo | Endpoints |
|---|---|
| Autenticación | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password` |
| Perfil | `GET` y `PATCH /me`, `POST /me/password` |
| Cuentas y categorías | `GET` y `POST /accounts`, `GET` y `POST /categories`, `DELETE /categories/{id}` |
| Movimientos | `GET /transactions` (filtros y totales), `GET /transactions/recent`, `GET`, `PATCH` y `DELETE /transactions/{id}`, `POST /transactions` |
| Reportes | `GET /reports/summary`, `/reports/by-category`, `/reports/monthly-trend` |
| Asistente | `POST /ai/chat` |

## Decisiones de diseño

**1. Una sola fuente de verdad.** Toda cifra (saldo, reportes, contexto de la
IA) sale de un `SUM()` sobre la tabla `transactions`. El frontend no suma ni
calcula porcentajes: los pide a la API. El prototipo original mostraba cuatro
cifras distintas para el mismo mes; está documentado en
[docs/auditoria-prototipo.md](docs/auditoria-prototipo.md).

**2. Dinero sin coma flotante.** `BigDecimal` y `NUMERIC(14,2)` en el backend.
El frontend envía los montos como texto (`"2450.50"`) y solo los formatea.

**3. Montos siempre positivos; el sentido va en `type`.** Un gasto mal firmado
no puede inflar el saldo.

**4. Aislamiento por usuario.** El `userId` sale siempre del token, nunca de un
parámetro. Un recurso ajeno devuelve 404 y no 403, para no confirmar que existe.

**5. La clave de la IA vive solo en el servidor.** El frontend llama a
`POST /api/v1/ai/chat`; el backend arma el contexto con los datos del usuario y
habla con el modelo.

**6. Sesión en el frontend.** El `accessToken` (15 min) vive en memoria y el
`refreshToken` en `localStorage`, para que recargar no cierre la sesión. El
riesgo es que un script inyectado lea `localStorage`, así que ningún texto de la
API, del usuario ni de la IA entra con `innerHTML`.

**7. Sin frameworks en el frontend.** No hay `npm install` ni compilación que
configurar para evaluar el proyecto: lo que se lee es lo que corre.

**8. Sistema de diseño medido.** Colores, tipografía, vidrio y movimiento están
en [frontend/DESIGN.md](frontend/DESIGN.md), con el contraste de cada texto
calculado sobre el peor caso. El movimiento respeta `prefers-reduced-motion`.

**9. Un solo frontend, también en el celular.** La app de Expo no reescribe las
pantallas: muestra la app web dentro de una WebView. Dos clientes con su propia
lógica podrían volver a mostrar cifras distintas, que es justo el error del
prototipo. El backend entrega la app web, así el celular necesita una sola
dirección y no hay CORS de por medio. Detalle en
[mobile/README.md](mobile/README.md).

Más detalle en [docs/arquitectura.md](docs/arquitectura.md) y las convenciones
en [CLAUDE.md](CLAUDE.md).

## Pruebas

```bash
cd backend
./mvnw test
```

40 pruebas unitarias: los cálculos (límites de períodos, convención de signos,
porcentajes del gráfico, tasa de ahorro y variaciones) y el asistente de IA
(modelos de respaldo, qué conversación le llega al modelo y cómo se traduce
cada respuesta del proveedor). No necesitan base de datos ni red. El SQL se
valida al arrancar contra PostgreSQL: con
`ddl-auto=validate`, el servidor no arranca si el esquema y las entidades
difieren.

Con el servidor corriendo, [`e2e/`](e2e/) prueba el sistema completo:

```bash
cd e2e
npm install
node api.mjs http://localhost:8080        # 78 pruebas de la API
node ui.mjs http://localhost:8080 390     # 36 de la interfaz, en Chrome
```

La de la API recorre todos los endpoints y comprueba que las cifras cuadren
entre pantallas (la dona suma el gasto del resumen, la lista del mes da los
mismos totales, la tendencia coincide). La de la interfaz ingresa, crea, edita
y borra un movimiento, registra un usuario, recupera la contraseña, simula una
caída de red y revisa que no haya errores de consola ni desbordes horizontales.

La app de Expo tiene sus propias pruebas: `cd mobile && npm test`.

## El prototipo original

[`docs/prototipo-movil.html`](docs/prototipo-movil.html) es el prototipo con el
que empezó el proyecto, cuando se llamaba «PagoIA». Se conserva como referencia
del flujo de pantallas. Sus cifras estaban escritas a mano y no cuadraban entre
sí; la auditoría explica por qué el backend las calcula.

## Pendientes

1. **Servicio de correo** para la recuperación de contraseña. Hoy, en el perfil
   `dev`, el código vuelve en la respuesta y la app lo carga sola.
2. **Service worker** para usar la app sin conexión. Solo debe guardar en caché
   los archivos de la app, nunca respuestas de la API.
3. **Colores de categoría para fondo oscuro:** los de la API vienen del
   prototipo claro; hoy se compensa en el frontend.
4. **Servidor publicado** con HTTPS, para que el APK funcione fuera de la red
   local. Hoy el celular se conecta a la IP del computador.
