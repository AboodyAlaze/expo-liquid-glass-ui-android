import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Platform, StatusBar, StyleSheet, Text, View, useColorScheme, useWindowDimensions, type ColorValue } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { GlassSurface } from './GlassView';
import { haptic } from './haptics';

export type GlassToastOptions = {
  /** Main line, e.g. 'Sunny · 32°'. */
  title: string;
  /** Second, smaller line, e.g. 'Erbil'. */
  subtitle?: string;
  /** 'sun' (animated, default), any element (your icon / emoji Text), or null for none. */
  icon?: 'sun' | ReactNode | null;
  /** How long it stays open, in ms. Default 3000. */
  duration?: number;
  /** Which way the pill opens from the bead. Default 'center' (both sides). */
  expandFrom?: 'center' | 'left' | 'right';
  dark?: boolean;
  tintColor?: ColorValue;
  /** Distance from the top of the screen. Default: just under the status bar. */
  topOffset?: number;
  onPress?: () => void;
};

type ToastApi = { show(options: GlassToastOptions): void; hide(): void };
const ToastContext = createContext<ToastApi | null>(null);

/**
 * Show a Liquid Glass toast from anywhere inside <LiquidGlassProvider>:
 *
 *   const toast = useGlassToast();
 *   toast.show({ title: 'Sunny · 32°', subtitle: 'Erbil' });
 */
export function useGlassToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) throw new Error('useGlassToast must be used inside <LiquidGlassProvider>');
  return api;
}

const BEAD = 46; // the small round "bead" it starts as
const ICON = 26;
const PAD = 12;

/** Internal: holds the current toast and renders it above everything. Used by LiquidGlassProvider. */
export function GlassToastProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<(GlassToastOptions & { id: number }) | null>(null);
  const next = useRef(0);
  const hideRef = useRef<() => void>(() => {});
  const api = useMemo<ToastApi>(
    () => ({
      show: (options) => setCurrent({ ...options, id: ++next.current }),
      hide: () => hideRef.current(),
    }),
    []
  );
  return (
    <ToastContext.Provider value={api}>
      {children}
      {current && (
        <ToastView
          key={current.id}
          options={current}
          registerHide={(fn) => (hideRef.current = fn)}
          onDone={() => setCurrent((c) => (c?.id === current.id ? null : c))}
        />
      )}
    </ToastContext.Provider>
  );
}

/**
 * Timeline (everything that moves runs on the UI thread; nothing loops while it's open):
 *  1. measure the open width (one layout pass, invisible)
 *  2. the bead drops and bounces (Reanimated spring)
 *  3. it opens into a pill and reveals its content (native morph spring)
 *  4. after `duration` it closes (native), then rises away (Reanimated)
 */
