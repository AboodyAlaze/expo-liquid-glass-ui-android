import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Animated,
  BackHandler,
  PanResponder,
  Pressable,
  StyleSheet,
  View,
  useColorScheme,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

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

/**
 * Floating Liquid Glass bottom sheet (iOS 26 style). Its height comes from its content.
 * Drag the sheet down to dismiss (rubber-band when pulled up), springs open and closed.
 * Needs <LiquidGlassProvider> around your app.
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
  const height = useRef(600);
  const y = useRef(new Animated.Value(800)).current; // translateY
  const scrim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      setMounted(true);
      haptic('light');
      requestAnimationFrame(() => {
        y.setValue(height.current + bottomOffset + 40);
        Animated.parallel([
          Animated.spring(y, { toValue: 0, damping: 22, stiffness: 220, mass: 1, useNativeDriver: true }),
          Animated.timing(scrim, { toValue: 1, duration: 220, useNativeDriver: true }),
        ]).start();
      });
    } else if (mounted) {
      Animated.parallel([
        Animated.spring(y, { toValue: height.current + bottomOffset + 40, damping: 30, stiffness: 260, useNativeDriver: true }),
        Animated.timing(scrim, { toValue: 0, duration: 200, useNativeDriver: true }),
      ]).start(({ finished }) => finished && setMounted(false));
    }
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

  const pan = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 6 && Math.abs(g.dy) > Math.abs(g.dx),
      onPanResponderMove: (_, g) => y.setValue(g.dy > 0 ? g.dy : g.dy * 0.25), // rubber band upward
      onPanResponderRelease: (_, g) => {
        if (dismissibleRef.current && (g.dy > Math.min(140, height.current * 0.35) || g.vy > 1.1)) {
          closeRef.current();
        } else {
          Animated.spring(y, { toValue: 0, damping: 16, stiffness: 260, useNativeDriver: true }).start();
        }
      },
    })
  ).current;
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const dismissibleRef = useRef(dismissible);
  dismissibleRef.current = dismissible;

  if (!mounted) return null;

  return (
    <Portal>
      <Animated.View style={[StyleSheet.absoluteFill, { opacity: scrim }]}>
        <Pressable style={[StyleSheet.absoluteFill, styles.scrim]} onPress={() => dismissible && onClose()} />
      </Animated.View>
      <Animated.View
        {...pan.panHandlers}
        style={[styles.holder, { left: sideMargin, right: sideMargin, bottom: bottomOffset, transform: [{ translateY: y }] }]}
        onLayout={(e) => (height.current = e.nativeEvent.layout.height)}>
        <GlassSurface
          {...look}
          dark={isDark}
          defaultRadius={34}
          blur={look.blur ?? 4}
          refraction={look.refraction ?? 15}
          style={[styles.card, style]}>
          <View style={styles.grabberRow}>
            <View style={[styles.grabber, { backgroundColor: isDark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.25)' }]} />
          </View>
          {children}
        </GlassSurface>
      </Animated.View>
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
