import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { LiquidGlassProvider } from 'expo-liquid-glass-ui-android';

export default function RootLayout() {
  return (
    // Once, around the whole app: GlassSheet and GlassMenu float above everything from here
    <LiquidGlassProvider>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: '#0b1a4a' } }} />
    </LiquidGlassProvider>
  );
}
