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
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
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
 */
class LiquidGlassView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val onPress by EventDispatcher()
  private val onLongPress by EventDispatcher()

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
  private val backdrop = Backdrop()
  private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val rect = RectF()
  private val ploc = IntArray(2)
  private var lastX = Int.MIN_VALUE
  private var lastY = Int.MIN_VALUE

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
  internal fun glowLocal(): FloatArray = floatArrayOf(touchX, touchY, glow.value)

  // ───────── backdrop: refreshed before every frame the window draws ─────────
  private val preDraw = ViewTreeObserver.OnPreDrawListener {
    if (!grouped && backdrop.capture(this)) {
      val (x, y) = windowOrigin()
      if (x != lastX || y != lastY) invalidate() // moved (e.g. scrolling) → re-place the glass
    }
    true
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    backdrop.onAttached()
    viewTreeObserver.addOnPreDrawListener(preDraw)
    postDelayed({ invalidate() }, 300) // first frame with a real backdrop
  }

  override fun onDetachedFromWindow() {
    viewTreeObserver.removeOnPreDrawListener(preDraw)
    Choreographer.getInstance().removeFrameCallback(frame); looping = false
    super.onDetachedFromWindow()
  }

  /** Where our untransformed (0,0) sits in the window (ignores our own press scale). */
  private fun windowOrigin(): Pair<Int, Int> {
    val p = parent as? View ?: return 0 to 0
    p.getLocationInWindow(ploc)
    return (ploc[0] + left - p.scrollX) to (ploc[1] + top - p.scrollY)
  }

  internal fun effectiveTint(): Int = tintColor ?: if (dark) Color.argb(46, 0, 0, 0) else Color.argb(36, 255, 255, 255)

  override fun dispatchDraw(canvas: Canvas) {
    val (x, y) = windowOrigin(); lastX = x; lastY = y
    look.tint = effectiveTint()
    val r = cornerRadiusDp * d
    if (!grouped) renderer.draw(
      canvas, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backdrop.renderNode() else null,
      0f, 0f, width.toFloat(), height.toFloat(), r, x.toFloat(), y.toFloat(), look,
      glowX = touchX, glowY = touchY, glow = glow.value,
    )
    if (borderWidthDp > 0f) {
      val bw = borderWidthDp * d
      border.strokeWidth = bw
      border.color = borderColor ?: Color.argb(90, 255, 255, 255)
      rect.set(bw / 2f, bw / 2f, width - bw / 2f, height - bw / 2f)
      val rr = min(r, min(width, height) / 2f) - bw / 2f
      canvas.drawRoundRect(rect, rr, rr, border)
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
  private val slop = ViewConfiguration.get(context).scaledTouchSlop
  private val longPress = Runnable {
    longFired = true
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    onLongPress(mapOf())
  }

  override fun onTouchEvent(e: MotionEvent): Boolean {
    if (!interactive || disabled) return super.onTouchEvent(e)
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downX = e.x; downY = e.y; downRawX = e.rawX; downRawY = e.rawY
        touchX = e.x; touchY = e.y; longFired = false; claimed = false
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
      moving = offX.step(dt) || moving
      moving = offY.step(dt) || moving
      moving = glow.step(dt) || moving
      // follows the finger, stretches along the pull and thins across it (volume is kept)
      val sx = 1f + abs(offX.value) / width.coerceAtLeast(1).toFloat() * 0.9f
      val sy = 1f + abs(offY.value) / height.coerceAtLeast(1).toFloat() * 0.9f
      scaleX = press.value * sx / kotlin.math.sqrt(sy)
      scaleY = press.value * sy / kotlin.math.sqrt(sx)
      translationX = offX.value * 0.75f
      translationY = offY.value * 0.75f
      invalidate()
      (parent as? LiquidGlassContainerView)?.invalidate() // merged glass follows us
      if (moving) Choreographer.getInstance().postFrameCallback(this) else { looping = false; lastFrame = 0L }
    }
  }

  private fun startLoop() {
    if (looping) return
    looping = true; lastFrame = 0L
    Choreographer.getInstance().postFrameCallback(frame)
  }
}
