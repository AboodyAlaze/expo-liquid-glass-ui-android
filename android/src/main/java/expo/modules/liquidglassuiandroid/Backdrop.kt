package expo.modules.liquidglassuiandroid

import android.annotation.TargetApi
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.reflect.Method
import java.util.WeakHashMap

/**
 * A glass view's view of "everything painted behind it", in ITS OWN local coordinates.
 *
 * One [BackdropManager] per window does all the capturing, right before frames Android draws
 * anyway. What makes it cheap:
 *  - REFERENCES, not copies: the views behind are drawn as references to their own display lists
 *    (exactly how Android draws them). When their content changes, the glass updates by itself —
 *    and only the part of the glass that overlaps the change is re-rendered.
 *  - NOTHING CHANGED → NOTHING DONE: every recorded layer has a signature (order, positions,
 *    scroll). If it's the same as last frame, the layer is left untouched, so the GPU doesn't
 *    redo the glass. An idle screen costs zero.
 *  - shared per ancestor: ten glass buttons in one screen share the same layers;
 *  - glass that's off screen is frozen (costs nothing on the GPU); hidden glass and switches /
 *    sliders at rest are skipped.
 */
class Backdrop(internal val view: View, internal val client: Client) {
  interface Client {
    /** Should the backdrop be kept up to date right now? */
    fun wantsBackdrop(): Boolean
    /** The first real backdrop is available (fade the glass in). */
    fun onBackdropReady()
  }

  private var node: Any? = null // RenderNode (API 29+), in the view's local coordinates
  var ready = false
    internal set
  internal var failedAt = 0L
  internal val inverse = Matrix()
  private var manager: Any? = null

  // what the local node was last recorded from (to skip identical re-recordings)
  internal var recBehind: Any? = null
  internal var recVersion = -1
  internal var recW = -1
  internal var recH = -1
  internal val recMatrix = FloatArray(9)
  internal val tmpMatrix = FloatArray(9)
  internal var frozen = false

  @TargetApi(Build.VERSION_CODES.Q)
  fun renderNode(): RenderNode? = if (ready) node as? RenderNode else null

  @TargetApi(Build.VERSION_CODES.Q)
  internal fun localNode(): RenderNode =
    (node as? RenderNode) ?: RenderNode("liquid-glass-backdrop").also { it.setClipToBounds(false); node = it }

  fun attach() {
    // Android 12+ only: older versions draw a simple translucent fill and need no capture
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val m = BackdropManager.of(view) ?: return
      m.add(this); manager = m
    }
  }

  fun detach() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) (manager as? BackdropManager)?.remove(this)
    manager = null
    ready = false // a re-attached view starts with a fresh capture (and fades in again)
    recBehind = null; recVersion = -1; frozen = false
  }
}


@TargetApi(Build.VERSION_CODES.Q)
internal class BackdropManager private constructor(private val root: View) : ViewTreeObserver.OnPreDrawListener {
  private val clients = ArrayList<Backdrop>()

  /** Per-view data reused across frames. */
  private class Entry {
    val behind = RenderNode("liquid-glass-behind").apply { setClipToBounds(false) }
    val window = Matrix()
    var matrixPass = -1
    var pass = -1          // pass in which `behind` was validated
    var version = 0        // bumps every time `behind` is actually re-recorded
    var signature = 0L
    var hasSignature = false
  }

  private val entries = WeakHashMap<View, Entry>()
  private var pass = 0
  private var running = false
  private var slow = 0
  private var skip = 0
  private val rect = RectF()
  private val values = FloatArray(9)
  private var hash = 0L

  private fun mix(x: Long) { hash = (hash xor x) * 1099511628211L } // FNV-1a style, no allocation

  fun add(b: Backdrop) {
    if (b !in clients) clients.add(b)
  }

  fun remove(b: Backdrop) {
    clients.remove(b)
    if (clients.isEmpty()) {
      root.viewTreeObserver.let { if (it.isAlive) it.removeOnPreDrawListener(this) }
      managers.remove(root)
    }
  }

  override fun onPreDraw(): Boolean {
    if (running || clients.isEmpty()) return true
    if (skip > 0) { skip--; return true }
    running = true
    pass++
    val t0 = System.nanoTime()
    try {
      // index loop: no allocation per frame (a client detaching mid-pass just ends the pass early)
      var i = 0
      while (i < clients.size) { captureFor(clients[i]); i++ }
    } finally {
      running = false
    }
    // a very slow device: refresh every other frame instead of every frame
    val ms = (System.nanoTime() - t0) / 1_000_000f
    if (ms > 8f) { if (++slow >= 3) { skip = 1; slow = 0 } } else slow = 0
    return true
  }

