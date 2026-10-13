# expo-liquid-glass-ui-android

Native **Liquid Glass** components for Expo on Android (iOS 26 style): real refraction of whatever
is behind them, a specular rim, touch glow and elastic, physical interactions.

`GlassView` · `GlassButton` · `GlassContainer` · `GlassCard` · `GlassSwitch` · `GlassSlider` · `GlassSheet` · `GlassMenu`

Every glass component is a **real React Native View**: `width`, `height`, `padding`, `margin`, `flex`,
`borderRadius`, `borderWidth`, `borderColor`… all work exactly like on a `<View>`, and you can put
anything inside (text, icons, images).

> Tab bar? See [`expo-liquid-glass-tabs-android`](https://github.com/AboodyAlaze/expo-liquid-glass-tabs-android).

## Install

```bash
npx expo install expo-liquid-glass-ui-android
npx expo prebuild   # or: eas build
```

It's a native module: it needs a development build or EAS build (not Expo Go).

## Setup (once)

Wrap your app with `LiquidGlassProvider` (e.g. in `app/_layout.tsx`). `GlassSheet` and `GlassMenu`
render on top of everything from there, so their glass bends the whole screen behind them.

```tsx
import { LiquidGlassProvider } from 'expo-liquid-glass-ui-android';

export default function RootLayout() {
  return (
    <LiquidGlassProvider>
      <Stack />
    </LiquidGlassProvider>
  );
}
```

## GlassButton

```tsx
<GlassButton title="Continue" onPress={...} />

<GlassButton
  style={{ width: 170, paddingVertical: 18, borderRadius: 18, borderWidth: 1.5, borderColor: '#fff8' }}
  onPress={...}
  onLongPress={...}>
  <Icon name="heart" />
  <Text>Like</Text>
</GlassButton>
```

It swells under your finger, stretches toward it, glows where you touch and wobbles back.

**No layout is imposed**: `style` is entirely yours (padding, alignment, flexDirection, gap…), like a
`View`. The only defaults are `borderRadius: 999` (pill) and `opacity: 0.5` when disabled — both
overridable from `style`.

| prop | |
|---|---|
| `title` / `titleStyle` | shortcut for a text button (or pass children) |
| `onPress`, `onLongPress`, `disabled` | |
| `style` | any View style. `borderRadius` shapes the glass (default: pill) |
| `backgroundColor` (in style) or `tintColor` | color of the glass |
| `dark` | dark / light glass (default: system) |
| `blur`, `refraction`, `chroma`, `highlight` | fine-tune the glass |

## GlassContainer

Like iOS 26's `GlassEffectContainer`: the glass components **directly inside** share one piece of
liquid glass. Pull a button toward its neighbour and they **melt together** with a liquid bridge;
let go and they separate.

```tsx
<GlassContainer style={{ flexDirection: 'row', gap: 14 }} spacing={16}>
  <GlassButton title="Edit" onPress={...} />
  <GlassButton title="Share" onPress={...} />
</GlassContainer>
```

`spacing` (dp) = how close they must get to merge. The glass color comes from the container
(`tintColor` / `backgroundColor` in its style / `dark`).

## GlassView / GlassCard

```tsx
<GlassCard style={{ width: 320 }}>
  <Text>Height follows the content</Text>
  <Text>Add a row → it grows</Text>
</GlassCard>
```

`GlassView` is the plain container (default radius 24), `GlassCard` the same with radius 28. Neither imposes any layout: padding, alignment etc. are yours.

## GlassSwitch

```tsx
<GlassSwitch value={on} onValueChange={setOn} onColor="#34C759" />
```

Tap or drag. While held, the knob turns into a liquid lens that magnifies the track. Default 63 × 28.

## GlassSlider

```tsx
<GlassSlider value={v} onValueChange={setV} />
<GlassSlider value={r} minimumValue={0} maximumValue={100} step={10} accentColor="#FF9F0A"
  onValueChange={setR} onSlidingComplete={save} />
```

The thumb becomes a lens while dragged and stretches with your speed. With `step` it snaps and
ticks (haptic) on every step.

## GlassSheet

```tsx
<GlassSheet visible={open} onClose={() => setOpen(false)}>
  <Text>Anything — the height follows the content</Text>
</GlassSheet>
```

Floating glass card, drag down to dismiss (rubber-band upward), tap outside or Back to close.
Props: `bottomOffset` (e.g. above your tab bar, default 24), `sideMargin`, `dismissible`, `style`, glass props.

## GlassMenu

```tsx
<GlassMenu
  items={[
    { title: 'Show Info', icon: <InfoIcon />, onPress: ... },
    { title: 'Sort By', items: [{ title: 'Date', checked: true, onPress: ... }, { title: 'Name', onPress: ... }] },
    { title: 'Delete', destructive: true, onPress: ... },
  ]}>
  <GlassButton title="•••" />
</GlassMenu>
```

Anchored to its trigger (the trigger must accept `onPress`). Press and **slide your finger across the
rows** (haptic tick on each), release to pick. `items` opens a nested page.

## haptic()

```ts
haptic('selection' | 'light' | 'medium' | 'heavy' | 'success' | 'error')
```

## Devices

| Android | look |
|---|---|
| 13+ | full Liquid Glass (refraction, chromatic rim, specular, lens) |
| 12 | frosted glass (blur + tint) |
| ≤ 11 | translucent tint |

On iOS / web the components render as plain translucent views with the same layout.

## How it works

No Jetpack Compose: each component is a plain Android View. Before every frame it records what is
painted behind it (references to the existing display lists, not pixels) and draws itself through an
AGSL shader with `RenderNode` + `RenderEffect`. The capture waits for screen transitions, never
re-enters, turns itself off on any error instead of breaking the app, and backs off on slow devices.

## Example

```bash
cd example
npm install
npx expo run:android
```

## License

MIT
