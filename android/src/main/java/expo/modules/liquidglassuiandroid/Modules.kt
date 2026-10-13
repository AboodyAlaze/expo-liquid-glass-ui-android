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
      Events("onPress", "onLongPress")
      Prop("cornerRadius") { v: LiquidGlassView, r: Float -> v.cornerRadiusDp = r; v.invalidate() }
      Prop("glassBorderWidth") { v: LiquidGlassView, w: Float -> v.borderWidthDp = w; v.invalidate() }
      Prop("glassBorderColor") { v: LiquidGlassView, c: Double? -> v.borderColor = c.toColorInt(); v.invalidate() }
      Prop("tintColor") { v: LiquidGlassView, c: Double? -> v.tintColor = c.toColorInt(); v.invalidate() }
      Prop("blur") { v: LiquidGlassView, x: Float -> v.look.blur = x; v.invalidate() }
      Prop("refraction") { v: LiquidGlassView, x: Float -> v.look.refraction = x; v.invalidate() }
      Prop("chroma") { v: LiquidGlassView, x: Float -> v.look.chroma = x; v.invalidate() }
      Prop("highlight") { v: LiquidGlassView, x: Float -> v.look.highlight = x; v.invalidate() }
      Prop("dark") { v: LiquidGlassView, x: Boolean -> v.dark = x; v.invalidate() }
      Prop("interactive") { v: LiquidGlassView, x: Boolean -> v.interactive = x }
      Prop("disabled") { v: LiquidGlassView, x: Boolean -> v.disabled = x }
    }
  }
}

class LiquidGlassContainerModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassContainer")
    View(LiquidGlassContainerView::class) {
      Prop("spacing") { v: LiquidGlassContainerView, x: Float -> v.spacingDp = x; v.invalidate() }
      Prop("tintColor") { v: LiquidGlassContainerView, c: Double? -> v.tintColor = c.toColorInt(); v.invalidate() }
      Prop("blur") { v: LiquidGlassContainerView, x: Float -> v.look.blur = x; v.invalidate() }
      Prop("refraction") { v: LiquidGlassContainerView, x: Float -> v.look.refraction = x; v.invalidate() }
      Prop("chroma") { v: LiquidGlassContainerView, x: Float -> v.look.chroma = x; v.invalidate() }
      Prop("highlight") { v: LiquidGlassContainerView, x: Float -> v.look.highlight = x; v.invalidate() }
      Prop("dark") { v: LiquidGlassContainerView, x: Boolean -> v.dark = x; v.invalidate() }
    }
  }
}

class LiquidGlassSwitchModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassSwitch")
    View(LiquidGlassSwitchView::class) {
      Events("onValueChange")
      Prop("value") { v: LiquidGlassSwitchView, x: Boolean -> v.setValueFromJs(x) }
      Prop("onColor") { v: LiquidGlassSwitchView, c: Double? -> c.toColorInt()?.let { v.onColor = it }; v.invalidate() }
      Prop("offColor") { v: LiquidGlassSwitchView, c: Double? -> v.offColor = c.toColorInt(); v.invalidate() }
      Prop("dark") { v: LiquidGlassSwitchView, x: Boolean -> v.dark = x; v.invalidate() }
      Prop("disabled") { v: LiquidGlassSwitchView, x: Boolean -> v.disabled = x; v.invalidate() }
    }
  }
}

class LiquidGlassSliderModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("LiquidGlassSlider")
    View(LiquidGlassSliderView::class) {
      Events("onValueChange", "onSlidingComplete")
      // range first, then value (props are applied in declaration order)
      Prop("minimumValue") { v: LiquidGlassSliderView, x: Float -> v.min = x; v.invalidate() }
      Prop("maximumValue") { v: LiquidGlassSliderView, x: Float -> v.max = x; v.invalidate() }
      Prop("step") { v: LiquidGlassSliderView, x: Float -> v.step = x }
      Prop("value") { v: LiquidGlassSliderView, x: Float -> v.setValueFromJs(x) }
      Prop("accentColor") { v: LiquidGlassSliderView, c: Double? -> c.toColorInt()?.let { v.accent = it }; v.invalidate() }
      Prop("trackColor") { v: LiquidGlassSliderView, c: Double? -> v.trackColor = c.toColorInt(); v.invalidate() }
      Prop("dark") { v: LiquidGlassSliderView, x: Boolean -> v.dark = x; v.invalidate() }
      Prop("disabled") { v: LiquidGlassSliderView, x: Boolean -> v.disabled = x; v.invalidate() }
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
