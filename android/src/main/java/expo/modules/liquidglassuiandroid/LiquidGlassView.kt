package expo.modules.liquidglassuiandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import kotlin.math.abs
import kotlin.math.min

/**
 * A normal React Native container (children, padding, width, flex… all work as usual)
 * whose background is liquid glass refracting whatever is behind it.
 *
 * With `interactive` it behaves like an iOS 26 glass button: it swells under the finger,
 * stretches toward it, glows where you touch, and wobbles back like jelly.
 *
 * Performance: it never captures anything itself (the window's BackdropManager does it once for
 * everybody) and it does NOT redraw while you scroll — only when its own look changes.
 *
 * Morph mode (GlassToast): a bead that opens into a pill, animated natively on the UI thread.
 */
class LiquidGlassView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val onPress by EventDispatcher()
  private val onLongPress by EventDispatcher()
  private val onMorphEnd by EventDispatcher()

  private val d = resources.displayMetrics.density
  internal val look = GlassLook()
  internal var cornerRadiusDp = 24f
  internal var borderWidthDp = 0f
  internal var borderColor: Int? = null
  internal var tintColor: Int? = null
  internal var dark = false
  internal var interactive = false
  internal var disabled = false

  private val renderer = GlassRenderer(d)
  private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val rect = RectF()
  private var glassDrawn = false

  /** Set by a GlassContainer parent: the container draws our glass (merged with siblings). */
  internal var grouped = false

  init {
    setWillNotDraw(false)
    clipChildren = false
    clipToPadding = false
  }

  // ExpoView is a LinearLayout: left alone, Android would re-arrange our React Native children in
  // a horizontal row (text pushed off to the right, nothing centered). React Native already
  // positions every child, so we never measure or lay them out ourselves — exactly like <View>.
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit

  internal fun cornerRadiusPx() = cornerRadiusDp * d
  /** Writes [touchX, touchY, glow] into [out] (no allocation — called every frame by a container). */
  internal fun glowLocal(out: FloatArray) { out[0] = touchX; out[1] = touchY; out[2] = glow.value }

  // ───────── backdrop (captured by the window's BackdropManager, shared) ─────────
  private val appear = Spring(0f, 600f, 1f) // glass fades in once it has a real backdrop
  private val backdrop = Backdrop(this, object : Backdrop.Client {
    override fun wantsBackdrop() = !grouped
    override fun onBackdropReady() { appear.target = 1f; startLoop(); invalidate() }
  })

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    backdrop.attach()
  }

  override fun onDetachedFromWindow() {
    backdrop.detach()
    appear.snap(0f)
    Choreographer.getInstance().removeFrameCallback(frame); looping = false
    removeCallbacks(longPress)
    super.onDetachedFromWindow()
  }

  internal fun effectiveTint(): Int = tintColor ?: if (dark) Color.argb(77, 60, 60, 67) else Color.argb(36, 255, 255, 255)

  // ───────── morph mode (GlassToast): a bead that opens into a pill ─────────
  internal var morphBeadDp = 0f              // > 0 turns morph mode on
  internal var morphAnchor = 0               // 0 centre · 1 grows right · -1 grows left
  private val openness = Spring(0f, 170f, 0.62f)
  private var morphNotified = true
  internal var morphOpen = false
    set(v) {
      if (field == v) return
      field = v
      openness.stiffness = if (v) 170f else 230f
      openness.dampingRatio = if (v) 0.62f else 0.9f
      openness.target = if (v) 1f else 0f
      morphNotified = false
      startLoop()
    }

  /** The glass shape right now: the whole view, or (morph mode) the bead/pill inside it. */
  private fun shapeRect(out: RectF) {
    if (morphBeadDp <= 0f) { out.set(0f, 0f, width.toFloat(), height.toFloat()); return }
    val bead = (morphBeadDp * d).coerceAtMost(height.toFloat())
    val w = bead + (width - bead) * openness.value
    val x0 = when {
      morphAnchor > 0 -> 0f
      morphAnchor < 0 -> width - w
      else -> (width - w) / 2f
    }
    out.set(x0, 0f, x0 + w, height.toFloat())
  }

  override fun dispatchDraw(canvas: Canvas) {
    look.tint = effectiveTint()
    shapeRect(rect)
    val r = if (morphBeadDp > 0f) rect.height() / 2f else min(cornerRadiusDp * d, min(width, height) / 2f)
    // Before the first real capture we draw no glass at all (instead of a grey stand-in that
    // would "pop" into glass); old devices without capture keep their simple translucent look.
    val canCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backdrop.renderNode() else null
    glassDrawn = !grouped && (node != null || !canCapture)
    if (glassDrawn) renderer.draw(
      canvas, node, rect.left, rect.top, rect.right, rect.bottom, r, look,
      glowX = touchX, glowY = touchY, glow = glow.value,
      alpha = if (canCapture) appear.value else 1f,
    )
    if (borderWidthDp > 0f) {
      val bw = borderWidthDp * d
      border.strokeWidth = bw
      border.color = borderColor ?: Color.argb(90, 255, 255, 255)
      val rr = r - bw / 2f
      rect.inset(bw / 2f, bw / 2f)
      canvas.drawRoundRect(rect, rr, rr, border)
      rect.inset(-bw / 2f, -bw / 2f)
    }
    if (morphBeadDp > 0f) {
      // morph: the content is revealed inside the opening pill
      val a = ((openness.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
      if (a <= 0.01f) return
      val count = canvas.save()
      // a plain rect clip (a GPU scissor — free) instead of a rounded path (a mask every frame);
      // the content's padding keeps it clear of the pill's rounded ends
      canvas.clipRect(rect)
      if (a < 0.99f) canvas.saveLayerAlpha(rect, (a * 255).toInt()) // only while it fades in / out
      super.dispatchDraw(canvas)
      canvas.restoreToCount(count)
      return
    }
    super.dispatchDraw(canvas)
  }



  // ───────── interaction (only when `interactive`) ─────────
  private val press = Spring(1f, 600f, 0.55f)
  private val offX = Spring(0f, 900f, 0.9f)   // rubber-banded finger offset (px)
  private val offY = Spring(0f, 900f, 0.9f)
  private val glow = Spring(0f, 300f, 1f)
  private var claimed = false

  /** Rubber band: follows the finger at first, then resists more and more. */
  private fun rubber(v: Float): Float {
    val limit = 26f * d
    val s = if (v < 0) -1f else 1f
    return s * limit * (1f - kotlin.math.exp(-abs(v) / (limit * 1.6f)))
  }

  private fun tracking(on: Boolean) {
    // while held: stiff + damped (follows the finger); released: soft + bouncy (jelly)
    val k = if (on) 900f else 240f; val z = if (on) 0.9f else 0.3f
    offX.stiffness = k; offX.dampingRatio = z; offY.stiffness = k; offY.dampingRatio = z
  }
  private var touchX = 0f
  private var touchY = 0f
  private var downX = 0f
  private var downY = 0f
  private var downRawX = 0f
  private var downRawY = 0f
  private var longFired = false
  private var touching = false
  private val slop = ViewConfiguration.get(context).scaledTouchSlop
  private val longPress = Runnable {
    longFired = true
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    onLongPress(mapOf())
  }

  // A button owns every touch on its surface: children like an Svg (react-native-svg grabs touches
  // for its own shapes) or an Image must never swallow the press.
  override fun onInterceptTouchEvent(e: MotionEvent): Boolean = interactive && !disabled

  override fun onTouchEvent(e: MotionEvent): Boolean {
    if (!interactive || disabled) return super.onTouchEvent(e)
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = e.x; downY = e.y; downRawX = e.rawX; downRawY = e.rawY
        touchX = e.x; touchY = e.y; longFired = false; claimed = false; touching = true
        tracking(true)
        press.target = 1.07f; glow.target = 1f
        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
        startLoop()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        // e.x/e.y are in our (moving) local space → convert back to a stable finger delta
        val dx = e.rawX - downRawX; val dy = e.rawY - downRawY
        touchX = downX + dx - translationX; touchY = downY + dy - translationY
        if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(longPress)
        // a mostly-horizontal drag is ours; a vertical one is left to the scroll view
        if (!claimed && abs(dx) > slop && abs(dx) > abs(dy)) { claimed = true; parent?.requestDisallowInterceptTouchEvent(true) }
        offX.target = rubber(dx); offY.target = rubber(dy)
        startLoop()
        return true
      }
      MotionEvent.ACTION_UP -> {
        removeCallbacks(longPress)
        val m = 24f * d
        val fx = downX + (e.rawX - downRawX); val fy = downY + (e.rawY - downRawY)
        val inside = fx >= -m && fy >= -m && fx <= width + m && fy <= height + m
        if (inside && !longFired) {
          performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
          onPress(mapOf())
        }
        release()
        return true
      }
      MotionEvent.ACTION_CANCEL -> { removeCallbacks(longPress); release(); return true }
    }
    return true
  }

  private fun release() {
    touching = false
    if (claimed) parent?.requestDisallowInterceptTouchEvent(false)
    claimed = false
    tracking(false)
    press.target = 1f; offX.target = 0f; offY.target = 0f; glow.target = 0f
    startLoop()
  }

  private var looping = false
  private var lastFrame = 0L
  private val frame = object : Choreographer.FrameCallback {
    override fun doFrame(now: Long) {
      val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0.001f, 0.032f)
      lastFrame = now
      var moving = press.step(dt)
      val redraw = appear.step(dt) or glow.step(dt) or openness.step(dt)
      moving = redraw || moving
      moving = offX.step(dt) || moving
      moving = offY.step(dt) || moving
      // follows the finger, stretches along the pull and thins across it (volume is kept)
      val sx = 1f + abs(offX.value) / width.coerceAtLeast(1).toFloat() * 0.9f
      val sy = 1f + abs(offY.value) / height.coerceAtLeast(1).toFloat() * 0.9f
      scaleX = press.value * sx / kotlin.math.sqrt(sy)
      scaleY = press.value * sy / kotlin.math.sqrt(sx)
      translationX = offX.value * 0.75f
      translationY = offY.value * 0.75f
      // scale / translation are applied by the GPU without redrawing; we only re-record when the
      // light, the fade-in or the morph shape changed (or while the finger moves the glow)
      if (redraw || touching) invalidate()
      (parent as? LiquidGlassContainerView)?.invalidate() // merged glass follows us
      if (!morphNotified && kotlin.math.abs(openness.value - openness.target) < 0.002f && kotlin.math.abs(openness.velocity) < 0.01f) {
        morphNotified = true
        onMorphEnd(mapOf("open" to morphOpen))
      }
      if (moving) Choreographer.getInstance().postFrameCallback(this) else { looping = false; lastFrame = 0L }
    }
  }

  private fun startLoop() {
    if (looping) return
    looping = true; lastFrame = 0L
    Choreographer.getInstance().postFrameCallback(frame)
  }
}
