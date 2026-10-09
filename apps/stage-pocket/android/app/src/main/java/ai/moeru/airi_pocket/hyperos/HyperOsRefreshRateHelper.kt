package ai.moeru.airi_pocket.hyperos

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Adaptive refresh-rate helpers for Xiaomi HyperOS 2.0.209.
 *
 * Background: HyperOS 2 ships an aggressive "smart refresh rate" scheduler.
 * For apps it does not recognize as animation-heavy (Capacitor WebView shells
 * in particular), it pins SurfaceFlinger to 60 Hz even on 120/144 Hz panels,
 * which makes Live2D / MMD rendering visibly stutter on AIRI Pocket.
 *
 * On Android 13+ (HyperOS 2 is Android 15 based) the supported way to opt in
 * is per-app frame-rate selection via Settings.ACTION_DISPLAY_SETTINGS and
 * the preferred-display-refresh-rate hint. We cannot set a system-wide value
 * without privileges, so this plugin deep-links the user to the exact HyperOS
 * screen ("Экрaн → Частота обновления → Выбрать вручную") and exposes the
 * current mode to the web layer for diagnostics.
 */
class HyperOsRefreshRateHelper(private val context: Context) {
    companion object {
        private const val TAG = "HyperOsRefreshRate"

        /** Reads the currently active refresh rate reported by the ROM, if available. */
        fun readReportedRefreshRate(): String? = try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            // HyperOS keeps the user's chosen mode here: "1" = standard, "2" = high, custom values vary
            (method.invoke(null, "persist.debug.miui.fps") as? String)?.takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            Log.d(TAG, "Refresh-rate property unavailable: ${e.message}")
            null
        }
    }

    /** Opens HyperOS display refresh-rate settings. */
    fun openRefreshRateSettings(): Boolean = try {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Display settings unavailable", e)
        false
    }

    /**
     * Diagnostics payload consumed by the web layer through the host bridge.
     */
    fun diagnostics(): Map<String, Any?> = mapOf(
        "isHyperOs" to HyperOsDetector.isHyperOs(),
        "isHyperOs2" to HyperOsDetector.isHyperOs2(),
        "hyperOsVersion" to HyperOsDetector.getHyperOsVersion(),
        "firmware" to HyperOsDetector.describeFirmware(),
        "reportedRefreshMode" to readReportedRefreshRate(),
        "recommendedSetting" to "Установите частоту обновления вручную на максимум (120/144 Гц) в настройках экрана HyperOS",
    )
}
