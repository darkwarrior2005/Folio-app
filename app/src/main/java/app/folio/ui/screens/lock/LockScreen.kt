package app.folio.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import app.folio.R
import app.folio.data.settings.PinHasher
import app.folio.data.settings.PrivacySettings
import app.folio.ui.screens.reader.findActivity

@Composable
fun LockScreen(privacy: PrivacySettings, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? FragmentActivity }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun promptBiometric() {
        val host = activity ?: return
        val manager = BiometricManager.from(host)
        val canAuthenticate = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) return

        val prompt = BiometricPrompt(
            host,
            androidx.core.content.ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(host.getString(R.string.lock_title))
                .setNegativeButtonText(host.getString(R.string.lock_enter_pin))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    LaunchedEffect(privacy.biometricEnabled) {
        if (privacy.biometricEnabled) promptBiometric()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { char -> char.isDigit() }.take(12)
                error = false
            },
            label = { Text(stringResource(R.string.lock_enter_pin)) },
            singleLine = true,
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (PinHasher.verify(pin, privacy.pinSalt, privacy.pinHash)) onUnlocked() else error = true
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_wrong_pin),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (PinHasher.verify(pin, privacy.pinSalt, privacy.pinHash)) onUnlocked() else error = true
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_open)) }

        if (privacy.biometricEnabled) {
            TextButton(onClick = { promptBiometric() }) {
                Text(stringResource(R.string.lock_use_biometric))
            }
        }
    }
}
