import {
  cloneElement,
  isValidElement,
  useCallback,
  useEffect,
  useRef,
  useState,
  type ComponentRef,
  type ReactElement,
  type ReactNode,
} from 'react';
import { BackHandler, Pressable, StyleSheet, Text, View, useColorScheme, useWindowDimensions } from 'react-native';
import { GestureDetector, usePanGesture } from 'react-native-gesture-handler';
import Animated, {
  interpolate,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
  type SharedValue,
} from 'react-native-reanimated';
import { scheduleOnRN } from 'react-native-worklets';

import { GlassSurface, type GlassLookProps } from './GlassView';
import { Portal } from './Provider';
import { haptic } from './haptics';

export type GlassMenuItem = {
  title: string;
  /** Any element: an icon from your icon library, an emoji Text… */
  icon?: ReactNode;
  /** Shows a checkmark in front. */
  checked?: boolean;
  /** Red, for delete-like actions. */
  destructive?: boolean;
  disabled?: boolean;
  onPress?: () => void;
  /** A nested page (shows a › chevron). */
  items?: GlassMenuItem[];
};

export type GlassMenuProps = GlassLookProps & {
  items: GlassMenuItem[];
  /** The trigger. It must accept `onPress` (GlassButton, Pressable, TouchableOpacity…). */
  children: ReactElement<{ onPress?: () => void }>;
  /** Menu width. Default 250. */
  width?: number;
};

const ROW = 48;
const PAD = 8;
const BACK_ROW = -2; // the "‹ back" header row of a nested page
const NONE = -1;

/**
 * iOS 26-style Liquid Glass context menu, anchored to its trigger.
 * Press, slide your finger across the rows (haptic tick on each), release to pick.
 * Rows with `items` open a nested page. Needs <LiquidGlassProvider> around your app.
 *
 * The slide runs on the UI thread (Gesture Handler + Reanimated): the row under your finger
 * lights up without a single React re-render.
 */
export function GlassMenu({ items, children, width = 250, dark, ...look }: GlassMenuProps) {
  const scheme = useColorScheme();
  const isDark = dark ?? scheme === 'dark';
  const screen = useWindowDimensions();
  const triggerRef = useRef<ComponentRef<typeof View>>(null); // RN 0.88+: a ReactNativeElement
  const [anchor, setAnchor] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const [pages, setPages] = useState<{ title: string; items: GlassMenuItem[] }[]>([]);

  const appear = useSharedValue(0);
  const hover = useSharedValue(NONE);

  const page = pages[pages.length - 1];
  const rows = page ? page.items : items;
  const hasHeader = !!page;
  const height = PAD * 2 + ROW * (rows.length + (hasHeader ? 1 : 0));

  const hide = useCallback(() => setAnchor(null), []);

  const open = () => {
    triggerRef.current?.measureInWindow((x, y, w, h) => {
      setPages([]);
      hover.value = NONE;
      setAnchor({ x, y, w, h });
      haptic('medium');
      appear.value = 0;
      appear.value = withSpring(1, { damping: 15, stiffness: 260, mass: 0.9 });
    });
  };

  const close = useCallback(() => {
    appear.value = withTiming(0, { duration: 140 }, (finished) => {
      if (finished) scheduleOnRN(hide);
    });
  }, [appear, hide]);

  // latest values for callbacks coming back from the UI thread
  const rowsRef = useRef(rows);
  rowsRef.current = rows;
  const pagesRef = useRef(pages);
  pagesRef.current = pages;
  const lastChoice = useRef(0);

  const choose = useCallback(
    (i: number) => {
      const now = Date.now();
      if (now - lastChoice.current < 250) return; // a slide and a tap can't both pick
      lastChoice.current = now;
      if (i === BACK_ROW) {
        setPages((p) => p.slice(0, -1));
        return;
      }
      const item = rowsRef.current[i];
      if (!item || item.disabled) return;
      haptic('light');
      if (item.items) {
        setPages((p) => [...p, { title: item.title, items: item.items! }]);
        return;
      }
      close();
      item.onPress?.();
    },
    [close]
  );
  const tick = useCallback(() => haptic('selection'), []);

  useEffect(() => {
    if (!anchor) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (pagesRef.current.length) setPages((p) => p.slice(0, -1));
      else close();
      return true;
    });
    return () => sub.remove();
  }, [anchor, close]);

  // press-and-slide, entirely on the UI thread
  const rowCount = rows.length;
  const slide = usePanGesture({
    minDistance: 0,
    onBegin: (e) => {
      'worklet';
      hover.value = indexAt(e.y, hasHeader, rowCount);
      if (hover.value !== NONE) scheduleOnRN(tick);
    },
    onUpdate: (e) => {
      'worklet';
      const i = indexAt(e.y, hasHeader, rowCount);
      if (i !== hover.value) {
        hover.value = i;
        if (i !== NONE) scheduleOnRN(tick);
      }
    },
    onFinalize: (e) => {
      'worklet';
      const i = hover.value;
      hover.value = NONE;
      if (!e.canceled && i !== NONE) scheduleOnRN(choose, i);
    },
  });

  // placement: hang below the trigger, aligned to the side it's on; flip above if no room
  let left = 0;
  let top = 0;
  let originX: 'left' | 'right' = 'left';
  let originY: 'top' | 'bottom' = 'top';
  if (anchor) {
    const rightSide = anchor.x + anchor.w / 2 > screen.width / 2;
    left = rightSide ? anchor.x + anchor.w - width : anchor.x;
    left = Math.max(12, Math.min(left, screen.width - width - 12));
    originX = rightSide ? 'right' : 'left';
    top = anchor.y + anchor.h + 8;
    if (top + height > screen.height - 24) {
      top = Math.max(24, anchor.y - 8 - height);
      originY = 'bottom';
    }
  }

  const panelStyle = useAnimatedStyle(() => ({
    opacity: interpolate(appear.value, [0, 0.3, 1], [0, 1, 1]),
    transform: [{ scale: interpolate(appear.value, [0, 1], [0.55, 1]) }],
  }));

  const ink = isDark ? '#fff' : '#111';
  const highlight = isDark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)';
  const trigger = isValidElement(children) ? cloneElement(children, { onPress: open }) : children;

  return (
    <>
      <View ref={triggerRef} collapsable={false} style={styles.triggerBox}>
        {trigger}
      </View>
      {anchor && (
        <Portal>
          <Pressable style={StyleSheet.absoluteFill} onPress={close} />
          <GestureDetector gesture={slide}>
            <Animated.View style={[styles.panel, { left, top, width, transformOrigin: `${originX} ${originY}` }, panelStyle]}>
              <GlassSurface
                {...look}
                dark={isDark}
                defaultRadius={28}
                blur={look.blur ?? 0}
                tintColor={look.tintColor ?? (isDark ? 'rgba(28,28,34,0.66)' : 'rgba(255,255,255,0.72)')}
                style={{ paddingVertical: PAD }}>
                {hasHeader && (
                  <Row
                    index={BACK_ROW}
                    hover={hover}
                    highlight={highlight}
                    ink={ink}
                    title={page!.title}
                    bold
                    icon={<Text style={[styles.glyph, { color: ink }]}>‹</Text>}
                  />
                )}
                {rows.map((item, i) => (
                  <Row
                    key={`${pages.length}-${i}`}
                    index={i}
                    hover={hover}
                    highlight={highlight}
                    ink={item.destructive ? '#FF453A' : ink}
                    title={item.title}
                    icon={item.icon}
                    checked={item.checked}
                    disabled={item.disabled}
                    chevron={!!item.items}
                  />
                ))}
              </GlassSurface>
            </Animated.View>
          </GestureDetector>
        </Portal>
      )}
    </>
  );
}

