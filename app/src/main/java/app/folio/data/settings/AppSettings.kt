package app.folio.data.settings

import app.folio.core.model.BookSort
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.core.model.LibraryGrouping
import app.folio.core.model.LibraryLayout
import kotlinx.serialization.Serializable

enum class UiDensity { COMFORTABLE, COMPACT }
enum class ReaderScrollMode { PAGINATED, SCROLL }
enum class ReaderTextAlign { START, JUSTIFY, CENTER, END }
enum class PdfViewMode { CONTINUOUS, PAGED_HORIZONTAL, PAGED_VERTICAL, DOUBLE_PAGE }
enum class PdfFitMode { WIDTH, PAGE }
enum class ComicDirection { LTR, RTL, VERTICAL }
enum class ComicFitMode { WIDTH, HEIGHT, SCREEN }
enum class TapZoneStyle { SIDES, EDGES, RIGHT_ONLY, DISABLED }
enum class PageAnimation { SLIDE, FADE, NONE }
enum class OrientationLock { AUTO, PORTRAIT, LANDSCAPE }
enum class ImportMode { LINK, COPY }

enum class HomeSection { CONTINUE_READING, READING_GOAL, STREAK, RECENTLY_ADDED, COLLECTIONS, ACTIVITY, POMODORO, QUEUE }

@Serializable
data class AppearanceSettings(
    val themeId: String = "paper",
    val autoNightTheme: Boolean = true,
    val nightThemeId: String = "midnight",
    val accentArgb: Long? = null,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val animations: Boolean = true,
)

@Serializable
data class LibrarySettings(
    val layout: LibraryLayout = LibraryLayout.GRID,
    val sort: BookSort = BookSort.RECENTLY_OPENED,
    val sortAscending: Boolean = false,
    val grouping: LibraryGrouping = LibraryGrouping.NONE,
    val showProgress: Boolean = true,
    val showTags: Boolean = true,
    val showFileType: Boolean = true,
    val showAuthor: Boolean = true,
    val showTitles: Boolean = true,
)

/** EPUB, TXT, Markdown and HTML. */
@Serializable
data class ReflowableSettings(
    val fontFamily: String = "Literata",
    val fontSizePercent: Int = 100,
    val fontWeight: Int = 400,
    val lineHeight: Double = 1.5,
    val letterSpacing: Double = 0.0,
    val wordSpacing: Double = 0.0,
    val paragraphSpacing: Double = 0.5,
    val pageMarginsPercent: Int = 100,
    val maxLineLength: Int = 0,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val scrollMode: ReaderScrollMode = ReaderScrollMode.PAGINATED,
    val columnCount: Int = 1,
    val hyphens: Boolean = false,
    val publisherStyles: Boolean = true,
    val readerThemeId: String? = null,
)

@Serializable
data class PdfSettings(
    val viewMode: PdfViewMode = PdfViewMode.CONTINUOUS,
    val fitMode: PdfFitMode = PdfFitMode.WIDTH,
    val nightMode: Boolean = false,
    val cropMargins: Boolean = false,
    val rotation: Int = 0,
    val showPageNumber: Boolean = true,
)

@Serializable
data class ComicSettings(
    val direction: ComicDirection = ComicDirection.LTR,
    val doublePage: Boolean = false,
    val coverAlone: Boolean = true,
    val fitMode: ComicFitMode = ComicFitMode.SCREEN,
    val gapBetweenPages: Boolean = true,
)

@Serializable
data class ReaderBehaviorSettings(
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val autoHideControlsMs: Int = 4000,
    val tapZones: TapZoneStyle = TapZoneStyle.SIDES,
    val invertTapZones: Boolean = false,
    val pageAnimation: PageAnimation = PageAnimation.SLIDE,
    val volumeKeysTurnPages: Boolean = false,
    val focusModeByDefault: Boolean = false,
    val screenBrightness: Float? = null,
    val zoomLockButton: Boolean = true,
    val zoomLockAlwaysVisible: Boolean = false,
)

@Serializable
data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 20,
    val sessionsBeforeLongBreak: Int = 4,
    val autoStartBreaks: Boolean = true,
    val autoStartNextFocus: Boolean = false,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val notifications: Boolean = true,
    val pauseMusicOnBreaks: Boolean = true,
)

@Serializable
data class NotificationSettings(
    val dailyReminder: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val goalReminder: Boolean = false,
    val streakReminder: Boolean = false,
)

@Serializable
data class PrivacySettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val hideInRecents: Boolean = false,
) {
    val lockEnabled: Boolean get() = pinHash != null
}

@Serializable
data class AccessibilitySettings(
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val largeTouchTargets: Boolean = false,
)

@Serializable
data class HomeSettings(
    val sections: List<HomeSection> = HomeSection.entries.toList(),
    val greeting: Boolean = true,
)

@Serializable
data class StorageSettings(
    val importMode: ImportMode = ImportMode.LINK,
    val trashRetentionDays: Int = 30,
    val fullTextIndexing: Boolean = true,
)

/** Last-used scribble tool and each inking tool's own colour, width and opacity. */
@Serializable
data class ScribbleSettings(
    val pen: InkToolStyle = InkStyle.PEN_DEFAULT,
    val highlighter: InkToolStyle = InkStyle.HIGHLIGHTER_DEFAULT,
    val lastTool: InkTool = InkTool.PEN,
) {
    /** The eraser has no style of its own; it keeps showing the pen's. */
    fun styleFor(tool: InkTool): InkToolStyle = if (tool == InkTool.HIGHLIGHTER) highlighter else pen

    fun withStyle(tool: InkTool, style: InkToolStyle): ScribbleSettings = when (tool) {
        InkTool.PEN -> copy(pen = style)
        InkTool.HIGHLIGHTER -> copy(highlighter = style)
        InkTool.ERASER -> this
    }
}

@Serializable
data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val reflowable: ReflowableSettings = ReflowableSettings(),
    val pdf: PdfSettings = PdfSettings(),
    val comic: ComicSettings = ComicSettings(),
    val behavior: ReaderBehaviorSettings = ReaderBehaviorSettings(),
    val pomodoro: PomodoroSettings = PomodoroSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
    val accessibility: AccessibilitySettings = AccessibilitySettings(),
    val home: HomeSettings = HomeSettings(),
    val storage: StorageSettings = StorageSettings(),
    val scribble: ScribbleSettings = ScribbleSettings(),
    val onboardingDone: Boolean = false,
)
