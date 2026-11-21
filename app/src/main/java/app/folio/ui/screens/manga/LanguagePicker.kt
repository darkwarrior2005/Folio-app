package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.folio.core.model.SourceLanguages

/**
 * The one app-wide manga-source language choice: "Show all" or "Only <device language>",
 * filtering what Extensions, Browse and search-all-sources offer.
 */
@Composable
fun LanguagePicker(
    availableLanguages: Set<String>,
    hiddenLanguages: Set<String>,
    onOnlyShow: (String) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceLanguage = SourceLanguages.deviceLanguage()
    val showingOnlyDevice = deviceLanguage !in hiddenLanguages &&
        availableLanguages.filterNot { it in hiddenLanguages } == listOf(deviceLanguage)

    Column(modifier.fillMaxWidth()) {
        Row {
            RadioButton(selected = !showingOnlyDevice, onClick = onShowAll)
            Text(SourceLanguages.displayName(SourceLanguages.SHOW_ALL))
        }
        if (deviceLanguage in availableLanguages) {
            Row {
                RadioButton(selected = showingOnlyDevice, onClick = { onOnlyShow(deviceLanguage) })
                Text("Only ${SourceLanguages.displayName(deviceLanguage)}")
            }
        }
    }
}
