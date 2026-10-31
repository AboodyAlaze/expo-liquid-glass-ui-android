package expo.modules.liquidglassuiandroid

import android.os.Build
import android.view.HapticFeedbackConstants
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

/** JS colors arrive as processColor() numbers (ARGB, possibly unsigned). */
private fun Double?.toColorInt(): Int? = this?.toLong()?.toInt()

class LiquidGlassViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassView")
    View(LiquidGlassView::class) {
      Events("onPress", "onLongPress", "onMorphEnd")
      Prop("cornerRadius") { v: LiquidGlassView, r: Float -> val nv = r; if (v.cornerRadiusDp != nv) { v.cornerRadiusDp = nv; v.invalidate() } }
      Prop("glassBorderWidth") { v: LiquidGlassView, w: Float -> val nv = w; if (v.borderWidthDp != nv) { v.borderWidthDp = nv; v.invalidate() } }
      Prop("glassBorderColor") { v: LiquidGlassView, c: Double? -> val nv = c.toColorInt(); if (v.borderColor != nv) { v.borderColor = nv; v.invalidate() } }
      Prop("tintColor") { v: LiquidGlassView, c: Double? -> val nv = c.toColorInt(); if (v.tintColor != nv) { v.tintColor = nv; v.invalidate() } }
      Prop("blur") { v: LiquidGlassView, x: Float -> val nv = x; if (v.look.blur != nv) { v.look.blur = nv; v.invalidate() } }
      Prop("refraction") { v: LiquidGlassView, x: Float -> val nv = x; if (v.look.refraction != nv) { v.look.refraction = nv; v.invalidate() } }
      Prop("chroma") { v: LiquidGlassView, x: Float -> val nv = x; if (v.look.chroma != nv) { v.look.chroma = nv; v.invalidate() } }
      Prop("highlight") { v: LiquidGlassView, x: Float -> val nv = x; if (v.look.highlight != nv) { v.look.highlight = nv; v.invalidate() } }
      Prop("dark") { v: LiquidGlassView, x: Boolean -> val nv = x; if (v.dark != nv) { v.dark = nv; v.invalidate() } }
      Prop("interactive") { v: LiquidGlassView, x: Boolean -> v.interactive = x }
      Prop("disabled") { v: LiquidGlassView, x: Boolean -> v.disabled = x }
      // morph mode (GlassToast): a bead that opens into a pill, animated natively
      Prop("morphBead") { v: LiquidGlassView, x: Float -> val nv = x; if (v.morphBeadDp != nv) { v.morphBeadDp = nv; v.invalidate() } }
      Prop("morphAnchor") { v: LiquidGlassView, x: String? ->
        val nv = when (x) { "right" -> 1; "left" -> -1; else -> 0 }
        if (v.morphAnchor != nv) { v.morphAnchor = nv; v.invalidate() }
      }
      Prop("morphOpen") { v: LiquidGlassView, x: Boolean -> v.morphOpen = x }
    }
  }
}

class LiquidGlassContainerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassContainer")
    View(LiquidGlassContainerView::class) {
      Prop("spacing") { v: LiquidGlassContainerView, x: Float -> val nv = x; if (v.spacingDp != nv) { v.spacingDp = nv; v.invalidate() } }
      Prop("tintColor") { v: LiquidGlassContainerView, c: Double? -> val nv = c.toColorInt(); if (v.tintColor != nv) { v.tintColor = nv; v.invalidate() } }
      Prop("blur") { v: LiquidGlassContainerView, x: Float -> val nv = x; if (v.look.blur != nv) { v.look.blur = nv; v.invalidate() } }
      Prop("refraction") { v: LiquidGlassContainerView, x: Float -> val nv = x; if (v.look.refraction != nv) { v.look.refraction = nv; v.invalidate() } }
      Prop("chroma") { v: LiquidGlassContainerView, x: Float -> val nv = x; if (v.look.chroma != nv) { v.look.chroma = nv; v.invalidate() } }
      Prop("highlight") { v: LiquidGlassContainerView, x: Float -> val nv = x; if (v.look.highlight != nv) { v.look.highlight = nv; v.invalidate() } }
      Prop("dark") { v: LiquidGlassContainerView, x: Boolean -> val nv = x; if (v.dark != nv) { v.dark = nv; v.invalidate() } }
    }
  }
}

class LiquidGlassSwitchModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassSwitch")
    View(LiquidGlassSwitchView::class) {
      Events("onValueChange")
      Prop("value") { v: LiquidGlassSwitchView, x: Boolean -> v.setValueFromJs(x) }
      Prop("onColor") { v: LiquidGlassSwitchView, c: Double? -> c.toColorInt()?.let { if (v.onColor != it) { v.onColor = it; v.invalidate() } } }
      Prop("offColor") { v: LiquidGlassSwitchView, c: Double? -> val nv = c.toColorInt(); if (v.offColor != nv) { v.offColor = nv; v.invalidate() } }
      Prop("dark") { v: LiquidGlassSwitchView, x: Boolean -> val nv = x; if (v.dark != nv) { v.dark = nv; v.invalidate() } }
      Prop("disabled") { v: LiquidGlassSwitchView, x: Boolean -> val nv = x; if (v.disabled != nv) { v.disabled = nv; v.invalidate() } }
    }
  }
}

class LiquidGlassSliderModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassSlider")
    View(LiquidGlassSliderView::class) {
      Events("onValueChange", "onSlidingComplete")
      // range first, then value (props are applied in declaration order)
      Prop("minimumValue") { v: LiquidGlassSliderView, x: Float -> val nv = x; if (v.min != nv) { v.min = nv; v.invalidate() } }
      Prop("maximumValue") { v: LiquidGlassSliderView, x: Float -> val nv = x; if (v.max != nv) { v.max = nv; v.invalidate() } }
      Prop("step") { v: LiquidGlassSliderView, x: Float -> v.step = x }
      Prop("value") { v: LiquidGlassSliderView, x: Float -> v.setValueFromJs(x) }
      Prop("accentColor") { v: LiquidGlassSliderView, c: Double? -> c.toColorInt()?.let { if (v.accent != it) { v.accent = it; v.invalidate() } } }
      Prop("trackColor") { v: LiquidGlassSliderView, c: Double? -> val nv = c.toColorInt(); if (v.trackColor != nv) { v.trackColor = nv; v.invalidate() } }
      Prop("dark") { v: LiquidGlassSliderView, x: Boolean -> val nv = x; if (v.dark != nv) { v.dark = nv; v.invalidate() } }
      Prop("disabled") { v: LiquidGlassSliderView, x: Boolean -> val nv = x; if (v.disabled != nv) { v.disabled = nv; v.invalidate() } }
    }
  }
}

/** Haptics for the JS-side components (menu, sheet). No permission needed. */
class LiquidGlassHapticsModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassHaptics")
    Function("impact") { style: String ->
      val view = appContext.currentActivity?.window?.decorView ?: return@Function
      val type = when (style) {
        "selection" -> HapticFeedbackConstants.CLOCK_TICK
        "light" -> HapticFeedbackConstants.KEYBOARD_TAP
        "medium" -> HapticFeedbackConstants.VIRTUAL_KEY
        "heavy" -> HapticFeedbackConstants.LONG_PRESS
        "success" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        "error" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        else -> HapticFeedbackConstants.KEYBOARD_TAP
      }
      view.post { view.performHapticFeedback(type) }
    }
  }
}
