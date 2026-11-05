# Flux IA — App móvil (Expo)

Flux IA en el celular: se abre con **Expo Go** para desarrollar y probar, y
se compila como **APK** para instalarla en Android.

Volver al [README del proyecto](../README.md).

## Qué es y por qué así

La app de Expo **no es un segundo frontend**. Muestra, dentro de una
WebView, la misma app web de [`../frontend/`](../frontend/), que entrega el
propio backend. Lo nativo se limita a lo que el navegador no da: pedir la
dirección del servidor, la pantalla de carga, el aviso de «sin conexión» y
el botón «atrás» de Android.

| Decisión | Por qué |
|---|---|
| WebView en lugar de reescribir las pantallas en React Native | Un solo frontend que mantener. Las reglas del proyecto (ninguna cifra calculada en el cliente, nada de `innerHTML` con texto externo, estados de carga, vacío y error) ya están cumplidas y probadas en la app web; duplicarlas abriría la puerta a que dos clientes muestren cifras distintas, el error que este proyecto existe para evitar. |
| El backend entrega la app web | La app y la API comparten origen: el celular necesita **una sola dirección**, sin CORS ni IPs escritas en el frontend. |
| En Expo Go la dirección se busca sola; en el APK se pide | La IP del computador cambia con cada red. En Expo Go, la app llega desde el computador que corre el servidor de Expo, el mismo del backend: busca Flux IA en esa IP y, si cambió, corrige la guardada. En el APK no hay servidor de Expo: la dirección se escribe una vez, se comprueba que responda Flux IA (y no otra app en el mismo puerto) y queda guardada. |
| Texto en claro permitido (`usesCleartextTraffic`, `NSAllowsArbitraryLoads`) | Un servidor en la red local no tiene HTTPS. Detrás de un túnel o en producción, la dirección es `https://` y esto no se usa. |

## Requisitos

- **Node 20 o superior** en el computador.
- En el celular, **Expo Go** de la tienda (Play Store o App Store). Este
  proyecto usa el **SDK 57**, el que trae Expo Go en las tiendas. El SDK 58
  ya salió, pero su Expo Go todavía no llegó a la App Store ni a Play Store:
  no actualizar el proyecto hasta que llegue, o Expo Go dirá que es
  incompatible.
- En iPhone, cuando Expo Go pida acceso a la **red local**, hay que
  permitirlo: sin eso no llega ni al servidor de Expo ni al backend.
- El backend corriendo (ver [backend/README.md](../backend/README.md)).

## Abrirla con Expo Go

1. Levanta el backend con datos de prueba, desde `backend/`:

   ```bash
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```

   Comprueba que la app web abre en el computador: `http://localhost:8080/`
   (o el `PORT` que tengas en `backend/.env`).

2. Desde `mobile/`:

   ```bash
   npm install
   npm start
   ```

   El servidor de Expo usa el puerto **8090**, para no chocar con el del
   backend.

3. Escanea el código QR: en Android, desde Expo Go; en iPhone, con la cámara.

4. La app busca el backend sola en el computador desde el que se cargó: la
   IP que anuncia el servidor de Expo, con el puerto guardado y con los
   habituales (8080 y 8081). Si lo encuentra, abre Flux IA directamente.

   Si no lo encuentra (el backend usa otro puerto o corre en otro
   computador), pide la dirección: escribe la IP y el puerto del backend,
   por ejemplo `http://192.168.1.20:8080`, y toca **Conectar**. La IP sale
   con `ipconfig getifaddr en0` en macOS o con `ipconfig` en Windows
   («Dirección IPv4»). Si en esa dirección responde otra cosa, la app lo
   dice.

5. Ingresa con el usuario de prueba: `diego.f@fluxia.app` / `Flux2026!`.

> **El celular y el computador tienen que estar en la misma red.** Muchas
> redes de universidades y oficinas aíslan a los dispositivos entre sí: el
> QR abre, pero la app no llega al servidor. En ese caso, activa el punto de
> acceso del celular, conecta el computador a él y vuelve a arrancar
> `npm start`.

> **Si el computador cambia de red** (otra Wi-Fi, o el router le da otra
> IP), el QR viejo deja de servir: detén `npm start` con Ctrl+C, arráncalo
> de nuevo y escanea el QR nuevo. La app encuentra el backend en la IP
> nueva sin que haya que escribirla.

