# Arquitectura del backend

Documento de referencia para sustentar las decisiones técnicas del proyecto.

## Por qué un backend separado

Están previstas tres aplicaciones cliente: móvil, escritorio y smartwatch. Si la
lógica viviera en cada una, habría que escribir tres veces el cálculo del saldo,
y tres implementaciones de la misma regla acaban divergiendo.

Este backend expone una API REST y concentra:

- el **modelo de datos** y su integridad,
- las **reglas de negocio** (qué es un movimiento válido, cómo se calcula el saldo),
- la **autenticación**,
- la **clave de la IA**, que no puede estar en un cliente.

Los clientes quedan reducidos a presentación. El reloj y el escritorio consumen
los mismos endpoints que el móvil, sin código duplicado.

## Modelo de datos

```
users ──┬── accounts ──┐
        │              │
        ├── categories ┤
        │              │
        └──────────────┴── transactions
                              ▲
                              │  única fuente de verdad:
                              │  todas las cifras salen de acá
```

Más dos tablas de soporte, `refresh_tokens` y `password_reset_tokens`.

### Decisiones del esquema

**`transactions` como única fuente de verdad.** No hay ninguna columna con un
saldo o un total precalculado. Cada consulta de reporte es un `SUM()`. Es más
trabajo para la base de datos, pero hace imposible que dos pantallas muestren
cifras distintas —que es exactamente lo que le pasaba al prototipo
(ver [auditoria-prototipo.md](auditoria-prototipo.md)).

**Montos positivos y columna `type`.** En lugar de `-2450`, se guarda `2450` con
`type = 'EXPENSE'`. El saldo es:

```sql
SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END)
```

Ventajas: el total de gastos es un `SUM(amount) WHERE type='EXPENSE'` sin
`ABS()`, y un gasto cargado con signo equivocado no puede sumar al saldo. Hay un
`CHECK (amount > 0)` que lo garantiza en la base.

**`NUMERIC(14,2)`, no `float`.** En coma flotante `0.1 + 0.2` no da `0.3`. En
dinero no es aceptable. En Java, `BigDecimal`.

**`TIMESTAMPTZ` para `occurred_at`.** Guarda el instante absoluto. La zona del
usuario se aplica al calcular los períodos, no al guardar.

**Categorías del sistema con `user_id NULL`.** Las siete categorías del
prototipo las comparten todos los usuarios y nadie puede editarlas: si alguien
pudiera renombrar «Comida», se la cambiaría a todos. Un usuario puede crear las
suyas propias.

Como en PostgreSQL dos filas con `NULL` no se consideran duplicadas, la
unicidad del slug se logra con dos índices parciales en lugar de un `UNIQUE`
común:

```sql
CREATE UNIQUE INDEX ... ON categories (slug) WHERE user_id IS NULL;
CREATE UNIQUE INDEX ... ON categories (user_id, slug) WHERE user_id IS NOT NULL;
```

**Correo único sin distinguir mayúsculas.** Índice único sobre `lower(email)`,
y la consulta de Java también compara en minúsculas. Si solo una de las dos
partes lo hiciera, se podrían intentar registrar dos cuentas que la base
considera la misma.

## Períodos y zona horaria

El detalle más delicado del backend.

Las fechas se guardan en UTC, pero «este mes» depende de dónde está el usuario.
Sin convertir, un gasto del 31 de diciembre a las 21:00 de Buenos Aires
(= 1 de enero a las 00:00 UTC) se contaría en el año siguiente.

`ReportPeriod` calcula los límites en la zona de `flux.timezone` y los convierte
al instante absoluto que se compara contra la base. La consulta de tendencia
mensual, que es nativa, aplica `AT TIME ZONE` antes de agrupar.

Los rangos son **semiabiertos**, `[desde, hasta)`. El fin de un período es
exactamente el inicio del siguiente, lo que evita contar un movimiento dos veces
o perderlo entre dos períodos contiguos. Hay una prueba que verifica esa
invariante para los tres períodos.

