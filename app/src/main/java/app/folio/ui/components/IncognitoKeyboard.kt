package app.folio.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the keyboard should be told not to learn from what's typed (search boxes especially).
 * The intended mechanism is Compose's `InterceptPlatformTextInput`, adding
 * `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING` to every text field's input connection app-wide;
 * that API's exact shape is version-sensitive enough that wiring it blind (no build available in
 * this environment to check against) risks a broken privacy feature that looks like it works. For
 * now this is a composition local a text field can read and react to - e.g. a search `TextField`
 * can pass `KeyboardOptions(imeAction = ...)` plus its own IME-options tweak when this is true -
 * wiring every existing text field to do so is a follow-up.
 */
val LocalIncognitoKeyboard: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

@Composable
fun IncognitoKeyboard(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIncognitoKeyboard provides enabled, content = content)
}
