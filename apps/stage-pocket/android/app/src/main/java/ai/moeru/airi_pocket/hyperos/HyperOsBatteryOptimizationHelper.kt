package ai.moeru.airi_pocket.hyperos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Battery-optimization helpers tuned for Xiaomi HyperOS 2.0.209.
 *
 * Problem: HyperOS aggressively freezes background apps (the "battery saver /
 * autostart" subsystem inherited from MIUI). AIRI Pocket keeps a native
 * WebSocket bridge (OkHttp) alive to the stage server; when HyperOS puts the
 * app into cached/freezer state, sockets get force-closed and the character
 * goes silent until the user returns to the foreground.
 *
 * Mitigations implemented here:
 *  1. Standard Android: request ignoring battery optimizations (Doze whitelist).
 *  2. HyperOS-specific: deep-link into the MIUI/HyperOS autostart manager
 *     so the user can allow background activity.
 *  3. HyperOS-specific: deep-link into per-app battery settings where the user
 *     should pick "No restrictions".
 */
object HyperOsBatteryOptimizationHelper {
    private const val TAG = "HyperOsBatteryOpt"

    /** Whether the OS already exempts this app from battery optimizations. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the standard "ignore battery optimizations" dialog.
     * On HyperOS 2 this dialog exists but is only half of the story — also call
     * [openHyperOsAutostartSettings] and [openHyperOsBatteryDetails].
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean = try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Standard battery-exemption screen unavailable, falling back to list", e)
        openBatteryOptimizationList(context)
    }

    private fun openBatteryOptimizationList(context: Context): Boolean = try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Battery optimization settings list unavailable", e)
        false
    }

    /**
     * Opens HyperOS/MIUI autostart management for our package.
     * Activity names changed across MIUI versions; we try every known variant
     * so the same build works on MIUI 12.5+, HyperOS 1 and HyperOS 2.0.209.
     */
    fun openHyperOsAutostartSettings(context: Context): Boolean {
        val candidates = listOf(
            // HyperOS 2 / newer MIUI
            Component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Legacy MIUI variants
            Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
            Component("com.miui.securitycenter", "com.miui.appmanager.ApplicationsDetailsActivity"),
        )
        for (c in candidates) {
            if (launch(
                    context,
                    c.component,
                    extras = mapOf(
                        "package_name" to context.packageName,
                        "packagename" to context.packageName,
                    ),
                )
            ) {
                return true
            }
        }
        Log.i(TAG, "No HyperOS autostart activity found; device may be non-Xiaomi or ROM repacked")
        return false
    }

    /**
     * Opens the per-app power-usage detail page on HyperOS, where the user must
     * select "No restrictions" so Live2D rendering + TTS audio survive idle.
     */
    fun openHyperOsBatteryDetails(context: Context): Boolean = launch(
        context,
        Component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"),
        extras = mapOf("package_name" to context.packageName),
    ) || launch(
        context,
        Component("android", "com.android.settings.Settings\$PowerUsageDetailActivity"),
        extras = mapOf(":settings:fragment_args_key" to context.packageName),
    )

    private data class Component(val pkg: String, val cls: String)

    private fun launch(
        context: Context,
        component: Component,
        extras: Map<String, String> = emptyMap(),
    ): Boolean = try {
        val intent = Intent().apply {
            setClassName(component.pkg, component.cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras.forEach { (k, v) -> putExtra(k, v) }
        }
        // Verify the activity exists before launching (HyperOS strips some of these).
        val resolved = context.packageManager.resolveActivity(intent, 0)
        if (resolved == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (e: Exception) {
        false
    }
}
