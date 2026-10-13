import { createContext, Fragment, useContext, useEffect, useId, useMemo, useState, type ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

type PortalApi = { mount(key: string, node: ReactNode): void; unmount(key: string): void };
const PortalContext = createContext<PortalApi | null>(null);

/**
 * Put this once around your app (e.g. in the root _layout). GlassSheet and GlassMenu render
 * into it, ABOVE everything else, so their glass refracts the whole screen behind them.
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
    <PortalContext.Provider value={api}>
      <View style={styles.fill}>{children}</View>
      <View pointerEvents="box-none" style={StyleSheet.absoluteFill}>
        {Object.entries(portals).map(([k, node]) => (
          <Fragment key={k}>{node}</Fragment>
        ))}
      </View>
    </PortalContext.Provider>
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
