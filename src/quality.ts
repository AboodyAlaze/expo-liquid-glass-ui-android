import { createContext, useContext } from 'react';

/**
 * How much GPU the glass may use.
 *  - 'auto'   (default) decided per device: certified fast phones get 'high', mid-range 'medium', everything else 'low'
 *  - 'high'   full effect (refraction + chromatic rim + glow)
 *  - 'medium' same glass, no chromatic split, big cards drawn cheap
 *  - 'low'    no shader / no capture: drawn frosted glass, near-zero cost; animations unchanged
 */
export type GlassQuality = 'auto' | 'high' | 'medium' | 'low';

export const QualityContext = createContext<GlassQuality>('auto');

/** The quality set by the nearest `LiquidGlassProvider` ('auto' without one). */
export function useGlassQuality(): GlassQuality {
  return useContext(QualityContext);
}
