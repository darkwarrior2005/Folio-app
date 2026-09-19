package app.folio.ui.theme

import androidx.compose.ui.graphics.Color
import app.folio.R

enum class ThemeGroup { LIGHT, DARK, READING, EXPERIMENTAL }

/**
 * A theme is a small set of colors used by the whole app, including the reader. Reader colors are
 * kept separate so a "paper" library can still open a book on a warmer page.
 */
data class FolioTheme(
    val id: String,
    val nameRes: Int,
    val group: ThemeGroup,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val readerBackground: Color,
    val readerText: Color,
)

object FolioThemes {

    val Classic = FolioTheme(
        id = "classic",
        nameRes = R.string.theme_classic,
        group = ThemeGroup.LIGHT,
        isDark = false,
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFF1F1F3),
        onSurface = Color(0xFF15171A),
        onSurfaceMuted = Color(0xFF5B6168),
        accent = Color(0xFF3A6FF7),
        onAccent = Color.White,
        outline = Color(0xFFE0E2E6),
        readerBackground = Color(0xFFFFFFFF),
        readerText = Color(0xFF15171A),
    )

    val Paper = FolioTheme(
        id = "paper",
        nameRes = R.string.theme_paper,
        group = ThemeGroup.LIGHT,
        isDark = false,
        background = Color(0xFFF6F1E7),
        surface = Color(0xFFFDFAF3),
        surfaceAlt = Color(0xFFEDE6D7),
        onSurface = Color(0xFF221E18),
        onSurfaceMuted = Color(0xFF6B6153),
        accent = Color(0xFF9A5B33),
        onAccent = Color.White,
        outline = Color(0xFFDFD5C2),
        readerBackground = Color(0xFFFDFAF3),
        readerText = Color(0xFF221E18),
    )

    val Ivory = FolioTheme(
        id = "ivory",
        nameRes = R.string.theme_ivory,
        group = ThemeGroup.LIGHT,
        isDark = false,
        background = Color(0xFFFFFDF7),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFF4F0E4),
        onSurface = Color(0xFF1C1B17),
        onSurfaceMuted = Color(0xFF63604F),
        accent = Color(0xFF7A6A2F),
        onAccent = Color.White,
        outline = Color(0xFFE6E1D2),
        readerBackground = Color(0xFFFFFDF7),
        readerText = Color(0xFF1C1B17),
    )

    val Warm = FolioTheme(
        id = "warm",
        nameRes = R.string.theme_warm,
        group = ThemeGroup.LIGHT,
        isDark = false,
        background = Color(0xFFFFF3EC),
        surface = Color(0xFFFFFAF6),
        surfaceAlt = Color(0xFFFAE5DA),
        onSurface = Color(0xFF2A1D16),
        onSurfaceMuted = Color(0xFF75594B),
        accent = Color(0xFFC1553B),
        onAccent = Color.White,
        outline = Color(0xFFF0D7C9),
        readerBackground = Color(0xFFFFFAF6),
        readerText = Color(0xFF2A1D16),
    )

    val Minimal = FolioTheme(
        id = "minimal",
        nameRes = R.string.theme_minimal,
        group = ThemeGroup.LIGHT,
        isDark = false,
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceAlt = Color(0xFFF5F5F5),
        onSurface = Color(0xFF111111),
        onSurfaceMuted = Color(0xFF6E6E6E),
        accent = Color(0xFF111111),
        onAccent = Color.White,
        outline = Color(0xFFE6E6E6),
        readerBackground = Color(0xFFFFFFFF),
        readerText = Color(0xFF111111),
    )

    val Amoled = FolioTheme(
        id = "amoled",
        nameRes = R.string.theme_amoled,
        group = ThemeGroup.DARK,
        isDark = true,
        background = Color(0xFF000000),
        surface = Color(0xFF080808),
        surfaceAlt = Color(0xFF151515),
        onSurface = Color(0xFFEDEDED),
        onSurfaceMuted = Color(0xFF9A9A9A),
        accent = Color(0xFF8AB4FF),
        onAccent = Color(0xFF00102B),
        outline = Color(0xFF242424),
        readerBackground = Color(0xFF000000),
        readerText = Color(0xFFD8D8D8),
    )

    val Charcoal = FolioTheme(
        id = "charcoal",
        nameRes = R.string.theme_charcoal,
        group = ThemeGroup.DARK,
        isDark = true,
        background = Color(0xFF17191C),
        surface = Color(0xFF1E2126),
        surfaceAlt = Color(0xFF272B31),
        onSurface = Color(0xFFE6E8EB),
        onSurfaceMuted = Color(0xFF9BA1A9),
        accent = Color(0xFF7FB2F0),
        onAccent = Color(0xFF07182B),
        outline = Color(0xFF32373E),
        readerBackground = Color(0xFF1E2126),
        readerText = Color(0xFFDCE0E5),
    )

    val Midnight = FolioTheme(
        id = "midnight",
        nameRes = R.string.theme_midnight,
        group = ThemeGroup.DARK,
        isDark = true,
        background = Color(0xFF0E1018),
        surface = Color(0xFF151827),
        surfaceAlt = Color(0xFF1E2234),
        onSurface = Color(0xFFE4E6F2),
        onSurfaceMuted = Color(0xFF959BB5),
        accent = Color(0xFF9B8CFF),
        onAccent = Color(0xFF12082B),
        outline = Color(0xFF2A2F44),
        readerBackground = Color(0xFF151827),
        readerText = Color(0xFFD8DBEA),
    )

    val DeepBlue = FolioTheme(
        id = "deepblue",
        nameRes = R.string.theme_deep_blue,
        group = ThemeGroup.DARK,
        isDark = true,
        background = Color(0xFF07182B),
        surface = Color(0xFF0C2039),
        surfaceAlt = Color(0xFF122C4B),
        onSurface = Color(0xFFDDE8F5),
        onSurfaceMuted = Color(0xFF8DA4BE),
        accent = Color(0xFF52B9E8),
        onAccent = Color(0xFF00121E),
        outline = Color(0xFF1B3B5E),
        readerBackground = Color(0xFF0C2039),
        readerText = Color(0xFFD3E1F0),
    )

    val Sepia = FolioTheme(
        id = "sepia",
        nameRes = R.string.theme_sepia,
        group = ThemeGroup.READING,
        isDark = false,
        background = Color(0xFFF3E6CF),
        surface = Color(0xFFF9EEDA),
        surfaceAlt = Color(0xFFEADCC0),
        onSurface = Color(0xFF3B2F1E),
        onSurfaceMuted = Color(0xFF7A6849),
        accent = Color(0xFF8A6323),
        onAccent = Color.White,
        outline = Color(0xFFDCC9A6),
        readerBackground = Color(0xFFF7EBD6),
        readerText = Color(0xFF3B2F1E),
    )

    val Cream = FolioTheme(
        id = "cream",
        nameRes = R.string.theme_cream,
        group = ThemeGroup.READING,
        isDark = false,
        background = Color(0xFFFBF5E9),
        surface = Color(0xFFFFFBF2),
        surfaceAlt = Color(0xFFF2E9D7),
        onSurface = Color(0xFF2E2A22),
        onSurfaceMuted = Color(0xFF6F6655),
        accent = Color(0xFF6D7F52),
        onAccent = Color.White,
        outline = Color(0xFFE5DAC4),
        readerBackground = Color(0xFFFFFBF2),
        readerText = Color(0xFF2E2A22),
    )

    val SoftPaper = FolioTheme(
        id = "softpaper",
        nameRes = R.string.theme_soft_paper,
        group = ThemeGroup.READING,
        isDark = false,
        background = Color(0xFFF0EFEA),
        surface = Color(0xFFF7F6F1),
        surfaceAlt = Color(0xFFE6E5DE),
        onSurface = Color(0xFF2B2B28),
        onSurfaceMuted = Color(0xFF6C6C66),
        accent = Color(0xFF4E7C6B),
        onAccent = Color.White,
        outline = Color(0xFFDDDCD4),
        readerBackground = Color(0xFFF7F6F1),
        readerText = Color(0xFF2B2B28),
    )

    val LowContrast = FolioTheme(
        id = "lowcontrast",
        nameRes = R.string.theme_low_contrast,
        group = ThemeGroup.READING,
        isDark = true,
        background = Color(0xFF23262B),
        surface = Color(0xFF2A2E34),
        surfaceAlt = Color(0xFF32373E),
        onSurface = Color(0xFFC3C7CC),
        onSurfaceMuted = Color(0xFF8B9096),
        accent = Color(0xFF86A5C4),
        onAccent = Color(0xFF0E1620),
        outline = Color(0xFF3B4149),
        readerBackground = Color(0xFF2A2E34),
        readerText = Color(0xFFBCC0C6),
    )

    val Cyber = FolioTheme(
        id = "cyber",
        nameRes = R.string.theme_cyber,
        group = ThemeGroup.EXPERIMENTAL,
        isDark = true,
        background = Color(0xFF0B0F14),
        surface = Color(0xFF111821),
        surfaceAlt = Color(0xFF17212D),
        onSurface = Color(0xFFD9F5FF),
        onSurfaceMuted = Color(0xFF7FA3B5),
        accent = Color(0xFF17E7C8),
        onAccent = Color(0xFF00201B),
        outline = Color(0xFF1E2C3A),
        readerBackground = Color(0xFF111821),
        readerText = Color(0xFFCDE8F2),
    )

    val Forest = FolioTheme(
        id = "forest",
        nameRes = R.string.theme_forest,
        group = ThemeGroup.EXPERIMENTAL,
        isDark = true,
        background = Color(0xFF0F1A14),
        surface = Color(0xFF15231B),
        surfaceAlt = Color(0xFF1D2F24),
        onSurface = Color(0xFFDDEBE0),
        onSurfaceMuted = Color(0xFF90A796),
        accent = Color(0xFF6FCF97),
        onAccent = Color(0xFF042012),
        outline = Color(0xFF24382C),
        readerBackground = Color(0xFF15231B),
        readerText = Color(0xFFD5E5D9),
    )

    val Ocean = FolioTheme(
        id = "ocean",
        nameRes = R.string.theme_ocean,
        group = ThemeGroup.EXPERIMENTAL,
        isDark = true,
        background = Color(0xFF071A21),
        surface = Color(0xFF0B2530),
        surfaceAlt = Color(0xFF103240),
        onSurface = Color(0xFFD6EDF4),
        onSurfaceMuted = Color(0xFF86AAB7),
        accent = Color(0xFF3FC5D8),
        onAccent = Color(0xFF00171D),
        outline = Color(0xFF17404F),
        readerBackground = Color(0xFF0B2530),
        readerText = Color(0xFFCFE6EE),
    )

    val Sunset = FolioTheme(
        id = "sunset",
        nameRes = R.string.theme_sunset,
        group = ThemeGroup.EXPERIMENTAL,
        isDark = true,
        background = Color(0xFF1C1116),
        surface = Color(0xFF261720),
        surfaceAlt = Color(0xFF34202B),
        onSurface = Color(0xFFF3DFE6),
        onSurfaceMuted = Color(0xFFB18E9C),
        accent = Color(0xFFFF8A5B),
        onAccent = Color(0xFF2B0C00),
        outline = Color(0xFF3F2833),
        readerBackground = Color(0xFF261720),
        readerText = Color(0xFFEBD8DF),
    )

    val all: List<FolioTheme> = listOf(
        Classic, Paper, Ivory, Warm, Minimal,
        Amoled, Charcoal, Midnight, DeepBlue,
        Sepia, Cream, SoftPaper, LowContrast,
        Cyber, Forest, Ocean, Sunset,
    )

    val byGroup: Map<ThemeGroup, List<FolioTheme>> = all.groupBy { it.group }

    fun byId(id: String?): FolioTheme = all.firstOrNull { it.id == id } ?: Paper

    /** Accent choices offered in settings; any of them stays readable on every theme. */
    val accentChoices: List<Color> = listOf(
        Color(0xFF3A6FF7), Color(0xFF7B5CFF), Color(0xFF9A5B33), Color(0xFFC1553B),
        Color(0xFF2E9E6B), Color(0xFF0E9BA8), Color(0xFFD08700), Color(0xFFCE3F73),
        Color(0xFF5E6AD2), Color(0xFF111111),
    )
}
