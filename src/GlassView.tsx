import { requireNativeView } from 'expo';
import { type ReactNode } from 'react';
import {
  Platform,
  StyleSheet,
  Text,
  View,
  processColor,
  useColorScheme,
  type ColorValue,
  type StyleProp,
  type TextStyle,
  type ViewProps,
  type ViewStyle,
} from 'react-native';

export type GlassLookProps = {
  /** Glass color laid over the refraction, e.g. 'rgba(255,255,255,0.15)'. Default depends on `dark`. */
  tintColor?: ColorValue;
  /** Blur of what's behind, in dp. Default 3 (liquid, not frosted). */
  blur?: number;
  /** How strongly the rim bends the background, in dp. Default 14. */
  refraction?: number;
  /** Rainbow split at the rim (0–0.3). Default 0.1. */
  chroma?: number;
  /** Specular rim strength. Default 1. */
  highlight?: number;
  /** Dark or light glass. Default: follows the system. */
  dark?: boolean;
};

export type GlassViewProps = ViewProps &
  GlassLookProps & {
    /**
     * Any normal style: width, height, padding, margin, flex, borderRadius, borderWidth,
     * borderColor… `borderRadius` shapes the glass; `borderWidth` / `borderColor` draw its border.
     */
    style?: StyleProp<ViewStyle>;
    children?: ReactNode;
  };

type NativeGlassProps = ViewProps & {
  cornerRadius: number;
  glassBorderWidth: number;
  glassBorderColor?: number | null;
  tintColor?: number | null;
  blur?: number;
  refraction?: number;
  chroma?: number;
  highlight?: number;
  dark: boolean;
  interactive?: boolean;
  disabled?: boolean;
  onPress?: () => void;
  onLongPress?: () => void;
};

const NativeGlass =
  Platform.OS === 'android' ? requireNativeView<NativeGlassProps>('LiquidGlassView') : null;

const color = (c?: ColorValue | null) => (c == null ? null : (processColor(c) as number | null));

/** Splits a style: shape/border/background go to the glass, everything else stays a normal style. */
function splitStyle(style: StyleProp<ViewStyle>, defaultRadius: number) {
  const flat = { ...(StyleSheet.flatten(style) ?? {}) } as ViewStyle;
  const radius = typeof flat.borderRadius === 'number' ? flat.borderRadius : defaultRadius;
  const borderWidth = typeof flat.borderWidth === 'number' ? flat.borderWidth : 0;
  const borderColor = flat.borderColor;
  const background = flat.backgroundColor;
  delete flat.borderRadius;
  delete flat.borderWidth;
  delete flat.borderColor;
  delete flat.backgroundColor;
  return { rest: flat, radius, borderWidth, borderColor, background };
}

/** Internal: shared by GlassView, GlassCard and GlassButton. */
export function GlassSurface({
  style,
  children,
  tintColor,
  blur,
  refraction,
  chroma,
  highlight,
  dark,
  interactive,
  disabled,
  onPress,
  onLongPress,
  defaultRadius = 24,
  ...rest
}: GlassViewProps & {
  interactive?: boolean;
  disabled?: boolean;
  onPress?: () => void;
  onLongPress?: () => void;
  defaultRadius?: number;
}) {
  const scheme = useColorScheme();
  const isDark = dark ?? scheme === 'dark';
  const s = splitStyle(style, defaultRadius);

  if (!NativeGlass) {
    // iOS / web fallback: a translucent rounded view with the same layout
    return (
      <View
        {...rest}
        style={[
          s.rest,
          {
            borderRadius: s.radius,
            borderWidth: s.borderWidth,
            borderColor: s.borderColor ?? 'rgba(255,255,255,0.35)',
            backgroundColor: (tintColor ?? s.background ?? (isDark ? 'rgba(30,30,36,0.55)' : 'rgba(255,255,255,0.45)')) as ColorValue,
            overflow: 'hidden',
          },
        ]}>
        {children}
      </View>
    );
  }

  return (
    <NativeGlass
      {...rest}
      style={s.rest}
      cornerRadius={s.radius}
      glassBorderWidth={s.borderWidth}
      glassBorderColor={color(s.borderColor)}
      tintColor={color(tintColor ?? s.background)}
      blur={blur}
      refraction={refraction}
      chroma={chroma}
      highlight={highlight}
      dark={isDark}
      interactive={interactive}
      disabled={disabled}
      onPress={onPress}
      onLongPress={onLongPress}>
      {children}
    </NativeGlass>
  );
}

