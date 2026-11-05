# Flux IA — Pruebas de punta a punta

Prueban el sistema completo contra el servidor corriendo: la API con su base
de datos real y la interfaz en un Chrome con tamaño de celular. Complementan
las pruebas unitarias del backend (`cd backend && ./mvnw test`), que no usan
base de datos.

Volver al [README del proyecto](../README.md).

## Cómo correrlas

1. Levanta el backend con el perfil `dev` (la semilla trae el usuario demo y
   la recuperación devuelve el código en la respuesta):

   ```bash
   cd backend
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```

2. Desde `e2e/`, la primera vez:

   ```bash
   npm install
   ```

   Instala solo `playwright-core`, sin navegadores: la prueba de interfaz usa
   el Google Chrome instalado. Para otro Chrome, `CHROME_PATH=/ruta/al/chrome`.

3. Correrlas (cambia el puerto si tu `backend/.env` usa otro):

   ```bash
   node api.mjs http://localhost:8080          # 78 pruebas de la API
   node ui.mjs  http://localhost:8080 390      # 36 de la interfaz, a 390 px
   node ui.mjs  http://localhost:8080 360      # las mismas, a 360 px
   ```

   Cada línea dice `PASS` o `FAIL`; al final, el total. Terminan con código 1
   si algo falla.

## Qué comprueban

**`api.mjs`**: todos los endpoints, con sus casos límite.

- La regla de oro: la dona suma exactamente el gasto del resumen, la lista
  del mes da los mismos totales y la tendencia coincide con el mes actual.
- Crear un gasto de 123,45 baja el saldo exactamente 123,45; borrarlo lo
  devuelve al valor inicial.
- Validaciones: monto negativo, tres decimales, texto en lugar de número,
  categoría que no corresponde al tipo, JSON roto. Siempre 400, nunca 500.
- Aislamiento: otro usuario no puede leer, editar ni borrar un movimiento
  ajeno, y recibe 404, no 403.
- Sesión y contraseñas: rotación del token de refresco, cierre de sesión,
  cambio y recuperación de contraseña (que cierra las demás sesiones).

**`ui.mjs`**: el recorrido de un usuario por todas las pantallas.

- Ingresar (con la contraseña mal y bien), crear, buscar, editar y borrar un
  movimiento, los tres períodos de reportes, el chat, el perfil y el cierre
  de sesión.
- Registrarse, recargar sin perder la sesión y recuperar la contraseña.
- Una caída de red simulada: la pantalla muestra el error y «Reintentar» la
  recupera.
- Que la cifra animada del saldo termine exactamente en el valor de la API,
  que no haya scroll horizontal y que la consola quede sin errores.

Deja capturas de cada pantalla en `capturas/<ancho>/`.

## Datos que dejan

Cada corrida crea dos usuarios de prueba (`prueba.e2e.<n>@fluxia.test` y
`ui.e2e.<n>@fluxia.test`). Los movimientos que crean en el usuario demo los
borran al terminar, así que el demo queda como estaba.
