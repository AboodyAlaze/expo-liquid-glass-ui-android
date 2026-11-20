# Changelog

## 1.3.0
- NEW `quality` ('auto' | 'high' | 'medium' | 'low') on `LiquidGlassProvider`, every glass component, `GlassSwitch`, `GlassSlider`. Default `auto`.
- NEW `useGlassQuality()`.
- `low`: no AGSL shader, no backdrop capture, no offscreen layer (BackdropManager is not even attached). Redesigned cheap look (body + sheen + rim).
- `medium`: no chromatic split (shader takes 1 sample instead of 3); large surfaces use the cheap look.
- Backdrop capture back-off is refresh-rate aware (40 % of a frame, min 3 ms) and skips 2 frames.
- Behaviour on performance-class devices is unchanged (`auto` → `high`). Pin the old behaviour everywhere with `<LiquidGlassProvider quality="high">`.
