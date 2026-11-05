/* =====================================================================
   La app web de Flux IA dentro de una WebView.

   La WebView abre lo que entrega el backend (frontend/), así que la API
   está en el mismo origen y no hay CORS ni IPs escritas en el frontend.
   Esta pantalla solo se ocupa de lo que el navegador no da: el botón
   «atrás» de Android, la espera inicial y qué hacer si el servidor no
   responde.
   ===================================================================== */

import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, BackHandler, KeyboardAvoidingView, Linking, Platform, StyleSheet, Text, View } from 'react-native';
import { WebView } from 'react-native-webview';
import { colors, space } from './theme';
import { BrandIcon, Button } from './ui';

export default function WebAppScreen({ serverUrl, onChangeServer }) {
  const web = useRef(null);
  const canGoBack = useRef(false);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  // «Atrás» recorre las pantallas de la app (#/inicio, #/movimientos…)
  // antes de cerrarla, como en el navegador.
  useEffect(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!canGoBack.current) return false;
      web.current?.goBack();
      return true;
    });
    return () => subscription.remove();
  }, []);

  function fail(detail) {
    // El detalle técnico va al registro de Expo, nunca a la pantalla.
    console.warn('No cargó Flux IA desde', serverUrl, detail);
    setFailed(true);
  }

  function retry() {
    setFailed(false);
    setLoading(true);
    web.current?.reload();
  }

  // Un enlace a otro sitio se abre en el navegador, no dentro de la app.
  function shouldStart(request) {
    if (request.url.startsWith(serverUrl) || request.url.startsWith('about:')) return true;
    Linking.openURL(request.url).catch(() => {});
    return false;
  }

  return (
    // Android dibuja de borde a borde: sin esto, el teclado taparía el
    // campo del chat, que está abajo. En iOS lo resuelve la app web
    // (frontend/js/keyboard.js), igual que en Safari.
    <KeyboardAvoidingView style={styles.flex} behavior="padding" enabled={Platform.OS === 'android'}>
      <WebView
        ref={web}
        source={{ uri: `${serverUrl}/` }}
        style={styles.web}
        originWhitelist={['*']}
        javaScriptEnabled
        // El refreshToken vive en localStorage (CLAUDE.md): sin esto, cada
        // apertura de la app pediría ingresar de nuevo.
        domStorageEnabled
        setSupportMultipleWindows={false}
        allowsBackForwardNavigationGestures
        overScrollMode="never"
        bounces={false}
        contentInsetAdjustmentBehavior="never"
        automaticallyAdjustContentInsets={false}
        // La barra de iOS sobre el teclado (‹ › y ✓) es de formularios web:
        // en el chat solo le quita espacio a la conversación.
        hideKeyboardAccessoryView
        onLoadEnd={() => setLoading(false)}
        onError={(event) => fail(event.nativeEvent.description)}
        onHttpError={(event) => fail(`HTTP ${event.nativeEvent.statusCode}`)}
        onNavigationStateChange={(state) => {
          canGoBack.current = state.canGoBack;
        }}
        onShouldStartLoadWithRequest={shouldStart}
      />

      {loading && !failed ? (
        <View style={styles.overlay} accessibilityLiveRegion="polite">
          <BrandIcon size={88} />
          <ActivityIndicator color={colors.accent} size="large" />
          <Text style={styles.muted}>Cargando Flux IA…</Text>
        </View>
      ) : null}

      {failed ? (
        <View style={styles.overlay}>
          <BrandIcon size={72} />
          <Text style={styles.title} accessibilityRole="header">No pudimos abrir Flux IA</Text>
          <Text style={styles.text}>
            El servidor en {serverUrl} no respondió. Revisa que el backend esté corriendo y que el
            celular esté en la misma red que el computador.
          </Text>
          <View style={styles.actions}>
            <Button label="Reintentar" onPress={retry} />
            <Button label="Cambiar servidor" kind="glass" onPress={onChangeServer} />
          </View>
        </View>
      ) : null}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  web: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: colors.bg,
    alignItems: 'center',
    justifyContent: 'center',
    padding: space[6],
    gap: space[4],
  },
  title: {
    color: colors.textStrong,
    fontSize: 24,
    fontWeight: '700',
    textAlign: 'center',
  },
  text: {
    color: colors.textMuted,
    fontSize: 16,
    lineHeight: 24,
    textAlign: 'center',
  },
  muted: {
    color: colors.textMuted,
    fontSize: 15,
  },
  actions: {
    alignSelf: 'stretch',
    gap: space[3],
    marginTop: space[2],
  },
});
