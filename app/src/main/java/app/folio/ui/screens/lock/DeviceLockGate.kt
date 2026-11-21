package app.folio.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine

private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** True once this session has unlocked; reset whenever the app goes to the background. */
@Composable
fun rememberDeviceAuth(): DeviceAuthState {
    val context = LocalContext.current
    val canAuthenticate = remember {
        BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
    }
    return remember { DeviceAuthState(canAuthenticate) }
}

class DeviceAuthState(val deviceHasScreenLock: Boolean) {
    var unlocked by mutableStateOf(!deviceHasScreenLock)

    fun lock() {
        if (deviceHasScreenLock) unlocked = false
    }
}

/** Wraps [content] in a biometric/screen-lock prompt. Phones with no screen lock are let in. */
@Composable
fun DeviceLockGate(state: DeviceAuthState, onCancelled: () -> Unit = {}, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    if (!state.unlocked && activity != null) {
        LaunchedEffect(Unit) {
            val prompt = suspendPrompt(activity)
            if (prompt) state.unlocked = true else onCancelled()
        }
    } else {
        content()
    }
}

private suspend fun suspendPrompt(activity: FragmentActivity): Boolean = suspendCancellableCoroutine { continuation ->
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(authResult: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(true) {}
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(false) {}
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock to continue")
        .setAllowedAuthenticators(ALLOWED)
        .build()
    prompt.authenticate(info)
}