## Autenticación

Dos tokens, con propósitos distintos:

| Token | Vigencia | Dónde vive | Se puede revocar |
|---|---|---|---|
| Acceso (JWT) | 15 min | solo en el cliente | no |
| Refresco (opaco) | 30 días | hash SHA-256 en la base | sí |

**Por qué dos.** Un JWT firmado no se puede «apagar»: sirve hasta que vence. Si
el único token durara 30 días, cerrar sesión sería imposible de verdad. Con este
esquema, el token de acceso dura poco (si se filtra, sirve 15 minutos) y el de
refresco es revocable: cerrar sesión es marcar una fila.

**Rotación.** Al refrescar, el token presentado se revoca y se entrega otro. Si
un token viejo se reutiliza, ya no sirve, y eso delata que fue robado.

**Qué lleva el JWT.** Id, correo y nombre, para resolver cada petición sin
consultar la base. Nada sensible: un JWT va firmado pero **no cifrado**, y
cualquiera que lo tenga puede leer su contenido. Lo que la firma garantiza es que
nadie pudo modificarlo.

**Hashes.** Las contraseñas con BCrypt de coste 10: un hash lento a propósito,
que hace inviable la fuerza bruta aunque se filtre la base. Los tokens, con
SHA-256: ahí sí sirve un hash rápido, porque son 32 bytes aleatorios y no hay
diccionario con el que probarlos.

**Enumeración de usuarios.** Si el correo no existe, el inicio de sesión
igualmente verifica la contraseña contra un hash ficticio. Sin eso, el servidor
respondería más rápido cuando el correo no existe, y esa diferencia de tiempo
permite averiguar quién tiene cuenta. Por el mismo motivo,
`/auth/forgot-password` responde siempre lo mismo.

## Aislamiento entre usuarios

Es la propiedad de seguridad más importante del sistema. Tres mecanismos:

1. El `userId` sale **siempre** del token. Ningún endpoint lo acepta como
   parámetro: si lo hiciera, bastaría cambiar un id en la URL.
2. Toda consulta filtra por `id` **y** `userId`
   (`findByIdAndUserId`, `TransactionSpecifications.ownedBy`).
3. Un recurso ajeno devuelve **404, no 403**: un 403 confirmaría que ese
   movimiento existe y es de alguien más.

Verificado: un segundo usuario que intenta leer, modificar o borrar un
movimiento ajeno recibe 404 en los tres casos, y su resumen muestra saldo cero.

> Esto es, en la capa de aplicación, lo que en Supabase haría Row Level Security
> en la capa de base de datos. Con un backend propio, la frontera está en el
> servicio: ningún cliente habla directamente con la base.

## Filtros y agregación

El listado de movimientos usa la API de **Specification** (Criteria de JPA) en
lugar de una consulta con `(:param IS NULL OR ...)`. Dos razones: construye solo
las condiciones que el usuario pidió, y evita pasar parámetros nulos cuyo tipo
Hibernate no puede deducir.

El detalle que lo hace valer la pena: el **mismo** `Specification` se reutiliza
para calcular los totales del filtro, mediante el fragmento de repositorio
`TransactionAggregationRepository`. Si las condiciones se escribieran dos veces
—una para la lista y otra para los totales—, las dos copias terminarían
desincronizándose. Que es, exactamente, lo que le pasó al prototipo.

Las sumas se hacen **en SQL**, nunca trayendo filas a Java. Con 12 movimientos
da igual; con un año de historia, no.

El `@EntityGraph` en las consultas de movimientos trae categoría y cuenta en la
misma consulta. Sin él, convertir 20 movimientos a DTO dispararía 40 consultas
extra (el problema N+1).

## Asistente de IA

