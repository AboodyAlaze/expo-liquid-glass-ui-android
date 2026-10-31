import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { BackHandler, Pressable, StyleSheet, View, useColorScheme, type StyleProp, type ViewStyle } from 'react-native';
import { GestureDetector, usePanGesture } from 'react-native-gesture-handler';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { GlassSurface, type GlassLookProps } from './GlassView';
import { Portal } from './Provider';
import { haptic } from './haptics';

export type GlassSheetProps = GlassLookProps & {
  visible: boolean;
  onClose: () => void;
  children?: ReactNode;
  /** Gap between the sheet and the bottom of the screen (e.g. above your tab bar). Default 24. */
  bottomOffset?: number;
  /** Side margins of the floating card. Default 12. */
  sideMargin?: number;
  /** Drag down / tap outside / back button closes it. Default true. */
  dismissible?: boolean;
  /** Style of the glass card (padding, borderRadius, borderWidth…). Height always follows content. */
  style?: StyleProp<ViewStyle>;
};

const OPEN = { damping: 22, stiffness: 220, mass: 1 };
const CLOSE = { damping: 30, stiffness: 260, mass: 1 };
const SNAP_BACK = { damping: 16, stiffness: 260, mass: 1 };

/**
 * Floating Liquid Glass bottom sheet (iOS 26 style). Its height comes from its content.
 * Drag the sheet down to dismiss (rubber-band when pulled up), springs open and closed.
 *
 * Everything runs on the UI thread: the drag (Gesture Handler) and every spring (Reanimated) —
 * it stays smooth even while JavaScript is busy. Needs <LiquidGlassProvider> around your app.
 */
export function GlassSheet({
  visible,
  onClose,
  children,
  bottomOffset = 24,
  sideMargin = 12,
  dismissible = true,
  style,
  dark,
  ...look
}: GlassSheetProps) {
  const scheme = useColorScheme();
  const isDark = dark ?? scheme === 'dark';
  const [mounted, setMounted] = useState(visible);

  const height = useSharedValue(600); // measured card height
  const y = useSharedValue(2000); // translateY: starts off screen
  const scrim = useSharedValue(0);

  // latest onClose for the gesture (which runs on the UI thread)
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const requestClose = useCallback(() => closeRef.current(), []);
  const unmount = useCallback(() => setMounted(false), []);

  useEffect(() => {
    if (visible) {
      setMounted(true);
      haptic('light');
      // wait one frame so the card is laid out and its height is known
      const raf = requestAnimationFrame(() => {
        y.value = height.value + bottomOffset + 40;
        y.value = withSpring(0, OPEN);
        scrim.value = withTiming(1, { duration: 220 });
      });
      return () => cancelAnimationFrame(raf);
    }
    if (mounted) {
      scrim.value = withTiming(0, { duration: 200 });
      y.value = withSpring(height.value + bottomOffset + 40, CLOSE, (finished) => {
        if (finished) scheduleOnRN(unmount);
      });
    }
    return undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  useEffect(() => {
    if (!visible || !dismissible) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => sub.remove();
  }, [visible, dismissible, onClose]);

  // vertical drag only: horizontal moves (a slider inside the sheet) make it fail
  const drag = usePanGesture({
    activeOffsetY: [-6, 6],
    failOffsetX: [-14, 14],
    onUpdate: (e) => {
      'worklet';
      y.value = e.translationY > 0 ? e.translationY : e.translationY * 0.25; // rubber band upward
    },
    onDeactivate: (e) => {
      'worklet';
      const far = e.translationY > Math.min(140, height.value * 0.35);
      if (dismissible && !e.canceled && (far || e.velocityY > 1100)) {
        scheduleOnRN(requestClose);
      } else {
        y.value = withSpring(0, SNAP_BACK);
      }
    },
  });

  const scrimStyle = useAnimatedStyle(() => ({ opacity: scrim.value }));
  const cardStyle = useAnimatedStyle(() => ({ transform: [{ translateY: y.value }] }));

  if (!mounted) return null;

  return (
    <Portal>
      <Animated.View style={[StyleSheet.absoluteFill, scrimStyle]}>
        <Pressable style={[StyleSheet.absoluteFill, styles.scrim]} onPress={() => dismissible && onClose()} />
      </Animated.View>
      <GestureDetector gesture={drag}>
        <Animated.View
          style={[styles.holder, { left: sideMargin, right: sideMargin, bottom: bottomOffset }, cardStyle]}
          onLayout={(e) => {
            height.value = e.nativeEvent.layout.height;
          }}>
          <GlassSurface
            {...look}
            dark={isDark}
            defaultRadius={34}
            blur={look.blur ?? 0}
            refraction={look.refraction ?? 15}
            style={[styles.card, style]}>
            <View style={styles.grabberRow}>
              <View style={[styles.grabber, { backgroundColor: isDark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.25)' }]} />
            </View>
            {children}
          </GlassSurface>
        </Animated.View>
      </GestureDetector>
    </Portal>
  );
}

const styles = StyleSheet.create({
  scrim: { backgroundColor: 'rgba(0,0,0,0.18)' },
  holder: { position: 'absolute' },
  card: { paddingHorizontal: 20, paddingBottom: 22 },
  grabberRow: { height: 26, alignItems: 'center', justifyContent: 'center' },
  grabber: { width: 40, height: 5, borderRadius: 3 },
});
