package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bridge.AndroidAppActionBridge

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBridgeScreen(
    actionBridge: AndroidAppActionBridge,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("webview_bridge_container"),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                // Inject the bridge with both standard names
                addJavascriptInterface(actionBridge, "AndroidAppActionBridge")
                addJavascriptInterface(actionBridge, "Android")

                // Load the bundled test console and bridge harness
                loadUrl("file:///android_asset/jarves_web_bridge.html")
            }
        }
    )
}