  private fun captureFor(b: Backdrop) {
    val v = b.view
    if (!v.isAttachedToWindow || !v.isShown || v.width == 0 || v.height == 0) return
    if (!b.client.wantsBackdrop()) return
    if (b.failedAt != 0L && SystemClock.uptimeMillis() - b.failedAt < 100) return
    try {
      val w = windowMatrix(v)
      rect.set(0f, 0f, v.width.toFloat(), v.height.toFloat())
      w.mapRect(rect)
      val m = 64f * v.resources.displayMetrics.density
      val offscreen = rect.right < -m || rect.bottom < -m || rect.left > root.width + m || rect.top > root.height + m
      val node = b.localNode()
      if (offscreen) {
        // Off screen: an EMPTY backdrop, so this glass stops being re-rendered by the GPU
        // while you scroll. It's rebuilt the moment it comes back.
        if (!b.frozen && b.ready) {
          node.setPosition(0, 0, v.width, v.height)
          node.beginRecording(); node.endRecording()
          b.frozen = true; b.recVersion = -1
        }
        return
      }

      val behind = behindOf(v) ?: return
      if (!w.invert(b.inverse)) return
      b.inverse.getValues(b.tmpMatrix)
      val same = !b.frozen && b.recBehind === behind && b.recVersion == behind.version &&
        b.recW == v.width && b.recH == v.height && b.tmpMatrix.contentEquals(b.recMatrix)
      if (!same) {
        node.setPosition(0, 0, v.width, v.height)
        val c = node.beginRecording()
        try {
          c.concat(b.inverse)          // window → this view's local coordinates
          c.drawRenderNode(behind.behind)
        } finally {
          node.endRecording()
        }
        b.recBehind = behind; b.recVersion = behind.version
        b.recW = v.width; b.recH = v.height
        System.arraycopy(b.tmpMatrix, 0, b.recMatrix, 0, 9)
        b.frozen = false
      }
      b.failedAt = 0L
      if (!b.ready) { b.ready = true; b.client.onBackdropReady() }
    } catch (t: Throwable) {
      b.failedAt = SystemClock.uptimeMillis()
      Log.w("LiquidGlassUI", "Backdrop capture paused for 100 ms after an error", t)
    }
  }

  /** Window transform of [v]'s local coordinates (layout + scroll + translation/scale/rotation). */
  private fun windowMatrix(v: View): Matrix {
    val e = entries.getOrPut(v) { Entry() }
    if (e.matrixPass == pass) return e.window
    val parent = v.parent as? View
    if (parent == null) e.window.reset() else e.window.set(windowMatrix(parent))
    if (parent != null) e.window.preTranslate(-parent.scrollX.toFloat(), -parent.scrollY.toFloat())
    e.window.preTranslate(v.left.toFloat(), v.top.toFloat())
    val own = v.matrix
    if (!own.isIdentity) e.window.preConcat(own)
    e.matrixPass = pass
    return e.window
  }

  /**
   * Everything painted behind [v], in window coordinates, shared per view:
   * behind(parent) + parent's background + the siblings drawn before [v].
   * (Glass nested in glass refracts what's behind the outer glass — re-processing the outer glass
   * for every nested element was the single most expensive thing on a scrolling page.)
   * Re-recorded ONLY when its signature changes.
   */
  private fun behindOf(v: View): Entry? {
    val parent = v.parent as? ViewGroup ?: return null
    val e = entries.getOrPut(v) { Entry() }
    if (e.pass == pass) return e
    val pe = behindOf(parent) // validated BEFORE we look at ours
    val pw = windowMatrix(parent)
    val index = parent.indexOfChild(v)

    val bg = parent.background

    // signature of everything this recording depends on (sibling positions / alpha / content are
    // NOT in it: they're references, the GPU picks those changes up by itself)
    hash = -3750763034362895579L
    mix(System.identityHashCode(pe).toLong()); mix((pe?.version ?: -1).toLong())
    pw.getValues(values)
    for (f in values) mix(f.toRawBits().toLong())
    mix(parent.scrollX.toLong()); mix(parent.scrollY.toLong())
    mix(parent.width.toLong()); mix(parent.height.toLong())
    // the parent's background is drawn as a copy: it's in the signature BY VALUE (its colour),
    // so a theme switch shows up — while an animation somewhere in the screen changes nothing
    mix(backgroundKey(bg))
    mix(index.toLong()); mix(root.width.toLong()); mix(root.height.toLong())
    for (i in 0 until index) {
      val s = parent.getChildAt(i)
      mix(System.identityHashCode(s).toLong()); mix(s.visibility.toLong())
    }
    val h = hash

    if (!e.hasSignature || h != e.signature) {
      val node = e.behind
      node.setPosition(0, 0, root.width.coerceAtLeast(1), root.height.coerceAtLeast(1))
      val c = node.beginRecording()
      try {
        if (pe != null) c.drawRenderNode(pe.behind)
        val base = c.save()
        c.concat(pw) // parent's local coordinates
        bg?.draw(c)
        c.translate(-parent.scrollX.toFloat(), -parent.scrollY.toFloat()) // parent's content coordinates
        val time = parent.drawingTime
        for (i in 0 until index) {
          val s = parent.getChildAt(i)
          if (s.visibility != View.VISIBLE) continue
          drawSibling(parent, c, s, time)
        }
        c.restoreToCount(base)
      } finally {
        node.endRecording()
      }
      e.version++
      e.signature = h; e.hasSignature = true
    }
    e.pass = pass
    return e
  }

