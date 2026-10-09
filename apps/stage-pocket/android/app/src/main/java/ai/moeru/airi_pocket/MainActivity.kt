package ai.moeru.airi_pocket

import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.webkit.WebView
import com.getcapacitor.Bridge
import com.getcapacitor.BridgeActivity
import com.getcapacitor.BridgeWebViewClient
import com.getcapacitor.Logger
import org.json.JSONObject

import ai.moeru.airi_pocket.hyperos.HyperOsBatteryOptimizationHelper
import ai.moeru.airi_pocket.hyperos.HyperOsDetector
import ai.moeru.airi_pocket.hyperos.HyperOsPlugin
import ai.moeru.airi_pocket.websocket.HostWebSocketBridge
import ai.moeru.airi_pocket.websocket.HostWebSocketEvent
import ai.moeru.airi_pocket.websocket.HostWebSocketSession
import ai.moeru.airi_pocket.websocket.OkHttpHostWebSocketSessionFactory
import ai.moeru.airi_pocket.websocket.WebSocketBridgeJavascriptInterface
import ai.moeru.airi_pocket.websocket.createHostWebSocketClient

class MainActivity : BridgeActivity() {
    companion object {
        internal var webSocketSessionFactoryOverrideForTesting: ((String, (HostWebSocketEvent) -> Unit) -> HostWebSocketSession)? = null
    }

    private val webSocketBridgeClient = createHostWebSocketClient()
    private var webSocketBridge: HostWebSocketBridge? = null

    override fun load() {
        registerPlugin(MicrophonePermissionPlugin::class.java)
        registerPlugin(WebAuthenticationPlugin::class.java)
        // Xiaomi HyperOS (target firmware: HyperOS 2.0.209) integration plugin
        registerPlugin(HyperOsPlugin::class.java)
        super.load()

        val bridge = bridge ?: return
        bridge.webView.setBackgroundColor(Color.TRANSPARENT)
        installWebSocketBridge(bridge)
        applyHyperOsWorkarounds()

        if (!bridge.isDevMode) {
            return
        }

        bridge.setWebViewClient(DebugTlsBypassWebViewClient(bridge))
    }

    override fun onDestroy() {
        webSocketBridge?.dispose()
        super.onDestroy()
    }

    internal fun evaluateJavascriptForTesting(script: String, callback: (String?) -> Unit) {
        bridge?.webView?.let { webView ->
            webView.post {
                webView.evaluateJavascript(script, callback)
            }
        } ?: callback(null)
    }

    private fun installWebSocketBridge(bridge: Bridge) {
        val hostWebSocketBridge = HostWebSocketBridge(
            sessionFactory = webSocketSessionFactoryOverrideForTesting
                ?: OkHttpHostWebSocketSessionFactory(webSocketBridgeClient)::create,
            eventSink = ::dispatchWebSocketBridgeEvent,
        )
        webSocketBridge = hostWebSocketBridge

        bridge.webView.addJavascriptInterface(
            WebSocketBridgeJavascriptInterface(hostWebSocketBridge),
            "AiriHostBridge",
        )
    }

    private fun dispatchWebSocketBridgeEvent(payload: String) {
        val webView = bridge?.webView ?: return
        webView.post {
            webView.evaluateJavascript(
                "window.__airiHostBridge?.onNativeMessage(${JSONObject.quote(payload)})",
                null,
            )
        }
    }

    /**
     * HyperOS 2.0.209 specific WebView tuning:
     *  - disable implicit dark mode: HyperOS applies a forced dark theme over
     *    WebView content on Xiaomi devices, which corrupts Live2D/MMD shaders
     *    and the app's own color scheme;
     *  - keep hardware acceleration explicit (HyperOS powerkeeper downgrades
     *    rendering for "light" apps otherwise);
     *  - log the detected firmware for field diagnostics.
     */
    private fun applyHyperOsWorkarounds() {
        if (!HyperOsDetector.isHyperOs()) {
            return
        }

        Logger.info("Running on ${HyperOsDetector.describeFirmware()}, applying HyperOS workarounds")

        val webView = bridge?.webView ?: return
        // Force-disable HyperOS implicit-dark injection into our WebView.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            webView.settings.forceDark = WebSettings.FORCE_DARK_OFF
        }

        // Nudge HyperOS to keep this process warm while it is in the foreground.
        if (!HyperOsBatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            Logger.info("AIRI is not whitelisted from HyperOS battery optimizations yet; web layer will prompt via HyperOs plugin")
        }
    }

    private class DebugTlsBypassWebViewClient(
        private val bridge: Bridge,
    ) : BridgeWebViewClient(bridge) {

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            if (shouldBypassDevServerCertificate(error)) {
                Logger.warn("Bypassing TLS certificate validation for debug dev server: ${error.url}")
                handler.proceed()
                return
            }

            super.onReceivedSslError(view, handler, error)
        }

        // NOTICE: Android WebView rejects the self-signed HTTPS cert used by the debug dev server.
        // Keep this bypass debug-only and scoped to the configured dev server origin.
        private fun shouldBypassDevServerCertificate(error: SslError?): Boolean {
            val serverUrl = bridge.serverUrl?.takeUnless(String::isEmpty) ?: return false
            val errorUrl = error?.url?.takeUnless(String::isEmpty) ?: return false

            val serverUri = Uri.parse(serverUrl)
            if (!serverUri.scheme.equals("https", ignoreCase = true)) {
                return false
            }

            val errorUri = Uri.parse(errorUrl)
            return serverUri.host.equals(errorUri.host, ignoreCase = true)
                && normalizePort(serverUri) == normalizePort(errorUri)
        }

        private fun normalizePort(uri: Uri): Int =
            uri.port.takeUnless { it == -1 }
                ?: when (uri.scheme?.lowercase()) {
                    "https" -> 443
                    "http" -> 80
                    else -> -1
                }
    }
}
