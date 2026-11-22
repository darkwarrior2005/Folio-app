package app.folio.ui.screens.manga

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.folio.util.matchUserAgentClientHints

/**
 * "Open the site": lets the user pass a check (Cloudflare, a login, a captcha) once in a real
 * WebView, sharing cookies with Folio's own requests (the WebView's own CookieManager - the same
 * one AndroidCookieJar reads from), so the screen that sent them here can retry on return.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceWebViewScreen(
    url: String,
    userAgent: String,
    headers: Map<String, String>,
    sourceName: String,
    onDone: () -> Unit,
) {
    var title by remember { mutableStateOf(sourceName) }

    Scaffold(topBar = { app.folio.ui.components.BackTopBar(title, onDone) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        matchUserAgentClientHints(userAgent)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, finishedUrl: String) {
                                title = view.title?.takeIf { it.isNotBlank() } ?: sourceName
                            }
                        }
                        loadUrl(url, headers)
                    }
                },
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Done - retry")
            }
        }
    }
}
