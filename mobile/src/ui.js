/* =====================================================================
   Piezas compartidas de las pantallas nativas: botones y la marca.
   ===================================================================== */

import { ActivityIndicator, Image, Pressable, StyleSheet, Text } from 'react-native';
import { colors, radius, space, TOUCH } from './theme';

const ICON = require('../assets/brand-icon.png');

/** Botón principal (relleno claro) o secundario (vidrio). */
export function Button({ label, busyLabel, busy = false, kind = 'primary', onPress }) {
  const primary = kind === 'primary';
  return (
    <Pressable
      onPress={onPress}
      disabled={busy}
      accessibilityRole="button"
      accessibilityState={{ busy, disabled: busy }}
      style={({ pressed }) => [
        styles.button,
        primary ? styles.primary : styles.glass,
        // La respuesta al toque es inmediata, igual que en la app web.
        pressed && styles.pressed,
      ]}
    >
      {busy ? <ActivityIndicator color={primary ? colors.onPrimary : colors.text} /> : null}
      <Text style={[styles.label, { color: primary ? colors.onPrimary : colors.text }]}>
        {busy ? busyLabel ?? label : label}
      </Text>
    </Pressable>
  );
}

/** El ícono de la app; decorativo, el nombre lo dice el texto de al lado. */
export function BrandIcon({ size = 72 }) {
  return (
    <Image
      source={ICON}
      style={{ width: size, height: size }}
      accessible={false}
      accessibilityIgnoresInvertColors
    />
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 52,
    minWidth: TOUCH,
    borderRadius: radius.md,
    paddingHorizontal: space[5],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[2],
  },
  primary: {
    backgroundColor: colors.primary,
  },
  glass: {
    backgroundColor: colors.glassButton,
    borderWidth: 1,
    borderColor: colors.line,
  },
  pressed: {
    opacity: 0.8,
    transform: [{ scale: 0.98 }],
  },
  label: {
    fontSize: 16,
    fontWeight: '700',
  },
});
