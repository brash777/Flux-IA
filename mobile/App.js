/* =====================================================================
   Flux IA para Expo (Expo Go y APK).

   No es una segunda app: muestra la misma app web que entrega el backend,
   dentro de una WebView. Hay un solo frontend que mantener, con sus reglas
   (CLAUDE.md), y el celular solo necesita saber dónde está el servidor.
   La decisión está explicada en mobile/README.md.
   ===================================================================== */

import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import ServerScreen from './src/ServerScreen';
import WebAppScreen from './src/WebAppScreen';
import { resolveServerUrl } from './src/serverStore';
import { colors } from './src/theme';

export default function App() {
  // undefined: todavía se está buscando la dirección.
  // null: no hay ninguna y hay que pedirla.
  const [serverUrl, setServerUrl] = useState(undefined);
  const [changing, setChanging] = useState(false);

  useEffect(() => {
    resolveServerUrl()
      .then(setServerUrl)
      .catch(() => setServerUrl(null));
  }, []);

  function connected(url) {
    setServerUrl(url);
    setChanging(false);
  }

  let screen = null;
  if (serverUrl === undefined) {
    // En Expo Go puede tardar un momento: busca el backend en el computador.
    screen = (
      <View style={styles.center}>
        <ActivityIndicator color={colors.accent} size="large" accessibilityLabel="Buscando el servidor" />
      </View>
    );
  } else if (serverUrl === null || changing) {
    screen = (
      <ServerScreen
        initialUrl={serverUrl ?? ''}
        onConnected={connected}
        onCancel={serverUrl ? () => setChanging(false) : null}
      />
    );
  } else if (serverUrl) {
    // key: con otra dirección se arma una WebView nueva, sin historial viejo.
    screen = <WebAppScreen key={serverUrl} serverUrl={serverUrl} onChangeServer={() => setChanging(true)} />;
  }

  return (
    <SafeAreaProvider>
      {/* La app web se diseñó sin contar las barras del sistema: el margen
          lo pone esta vista, del mismo color que el fondo de la app. */}
      <SafeAreaView style={styles.root} edges={['top', 'bottom', 'left', 'right']}>
        <StatusBar style="light" />
        {screen}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
