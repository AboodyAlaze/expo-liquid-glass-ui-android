package expo.modules.liquidglassuiandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * iOS 26-style slider. While dragging, the thumb becomes a liquid-glass lens that magnifies
 * the track, and stretches with your speed. With a `step`, it ticks (haptic) on every step.
 */
class LiquidGlassSliderView(context: Context, appContext: AppContext) : LensControl(context, appContext) {
  private val onValueChange by EventDispatcher()
  private val onSlidingComplete by EventDispatcher()

  internal var accent = Color.rgb(10, 132, 255)
  internal var trackColor: Int? = null
  internal var min = 0f
  internal var max = 1f
  internal var step = 0f
  private var initialized = false

  private val pos = Spring(0f, 900f, 0.85f)    // 0..1 along the track
  private val hold = Spring(0f, 520f, 0.7f)
  private val stretch = Spring(0f, 300f, 0.5f)
  private var dragging = false
  private var grab = 0f
  private var lastX = 0f
  private var lastT = 0L
  private var lastEmitted = Float.NaN

  internal fun setValueFromJs(v: Float) {
    val f = norm(v)
    if (!initialized) { initialized = true; pos.snap(f); invalidate(); return }
    if (dragging) return
    pos.target = f; startLoop()
  }

  private fun norm(v: Float) = if (max > min) ((v - min) / (max - min)).coerceIn(0f, 1f) else 0f
  private fun denorm(f: Float): Float {
    var v = min + (max - min) * f.coerceIn(0f, 1f)
    if (step > 0f) v = (Math.round((v - min) / step) * step + min).coerceIn(min, max)
    return v
  }

  private fun thumbH() = (24f * d).coerceAtMost(height - 4f * d)
  private fun thumbW() = thumbH() * 1.55f
  private fun thumbX(f: Float): Float {
    val a = thumbW() / 2f; val b = width - thumbW() / 2f
    return a + (b - a) * f.coerceIn(0f, 1f)
  }

  override fun drawTrack(canvas: Canvas) {
    val th = 6f * d; val cy = height / 2f
    paint.style = Paint.Style.FILL
    paint.color = trackColor ?: if (dark) Color.argb(90, 120, 120, 128) else Color.argb(46, 120, 120, 128)
    rect.set(0f, cy - th / 2f, width.toFloat(), cy + th / 2f)
    canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
    paint.color = accent
    rect.set(0f, cy - th / 2f, thumbX(pos.value), cy + th / 2f)
    canvas.drawRoundRect(rect, th / 2f, th / 2f, paint)
  }

  override fun onDraw(canvas: Canvas) {
    if (disabled) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), 128)
    drawTrack(canvas)
    val h = hold.value.coerceIn(0f, 1.2f)
    val s = stretch.value
    val cx = thumbX(pos.value); val cy = height / 2f
    val w = thumbW() * (1f + 0.45f * h) * (1f + s); val hh = thumbH() * (1f + 0.55f * h) / (1f + s * 0.5f)
    drawKnob(canvas, cx, cy, w, hh, 1f - h.coerceIn(0f, 1f))
    if (h > 0.01f) drawLens(canvas, cx, cy, w, hh, 1f + 0.22f * h)
    if (disabled) canvas.restore()
  }

  override fun stepSprings(dt: Float): Boolean {
    var m = pos.step(dt)
    m = hold.step(dt) || m
    m = stretch.step(dt) || m
    return m
  }

  private fun fractionAt(x: Float): Float {
    val a = thumbW() / 2f; val b = width - thumbW() / 2f
    return ((x - a) / (b - a).coerceAtLeast(1f)).coerceIn(0f, 1f)
  }

  private fun emit(f: Float) {
    val v = denorm(f)
    if (v == lastEmitted) return
    if (step > 0f && !lastEmitted.isNaN()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    lastEmitted = v
    onValueChange(mapOf("value" to v))
  }

  override fun onTouchEvent(e: MotionEvent): Boolean {
    if (disabled) return false
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        val onThumb = abs(e.x - thumbX(pos.value)) < thumbW()
        grab = if (onThumb) e.x - thumbX(pos.value) else 0f
        if (!onThumb) pos.target = fractionAt(e.x) else pos.snap(pos.value)
        hold.target = 1f
        lastX = e.x; lastT = e.eventTime; lastEmitted = denorm(pos.value)
        if (!onThumb) emit(pos.target)
        startLoop()
      }
      MotionEvent.ACTION_MOVE -> {
        val f = fractionAt(e.x - grab)
        val stepped = if (step > 0f && max > min) norm(denorm(f)) else f
        pos.target = stepped
        val dt = ((e.eventTime - lastT).coerceAtLeast(1L)) / 1000f
        val v = abs(e.x - lastX) / dt / d // dp per second
        stretch.target = (v / 2600f).coerceAtMost(0.3f)
        lastX = e.x; lastT = e.eventTime
        emit(f)
        startLoop()
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        dragging = false
        hold.target = 0f; stretch.target = 0f
        if (step > 0f) pos.target = norm(denorm(pos.target))
        parent?.requestDisallowInterceptTouchEvent(false)
        onSlidingComplete(mapOf("value" to denorm(pos.target)))
        startLoop()
      }
    }
    return true
  }

  @Suppress("unused")
  private fun Float.r2() = (this * 100f).roundToInt() / 100f
}
