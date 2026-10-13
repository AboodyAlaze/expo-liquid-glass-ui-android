import { cloneElement, isValidElement, useEffect, useRef, useState, type ReactElement, type ReactNode } from 'react';
import {
  Animated,
  BackHandler,
  PanResponder,
  Pressable,
  StyleSheet,
  Text,
  View,
  useColorScheme,
  useWindowDimensions,
} from 'react-native';

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

/**
 * iOS 26-style Liquid Glass context menu, anchored to its trigger.
 * Press, slide your finger across the rows (haptic tick on each), release to pick.
 * Rows with `items` open a nested page. Needs <LiquidGlassProvider> around your app.
 */
export function GlassMenu({ items, children, width = 250, dark, ...look }: GlassMenuProps) {
  const scheme = useColorScheme();
  const isDark = dark ?? scheme === 'dark';
  const screen = useWindowDimensions();
  const triggerRef = useRef<View>(null);
  const [anchor, setAnchor] = useState<{ x: number; y: number; w: number; h: number } | null>(null);
  const [pages, setPages] = useState<{ title: string; items: GlassMenuItem[] }[]>([]);
  const [hover, setHover] = useState(-1);
  const appear = useRef(new Animated.Value(0)).current;
  const panelTop = useRef(0);

  const page = pages[pages.length - 1];
  const rows = page ? page.items : items;
  const hasHeader = !!page;
  const height = PAD * 2 + ROW * (rows.length + (hasHeader ? 1 : 0));

  const open = () => {
    triggerRef.current?.measureInWindow((x, y, w, h) => {
      setPages([]);
      setHover(-1);
      setAnchor({ x, y, w, h });
      haptic('medium');
      appear.setValue(0);
      Animated.spring(appear, { toValue: 1, damping: 15, stiffness: 260, mass: 0.9, useNativeDriver: true }).start();
    });
  };
  const close = () => {
    Animated.timing(appear, { toValue: 0, duration: 140, useNativeDriver: true }).start(() => setAnchor(null));
  };

  useEffect(() => {
    if (!anchor) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (pagesRef.current.length) setPages((p) => p.slice(0, -1));
      else close();
      return true;
    });
    return () => sub.remove();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [anchor]);

  // latest values for the gesture handler
  const rowsRef = useRef(rows);
  rowsRef.current = rows;
  const headerRef = useRef(hasHeader);
  headerRef.current = hasHeader;
  const pagesRef = useRef(pages);
  pagesRef.current = pages;
  const hoverRef = useRef(-1);

  const indexAt = (pageY: number) => {
    let y = pageY - panelTop.current - PAD;
    if (headerRef.current) {
      if (y >= 0 && y < ROW) return -2; // back row
      y -= ROW;
    }
    const i = Math.floor(y / ROW);
    return y < 0 || i >= rowsRef.current.length ? -1 : i;
  };
  const setHoverTo = (i: number) => {
    if (i !== hoverRef.current) {
      hoverRef.current = i;
      setHover(i);
      if (i !== -1) haptic('selection');
    }
  };
  const choose = (i: number) => {
    if (i === -2) {
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
  };

  const pan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (e) => setHoverTo(indexAt(e.nativeEvent.pageY)),
      onPanResponderMove: (e) => setHoverTo(indexAt(e.nativeEvent.pageY)),
      onPanResponderRelease: () => {
        const i = hoverRef.current;
        setHoverTo(-1);
        if (i !== -1) choose(i);
      },
      onPanResponderTerminate: () => setHoverTo(-1),
    })
  ).current;

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

  panelTop.current = top; // the portal host sits at the window origin → same coordinates as pageY
  const ink = isDark ? '#fff' : '#111';
  const trigger = isValidElement(children) ? cloneElement(children, { onPress: open }) : children;

  return (
    <>
      <View ref={triggerRef} collapsable={false} style={styles.triggerBox}>
        {trigger}
      </View>
      {anchor && (
        <Portal>
          <Pressable style={StyleSheet.absoluteFill} onPress={close} />
          <Animated.View
            {...pan.panHandlers}
            style={[
              styles.panel,
              {
                left,
                top,
                width,
                opacity: appear.interpolate({ inputRange: [0, 0.3, 1], outputRange: [0, 1, 1] }),
                transformOrigin: `${originX} ${originY}`,
                transform: [{ scale: appear.interpolate({ inputRange: [0, 1], outputRange: [0.55, 1] }) }],
              },
            ]}>
            <GlassSurface
              {...look}
              dark={isDark}
              defaultRadius={28}
              blur={look.blur ?? 6}
              tintColor={look.tintColor ?? (isDark ? 'rgba(28,28,34,0.66)' : 'rgba(255,255,255,0.72)')}
              style={{ paddingVertical: PAD }}>
              {hasHeader && (
                <Row ink={ink} highlighted={hover === -2} title={page!.title} bold icon={<Text style={[styles.glyph, { color: ink }]}>‹</Text>} />
              )}
              {rows.map((item, i) => (
                <Row
                  key={`${pages.length}-${i}`}
                  ink={item.destructive ? '#FF453A' : ink}
                  highlighted={hover === i}
                  title={item.title}
                  icon={item.icon}
                  checked={item.checked}
                  disabled={item.disabled}
                  chevron={!!item.items}
                />
              ))}
            </GlassSurface>
          </Animated.View>
        </Portal>
      )}
    </>
  );
}

function Row(props: {
  ink: string;
  highlighted: boolean;
  title: string;
  icon?: ReactNode;
  checked?: boolean;
  disabled?: boolean;
  chevron?: boolean;
  bold?: boolean;
}) {
  return (
    <View
      style={[
        styles.row,
        props.highlighted && { backgroundColor: props.ink === '#fff' ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)' },
        props.disabled && { opacity: 0.4 },
      ]}>
      <Text style={[styles.check, { color: props.ink }]}>{props.checked ? '✓' : ''}</Text>
      <View style={styles.icon}>{props.icon}</View>
      <Text numberOfLines={1} style={[styles.title, { color: props.ink }, props.bold && { fontWeight: '600' }]}>
        {props.title}
      </Text>
      {props.chevron && <Text style={[styles.chevron, { color: props.ink }]}>›</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  triggerBox: {},
  panel: { position: 'absolute' },
  row: { height: ROW, marginHorizontal: 6, borderRadius: 16, flexDirection: 'row', alignItems: 'center', paddingRight: 14 },
  check: { width: 26, textAlign: 'center', fontSize: 15, fontWeight: '600' },
  icon: { width: 26, alignItems: 'center', justifyContent: 'center', marginRight: 10 },
  glyph: { fontSize: 24, marginTop: -3 },
  title: { flex: 1, fontSize: 17 },
  chevron: { fontSize: 22, opacity: 0.5, marginLeft: 6 },
});
