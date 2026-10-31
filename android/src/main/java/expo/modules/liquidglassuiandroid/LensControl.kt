package expo.modules.liquidglassuiandroid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderNode
import android.os.Build
import android.view.Choreographer
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView

/**
 * Base for GlassSwitch and GlassSlider.
 * At rest they draw a crisp white knob; while you hold them the knob turns into a liquid-glass
 * lens that magnifies the track and the page behind it.
 *
 * Performance: the page behind is captured ONLY while the lens is visible — a switch or slider
 * at rest costs nothing per frame.
 */
abstract class LensControl(context: Context, appContext: AppContext) : ExpoView(context, appContext) {
  protected val d = resources.displayMetrics.density
  internal var dark = false
  internal var disabled = false
  protected val renderer = GlassRenderer(d)
  protected val backdrop = Backdrop(this, object : Backdrop.Client {
    override fun wantsBackdrop() = lensVisible()
    override fun onBackdropReady() = invalidate()
  })

  /** True while the lens is (or is about to be) on screen. */
  protected abstract fun lensVisible(): Boolean
  protected val lensLook = GlassLook(blur = 0f, refraction = 10f, bezel = 10f, chroma = 0.16f, highlight = 1.3f, saturation = 1.25f, tint = Color.argb(10, 255, 255, 255))
  protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  protected val rect = RectF()
  private var lensNode: Any? = null

  init {
    setWillNotDraw(false)
    clipChildren = false
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    backdrop.attach()
  }

  override fun onDetachedFromWindow() {
    backdrop.detach()
    Choreographer.getInstance().removeFrameCallback(frame); looping = false
    super.onDetachedFromWindow()
  }

  /** The control's own track, drawn in local coordinates (also used inside the lens). */
  protected abstract fun drawTrack(canvas: Canvas)

  /** Glass lens centered on (cx, cy): refracts the page AND our track. */
  protected fun drawLens(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, zoom: Float) {
    var src: RenderNode? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // page (already in our local coordinates) + our own track, both refracted by the lens
      val page = backdrop.renderNode()
      val rn = (lensNode as? RenderNode) ?: RenderNode("liquid-glass-lens-src").also { it.setClipToBounds(false); lensNode = it }
      rn.setPosition(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
      val c = rn.beginRecording()
      try {
        if (page != null) c.drawRenderNode(page)
        drawTrack(c)
      } finally { rn.endRecording() }
      src = rn
    }
    renderer.draw(canvas, src, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, h / 2f, lensLook, zoom = zoom)
  }

  /** White resting knob with a soft shadow. */
  protected fun drawKnob(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb((a * 0.18f).toInt(), 0, 0, 0)
    rect.set(cx - w / 2f, cy - h / 2f + 1.5f * d, cx + w / 2f, cy + h / 2f + 1.5f * d)
    canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    paint.color = Color.argb(a, 255, 255, 255)
    rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
  }

  // ───────── animation loop ─────────
  /** Advance springs by dt; return true while anything still moves. */
  protected abstract fun stepSprings(dt: Float): Boolean

  private var looping = false
  private var lastFrame = 0L
  private val frame = object : Choreographer.FrameCallback {
    override fun doFrame(now: Long) {
      val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1e9f).coerceIn(0.001f, 0.032f)
      lastFrame = now
      val moving = stepSprings(dt)
      invalidate()
      if (moving) Choreographer.getInstance().postFrameCallback(this) else { looping = false; lastFrame = 0L }
    }
  }

  protected fun startLoop() {
    if (looping) return
    looping = true; lastFrame = 0L
    Choreographer.getInstance().postFrameCallback(frame)
  }

  protected fun blend(a: Int, b: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    return Color.argb(
      (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).toInt(),
      (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
      (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
      (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt(),
    )
  }
}
