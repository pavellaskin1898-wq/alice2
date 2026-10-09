package ai.moeru.airi_pocket.hyperos

import android.os.Build
import android.util.Log

/**
 * Detects Xiaomi HyperOS (and legacy MIUI) at runtime.
 *
 * Targeted firmware: Xiaomi HyperOS 2.0.209 (Android 15 / SDK 35, API level 35).
 *
 * HyperOS exposes itself through several system properties:
 *  - ro.miui.ui.version.name       -> "V140" / "V141" on HyperOS 1.x, "V160"+ on HyperOS 2.x
 *  - ro.product.marketname         -> marketing name, e.g. "Xiaomi 14 Ultra"
 *
 * All reads are defensive: unknown props simply return null and never crash
 * (HyperOS's restricted SELinux policy may deny some property reads).
 */
object HyperOsDetector {
    private const val TAG = "HyperOsDetector"

    /** Manufacturer check: Xiaomi / Redmi / POCO devices. */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("xiaomi")
            || manufacturer.contains("redmi")
            || manufacturer.contains("poco")
            || manufacturer.contains("hyperos")
    }

    /** True when running on MIUI or HyperOS (both share the miui property namespace). */
    fun isMiuiOrHyperOs(): Boolean {
        if (!isXiaomiDevice()) return false
        return getProp("ro.miui.ui.version.name") != null
    }

    /**
     * True when running specifically on Xiaomi HyperOS.
     * HyperOS reports a miui UI version of V140 or higher (MIUI 14 was V130).
     */
    fun isHyperOs(): Boolean {
        if (!isMiuiOrHyperOs()) return false
        val uiVersion = getProp("ro.miui.ui.version.name") ?: return false
        val number = uiVersion.removePrefix("V").toIntOrNull() ?: return false
        // HyperOS 1.x == V140..V159, HyperOS 2.x == V160+ (target here: 2.0.209 => V160 family)
        return number >= 140
    }

    /**
     * True when running on HyperOS 2.x, i.e. the 2.0.209 target firmware family.
     */
    fun isHyperOs2(): Boolean {
        if (!isHyperOs()) return false
        val uiVersion = getProp("ro.miui.ui.version.name") ?: return false
        val number = uiVersion.removePrefix("V").toIntOrNull() ?: return false
        return number >= 160
    }

    /**
     * Exact ROM build version, e.g. "OS2.0.209.0.WCCCNXM" (HyperOS 2.0.209).
     *
     * HyperOS exposes it through `ro.mi.os.version.incremental`; MIUI-era
     * devices only have `ro.build.version.incremental`, so we fall back to it.
     */
    fun getHyperOsVersion(): String =
        getProp("ro.mi.os.version.incremental")
            ?: getProp("ro.build.version.incremental")
            ?: "N/A"

    /**
     * True when the device runs the exact targeted firmware build 2.0.209
     * (any regional codename suffix such as WCCCNXM / VNUCNXM is accepted).
     */
    fun isHyperOsTargetBuild(): Boolean = versionMatches("2.0.209")

    private fun versionMatches(target: String): Boolean {
        val version = getHyperOsVersion()
        if (version == "N/A") return false
        // Normalize forms like "OS2.0.209.0.WCCCNXM", "V816.2.0.209.0", "HyperOS-2.0.209".
        val normalized = version.lowercase().replace("hyperos", "").replace("os", "")
        // Require the target to appear as a complete dotted segment, not part of a longer number.
        val pattern = Regex("""(?<!\d)${Regex.escape(target)}(?!\d)""")
        return pattern.containsMatchIn(normalized)
    }

    /** Human-readable firmware description for logs/diagnostics pages. */
    fun describeFirmware(): String {
        val marketName = getProp("ro.product.marketname") ?: Build.MODEL
        val osVersion = getProp("ro.miui.ui.version.name") ?: "N/A"
        val hyperOsVersion = getHyperOsVersion()
        val androidRelease = Build.VERSION.RELEASE
        return "$marketName | HyperOS $hyperOsVersion (UI $osVersion) | Android $androidRelease (API ${Build.VERSION.SDK_INT})"
    }

    private fun getProp(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        (method.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        Log.d(TAG, "Property read denied on this ROM: $key (${e.message})")
        null
    }
}
