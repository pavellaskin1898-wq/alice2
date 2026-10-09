package ai.moeru.airi_pocket.hyperos

import android.content.Context
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Capacitor plugin exposing Xiaomi HyperOS 2.0.209 specific tuning knobs to
 * the Vue web layer of AIRI Pocket (stage-pocket).
 *
 * JS usage:
 *   import { registerPlugin } from '@capacitor/core'
 *   const HyperOs = registerPlugin<HiperOsPlugin>('HyperOs')
 *   await HyperOs.getEnvironment()
 */
@CapacitorPlugin(name = "HyperOs")
class HyperOsPlugin : Plugin() {

    private lateinit var refreshRateHelper: HyperOsRefreshRateHelper

    override fun load() {
        super.load()
        refreshRateHelper = HyperOsRefreshRateHelper(context)
    }

    /** Firmware / ROM identification for diagnostics and conditional UI. */
    @PluginMethod
    fun getEnvironment(call: PluginCall) {
        val result = JSObject().apply {
            put("isXiaomiDevice", HyperOsDetector.isXiaomiDevice())
            put("isMiuiOrHyperOs", HyperOsDetector.isMiuiOrHyperOs())
            put("isHyperOs", HyperOsDetector.isHyperOs())
            put("isHyperOs2", HyperOsDetector.isHyperOs2())
            put("hyperOsVersion", HyperOsDetector.getHyperOsVersion())
            put("isHyperOsTargetBuild", HyperOsDetector.isHyperOsTargetBuild())
            put("firmware", HyperOsDetector.describeFirmware())
            put("androidSdkInt", android.os.Build.VERSION.SDK_INT)
        }
        call.resolve(result)
    }

    /** Whether the app is already whitelisted from HyperOS battery freezing. */
    @PluginMethod
    fun isIgnoringBatteryOptimizations(call: PluginCall) {
        val result = JSObject().apply {
            put("ignoring", HyperOsBatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext()))
            put("isHyperOs", HyperOsDetector.isHyperOs())
        }
        call.resolve(result)
    }

    /**
     * Full HyperOS survival flow:
     *  1. standard Doze-exemption dialog,
     *  2. then HyperOS autostart manager,
     *  3. then per-app "No restrictions" battery page.
     * Steps that are unavailable on non-Xiaomi ROMs are skipped gracefully.
     */
    @PluginMethod
    fun requestHyperOsSurvivalSettings(call: PluginCall) {
        val ctx: Context = requireContext()
        val steps = JSObject()
        steps.put("batteryExemptionDialog", HyperOsBatteryOptimizationHelper.requestIgnoreBatteryOptimizations(ctx))
        steps.put("autostartSettings", HyperOsBatteryOptimizationHelper.openHyperOsAutostartSettings(ctx))
        steps.put("batteryDetails", HyperOsBatteryOptimizationHelper.openHyperOsBatteryDetails(ctx))
        steps.put("isHyperOs", HyperOsDetector.isHyperOs())
        call.resolve(steps)
    }

    /** Diagnostics about refresh-rate throttling on HyperOS 2. */
    @PluginMethod
    fun getRefreshRateDiagnostics(call: PluginCall) {
        val map = refreshRateHelper.diagnostics()
        val result = JSObject()
        for ((k, v) in map) {
            when (v) {
                is Boolean -> result.put(k, v)
                is String -> result.put(k, v)
                null -> result.put(k, JSObject.NULL)
                else -> result.put(k, v.toString())
            }
        }
        call.resolve(result)
    }

    /** Opens HyperOS display settings so the user can force max Hz. */
    @PluginMethod
    fun openRefreshRateSettings(call: PluginCall) {
        val result = JSObject().apply {
            put("opened", refreshRateHelper.openRefreshRateSettings())
        }
        call.resolve(result)
    }

    private fun requireContext(): Context = bridge.activity.applicationContext
}
