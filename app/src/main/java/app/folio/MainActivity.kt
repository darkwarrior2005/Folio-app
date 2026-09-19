package app.folio

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.folio.data.settings.AppSettings
import app.folio.importer.ImportEvent
import app.folio.pomodoro.PomodoroNotifications
import app.folio.reader.epub.ReaderFragmentFactory
import app.folio.ui.FolioRoot
import app.folio.ui.LocalContainer
import app.folio.ui.theme.FolioAppTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val container: AppContainer by lazy { (application as FolioApp).container }

    private val openBookRequest = MutableStateFlow<Long?>(null)
    private val locked = MutableStateFlow(false)

    /** Set when the user opened a file with Folio, so that import goes straight into the reader. */
    private var awaitingOpen = false
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium's navigator fragment needs its factory in place before any fragment is restored.
        supportFragmentManager.fragmentFactory = ReaderFragmentFactory
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PomodoroNotifications.ensureChannels(this)

        lifecycleScope.launch {
            val settings = container.settings.settings.first()
            locked.value = settings.privacy.lockEnabled
            applySecureFlag(settings.privacy.hideInRecents)
        }

        lifecycleScope.launch {
            container.imports.events.collect { event ->
                if (event is ImportEvent.Imported && awaitingOpen) {
                    awaitingOpen = false
                    openBookRequest.value = event.bookId
                }
            }
        }

        handleIntent(intent)

        setContent {
            val settings by container.settings.settings.collectAsState(initial = AppSettings())
            val isLocked by locked.collectAsState()
            val openBook by openBookRequest.collectAsState()

            androidx.compose.runtime.LaunchedEffect(settings.privacy.hideInRecents) {
                applySecureFlag(settings.privacy.hideInRecents)
            }

            CompositionLocalProvider(LocalContainer provides container) {
                FolioAppTheme(settings) {
                    Surface(Modifier.fillMaxSize()) {
                        FolioRoot(
                            settings = settings,
                            locked = isLocked && settings.privacy.lockEnabled,
                            onUnlocked = { locked.value = false },
                            openBookId = openBook,
                            onOpenHandled = { openBookRequest.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val privacy = container.settings.settings.first().privacy
            if (!privacy.lockEnabled) {
                locked.value = false
                return@launch
            }
            if (backgroundedAt > 0) {
                val away = System.currentTimeMillis() - backgroundedAt
                if (away >= privacy.lockTimeoutSeconds * 1000L) locked.value = true
            }
        }
    }

    private fun applySecureFlag(hide: Boolean) {
        if (hide) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Files arriving from "Open with" or a share are copied in, since the grant is temporary. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                awaitingOpen = true
                container.imports.enqueue(listOf(uri.toString()), forceCopy = true)
            }

            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    awaitingOpen = true
                    container.imports.enqueue(listOf(it.toString()), forceCopy = true)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    container.imports.enqueue(uris.map { it.toString() }, forceCopy = true)
                }
            }
        }
    }
}
