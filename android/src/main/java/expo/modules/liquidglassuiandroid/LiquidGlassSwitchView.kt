package expo.modules.liquidglassuiandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import kotlin.math.abs

/**
 * iOS 26-style switch. Tap to toggle or drag the knob. While held, the knob swells into a
 * liquid-glass lens that pokes out of the track and magnifies it.
 */
class LiquidGlassSwitchView(context: Context, appContext: AppContext) : LensControl(context, appContext) {
  private val onValueChange by EventDispatcher()

  internal var onColor = Color.rgb(52, 199, 89)
  internal var offColor: Int? = null
  private var value = false
  private var initialized = false

  private val progress = Spring(0f, 420f, 0.78f)  // 0 off … 1 on
  private val hold = Spring(0f, 520f, 0.7f)
  override fun lensVisible() = hold.value > 0.001f || hold.target > 0f       // 0 knob … 1 lens

  private var pressed = false
  private var dragging = false
  private var downX = 0f
  private var startProgress = 0f
  private val slop = ViewConfiguration.get(context).scaledTouchSlop

  internal fun setValueFromJs(v: Boolean) {
    if (!initialized) { initialized = true; value = v; progress.snap(if (v) 1f else 0f); invalidate(); return }
    if (pressed) return
    if (v != value) { value = v; progress.target = if (v) 1f else 0f; startLoop() }
  }

  private fun inset() = 2f * d
  private fun knobH() = height - inset() * 2
  private fun knobW() = (knobH() * 1.55f).coerceAtMost(width * 0.62f)
  private fun knobCenterX(p: Float): Float {
    val min = inset() + knobW() / 2f; val max = width - inset() - knobW() / 2f
    return min + (max - min) * p.coerceIn(0f, 1f)
  }

  private fun trackColor(): Int {
    val off = offColor ?: if (dark) Color.argb(110, 120, 120, 128) else Color.argb(52, 120, 120, 128)
    return blend(off, onColor, progress.value)
  }

  override fun drawTrack(canvas: Canvas) {
    paint.style = Paint.Style.FILL
    paint.color = trackColor()
    rect.set(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, height / 2f, height / 2f, paint)
  }

  override fun onDraw(canvas: Canvas) {
    if (disabled) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 128)
    drawTrack(canvas)
    val h = hold.value.coerceIn(0f, 1.2f)
    val cx = knobCenterX(progress.value); val cy = height / 2f
    val kw = knobW() * (1f + 0.38f * h); val kh = knobH() * (1f + 0.8f * h)
    drawKnob(canvas, cx, cy, kw, kh, 1f - h.coerceIn(0f, 1f))
    if (h > 0.01f) drawLens(canvas, cx, cy, kw, kh, 1f + 0.18f * h)
    if (disabled) canvas.restore()
  }

  override fun stepSprings(dt: Float): Boolean {
    var m = progress.step(dt)
    m = hold.step(dt) || m
    return m
  }

  override fun onTouchEvent(e: MotionEvent): Boolean {
    if (disabled) return false
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        pressed = true; dragging = false
        downX = e.x; startProgress = progress.value
        hold.target = 1f
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        startLoop()
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = e.x - downX
        if (!dragging && abs(dx) > slop) dragging = true
        if (dragging) {
          val range = (width - inset() * 2 - knobW()).coerceAtLeast(1f)
          progress.snap((startProgress + dx / range).coerceIn(-0.08f, 1.08f)) // tiny rubber band
          invalidate()
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val newValue = when {
          e.actionMasked == MotionEvent.ACTION_CANCEL -> value
          dragging -> progress.value > 0.5f
          else -> !value
        }
        pressed = false; dragging = false
        hold.target = 0f
        progress.target = if (newValue) 1f else 0f
        if (newValue != value) {
          value = newValue
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
          onValueChange(mapOf("value" to newValue))
        }
        parent?.requestDisallowInterceptTouchEvent(false)
        startLoop()
      }
    }
    return true
  }
}
