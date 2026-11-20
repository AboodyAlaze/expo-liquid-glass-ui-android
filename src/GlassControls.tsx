import { requireNativeView } from 'expo';
import { useContext } from 'react';
import {
  Platform,
  Switch,
  View,
  processColor,
  useColorScheme,
  type ColorValue,
  type StyleProp,
  type ViewProps,
  type ViewStyle,
} from 'react-native';

import { QualityContext, type GlassQuality } from './quality';

type ValueEvent<T> = { nativeEvent: { value: T } };

// ───────────────────────── Switch ─────────────────────────

export type GlassSwitchProps = {
  value: boolean;
  onValueChange?: (value: boolean) => void;
  /** Track color when on. Default iOS green. */
  onColor?: ColorValue;
  /** Track color when off. Default: translucent gray. */
  offColor?: ColorValue;
  disabled?: boolean;
  dark?: boolean;
  /** GPU budget for the lens: 'auto' (default) | 'high' | 'medium' | 'low'. */
  quality?: GlassQuality;
  /** Default size 63 × 28 (iOS 26). */
  style?: StyleProp<ViewStyle>;
};

type NativeSwitchProps = ViewProps & {
  value: boolean;
  onColor?: number | null;
  offColor?: number | null;
  disabled?: boolean;
  dark: boolean;
  quality?: GlassQuality;
  onValueChange?: (e: ValueEvent<boolean>) => void;
};

const NativeSwitch =
  Platform.OS === 'android' ? requireNativeView<NativeSwitchProps>('LiquidGlassSwitch') : null;

/** iOS 26-style switch: tap or drag; the knob turns into a liquid lens while you hold it. */
export function GlassSwitch({ value, onValueChange, onColor, offColor, disabled, dark, quality, style }: GlassSwitchProps) {
  const scheme = useColorScheme();
  const providerQuality = useContext(QualityContext);
  if (!NativeSwitch) {
    return <Switch value={value} onValueChange={onValueChange} disabled={disabled} trackColor={{ true: onColor as string }} style={style} />;
  }
  return (
    <NativeSwitch
      style={[{ width: 63, height: 28 }, style]}
      value={value}
      onColor={onColor == null ? null : (processColor(onColor) as number)}
      offColor={offColor == null ? null : (processColor(offColor) as number)}
      disabled={disabled}
      dark={dark ?? scheme === 'dark'}
      quality={quality ?? providerQuality}
      onValueChange={(e) => onValueChange?.(e.nativeEvent.value)}
    />
  );
}

// ───────────────────────── Slider ─────────────────────────

export type GlassSliderProps = {
  value: number;
  onValueChange?: (value: number) => void;
  /** Called once when the finger lifts. */
  onSlidingComplete?: (value: number) => void;
  minimumValue?: number;
  maximumValue?: number;
  /** 0 = continuous. With a step, the thumb snaps and ticks (haptic) on every step. */
  step?: number;
  /** Filled part of the track. Default iOS blue. */
  accentColor?: ColorValue;
  /** Empty part of the track. */
  trackColor?: ColorValue;
  disabled?: boolean;
  dark?: boolean;
  /** GPU budget for the lens: 'auto' (default) | 'high' | 'medium' | 'low'. */
  quality?: GlassQuality;
  /** Default: full width, 36 high. */
  style?: StyleProp<ViewStyle>;
};

type NativeSliderProps = ViewProps & {
  value: number;
  minimumValue: number;
  maximumValue: number;
  step: number;
  accentColor?: number | null;
  trackColor?: number | null;
  disabled?: boolean;
  dark: boolean;
  quality?: GlassQuality;
  onValueChange?: (e: ValueEvent<number>) => void;
  onSlidingComplete?: (e: ValueEvent<number>) => void;
};

const NativeSlider =
  Platform.OS === 'android' ? requireNativeView<NativeSliderProps>('LiquidGlassSlider') : null;

/** iOS 26-style slider: the thumb becomes a liquid lens while dragged and stretches with speed. */
export function GlassSlider({
  value,
  onValueChange,
  onSlidingComplete,
  minimumValue = 0,
  maximumValue = 1,
  step = 0,
  accentColor,
  trackColor,
  disabled,
  dark,
  quality,
  style,
}: GlassSliderProps) {
  const scheme = useColorScheme();
  const providerQuality = useContext(QualityContext);
  if (!NativeSlider) return <View style={[{ height: 36, alignSelf: 'stretch' }, style]} />;
  return (
    <NativeSlider
      style={[{ height: 36, alignSelf: 'stretch' }, style]}
      minimumValue={minimumValue}
      maximumValue={maximumValue}
      step={step}
      value={value}
      accentColor={accentColor == null ? null : (processColor(accentColor) as number)}
      trackColor={trackColor == null ? null : (processColor(trackColor) as number)}
      disabled={disabled}
      dark={dark ?? scheme === 'dark'}
      quality={quality ?? providerQuality}
      onValueChange={(e) => onValueChange?.(e.nativeEvent.value)}
      onSlidingComplete={(e) => onSlidingComplete?.(e.nativeEvent.value)}
    />
  );
}
