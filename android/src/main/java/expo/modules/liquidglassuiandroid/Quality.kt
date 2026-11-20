package expo.modules.liquidglassuiandroid

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * How much GPU the glass is allowed to use.
 *
 * HIGH   → the full effect: refraction, chromatic rim, specular rim, glow.
 * MEDIUM → the same glass without the costly extras (no chromatic split = 1 texture sample
 *          instead of 3 at the rim, no blur) and big surfaces (cards) drawn as LOW.
 * LOW    → NO shader, NO backdrop capture, NO offscreen layer: a drawn frosted-glass look
 *          (translucent body + soft sheen + 1dp rim) that costs almost nothing per frame.
 *          Press / stretch / jelly animations stay exactly the same.
 */
enum class Quality {
  HIGH, MEDIUM, LOW;

  companion object {
    fun resolve(context: Context, requested: String?): Quality = when (requested?.lowercase()) {
      "high" -> HIGH
      "medium" -> MEDIUM
      "low" -> LOW
      else -> auto(context)
    }

    @Volatile private var base: Quality? = null

    private fun auto(context: Context): Quality {
      val tier = base ?: detect(context).also { base = it }
      // Battery saver → one step down (never cached: it can change while the app runs)
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      return if (pm?.isPowerSaveMode == true) {
        if (tier == HIGH) MEDIUM else LOW
      } else tier
    }

    /**
     * Conservative on purpose: a device only gets the heavy glass when there is evidence it can
     * afford it. (Core count says nothing about the GPU — cheap phones have 8 slow cores.)
     */
    private fun detect(context: Context): Quality {
      // No AGSL before Android 13 (Android 12's frosted blur is itself expensive on old GPUs)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return LOW

      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      if (am.isLowRamDevice) return LOW

      val info = ActivityManager.MemoryInfo()
      am.getMemoryInfo(info)
      val ram = info.totalMem // an "8 GB" phone reports ~7.3 GB, a "6 GB" one ~5.5 GB

      return when {
        // The OS itself certifies the device as a performance-class device
        Build.VERSION.MEDIA_PERFORMANCE_CLASS >= Build.VERSION_CODES.S -> HIGH
        ram >= 11_000_000_000L -> HIGH
        ram >= 6_500_000_000L && Runtime.getRuntime().availableProcessors() >= 8 -> MEDIUM
        else -> LOW
      }
    }
  }
}
