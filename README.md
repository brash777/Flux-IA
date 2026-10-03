# Flux IA — Backend

API REST de una aplicación de finanzas personales con asistente de IA.

Este backend es independiente de cualquier cliente: lo consumen por igual la app
móvil, la de escritorio y la del smartwatch. No contiene nada específico de
ninguna de ellas.

**Estado:** funcional y verificado contra PostgreSQL. Autenticación real,
transacciones, reportes calculados y chat con IA.

---

## Tecnologías

| Pieza | Versión | Para qué |
|---|---|---|
| Java | 21 (LTS) | Lenguaje |
| Spring Boot | 3.5.16 | Framework web y de inyección de dependencias |
| PostgreSQL | 13 o superior | Base de datos |
| Flyway | 11.7.2 | Migraciones versionadas del esquema |
| Spring Security + jjwt | 6.5 / 0.13.0 | Autenticación con JWT |
| springdoc-openapi | 2.9.1 | Documentación navegable en `/docs` |
| anthropic-java | 2.68.0 | SDK oficial para llamar al modelo de IA |

No se usa Lombok: los getters y setters están escritos a mano, para que no haya
procesamiento de anotaciones que configurar en el IDE.

---

## Puesta en marcha

### 1. Instalar un JDK 21

No hace falta instalar Maven: el proyecto incluye el *wrapper*, que lo descarga
solo la primera vez que se ejecuta `./mvnw`.

```bash
# macOS con Homebrew
brew install --cask temurin@21

# Comprobar
java -version    # debe decir 21.x
```

Si no usás Homebrew, descargá el instalador de
[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21).

### 2. Tener una base de datos PostgreSQL

Cualquiera de estas tres opciones sirve:

```bash
# a) PostgreSQL local con Homebrew
brew install postgresql@16 && brew services start postgresql@16
createdb fluxia

# b) Con Docker
docker run -d --name fluxia-db -p 5432:5432 \
  -e POSTGRES_DB=fluxia -e POSTGRES_USER=fluxia -e POSTGRES_PASSWORD=unaClave \
  postgres:16

# c) Supabase: crear el proyecto y copiar la cadena de conexión JDBC desde
#    Project Settings → Database → Connection string → JDBC
```

### 3. Configurar las variables de entorno

```bash
cp .env.example .env
```

Después editá `.env`:

```bash
DB_URL=jdbc:postgresql://localhost:5432/fluxia
DB_USERNAME=fluxia
DB_PASSWORD=unaClave

# Generar una clave de firma nueva:
#   openssl rand -base64 48
JWT_SECRET=<pegá acá el resultado>

# Opcional: sin clave de IA, poné AI_ENABLED=false y todo lo demás funciona.
ANTHROPIC_API_KEY=
AI_ENABLED=false
```

`.env` está en `.gitignore` y nunca se sube al repositorio.

### 4. Levantar el servidor

```bash
./mvnw spring-boot:run
```

El `.env` se lee solo: `application.yml` lo importa con
`spring.config.import: optional:file:./.env[.properties]`, que es el mecanismo
nativo de Spring Boot. **No hace falta `source .env`.**

Si querés además los datos de prueba del prototipo:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

> Una variable de entorno real tiene prioridad sobre el `.env`. Eso es
> deliberado: en producción no hay `.env` y la configuración llega del entorno
> del servidor. También sirve para una prueba puntual:
> `DB_URL=... ./mvnw spring-boot:run` sin tocar el archivo.

El perfil `dev` agrega la semilla de prueba. Al arrancar, Flyway crea las tablas
y carga los datos de ejemplo.

| Recurso | URL |
|---|---|
| Documentación navegable | http://localhost:8080/docs |
| Especificación OpenAPI | http://localhost:8080/api-docs |

**Usuario de prueba:** `diego.f@fluxia.app` / `Flux2026!`

### 5. Probar que anda

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"diego.f@fluxia.app","password":"Flux2026!"}'
```

Copiá el `accessToken` de la respuesta y usalo en el resto de las llamadas:

```bash
TOKEN=<accessToken>
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/api/v1/reports/summary?period=MONTH'
```

---

## Endpoints

Todos exigen `Authorization: Bearer <token>`, salvo los de `/auth`.

### Autenticación

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/v1/auth/register` | Crea la cuenta, su cuenta de dinero inicial y abre sesión |
| POST | `/api/v1/auth/login` | Devuelve token de acceso (15 min) y de refresco (30 días) |
| POST | `/api/v1/auth/refresh` | Cambia el token de refresco por un par nuevo |
| POST | `/api/v1/auth/logout` | Revoca el token de refresco |
| POST | `/api/v1/auth/forgot-password` | Genera un token de recuperación (30 min) |
| POST | `/api/v1/auth/reset-password` | Cambia la contraseña y cierra todas las sesiones |