```
cliente móvil
    │  POST /api/v1/ai/chat  { question }
    │  Authorization: Bearer <token>
    ▼
AiController ──► AiChatService
                      │
                      ├─► AiContextBuilder ──► ReportService + TransactionService
                      │        (saldo, categorías, tendencia, movimientos)
                      │
                      └─► SDK de Anthropic ──► modelo
                               (la clave vive solo acá)
```

**La clave de la API nunca sale del servidor.** Si estuviera en el frontend,
cualquiera podría extraerla del código descargado y gastar crédito a nombre del
proyecto. Este endpoint es la única puerta al modelo, y exige token de sesión.

**El contexto lo arma el servidor**, a partir del `userId` del token. El cliente
no lo envía ni puede modificarlo, así que no puede pedir un resumen de las
finanzas de otra persona.

**Se envía un resumen agregado, no la tabla completa.** Con un año de historia,
mandar todos los movimientos costaría una fortuna en tokens; los totales ya
responden la mayoría de las preguntas. Se adjuntan los 15 movimientos más
recientes para las preguntas de detalle.

**Las instrucciones prohíben inventar cifras.** Es el error exacto que cometía
el chat del prototipo, cuyas respuestas estaban escritas a mano y afirmaban
cosas que los datos no respaldaban. También prohíben dar consejos de inversión,
que en una aplicación financiera es tanto un problema legal como de
responsabilidad.

**Configuración del modelo:** `claude-opus-5-5` con nivel de esfuerzo `LOW`, que
alcanza para respuestas de chat y es la opción más económica. En este modelo el
razonamiento está siempre activo y consume del tope de `max_tokens`, por eso el
tope es 4096 y no un valor ajustado.

**Degradación.** Con `AI_ENABLED=false` el endpoint responde 503 y el resto de la
aplicación funciona igual. Permite desarrollar sin clave y sin gastar crédito.

## Manejo de errores

Un solo formato para todos los errores, de modo que los tres clientes escriban
el manejo una sola vez:

```json
{
  "code": "CATEGORY_KIND_MISMATCH",
  "message": "La categoría 'Ingresos' es de tipo INCOME y no admite un movimiento de tipo EXPENSE.",
  "fields": null,
  "path": "/api/v1/transactions",
  "timestamp": "2026-10-02T21:00:00-03:00"
}
```

`code` es un identificador estable para programar contra él; `message` es texto
en español apto para mostrar al usuario; `fields` trae el detalle campo por campo
cuando falla la validación.

Las excepciones inesperadas **no** llegan al cliente: sus mensajes suelen
filtrar nombres de tablas, rutas o consultas SQL. Se registran en el log y el
cliente recibe un texto genérico.

## Los nulos son información

`savingsRate` e `incomeChangePercent` pueden venir en `null`, y el cliente debe
mostrar un guion, no un cero:

- `savingsRate: null` significa «no hubo ingresos, no se puede calcular el
  ahorro», que no es lo mismo que «ahorré 0 %».
- `incomeChangePercent: null` significa «no hay período anterior con datos».
  Pasar de 0 a 100 no es un aumento del infinito por ciento.

Por eso Jackson está configurado para **serializar los nulos** en lugar de omitir
la clave: un campo ausente obligaría al cliente a distinguir entre ausente y
nulo, y haría el contrato ambiguo.

## Migraciones

El esquema lo manda Flyway; Hibernate está en `ddl-auto: validate` y solo
verifica. Si una entidad y su tabla no coinciden, **el servidor no arranca**.

Eso ya sirvió: la primera ejecución falló porque la columna `currency` era
`CHAR(3)` y Hibernate esperaba `VARCHAR(3)`. Con `ddl-auto: update` el error
habría pasado inadvertido hasta producción.

Las migraciones aplicadas no se editan: se agrega una nueva. Editar una ya
corrida rompe la suma de control de Flyway.

La semilla de prueba vive en `db/dev/` y solo la carga el perfil `dev`; en
producción esa ruta no se incluye.
