import { requireOptionalNativeModule } from 'expo';
import { Platform } from 'react-native';

export type HapticStyle = 'selection' | 'light' | 'medium' | 'heavy' | 'success' | 'error';

const Native =
  Platform.OS === 'android'
    ? requireOptionalNativeModule<{ impact(style: HapticStyle): void }>('LiquidGlassHaptics')
    : null;

/** Small system haptic (no permission needed). No-op where unavailable. */
export function haptic(style: HapticStyle = 'light') {
  try {
    Native?.impact(style);
  } catch {
    // ignore
  }
}
