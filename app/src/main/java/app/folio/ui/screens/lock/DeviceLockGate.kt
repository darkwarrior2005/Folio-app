package app.folio.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.folio.R
import app.folio.ui.icons.FolioIcons
import app.folio.ui.screens.reader.findActivity

/** A fingerprint or face, or the phone's own PIN, pattern or password. */
private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Asks the phone to confirm it is its owner: fingerprint, face, or the screen lock. Returns a
 * function that shows the prompt and calls `onSuccess` once confirmed, or straight away when the
 * phone has no screen lock to check against.
 */
@Composable
fun rememberDeviceAuth(title: String, onError: (String) -> Unit = {}): (onSuccess: () -> Unit) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    val currentOnError by rememberUpdatedState(onError)
    return remember(activity, title) {
        { onSuccess ->
            val host = activity
            if (host == null || !isDeviceSecured(host)) {
                onSuccess()
            } else {
                BiometricPrompt(
                    host,
                    ContextCompat.getMainExecutor(host),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            val dismissed = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                                errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_CANCELED
                            if (!dismissed) currentOnError(errString.toString())
                        }
                    },
                ).authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setAllowedAuthenticators(AUTHENTICATORS)
                        .build(),
                )
            }
        }
    }
}

/** Whether the phone has a screen lock or enrolled biometric to check against. */
private fun isDeviceSecured(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows [content] only after the phone's owner is confirmed, when [required]. Unlocking lasts
 * until the app leaves the screen (goes to the background), so moving around inside the app does
 * not ask again. [required] null means the setting has not loaded yet, and nothing is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceLockGate(
    required: Boolean?,
    title: String,
    message: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (required == null) return
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    if (activity != null) {
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = activity) {
            unlocked = false
        }
    }

    val authenticate = rememberDeviceAuth(title, onError = { error = it })
    val unlock = {
        error = null
        authenticate { unlocked = true }
    }

    if (!required || unlocked) {
        content()
        return
    }

    LaunchedEffect(Unit) { unlock() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(FolioIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = unlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
