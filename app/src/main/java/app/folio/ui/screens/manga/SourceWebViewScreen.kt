package app.folio.ui.screens.manga

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import app.folio.AppContainer
import app.folio.R
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.setUserAgentWithClientHints
import java.util.Locale

/** What the WebView needs to look, to the site, like the source's own requests. */
class SourceWebViewViewModel(container: AppContainer, sourceId: Long, url: String?) : ViewModel() {

    private val source = container.sources.get(sourceId) as? HttpSource

    val sourceName: String = source?.name.orEmpty()

    val startUrl: String? = url?.takeIf { it.isNotBlank() } ?: source?.baseUrl

    /**
     * Cloudflare ties its pass to the browser that earned it, so the WebView must present the
     * same user agent the source's requests do, or the cookie it earns is refused.
     */
    val userAgent: String = source?.headers?.get("User-Agent")
        ?: container.sourceNetwork.defaultUserAgentProvider()

    val headers: Map<String, String> = source?.headers
        ?.filter { (name, _) -> name.lowercase(Locale.ENGLISH) !in UNSAFE_HEADERS }
        ?.associate { (name, value) -> name to value }
        .orEmpty()

    private companion object {
        // Chromium refuses to send these, failing the whole load.
        val UNSAFE_HEADERS = setOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive",
            "transfer-encoding", "set-cookie", "user-agent",
        )
    }
}

/**
 * A source's own website, for when Cloudflare (or a login) needs a person: the cookie the site
 * sets here is the same one Folio's requests send, so passing the check once lets the source work.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceWebViewScreen(
    sourceId: Long,
    url: String?,
    onBack: () -> Unit,
    viewModel: SourceWebViewViewModel = folioViewModel(key = "webview-$sourceId-$url") {
        SourceWebViewViewModel(it, sourceId, url)
    },
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf(viewModel.sourceName) }
    var progress by remember { mutableStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var passedCheck by remember { mutableStateOf(false) }
    // A pass left over from an earlier visit says nothing about this one.
    val clearanceAtStart = remember { viewModel.startUrl?.let(::clearanceCookie) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    DisposableEffect(Unit) {
        // Make sure a cookie the user just earned is on disk before the source next asks for it.
        onDispose { CookieManager.getInstance().flush() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { stringResource(R.string.manga_open_site) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }, enabled = webView != null) {
                        Icon(FolioIcons.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        val startUrl = viewModel.startUrl
        if (startUrl == null) {
            EmptyState(
                title = stringResource(R.string.manga_source_error_title),
                message = stringResource(R.string.manga_open_site_unavailable),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (passedCheck) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.manga_open_site_passed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onBack) { Text(stringResource(R.string.manga_open_site_done)) }
                    }
                }
            } else {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.manga_open_site_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            if (progress in 0f..0.99f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // The same user agent and client hints the source's requests carry, so
                        // the pass the user earns here is accepted when Folio presents it.
                        setUserAgentWithClientHints(viewModel.userAgent)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        // Nothing typed into a site is offered to autofill services to save.
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

                        webViewClient = object : WebViewClient() {
                            // Links stay in this WebView, where the cookies are shared with Folio.
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                progress = 0f
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 1f
                                title = view.title?.takeIf { it.isNotBlank() } ?: title
                                canGoBack = view.canGoBack()
                                val clearance = url?.let(::clearanceCookie)
                                if (clearance != null && clearance != clearanceAtStart) {
                                    CookieManager.getInstance().flush()
                                    passedCheck = true
                                }
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        loadUrl(startUrl, viewModel.headers)
                        webView = this
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    // Leave no trail of the visit: cached pages, history, form entries and site
                    // storage go. Cookies stay, since they carry the pass the user just earned.
                    view.clearCache(true)
                    view.clearHistory()
                    view.clearFormData()
                    WebStorage.getInstance().deleteAllData()
                    view.destroy()
                    webView = null
                },
            )
        }
    }
}

/** The site's Cloudflare pass, if the WebView holds one for [url]. */
private fun clearanceCookie(url: String): String? = runCatching {
    CookieManager.getInstance().getCookie(url)
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("cf_clearance=") }
}.getOrNull()