/** Which row is under y (panel-local): a row index, BACK_ROW, or NONE. Runs on the UI thread. */
function indexAt(y: number, hasHeader: boolean, rowCount: number): number {
  'worklet';
  let p = y - PAD;
  if (hasHeader) {
    if (p >= 0 && p < ROW) return BACK_ROW;
    p -= ROW;
  }
  const i = Math.floor(p / ROW);
  return p < 0 || i >= rowCount ? NONE : i;
}

function Row(props: {
  index: number;
  hover: SharedValue<number>;
  highlight: string;
  ink: string;
  title: string;
  icon?: ReactNode;
  checked?: boolean;
  disabled?: boolean;
  chevron?: boolean;
  bold?: boolean;
}) {
  const { index, hover, highlight } = props;
  // lights up when the finger is over it — no React re-render, straight on the UI thread
  const lit = useAnimatedStyle(() => ({
    backgroundColor: hover.value === index ? highlight : 'transparent',
  }));
  return (
    <Animated.View style={[styles.row, props.disabled && styles.disabled, lit]}>
      <Text style={[styles.check, { color: props.ink }]}>{props.checked ? '✓' : ''}</Text>
      <View style={styles.icon}>{props.icon}</View>
      <Text numberOfLines={1} style={[styles.title, { color: props.ink }, props.bold && styles.bold]}>
        {props.title}
      </Text>
      {props.chevron && <Text style={[styles.chevron, { color: props.ink }]}>›</Text>}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  triggerBox: {},
  panel: { position: 'absolute' },
  row: { height: ROW, marginHorizontal: 6, borderRadius: 16, flexDirection: 'row', alignItems: 'center', paddingRight: 14 },
  disabled: { opacity: 0.4 },
  check: { width: 26, textAlign: 'center', fontSize: 15, fontWeight: '600' },
  icon: { width: 26, alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  glyph: { fontSize: 24, marginTop: -3 },
  title: { flex: 1, fontSize: 17 },
  bold: { fontWeight: '600' },
  chevron: { fontSize: 22, opacity: 0.5, marginLeft: 6 },
});
