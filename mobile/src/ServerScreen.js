/* =====================================================================
   Conectar con el servidor: aparece la primera vez y cuando el usuario
   elige «Cambiar servidor». La dirección se prueba antes de guardarla,
   así un error de IP o de puerto se ve aquí y no como una pantalla en
   blanco.
   ===================================================================== */

import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { checkServer, saveServerUrl } from './serverStore';
import { normalizeServerUrl } from './serverUrl';
import { colors, radius, space } from './theme';
import { BrandIcon, Button } from './ui';

const EXAMPLE = 'http://192.168.1.20:8080';

export default function ServerScreen({ initialUrl, onConnected, onCancel }) {
  const [value, setValue] = useState(initialUrl ?? '');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function connect() {
    if (busy) return;
    const url = normalizeServerUrl(value);
    if (!url) {
      setError(`Escribe una dirección como ${EXAMPLE}.`);
      return;
    }
    setBusy(true);
    setError(null);
    const problem = await checkServer(url);
    if (problem) {
      setBusy(false);
      setError(problem);
      return;
    }
    await saveServerUrl(url);
    onConnected(url);
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.brand}>
          <BrandIcon size={56} />
          <Text style={styles.brandName}>Flux IA</Text>
        </View>

        <Text style={styles.title} accessibilityRole="header">Conecta con tu servidor</Text>
        <Text style={styles.text}>
          Escribe la dirección del backend de Flux IA. Si corre en tu computador, es la IP del
          computador en la red Wi-Fi y el puerto del servidor.
        </Text>

        <View style={styles.card}>
          <Text style={styles.label} nativeID="server-label">Dirección del servidor</Text>
          <TextInput
            value={value}
            onChangeText={(text) => {
              setValue(text);
              setError(null);
            }}
            onSubmitEditing={connect}
            placeholder={EXAMPLE}
            placeholderTextColor={colors.textSubtle}
            keyboardType="url"
            autoCapitalize="none"
            autoCorrect={false}
            autoComplete="off"
            returnKeyType="go"
            selectionColor={colors.accent}
            accessibilityLabelledBy="server-label"
            accessibilityLabel="Dirección del servidor"
            style={[styles.input, error && styles.inputError]}
          />
          {error ? (
            <Text style={styles.error} accessibilityRole="alert" accessibilityLiveRegion="polite">
              {error}
            </Text>
          ) : null}
          <Button label="Conectar" busyLabel="Conectando…" busy={busy} onPress={connect} />
          {onCancel ? <Button label="Volver" kind="glass" onPress={onCancel} /> : null}
        </View>

        <Text style={styles.hint}>
          El celular y el computador tienen que estar en la misma red. En el computador, la IP sale
          con «ipconfig getifaddr en0» (macOS) o con «ipconfig» (Windows).
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: space[6],
    gap: space[4],
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    marginBottom: space[4],
  },
  brandName: {
    color: colors.textStrong,
    fontSize: 20,
    fontWeight: '700',
  },
  title: {
    color: colors.textStrong,
    fontSize: 30,
    lineHeight: 36,
    fontWeight: '700',
  },
  text: {
    color: colors.textMuted,
    fontSize: 16,
    lineHeight: 24,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.line,
    padding: space[5],
    gap: space[3],
    marginTop: space[2],
  },
  label: {
    color: colors.text,
    fontSize: 14,
    fontWeight: '600',
  },
  input: {
    minHeight: 52,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    color: colors.textStrong,
    fontSize: 16,
    paddingHorizontal: space[4],
  },
  inputError: {
    borderColor: colors.danger,
  },
  error: {
    color: colors.danger,
    fontSize: 14,
    lineHeight: 20,
  },
  hint: {
    color: colors.textSubtle,
    fontSize: 13,
    lineHeight: 19,
  },
});