function ToastView({
  options,
  registerHide,
  onDone,
}: {
  options: GlassToastOptions;
  registerHide: (fn: () => void) => void;
  onDone: () => void;
}) {
  const { title, subtitle, icon = 'sun', duration = 3000, expandFrom = 'center', tintColor, onPress } = options;
  const scheme = useColorScheme();
  const dark = options.dark ?? scheme === 'dark';
  const screen = useWindowDimensions();
  const top = options.topOffset ?? (Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 8 : 54);

  const drop = useSharedValue(-140); // translateY of the bead
  const beadIcon = useSharedValue(1); // the icon shown while it's still a bead
  const [width, setWidth] = useState(0); // measured open width
  const [open, setOpen] = useState(false);
  const stage = useRef<'drop' | 'open' | 'closing' | 'gone'>('drop');
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  const ink = dark ? '#FFFFFF' : '#111111';
  const sub = dark ? 'rgba(255,255,255,0.65)' : 'rgba(17,17,17,0.6)';
  const maxWidth = Math.min(screen.width * 0.7, 320);

  const rise = useCallback(() => {
    if (stage.current === 'gone') return;
    stage.current = 'gone';
    drop.value = withTiming(-140, { duration: 260, easing: Easing.in(Easing.cubic) }, (finished) => {
      if (finished) scheduleOnRN(onDone);
    });
  }, [drop, onDone]);

  const close = useCallback(() => {
    if (stage.current === 'closing' || stage.current === 'gone') return;
    timers.current.forEach(clearTimeout);
    if (stage.current === 'drop') return rise(); // never opened: just leave
    stage.current = 'closing';
    setOpen(false); // native: folds back into the bead → onMorphEnd → rise
    beadIcon.value = withDelay(180, withTiming(1, { duration: 160 }));
    timers.current.push(setTimeout(rise, 900)); // safety net if the end event never comes
  }, [beadIcon, rise]);

  useEffect(() => registerHide(close), [registerHide, close]);
  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  // once measured: drop → open → hold → close
  useEffect(() => {
    if (!width) return;
    drop.value = withSpring(0, { damping: 11, stiffness: 190, mass: 0.8 });
    timers.current.push(setTimeout(() => haptic('light'), 220));
    timers.current.push(
      setTimeout(() => {
        if (stage.current !== 'drop') return;
        stage.current = 'open';
        setOpen(true); // native morph spring on the UI thread
        beadIcon.value = withTiming(0, { duration: 140 });
        haptic('selection');
      }, 320)
    );
    timers.current.push(setTimeout(close, 320 + duration));
  }, [width, drop, beadIcon, close, duration]);

  // where the view sits: the bead is at the centre of the screen at first
  const center = screen.width / 2;
  const left = expandFrom === 'right' ? center - BEAD / 2 : expandFrom === 'left' ? center + BEAD / 2 - width : center - width / 2;
  const beadLeft = expandFrom === 'right' ? 0 : expandFrom === 'left' ? width - BEAD : (width - BEAD) / 2;
  const iconNode = icon === 'sun' ? <Sun /> : icon;
  const dropStyle = useAnimatedStyle(() => ({ transform: [{ translateY: drop.value }] }));
  const beadIconStyle = useAnimatedStyle(() => ({ opacity: beadIcon.value }));

  return (
    <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
      {/* invisible copy of the content: how wide the pill opens */}
      {!width && (
        <View
          pointerEvents="none"
          style={[styles.measure, { maxWidth }]}
          onLayout={(e) => setWidth(Math.max(BEAD, Math.min(Math.ceil(e.nativeEvent.layout.width), maxWidth)))}>
          <Content title={title} subtitle={subtitle} icon={iconNode} ink={ink} sub={sub} />
        </View>
      )}

      {width > 0 && (
        <Animated.View pointerEvents="box-none" style={[styles.dropper, { top, left, width }, dropStyle]}>
          <GlassSurface
            dark={dark}
            interactive={!!onPress}
            onPress={() => {
              onPress?.();
              close();
            }}
            tintColor={tintColor ?? (dark ? 'rgba(20,20,24,0.45)' : 'rgba(255,255,255,0.45)')}
            refraction={10}
            defaultRadius={BEAD / 2}
            morphBead={BEAD}
            morphAnchor={expandFrom}
            morphOpen={open}
            onMorphEnd={(e) => {
              if (!e.nativeEvent.open && stage.current === 'closing') rise();
            }}
            style={styles.glass}>
            <Content title={title} subtitle={subtitle} icon={iconNode} ink={ink} sub={sub} />
          </GlassSurface>
          {/* while it's a bead: just the icon in the middle of it */}
          <Animated.View pointerEvents="none" style={[styles.beadIcon, { left: beadLeft }, beadIconStyle]}>
            {iconNode}
          </Animated.View>
        </Animated.View>
      )}
    </View>
  );
}

function Content({ title, subtitle, icon, ink, sub }: { title: string; subtitle?: string; icon: ReactNode; ink: string; sub: string }) {
  return (
    <View style={styles.row}>
      {icon ? <View style={styles.icon}>{icon}</View> : null}
      <View style={styles.texts}>
        <Text numberOfLines={1} style={[styles.title, { color: ink }]}>
          {title}
        </Text>
        {subtitle ? (
          <Text numberOfLines={1} style={[styles.subtitle, { color: sub }]}>
            {subtitle}
          </Text>
        ) : null}
      </View>
    </View>
  );
}

/**
 * Small sun: a warm core, a soft halo and 8 rays. Deliberately NOT animated: a looping animation
 * (Reanimated on Android, by default) costs a React Native commit on every frame for as long as
 * the toast is visible — that was the toast's frame drops.
 */
function Sun() {
  return (
    <View style={styles.sun}>
      {Array.from({ length: 8 }).map((_, i) => (
        <View key={i} style={[styles.rayHolder, { transform: [{ rotate: `${i * 45}deg` }] }]}>
          <View style={styles.ray} />
        </View>
      ))}
      <View style={styles.halo} />
      <View style={styles.sunCore} />
    </View>
  );
}

const styles = StyleSheet.create({
  measure: { position: 'absolute', opacity: 0, top: -1000, left: 0, paddingHorizontal: PAD },
  dropper: { position: 'absolute', height: BEAD },
  glass: { flex: 1, justifyContent: 'center', paddingHorizontal: PAD },
  beadIcon: { position: 'absolute', top: 0, width: BEAD, height: BEAD, alignItems: 'center', justifyContent: 'center' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  icon: { width: ICON, height: ICON, alignItems: 'center', justifyContent: 'center' },
  texts: { flexShrink: 1 },
  title: { fontSize: 15, fontWeight: '700' },
  subtitle: { fontSize: 12, marginTop: 1 },
  sun: { width: ICON, height: ICON, alignItems: 'center', justifyContent: 'center' },
  rayHolder: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, alignItems: 'center' },
  ray: { width: 2.5, height: 5, borderRadius: 2, backgroundColor: '#FFC83D' },
  halo: { position: 'absolute', width: 19, height: 19, borderRadius: 10, backgroundColor: 'rgba(255,200,61,0.28)' },
  sunCore: { width: 13, height: 13, borderRadius: 7, backgroundColor: '#FFC83D' },
});
