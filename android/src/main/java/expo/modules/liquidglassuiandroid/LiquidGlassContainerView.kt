package expo.modules.liquidglassuiandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.Choreographer
import android.view.View
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView

/**
 * Like iOS 26's GlassEffectContainer: the GlassButtons / GlassViews directly inside it share ONE
 * piece of liquid glass. Pull a button toward its neighbour and they melt together with a liquid
 * bridge; let go and they separate again.
 *
 * The container itself is invisible (it only lays its children out, like a View). It captures
 * nothing itself (the window's BackdropManager does) and doesn't redraw while you scroll.
 */
class LiquidGlassContainerView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  private val d = resources.displayMetrics.density
  internal val look = GlassLook()
  internal var spacingDp = 16f
  internal var tintColor: Int? = null
  internal var dark = false

  private val renderer = GlassRenderer(d)
  private val shapes = FloatArray(8 * 5)
  private val glowTmp = FloatArray(3)

  private val appear = Spring(0f, 600f, 1f)
  private var fading = false
  private val fadeFrame = object : Choreographer.FrameCallback {
    private var last = 0L
    override fun doFrame(now: Long) {
      val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.032f)
      last = now
      val moving = appear.step(dt)
      invalidate()
      if (moving) Choreographer.getInstance().postFrameCallback(this) else { fading = false; last = 0L }
    }
  }

  private val backdrop = Backdrop(this, object : Backdrop.Client {
    override fun wantsBackdrop() = childCount > 0
    override fun onBackdropReady() {
      appear.target = 1f
      if (!fading) { fading = true; Choreographer.getInstance().postFrameCallback(fadeFrame) }
    }
  })

  init {
    setWillNotDraw(false)
    clipChildren = false
    clipToPadding = false
  }

  // React Native positions the children (ExpoView is a LinearLayout — never let it re-arrange them)
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit

  override fun onViewAdded(child: View?) {
    super.onViewAdded(child)
    (child as? LiquidGlassView)?.let { it.grouped = true; it.invalidate() }
    invalidate()
  }

  override fun onViewRemoved(child: View?) {
    (child as? LiquidGlassView)?.let { it.grouped = false; it.invalidate() }
    super.onViewRemoved(child)
    invalidate()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    backdrop.attach()
  }

  override fun onDetachedFromWindow() {
    backdrop.detach()
    Choreographer.getInstance().removeFrameCallback(fadeFrame); fading = false
    appear.snap(0f)
    super.onDetachedFromWindow()
  }

  override fun dispatchDraw(canvas: Canvas) {
    var n = 0
    var glowX = 0f; var glowY = 0f; var glow = 0f
    for (i in 0 until childCount) {
      if (n == 8) break
      val c = getChildAt(i) as? LiquidGlassView ?: continue
      if (c.visibility != View.VISIBLE || c.width == 0 || c.height == 0) continue
      // the child's current on-screen box: its layout + press/stretch transforms (pivot = center)
      val cx = c.left + c.width / 2f + c.translationX
      val cy = c.top + c.height / 2f + c.translationY
      val hw = c.width * c.scaleX / 2f; val hh = c.height * c.scaleY / 2f
      val o = n * 5
      shapes[o] = cx - hw; shapes[o + 1] = cy - hh; shapes[o + 2] = cx + hw; shapes[o + 3] = cy + hh
      shapes[o + 4] = c.cornerRadiusPx() * minOf(c.scaleX, c.scaleY)
      c.glowLocal(glowTmp)
      if (glowTmp[2] > glow) {
        glow = glowTmp[2]
        glowX = cx + (glowTmp[0] - c.width / 2f) * c.scaleX
        glowY = cy + (glowTmp[1] - c.height / 2f) * c.scaleY
      }
      n++
    }
    val canCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backdrop.renderNode() else null
    if (n > 0 && (node != null || !canCapture)) {
      look.tint = tintColor ?: if (dark) Color.argb(77, 60, 60, 67) else Color.argb(36, 255, 255, 255)
      renderer.drawShapes(
        canvas, node, shapes, n, look, spacingDp * d,
        glowX = glowX, glowY = glowY, glow = glow,
        alpha = if (canCapture) appear.value else 1f,
      )
    }
    super.dispatchDraw(canvas)
  }
}
