import { useState, type ReactNode } from 'react';
import { LinearGradient } from 'expo-linear-gradient';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import {
  GlassButton,
  GlassCard,
  GlassContainer,
  GlassMenu,
  GlassSheet,
  GlassSlider,
  GlassSwitch,
  GlassView,
  haptic,
} from 'expo-liquid-glass-ui-android';

const INK = '#fff';
const SUB = 'rgba(255,255,255,0.7)';

export default function Demo() {
  const [wifi, setWifi] = useState(true);
  const [dark, setDark] = useState(false);
  const [volume, setVolume] = useState(0.6);
  const [rating, setRating] = useState(40);
  const [sheet, setSheet] = useState(false);
  const [sortBy, setSortBy] = useState('Date');
  const [showDone, setShowDone] = useState(true);
  const [last, setLast] = useState('Tap something');

  return (
    <View style={styles.screen}>
      <Wallpaper />
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.h1}>Liquid Glass UI</Text>
        <Text style={styles.sub}>{last}</Text>

        {/* ───────── Buttons: styled like any View ───────── */}
        <Section title="GlassButton · GlassContainer">
          <Text style={styles.hint}>Pull a button toward its neighbour: they melt together</Text>
          <GlassContainer dark style={styles.row}>
            <GlassButton style={styles.btn} title="Default" dark onPress={() => setLast('Default pressed')} />
            <GlassButton
              title="Bordered"
              dark
              style={[styles.btn, { borderWidth: 1.5, borderColor: 'rgba(255,255,255,0.7)' }]}
              onPress={() => setLast('Bordered pressed')}
            />
          </GlassContainer>
          <GlassContainer dark style={styles.row}>
            <GlassButton
              dark
              style={[styles.btn, { width: 170, paddingVertical: 18, borderRadius: 18 }]}
              onPress={() => setLast('Custom width / padding / radius')}>
              <Text style={styles.btnIcon}>♥</Text>
              <Text style={styles.btnText}>Like</Text>
            </GlassButton>
            <GlassButton
              dark
              style={[styles.btn, { width: 58, height: 58, paddingHorizontal: 0, paddingVertical: 0 }]}
              onPress={() => setLast('Round icon button')}
              onLongPress={() => setLast('Long press!')}>
              <Text style={styles.btnIcon}>＋</Text>
            </GlassButton>
          </GlassContainer>
          <View style={styles.row}>
            <GlassButton
              title="Tinted"
              style={[styles.btn, { backgroundColor: 'rgba(10,132,255,0.45)', paddingHorizontal: 30 }]}
              titleStyle={{ color: '#fff' }}
              onPress={() => setLast('Tinted pressed')}
            />
            <GlassButton style={styles.btn} title="Disabled" dark disabled />
          </View>
        </Section>

        {/* ───────── Card: width from you, height from its content ───────── */}
        <Section title="GlassCard">
          <GlassCard dark style={{ width: '100%', padding: 0 }}>
            <View style={styles.cardHeader}>
              <View style={styles.avatar}><Text style={styles.avatarText}>AA</Text></View>
              <View style={{ flex: 1 }}>
                <Text style={styles.cardName}>Abdullrahman <Text style={styles.pro}> PRO </Text></Text>
                <Text style={styles.cardMail}>abdullrahmanalaa@gmail.com</Text>
              </View>
            </View>
            <View style={styles.divider} />
            <CardRow icon="⚙︎" title="Account Settings" onPress={() => setLast('Account Settings')} />
            <View style={styles.cardRow}>
              <Text style={styles.rowIcon}>☾</Text>
              <Text style={styles.rowTitle}>Dark mode</Text>
              <GlassSwitch value={dark} onValueChange={setDark} />
            </View>
            <CardRow icon="▤" title="View Analytics" onPress={() => setLast('View Analytics')} />
            <View style={[styles.divider, { marginHorizontal: 20 }]} />
            <CardRow icon="⇥" title="Logout" onPress={() => setLast('Logout')} />
            <View style={{ height: 8 }} />
          </GlassCard>
        </Section>

        {/* ───────── Switch & Slider ───────── */}
        <Section title="GlassSwitch · GlassSlider">
          <GlassView dark style={{ padding: 18, gap: 18 }}>
            <View style={styles.between}>
              <Text style={styles.label}>Wi-Fi</Text>
              <GlassSwitch value={wifi} onValueChange={setWifi} />
            </View>
            <View style={styles.between}>
              <Text style={styles.label}>Volume</Text>
              <Text style={styles.value}>{Math.round(volume * 100)}%</Text>
            </View>
            <GlassSlider value={volume} onValueChange={setVolume} />
            <View style={styles.between}>
              <Text style={styles.label}>Rating (step 10)</Text>
              <Text style={styles.value}>{rating}</Text>
            </View>
            <GlassSlider value={rating} minimumValue={0} maximumValue={100} step={10} accentColor="#FF9F0A" onValueChange={setRating} />
          </GlassView>
        </Section>

        {/* ───────── Sheet & Menu ───────── */}
        <Section title="GlassSheet · GlassMenu">
          <View style={styles.row}>
            <GlassButton style={styles.btn} title="Open sheet" dark onPress={() => setSheet(true)} />
            <GlassMenu
              dark
              items={[
                { title: 'Show Info', icon: <Text style={styles.menuIcon}>ⓘ</Text>, onPress: () => setSheet(true) },
                { title: 'Select Items', icon: <Text style={styles.menuIcon}>◎</Text>, onPress: () => setLast('Select Items') },
                {
                  title: 'Sort By',
                  icon: <Text style={styles.menuIcon}>⇅</Text>,
                  items: ['Date', 'Amount', 'Name'].map((m) => ({
                    title: m,
                    checked: sortBy === m,
                    onPress: () => { setSortBy(m); setLast(`Sorted by ${m}`); },
                  })),
                },
                { title: 'Show Completed', icon: <Text style={styles.menuIcon}>☑︎</Text>, checked: showDone, onPress: () => setShowDone((v) => !v) },
                { title: 'Delete List', icon: <Text style={[styles.menuIcon, { color: '#FF453A' }]}>⌫</Text>, destructive: true, onPress: () => setLast('Deleted (demo)') },
              ]}>
              <GlassButton style={styles.btn} title="Menu  •••" dark />
            </GlassMenu>
          </View>
        </Section>

        <Text style={[styles.sub, { marginTop: 24 }]}>Scroll: the glass bends the wallpaper behind it ↓</Text>
        <View style={{ height: 240 }} />
      </ScrollView>

      <GlassSheet visible={sheet} onClose={() => setSheet(false)} dark>
        <Text style={styles.sheetTitle}>Quick Controls</Text>
        <Text style={styles.sheetSub}>Drag down to close</Text>
        <View style={[styles.between, { marginTop: 18 }]}>
          <Text style={styles.label}>Volume</Text>
          <Text style={styles.value}>{Math.round(volume * 100)}%</Text>
        </View>
        <GlassSlider value={volume} onValueChange={setVolume} />
        <View style={[styles.between, { marginTop: 14 }]}>
          <Text style={styles.label}>Wi-Fi</Text>
          <GlassSwitch value={wifi} onValueChange={setWifi} />
        </View>
        <GlassButton title="Done" dark style={[styles.btn, { marginTop: 20 }]} onPress={() => { haptic('success'); setSheet(false); }} />
      </GlassSheet>
    </View>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View style={{ marginTop: 26, gap: 12 }}>
      <Text style={styles.section}>{title}</Text>
      {children}
    </View>
  );
}