  /**
   * What a background looks like, as a number: identity, alpha, bounds and — the part that changes
   * on a theme switch — its colour. React Native mutates the colour IN PLACE
   * (CompositeBackgroundDrawable.background.backgroundColor), so it's read from there.
   */
  private fun backgroundKey(d: Drawable?): Long {
    if (d == null) return 0L
    var k = System.identityHashCode(d).toLong()
    k = k * 31 + d.alpha
    val b = d.bounds
    k = k * 31 + b.left; k = k * 31 + b.top; k = k * 31 + b.right; k = k * 31 + b.bottom
    if (d is ColorDrawable) k = k * 31 + d.color
    val inner = call(d, "getBackground")             // RN: CompositeBackgroundDrawable.background
    val color = call(inner ?: d, "getBackgroundColor") // RN: BackgroundDrawable.backgroundColor
    if (color is Int) k = k * 31 + color
    return k
  }

  /** Calls a public no-arg getter if the object has it (looked up once per class, then cached). */
  private fun call(target: Any, name: String): Any? {
    val cache = if (name == "getBackground") backgroundGetters else colorGetters
    val cls = target.javaClass
    val m = cache[cls] ?: (try { cls.getMethod(name) } catch (t: Throwable) { NO_METHOD }).also { cache[cls] = it }
    if (m !is Method) return null
    return try { m.invoke(target) } catch (t: Throwable) { null }
  }

  /** Draws a sibling the way its parent does: a reference to its display list (+ its transform). */
  private fun drawSibling(parent: ViewGroup, c: Canvas, s: View, time: Long) {
    val m = drawChild
    if (m != null) {
      try {
        m.invoke(parent, c, s, time)
        return
      } catch (t: Throwable) {
        drawChild = null
        Log.w("LiquidGlassUI", "Falling back to copying views behind the glass", t)
      }
    }
    // fallback: copy the view's drawing (works everywhere, a bit more work)
    if (s.alpha <= 0f || s.width == 0 || s.height == 0) return
    val count = c.save()
    c.translate(s.left.toFloat(), s.top.toFloat())
    val sm = s.matrix
    if (!sm.isIdentity) c.concat(sm)
    c.translate(-s.scrollX.toFloat(), -s.scrollY.toFloat())
    if (s.alpha < 1f) {
      c.saveLayerAlpha(
        s.scrollX.toFloat(), s.scrollY.toFloat(),
        (s.scrollX + s.width).toFloat(), (s.scrollY + s.height).toFloat(),
        (s.alpha * 255).toInt(),
      )
    }
    s.draw(c)
    c.restoreToCount(count)
  }

  companion object {
    private val managers = WeakHashMap<View, BackdropManager>()
    private val backgroundGetters = HashMap<Class<*>, Any>()
    private val colorGetters = HashMap<Class<*>, Any>()
    private val NO_METHOD = Any()

    /** ViewGroup.drawChild (protected SDK API): draws a child as a reference to its display list. */
    private var drawChild: Method? = try {
      ViewGroup::class.java
        .getDeclaredMethod("drawChild", Canvas::class.java, View::class.java, java.lang.Long.TYPE)
        .apply { isAccessible = true }
    } catch (t: Throwable) {
      null
    }

    fun of(v: View): BackdropManager? {
      val root = v.rootView ?: return null
      return managers.getOrPut(root) {
        BackdropManager(root).also { root.viewTreeObserver.addOnPreDrawListener(it) }
      }
    }
  }
}
