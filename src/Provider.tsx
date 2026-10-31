import { createContext, Fragment, useContext, useEffect, useId, useMemo, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { GlassToastProvider } from './GlassToast';

type PortalApi = { mount(key: string, node: ReactNode): void; unmount(key: string): void };
const PortalContext = createContext<PortalApi | null>(null);

/**
 * Put this once around your app (e.g. in the root _layout). GlassSheet and GlassMenu render
 * into it, ABOVE everything else, so their glass refracts the whole screen behind them.
 * It also sets up Gesture Handler (the sheet and menu gestures run on the UI thread), so you
 * don't need your own GestureHandlerRootView for them.
 */
export function LiquidGlassProvider({ children }: { children: ReactNode }) {
  const [portals, setPortals] = useState<Record<string, ReactNode>>({});
  const api = useMemo<PortalApi>(
    () => ({
      mount: (key, node) => setPortals((p) => ({ ...p, [key]: node })),
      unmount: (key) =>
        setPortals((p) => {
          if (!(key in p)) return p;
          const next = { ...p };
          delete next[key];
          return next;
        }),
    }),
    []
  );
  return (
    <GestureHandlerRootView style={styles.fill}>
    <PortalContext.Provider value={api}>
      <GlassToastProvider>
        <View style={styles.fill}>{children}</View>
        <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
          {Object.entries(portals).map(([k, node]) => (
            <Fragment key={k}>{node}</Fragment>
          ))}
        </View>
      </GlassToastProvider>
    </PortalContext.Provider>
    </GestureHandlerRootView>
  );
}

/** Renders its children at the top of LiquidGlassProvider (or in place if there's none). */
export function Portal({ children }: { children: ReactNode }) {
  const api = useContext(PortalContext);
  const key = useId();
  useEffect(() => {
    api?.mount(key, children);
  }, [api, key, children]);
  useEffect(() => () => api?.unmount(key), [api, key]);
  if (!api) return <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>{children}</View>;
  return null;
}

const styles = StyleSheet.create({ fill: { flex: 1 } });
