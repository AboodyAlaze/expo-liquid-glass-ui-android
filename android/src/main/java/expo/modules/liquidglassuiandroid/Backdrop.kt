package expo.modules.liquidglassuiandroid

import android.graphics.RenderNode
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

/**
 * Records "everything painted behind [target]" into a RenderNode, in WINDOW coordinates.
 *
 * Walks from the window root down to the target. At each level it draws the parent's
 * background and every sibling that comes before the next view in the chain — i.e. exactly
 * what the system has painted under the target. Ancestors themselves are never drawn (only
 * their backgrounds) and the target's own subtree is never drawn, so it can't loop.
 *
 * It's cheap: child.draw() on a hardware canvas records references to each view's existing
 * display list instead of re-drawing pixels.
 *
 * Safety (lessons from the Tabs package):
 *  - ready from the very first frame (no Compose here, so no attach-timing issues)
 *  - no re-entrancy, nothing while hidden or not laid out
 *  - an exception pauses capture for this view for a second (then it retries) instead of breaking the app
 *  - on a slow frame it backs off to every 3rd frame
 */
class Backdrop {
  private var node: Any? = null // RenderNode (API 29+)
  private var failed = false
  private var failedAt = 0L

  /** True once a backdrop was recorded successfully at least once. */
  var ready = false
    private set
  private var attachedAt = 0L
  private var slowFrames = 0
  private var skip = 0
  private val loc = IntArray(2)

  /** True while ANY backdrop is being recorded: glass views draw a cheap stand-in meanwhile. */
  companion object {
    @JvmStatic var capturing = false
      private set
  }

  fun onAttached() { attachedAt = SystemClock.uptimeMillis() }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun renderNode(): RenderNode? = node as? RenderNode

  /** Call from an OnPreDrawListener. Returns true when a fresh backdrop was recorded. */
  fun capture(target: View): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    if (failed) {
      if (SystemClock.uptimeMillis() - failedAt < 100) return false
      failed = false
    }
    if (capturing || !target.isShown || target.width == 0 || target.height == 0) return false
    if (skip > 0) { skip--; return false }
    capturing = true
    val t0 = System.nanoTime()
    try {
      record(target)
      ready = true
    } catch (t: Throwable) {
      failed = true; failedAt = SystemClock.uptimeMillis()
      Log.w("LiquidGlassUI", "Backdrop capture paused after an error", t)
    } finally {
      capturing = false
    }
    val ms = (System.nanoTime() - t0) / 1_000_000f
    if (ms > 4f) { if (++slowFrames >= 3) skip = 2 } else slowFrames = 0
    return !failed
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun record(target: View) {
    val root = target.rootView ?: return
    if (root.width == 0 || root.height == 0) return
    val rn = (node as? RenderNode) ?: RenderNode("liquid-glass-backdrop").also { node = it }
    rn.setPosition(0, 0, root.width, root.height)

    // chain: root … parent, target
    val chain = ArrayList<View>(16)
    var v: View = target
    chain.add(v)
    while (true) {
      val p = v.parent as? ViewGroup ?: break
      chain.add(p); v = p
    }
    chain.reverse()

    val c = rn.beginRecording()
    try {
      for (i in 0 until chain.size - 1) {
        val group = chain[i] as ViewGroup
        val next = chain[i + 1]
        group.background?.let { bg ->
          group.getLocationInWindow(loc)
          c.save(); c.translate(loc[0].toFloat(), loc[1].toFloat()); bg.draw(c); c.restore()
        }
        val index = group.indexOfChild(next)
        for (k in 0 until index) {
          val child = group.getChildAt(k)
          if (child.visibility != View.VISIBLE || child.alpha == 0f || child.width == 0 || child.height == 0) continue
          child.getLocationInWindow(loc)
          val count = c.save()
          c.translate(loc[0].toFloat(), loc[1].toFloat())
          if (child.alpha < 1f) c.saveLayerAlpha(0f, 0f, child.width.toFloat(), child.height.toFloat(), (child.alpha * 255).toInt())
          child.draw(c)
          c.restoreToCount(count)
        }
      }
    } finally {
      rn.endRecording()
    }
  }
}