function CardRow({ icon, title, onPress }: { icon: string; title: string; onPress: () => void }) {
  return (
    <GlassButton
      dark
      tintColor="rgba(255,255,255,0.02)"
      refraction={6}
      style={[styles.btn, { marginHorizontal: 10, borderRadius: 14, paddingHorizontal: 14, paddingVertical: 12, justifyContent: 'flex-start' }]}
      onPress={onPress}>
      <Text style={styles.rowIcon}>{icon}</Text>
      <Text style={styles.rowTitle}>{title}</Text>
    </GlassButton>
  );
}

/** Smooth gradient + faint lettering, so the glass has something to bend. */
function Wallpaper() {
  return (
    <View style={StyleSheet.absoluteFill} pointerEvents="none">
      <LinearGradient
        colors={['#06104a', '#1d3fd6', '#1e9fd0', '#5fd6e0']}
        locations={[0, 0.4, 0.75, 1]}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
        style={StyleSheet.absoluteFill}
      />
      {Array.from({ length: 22 }).map((_, i) => (
        <Text key={i} style={[styles.stripe, { top: 40 + i * 64 }]}>
          LIQUID  GLASS  ·  LIQUID  GLASS
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#0b1a4a' },
  content: { paddingHorizontal: 20, paddingTop: 70 },
  h1: { color: INK, fontSize: 34, fontWeight: '800', textAlign: 'center' },
  sub: { color: SUB, fontSize: 15, marginTop: 4, textAlign: 'center' },
  section: { color: SUB, fontSize: 13, fontWeight: '700', letterSpacing: 1.5, textTransform: 'uppercase', textAlign: 'center' },
  // your button layout (the package imposes none)
  btn: { paddingHorizontal: 22, paddingVertical: 13, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  row: { flexDirection: 'row', gap: 14, alignItems: 'center', justifyContent: 'center', flexWrap: 'wrap' },
  hint: { color: SUB, fontSize: 13, textAlign: 'center', marginTop: -4 },
  btnIcon: { color: INK, fontSize: 20 },
  btnText: { color: INK, fontSize: 17, fontWeight: '600' },
  between: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  label: { color: INK, fontSize: 17 },
  value: { color: SUB, fontSize: 17 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 20 },
  avatar: { width: 48, height: 48, borderRadius: 24, backgroundColor: '#c97a55', alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#fff', fontWeight: '700', fontSize: 17 },
  cardName: { color: INK, fontSize: 17, fontWeight: '600' },
  pro: { color: '#a8e8ff', fontSize: 11, fontWeight: '800' },
  cardMail: { color: SUB, fontSize: 13, marginTop: 2 },
  divider: { height: StyleSheet.hairlineWidth, backgroundColor: 'rgba(255,255,255,0.3)' },
  cardRow: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 24, paddingVertical: 10 },
  rowIcon: { color: INK, fontSize: 18, width: 30 },
  rowTitle: { color: INK, fontSize: 16, flex: 1 },
  menuIcon: { color: INK, fontSize: 18 },
  sheetTitle: { color: INK, fontSize: 26, fontWeight: '800' },
  sheetSub: { color: SUB, fontSize: 14 },
  stripe: { position: 'absolute', left: 0, right: 0, textAlign: 'center', color: 'rgba(255,255,255,0.07)', fontSize: 28, fontWeight: '900' },
});