/** A Liquid Glass container. Lay it out like any View; its children sit on the glass. */
export function GlassView(props: GlassViewProps) {
  return <GlassSurface {...props} />;
}

/**
 * A Liquid Glass card: give it a width, its height follows its content.
 * No layout is imposed — style it as you like. Only default: borderRadius 28.
 */
export function GlassCard(props: GlassViewProps) {
  return <GlassSurface {...props} defaultRadius={28} />;
}

// ───────────────────────── GlassContainer ─────────────────────────

type NativeContainerProps = ViewProps & {
  spacing: number;
  tintColor?: number | null;
  blur?: number;
  refraction?: number;
  chroma?: number;
  highlight?: number;
  dark: boolean;
};

const NativeContainer =
  Platform.OS === 'android' ? requireNativeView<NativeContainerProps>('LiquidGlassContainer') : null;

export type GlassContainerProps = ViewProps &
  GlassLookProps & {
    /** How close (dp) two pieces of glass must get before they melt together. Default 16. */
    spacing?: number;
    /** Lay it out like a View (e.g. flexDirection: 'row', gap: 14). The container itself is invisible. */
    style?: StyleProp<ViewStyle>;
    children?: ReactNode;
  };

/**
 * Like iOS 26's GlassEffectContainer: the GlassButtons / GlassViews DIRECTLY inside share one
 * piece of liquid glass. Pull a button toward its neighbour and they melt together; let go and
 * they separate. (The glass color comes from the container: `tintColor` / `dark`.)
 */
export function GlassContainer({
  spacing = 16,
  style,
  children,
  tintColor,
  blur,
  refraction,
  chroma,
  highlight,
  dark,
  ...rest
}: GlassContainerProps) {
  const scheme = useColorScheme();
  if (!NativeContainer) return <View {...rest} style={style}>{children}</View>;
  const flat = { ...(StyleSheet.flatten(style) ?? {}) } as ViewStyle;
  const background = flat.backgroundColor;
  delete flat.backgroundColor;
  return (
    <NativeContainer
      {...rest}
      style={flat}
      spacing={spacing}
      tintColor={color(tintColor ?? background)}
      blur={blur}
      refraction={refraction}
      chroma={chroma}
      highlight={highlight}
      dark={dark ?? scheme === 'dark'}>
      {children}
    </NativeContainer>
  );
}

export type GlassButtonProps = GlassViewProps & {
  /** Shortcut for a text-only button. Or pass any children (icon + text…). */
  title?: string;
  titleStyle?: StyleProp<TextStyle>;
  onPress?: () => void;
  onLongPress?: () => void;
  disabled?: boolean;
};

/**
 * A Liquid Glass button (iOS 26 style): swells under your finger, stretches toward it,
 * glows where you touch, and wobbles back.
 *
 * No layout is imposed: `style` is entirely yours (padding, alignment, direction, gap, width…),
 * exactly like a View. Only defaults: width hugs the content (alignSelf: 'flex-start'), borderRadius = pill
 * (999), opacity 0.5 when disabled — all overridable from `style`.
 */
export function GlassButton({ title, titleStyle, children, style, disabled, dark, ...props }: GlassButtonProps) {
  const scheme = useColorScheme();
  const isDark = dark ?? scheme === 'dark';
  return (
    <GlassSurface
      {...props}
      dark={dark}
      interactive
      disabled={disabled}
      defaultRadius={999}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      style={[styles.hug, disabled && styles.disabled, style]}>
      {title != null ? (
        <Text style={[styles.title, { color: isDark ? '#fff' : '#111' }, titleStyle]}>{title}</Text>
      ) : (
        children
      )}
    </GlassSurface>
  );
}

const styles = StyleSheet.create({
  hug: { alignSelf: 'flex-start' }, // width = content; override from `style` (alignSelf / width)
  title: { fontSize: 17, fontWeight: '600' },
  disabled: { opacity: 0.5 },
});
