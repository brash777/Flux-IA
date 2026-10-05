# Convenciones del proyecto Flux IA

Este archivo recoge las reglas del proyecto. Si una decisión contradice algo de
acá, hay que justificarla en el mensaje del commit.

## Estructura del repositorio

```
backend/    Spring Boot: pom.xml, mvnw, .mvn/, src/, .env.example
frontend/   app móvil en HTML, CSS y JavaScript puros
docs/       arquitectura, auditoría del prototipo, prototipo y referencias/
```

- Los comandos de Maven se ejecutan **desde `backend/`**
  (`cd backend && ./mvnw test`).
- El `.env` vive en `backend/.env`. `application.yml` lo carga con la ruta
  relativa `./.env`, así que el servidor tiene que arrancarse desde `backend/`;
  desde otra carpeta no lo encuentra y falla por falta de variables.

## Idioma

- **Código en inglés:** nombres de clases, métodos, variables, tablas y columnas.
- **Todo lo demás en español:** comentarios, Javadoc, mensajes de error de la
  API, textos de validación, descripciones de OpenAPI, commits y documentación.
- Los mensajes de error que ve el usuario se escriben para el usuario, no para
  el programador: «El correo o la contraseña no son correctos», no
  «BadCredentialsException».

## Regla de oro: una sola fuente de verdad

**Toda cifra sale de la tabla `transactions`.** Nunca se guarda un total
calculado en otra columna ni se escribe un número a mano en el código, ni
siquiera como valor por omisión.

Si hace falta un total nuevo, se agrega una consulta de agregación en
`TransactionRepository`, no un campo en otra tabla.

Este proyecto nació de un prototipo cuyas pantallas mostraban cuatro cifras
distintas para el mismo mes (ver `docs/auditoria-prototipo.md`). Esa es la regla
que lo evita.

## Dinero

- `BigDecimal` en Java, `NUMERIC(14,2)` en PostgreSQL. **Nunca `double` ni
  `float`.**
- Los montos se guardan **siempre positivos**. El sentido del dinero vive en la
  columna `type` (`INCOME` / `EXPENSE`).
- Las sumas se hacen en SQL, no trayendo filas a Java. PostgreSQL suma sobre un
  índice; Java tendría que mover todos los datos.
- Al dividir, siempre con escala y modo de redondeo explícitos
  (`divide(x, 6, RoundingMode.HALF_UP)`). Sin ellos, `BigDecimal.divide` lanza
  excepción con los decimales periódicos.

## Fechas

- En la base de datos, `TIMESTAMPTZ`. En Java, `OffsetDateTime`.
- Los rangos son **semiabiertos**: `[desde, hasta)`. Así un movimiento no se
  cuenta dos veces entre dos períodos contiguos.
- Los límites de los períodos se calculan en la zona de `flux.timezone`, no en
  UTC. Un gasto del 31 de diciembre a las 21:00 de Buenos Aires pertenece a
  diciembre, no a enero.

## Seguridad

- **Nunca un secreto en el código.** Todo valor sensible se lee de una variable
  de entorno. Si falta, la aplicación no arranca: es mejor fallar al inicio que
  correr con una clave por omisión.
- El `userId` sale **siempre** del token JWT. Ningún endpoint lo acepta como
  parámetro.
- Toda consulta de un recurso propio filtra por `id` **y** `userId`. Un recurso
  ajeno devuelve 404, no 403.
- Contraseñas con BCrypt de coste 10. Tokens de sesión y de recuperación: se
  guarda el SHA-256, nunca el token.
- Al cliente nunca se le manda el mensaje de una excepción inesperada: puede
  filtrar nombres de tablas o consultas SQL. Van al log del servidor.
- La clave de la IA no sale del servidor. El frontend llama a
  `POST /api/v1/ai/chat`; el backend llama al modelo.

## Base de datos

- El esquema lo manda **Flyway**, no Hibernate. `ddl-auto` está en `validate`:
  Hibernate solo verifica y aborta si las entidades no coinciden con las tablas.
- **Las migraciones aplicadas no se editan.** Se agrega una `V{n+1}` nueva.
  Editar una ya aplicada rompe la suma de control de Flyway en cualquier entorno
  que la haya corrido.
- La semilla de prueba vive en `backend/src/main/resources/db/dev/` y se carga solo con el perfil `dev`.
  En producción esa carpeta no se incluye.
- Toda columna que se filtre o se ordene necesita índice. Los que ya existen
  están justificados con un comentario en la migración.

## Código

- **Sin Lombok.** Getters y setters escritos a mano, para que no haya
  procesamiento de anotaciones que configurar en el IDE.
- Inyección **por constructor**, nunca con `@Autowired` en el campo. Deja claras
  las dependencias y permite construir la clase en una prueba.
- `record` para los DTO; clases normales para las entidades JPA.
- Las entidades nunca se devuelven desde un controlador: siempre un DTO. Así la
  forma de la API no queda atada a la forma de las tablas.
- Un paquete por concepto del dominio (`transaction/`, `report/`), no por capa
  técnica. Todo lo que toca movimientos vive junto.
- Las relaciones `@ManyToOne` van en `LAZY`. Cuando hacen falta en la misma
  consulta, se usa `@EntityGraph` para evitar el problema N+1.

## Comentarios

Se comenta **el por qué, no el qué**. `// suma los montos` sobra; explicar por
qué la suma va en SQL y no en Java, no.

Las decisiones que un evaluador podría cuestionar llevan una o dos frases de
justificación en el Javadoc de la clase. Este es un proyecto académico y hay que
poder sustentarlo oralmente.

## Pruebas

- Las pruebas unitarias no necesitan base de datos: se usan dobles de prueba con
  Mockito.
- Se prueba la aritmética y los casos límite, no los getters.
- Los nombres de prueba van en español con `@DisplayName` y describen el
  comportamiento esperado, no el método: «Sin ingresos, la tasa de ahorro es
  null y no 0 %».
- El SQL se valida levantando la aplicación contra PostgreSQL. `ddl-auto` en
  `validate` hace que el arranque falle si el esquema y las entidades difieren.

## Commits

- Pequeños y con un solo propósito.
- Mensaje en español, en imperativo, con prefijo:
  `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, `build:`.
- El cuerpo explica **por qué**, no qué archivos cambiaron: eso ya lo dice el
  diff.

## Lo que falta antes de producción

1. **Servicio de correo** para los enlaces de recuperación. Hoy el token se
   devuelve en la respuesta en perfil `dev`; en producción hay que enviarlo por
   correo y dejar de devolverlo.
2. **Apagar la documentación pública:** `springdoc.api-docs.enabled=false`.
3. **Limitar la frecuencia de peticiones** en `/auth/login` y en `/ai/chat`.
4. **Limpieza periódica** de `refresh_tokens` y `password_reset_tokens`
   vencidos (el método del repositorio ya existe, falta la tarea programada).
5. **Evaluar los `fallbacks` del modelo de IA** para que una negativa del modelo
   no deje al chat sin respuesta.