## Generar el APK

El APK se compila en la nube con **EAS Build**: no hace falta Android
Studio. Necesitas una cuenta gratuita de Expo (https://expo.dev/signup).

```bash
npx eas-cli@latest login     # una sola vez
npm run apk                  # perfil «preview» de eas.json: APK, no AAB
```

El proyecto ya está vinculado a EAS (`extra.eas.projectId` en `app.json`) y
la clave de firma vive en los servidores de Expo. Al terminar (unos minutos,
según la cola) muestra un enlace y un QR para descargar el APK; también queda
en expo.dev → el proyecto → Builds. En el celular, permite instalar apps de
origen desconocido cuando Android lo pida.

> **Por qué el script lleva `EAS_NO_VCS` y `EAS_PROJECT_ROOT`.** La carpeta del
> repositorio termina en espacios («Flux IA  »), y `eas-cli` recorta la ruta
> que le devuelve git: busca una carpeta que no existe y falla con
> `spawn git ENOENT`. Sin git, sube solo `mobile/` desde la ruta exacta, que
> además es todo lo que la compilación necesita. Si la carpeta se renombra sin
> espacios al final, el script funciona igual.

El APK solo se instala en **Android**. En un iPhone, la app se abre con Expo Go
o en Safari (`http://<IP>:<PORT>/`, y «Compartir → Agregar a inicio»).

El APK no tiene un servidor de Expo del que sacar la IP: la primera vez pide
la dirección del servidor. Para que ya traiga una, defínela en `eas.json`
antes de compilar:

```json
"preview": {
  "distribution": "internal",
  "env": { "EXPO_PUBLIC_SERVER_URL": "https://tu-servidor" },
  "android": { "buildType": "apk" }
}
```

## Si algo no funciona

| Síntoma | Causa probable |
|---|---|
| «En esa dirección responde otra aplicación» | El puerto es de otro programa. Revisa el `PORT` de `backend/.env`. |
| «El servidor no respondió a tiempo» | El celular no llega al computador: otra red, o una red que aísla dispositivos. Usa el punto de acceso del celular. |
| Expo Go no abre el proyecto después de cambiar de red | El QR tiene la IP vieja del computador. Reinicia `npm start` y escanea el QR nuevo. |
| «Algo salió mal» al ingresar, después de un rato sin tocar el computador | El computador se suspendió y cortó la conexión con la base de datos. El backend la recupera solo en unos segundos: vuelve a intentar. Para una demostración, conecta el cargador y arranca el backend con `caffeinate -ims` delante (macOS), para que no se suspenda mientras corre. |
| Expo Go dice «Project is incompatible with this version of Expo Go» | El proyecto usa un SDK más nuevo que el Expo Go de la tienda. Este usa el 57, el de la tienda; si alguien lo subió, volver al 57 con `npx expo install expo@~57.0.0 --fix`. |
| La app abre, pero el chat dice que el asistente está apagado | El backend corre con `AI_ENABLED=false`. Ver [backend/README.md](../backend/README.md). |
| Hay que cambiar la dirección guardada | Si el servidor no responde, la app ofrece **Cambiar servidor**. |

## Estructura

```
mobile/
├── App.js              elige entre pedir la dirección y mostrar la app
├── app.json            nombre, ícono, permisos de red, pantalla de carga
├── eas.json            perfil «preview»: compila un APK
├── .env.example        dirección por omisión (opcional)
├── src/
│   ├── ServerScreen.js     pide y comprueba la dirección del servidor
│   ├── WebAppScreen.js     la app web en una WebView; carga y error
│   ├── serverStore.js      guarda la dirección, comprueba que sea Flux IA y la busca en Expo Go
│   ├── serverUrl.js        normaliza la dirección y elige dónde buscar (sin React Native)
│   ├── theme.js            colores de frontend/css/tokens.css
│   └── ui.js               botones y la marca
├── assets/             íconos generados desde frontend/assets/icons
└── test/               pruebas de serverUrl.js (npm test)
```

Las carpetas `android/` e `ios/` no existen a propósito: Expo las genera al
compilar a partir de `app.json`.

## Pruebas

```bash
npm test             # dirección del servidor y dónde buscar el backend
npx expo-doctor      # dependencias compatibles con el SDK 57
```
