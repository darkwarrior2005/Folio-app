package app.folio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.folio.AppContainer

val LocalContainer: ProvidableCompositionLocal<AppContainer> =
    staticCompositionLocalOf { error("AppContainer not provided") }

/** Creates a view model with the app container, without a DI framework. */
@Composable
inline fun <reified VM : ViewModel> folioViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
