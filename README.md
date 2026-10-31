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
npx expo install expo-liquid-glass-ui-android react-native-reanimated react-native-worklets react-native-gesture-handler
```

Needs **Expo SDK 58+** (Reanimated 4, Gesture Handler 3) and a development build (not Expo Go).
Reanimated's Babel plugin is already included in `babel-preset-expo`.

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

## GlassToast

A small Liquid Glass bead **drops from the top centre**, bounces, then **opens into a pill**
(centre, left or right) with your message — and folds back up after a few seconds.

```tsx
import { useGlassToast } from 'expo-liquid-glass-ui-android';

const toast = useGlassToast();

toast.show({ title: 'Sunny · 32°', subtitle: 'Erbil' });               // animated sun by default
toast.show({ title: 'Saved', icon: <MyIcon />, expandFrom: 'right' });   // any icon, opens to the right
```

| option | |
|---|---|
| `title`, `subtitle` | the two lines |
| `icon` | `'sun'` (animated, default), any element, or `null` |
| `duration` | ms it stays open (default 3000) |
| `expandFrom` | `'center'` (default) · `'left'` · `'right'` |
| `dark`, `tintColor`, `topOffset`, `onPress` | |

Works anywhere inside `LiquidGlassProvider` (it hosts the toast). `toast.hide()` closes it early.

## Performance

Built to stay at full frame rate with many glass elements on screen, even while scrolling:

- **References, not copies.** What's behind the glass is drawn as references to the views' own
  display lists, exactly like Android draws them. Content changes reach the glass by themselves,
  and only the glass that overlaps the change is re-rendered.
- **Nothing changed → nothing done.** Every captured layer has a signature (order, positions,
  scroll). Same signature as last frame → untouched, so the GPU doesn't redo the glass. An idle
  screen costs zero.
- **One shared capture per window per frame**, shared between all glass on the screen.
- **Off-screen glass is frozen** (no GPU work while you scroll past it); hidden glass and switches /
  sliders at rest cost nothing.
- **No blur by default** — blur is a full extra GPU pass per glass layer. Opt in with `blur`.
- **All animations on the UI thread** (Kotlin springs, Reanimated, Gesture Handler).

### Android: let Reanimated skip React commits (recommended)

By default, Reanimated 4 on Android sends every animation frame through a React Native commit.
Turn on its synchronous UI props in **your app's** `package.json` (then rebuild) — every
transform / opacity animation in your app gets cheaper, not just this package's:

```json
"reanimated": {
  "staticFeatureFlags": { "ANDROID_SYNCHRONOUSLY_UPDATE_UI_PROPS": true }
}
```

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

## Changelog

**1.2.2** — toast & nested glass
- Toast: the sun no longer loops (a looping Reanimated animation costs a React commit every frame
  on Android); no Android shadow on it either
- Glass nested in glass no longer re-processes the outer glass (the big cost of a glass card with
  glass rows inside it)
- No per-frame allocations in the capture pass
- README: Reanimated's `ANDROID_SYNCHRONOUSLY_UPDATE_UI_PROPS` flag

**1.2.1** — no more periodic stutter while something animates
- Background changes are detected by their colour, not by "something in the screen changed": an
  animation anywhere (a spinner, the toast's sun, a switch) no longer re-renders all the glass
- Props only redraw when their value actually changes (React re-renders cost nothing)
- Toast content uses a rect clip instead of a path mask

**1.2.0** — performance
- Captures by reference (`ViewGroup.drawChild`) instead of copying the views behind
- Skips re-recording when nothing changed: idle screens cost zero, only overlapping glass
  re-renders when something changes behind it
- Off-screen glass is frozen while scrolling
- `blur` now defaults to 0 (was 3 / 4 / 6)
- Dark glass is a translucent dark grey, visible on pure black
- Example: plain black background

**1.1.0**
- All animations moved from React Native `Animated` to **Reanimated 4**
- GlassSheet drag and GlassMenu slide-to-select on **Gesture Handler 3**: fully on the UI thread
- `LiquidGlassProvider` sets up `GestureHandlerRootView` for you
- New peer dependencies: `react-native-reanimated`, `react-native-worklets`, `react-native-gesture-handler`

**1.0.0**
- New capture architecture: one shared pass per window, glass doesn't redraw on scroll, smaller
  GPU layers, off-screen / idle glass skipped, switch & slider capture only while held
- Fixes a flicker where a neighbouring glass element could briefly draw as a flat fill
- Fixes glass behind a scrolled sibling showing the wrong part of it
- GlassToast: open / close morph now runs natively on the UI thread
- Nested glass refracts the glass it sits on
- Expo SDK 58 / React Native 0.88 support (ReactNativeElement refs, readonly styles)

**0.2.0** — GlassToast · **0.1.2** — buttons own all touches · **0.1.1** — glass fades in

## License

MIT
