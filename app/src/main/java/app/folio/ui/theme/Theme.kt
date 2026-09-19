package app.folio.ui.theme

import android.content.res.AssetManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.data.settings.AppSettings
import app.folio.data.settings.UiDensity
import kotlin.math.max
import kotlin.math.min

val LocalFolioTheme: ProvidableCompositionLocal<FolioTheme> = staticCompositionLocalOf { FolioThemes.Paper }
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }
val LocalSpacing: ProvidableCompositionLocal<FolioSpacing> = compositionLocalOf { FolioSpacing() }

data class FolioSpacing(
    val cardPadding: Dp = 16.dp,
    val screenPadding: Dp = 20.dp,
    val sectionGap: Dp = 28.dp,
    val itemGap: Dp = 12.dp,
    val rowHeight: Dp = 56.dp,
) {
    companion object {
        val Compact = FolioSpacing(
            cardPadding = 12.dp,
            screenPadding = 14.dp,
            sectionGap = 20.dp,
            itemGap = 8.dp,
            rowHeight = 48.dp,
        )
        val Comfortable = FolioSpacing()
        val LargeTargets = FolioSpacing(rowHeight = 64.dp, itemGap = 16.dp)
    }
}

val FolioShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun FolioAppTheme(
    settings: AppSettings,
    systemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val theme = resolveTheme(settings, systemDark)
    val chosenAccent = settings.appearance.accentArgb?.let { AccentColor.decode(it) } ?: theme.accent
    val accent = ensureReadable(chosenAccent, theme.surface, theme.isDark)
    val colorScheme = theme.toColorScheme(accent, settings.accessibility.highContrast)

    val spacing = when {
        settings.accessibility.largeTouchTargets -> FolioSpacing.LargeTargets
        settings.appearance.density == UiDensity.COMPACT -> FolioSpacing.Compact
        else -> FolioSpacing.Comfortable
    }

    CompositionLocalProvider(
        LocalFolioTheme provides theme,
        LocalReduceMotion provides (settings.accessibility.reduceMotion || !settings.appearance.animations),
        LocalSpacing provides spacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = folioTypography(settings.accessibility.textScale),
            shapes = FolioShapes,
            content = content,
        )
    }
}

fun resolveTheme(settings: AppSettings, systemDark: Boolean): FolioTheme {
    val base = FolioThemes.byId(settings.appearance.themeId)
    return if (settings.appearance.autoNightTheme && systemDark && !base.isDark) {
        FolioThemes.byId(settings.appearance.nightThemeId)
    } else {
        base
    }
}

fun FolioTheme.toColorScheme(accent: Color, highContrast: Boolean): ColorScheme {
    val text = if (highContrast) {
        if (isDark) Color.White else Color.Black
    } else {
        onSurface
    }
    val muted = if (highContrast) text.copy(alpha = 0.85f) else onSurfaceMuted
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = readableOn(accent),
        primaryContainer = accent.copy(alpha = if (isDark) 0.26f else 0.16f).compositeOver(surface),
        onPrimaryContainer = text,
        secondary = accent,
        onSecondary = readableOn(accent),
        // Selected chips and slider tracks use this, so it must stand out from cards.
        secondaryContainer = accent.copy(alpha = if (isDark) 0.38f else 0.24f).compositeOver(surfaceAlt),
        onSecondaryContainer = text,
        tertiary = accent,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = muted,
        surfaceContainer = surfaceAlt,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        outline = if (highContrast) text.copy(alpha = 0.6f) else outline,
        outlineVariant = outline,
        error = if (isDark) Color(0xFFFF8A80) else Color(0xFFB3261E),
        onError = if (isDark) Color(0xFF3A0905) else Color.White,
        scrim = Color.Black.copy(alpha = 0.6f),
    )
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

fun readableOn(color: Color): Color = if (color.luminance() > 0.5f) Color(0xFF101010) else Color.White

/** Keeps a chosen accent legible: nudges it until it has enough contrast with the surface. */
fun ensureReadable(accent: Color, surface: Color, isDark: Boolean): Color {
    var candidate = accent
    var guard = 0
    while (contrastRatio(candidate, surface) < MIN_ACCENT_CONTRAST && guard < 12) {
        candidate = if (isDark) candidate.lighten(0.08f) else candidate.darken(0.08f)
        guard++
    }
    return candidate
}

fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return max(l1, l2) / min(l1, l2)
}

private fun Color.lighten(amount: Float) = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
    red = (red - amount).coerceAtLeast(0f),
    green = (green - amount).coerceAtLeast(0f),
    blue = (blue - amount).coerceAtLeast(0f),
    alpha = alpha,
)

private const val MIN_ACCENT_CONTRAST = 3.0f

@OptIn(ExperimentalTextApi::class)
@Composable
fun folioTypography(scale: Float): Typography {
    val assets = LocalContext.current.assets
    val display = remember(assets) { variableFamily(assets, "fonts/fraunces.ttf") }
    val body = remember(assets) { variableFamily(assets, "fonts/inter.ttf") }
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun style(
        family: FontFamily,
        size: Float,
        lineHeight: Float,
        weight: FontWeight,
        letterSpacing: Float = 0f,
    ) = TextStyle(
        fontFamily = family,
        fontSize = (size * scale).sp,
        lineHeight = (lineHeight * scale).sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = lineHeightStyle,
    )

    return Typography(
        displayLarge = style(display, 40f, 46f, FontWeight.SemiBold, (-0.5f)),
        displayMedium = style(display, 32f, 38f, FontWeight.SemiBold, (-0.4f)),
        displaySmall = style(display, 28f, 34f, FontWeight.SemiBold, (-0.3f)),
        headlineLarge = style(display, 26f, 32f, FontWeight.SemiBold, (-0.3f)),
        headlineMedium = style(display, 22f, 28f, FontWeight.SemiBold, (-0.2f)),
        headlineSmall = style(display, 19f, 25f, FontWeight.SemiBold),
        titleLarge = style(body, 19f, 25f, FontWeight.SemiBold, (-0.1f)),
        titleMedium = style(body, 16f, 22f, FontWeight.Medium),
        titleSmall = style(body, 14f, 19f, FontWeight.Medium),
        bodyLarge = style(body, 16f, 24f, FontWeight.Normal),
        bodyMedium = style(body, 14f, 21f, FontWeight.Normal),
        bodySmall = style(body, 12.5f, 18f, FontWeight.Normal),
        labelLarge = style(body, 14f, 18f, FontWeight.Medium, 0.1f),
        labelMedium = style(body, 12f, 16f, FontWeight.Medium, 0.2f),
        labelSmall = style(body, 11f, 15f, FontWeight.Medium, 0.3f),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(assets: AssetManager, path: String): FontFamily = FontFamily(
    listOf(
        FontWeight.Light to 300,
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700,
    ).map { (weight, axis) ->
        Font(
            path = path,
            assetManager = assets,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
        )
    },
)

/** Page colours for reflowable books: the chosen page theme, or the app theme when none is set. */
@Composable
fun readerPageColors(settings: AppSettings): Pair<Color, Color> {
    val theme = settings.reflowable.readerThemeId?.let { id -> FolioThemes.all.firstOrNull { it.id == id } }
    return if (theme != null) {
        theme.readerBackground to theme.readerText
    } else {
        MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
}