### Perfil, cuentas y categorías

| Método | Ruta | Qué hace |
|---|---|---|
| GET / PATCH | `/api/v1/me` | Ver y modificar el perfil |
| POST | `/api/v1/me/password` | Cambiar la contraseña con sesión abierta |
| GET / POST | `/api/v1/accounts` | Cuentas de dinero |
| GET / POST | `/api/v1/categories` | Categorías del sistema y propias |
| DELETE | `/api/v1/categories/{id}` | Borrar una categoría propia sin movimientos |

### Movimientos

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/transactions` | Listado paginado con filtros, **con los totales del filtro** |
| GET | `/api/v1/transactions/recent` | Los más recientes, para la pantalla de inicio |
| GET / PATCH / DELETE | `/api/v1/transactions/{id}` | Ver, modificar y borrar |
| POST | `/api/v1/transactions` | Registrar uno nuevo |

Filtros de `GET /transactions`: `from`, `to` (ISO-8601), `category` (slug),
`type` (`INCOME` / `EXPENSE`), `search`, `page`, `size` (máximo 100).

### Reportes

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/v1/reports/summary?period=MONTH` | Saldo, totales y variación vs el período anterior |
| GET | `/api/v1/reports/by-category?period=MONTH` | Gasto por categoría, con porcentajes |
| GET | `/api/v1/reports/monthly-trend?months=7` | Serie mensual para el gráfico de barras |

`period` admite `MONTH`, `QUARTER` y `YEAR`.

### Asistente de IA

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/v1/ai/chat` | Pregunta al asistente con el contexto financiero real |

---

## Decisiones de diseño

Las cinco que conviene poder sustentar:

**1. Una sola fuente de verdad.** Toda cifra —saldo, reportes, contexto de la
IA— sale de un `SUM()` sobre la tabla `transactions`. No hay totales guardados
aparte, así que dos pantallas no pueden discrepar. El prototipo mostraba cuatro
cifras distintas para lo mismo; está documentado en
[docs/auditoria-prototipo.md](docs/auditoria-prototipo.md).

**2. Montos siempre positivos, sentido en `type`.** En lugar de guardar `-2450`,
se guarda `2450` con `type = EXPENSE`. El saldo es
`SUM(CASE WHEN type='INCOME' THEN amount ELSE -amount END)`, y un gasto mal
firmado no puede inflar el saldo. Es la convención estándar en sistemas
contables.

**3. `BigDecimal` y `NUMERIC(14,2)`, nunca `double`.** En coma flotante
`0.1 + 0.2` no da `0.3`. En dinero eso es inaceptable.

**4. Aislamiento por usuario en cada consulta.** El `userId` sale siempre del
token, nunca de un parámetro. Buscar un recurso ajeno devuelve 404 y no 403: un
403 confirmaría que ese recurso existe y es de alguien más.

**5. La clave de la IA vive solo en el servidor.** El cliente llama a
`POST /api/v1/ai/chat` con su token; es el backend el que habla con el modelo y
el que arma el contexto a partir de los datos del usuario. Si la clave estuviera
en el frontend, cualquiera podría extraerla del código descargado.

Más detalle en [docs/arquitectura.md](docs/arquitectura.md).

---

## Estructura

```
src/main/java/com/fluxia/backend/
├── FluxIaApplication.java
├── config/        FluxProperties, SecurityConfig, OpenApiConfig
├── shared/        Excepciones y manejo uniforme de errores
├── auth/          Registro, sesión, JWT, recuperación de contraseña
├── user/          Perfil
├── account/       Cuentas de dinero
├── category/      Categorías del sistema y propias
├── transaction/   Entidad central y consultas de agregación
├── report/        Cálculo de saldo, categorías y tendencia
└── ai/            Contexto financiero y chat con el modelo

src/main/resources/db/
├── migration/     V1 esquema · V2 categorías del sistema
└── dev/           V900 semilla de prueba (solo perfil dev)
```

Un paquete por concepto del dominio, no por capa técnica: todo lo que toca
movimientos vive en `transaction/`. Así, al cambiar una regla, no hay que editar
cuatro carpetas distintas.

---

## Pruebas

```bash
./mvnw test
```

24 pruebas unitarias sobre los cálculos: límites de períodos, convención de
signos, porcentajes del gráfico, tasa de ahorro y variaciones. No necesitan base
de datos.

Para verificar el SQL hay que levantar la aplicación contra PostgreSQL: con
`spring.jpa.hibernate.ddl-auto=validate`, el servidor no arranca si las
entidades y las tablas no coinciden.

---

## Convenciones

Están en [CLAUDE.md](CLAUDE.md).
