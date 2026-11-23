package app.folio.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.BuildConfig
import app.folio.R
import app.folio.core.model.BookSort
import app.folio.core.model.LibraryLayout
import app.folio.core.model.NightLight
import app.folio.core.model.NightLightMode
import app.folio.data.settings.ComicDirection
import app.folio.data.settings.HomeSection
import app.folio.data.settings.ImportMode
import app.folio.data.settings.OrientationLock
import app.folio.data.settings.PdfViewMode
import app.folio.data.settings.TapZoneStyle
import app.folio.data.settings.UiDensity
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.FolioChip
import app.folio.ui.components.NavigationRow
import app.folio.ui.components.SettingsGroup
import app.folio.ui.components.SettingsLink
import app.folio.ui.components.SettingsSectionHeader
import app.folio.ui.components.SettingsScaffold
import app.folio.ui.components.SliderRow
import app.folio.ui.components.SwitchRow
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import app.folio.ui.screens.library.label
import app.folio.ui.screens.reader.label
import app.folio.ui.theme.AccentColor
import app.folio.ui.theme.FolioTheme
import app.folio.ui.theme.FolioThemes
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.theme.ThemeGroup
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding)
                .padding(bottom = 96.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_offline_note),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp),
                )
            }

            val go = { route: String -> { onNavigate(route) } }
            SettingsGroup(
                stringResource(R.string.settings_appearance),
                listOf(
                    SettingsLink(
                        FolioIcons.Palette,
                        stringResource(R.string.settings_theme),
                        FolioThemes.byId(settings.appearance.themeId).name(),
                        go(app.folio.ui.nav.Routes.THEMES),
                    ),
                    SettingsLink(
                        FolioIcons.Moon,
                        stringResource(R.string.settings_night_light),
                        nightLightSummary(settings.nightLight),
                        go(app.folio.ui.nav.Routes.NIGHT_LIGHT),
                    ),
                    SettingsLink(
                        FolioIcons.Eye,
                        stringResource(R.string.settings_accessibility),
                        onClick = go(app.folio.ui.nav.Routes.ACCESSIBILITY),
                    ),
                ),
            )
            SettingsGroup(
                stringResource(R.string.settings_section_reading),
                listOf(
                    SettingsLink(FolioIcons.MenuBook, stringResource(R.string.settings_reader), onClick = go(app.folio.ui.nav.Routes.READER_SETTINGS)),
                    SettingsLink(FolioIcons.Bookshelf, stringResource(R.string.settings_library), onClick = go(app.folio.ui.nav.Routes.LIBRARY_SETTINGS)),
                ),
            )
            SettingsGroup(
                stringResource(R.string.settings_section_focus),
                listOf(
                    SettingsLink(FolioIcons.Timer, stringResource(R.string.settings_pomodoro), onClick = go(app.folio.ui.nav.Routes.POMODORO_SETTINGS)),
                    SettingsLink(FolioIcons.Bell, stringResource(R.string.settings_notifications), onClick = go(app.folio.ui.nav.Routes.NOTIFICATIONS)),
                ),
            )
            SettingsGroup(
                stringResource(R.string.settings_section_content_data),
                listOf(
                    SettingsLink(FolioIcons.Extension, stringResource(R.string.settings_manga), onClick = go(app.folio.ui.nav.Routes.MANGA_SETTINGS)),
                    SettingsLink(FolioIcons.Folder, stringResource(R.string.settings_storage), onClick = go(app.folio.ui.nav.Routes.STORAGE)),
                    SettingsLink(FolioIcons.Upload, stringResource(R.string.settings_data), onClick = go(app.folio.ui.nav.Routes.BACKUP)),
                ),
            )
            SettingsGroup(
                stringResource(R.string.settings_section_other),
                listOf(
                    SettingsLink(FolioIcons.Shield, stringResource(R.string.settings_privacy), onClick = go(app.folio.ui.nav.Routes.PRIVACY)),
                    SettingsLink(FolioIcons.Info, stringResource(R.string.settings_about), onClick = go(app.folio.ui.nav.Routes.ABOUT)),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemePickerScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    SettingsScaffold(stringResource(R.string.settings_theme), onBack) {
        ThemeGroup.entries.forEach { group ->
            SettingsSectionHeader(group.label())
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FolioThemes.byGroup[group].orEmpty().forEach { theme ->
                    Box(Modifier.width(156.dp)) {
                        ThemePreview(
                            theme = theme,
                            selected = settings.appearance.themeId == theme.id,
                            onClick = {
                                viewModel.update { it.copy(appearance = it.appearance.copy(themeId = theme.id)) }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        SettingsSectionHeader(stringResource(R.string.settings_accent))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FolioThemes.accentChoices.forEach { color ->
                val selected = settings.appearance.accentArgb == AccentColor.encode(color)
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape,
                        )
                        .clickable {
                            viewModel.update {
                                it.copy(appearance = it.appearance.copy(accentArgb = AccentColor.encode(color)))
                            }
                        },
                )
            }
            TextButton(
                onClick = { viewModel.update { it.copy(appearance = it.appearance.copy(accentArgb = null)) } },
            ) { Text(stringResource(R.string.action_undo)) }
        }

        SwitchRow(
            label = stringResource(R.string.settings_auto_night),
            checked = settings.appearance.autoNightTheme,
        ) { checked ->
            viewModel.update { it.copy(appearance = it.appearance.copy(autoNightTheme = checked)) }
        }

        if (settings.appearance.autoNightTheme) {
            SettingsSectionHeader(stringResource(R.string.settings_night_theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FolioThemes.all.filter { it.isDark }.forEach { theme ->
                    FilterChip(
                        selected = settings.appearance.nightThemeId == theme.id,
                        onClick = {
                            viewModel.update { it.copy(appearance = it.appearance.copy(nightThemeId = theme.id)) }
                        },
                        label = { Text(theme.name()) },
                    )
                }
            }
        }

        SettingsSectionHeader(stringResource(R.string.settings_density))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UiDensity.entries.forEach { density ->
                FilterChip(
                    selected = settings.appearance.density == density,
                    onClick = { viewModel.update { it.copy(appearance = it.appearance.copy(density = density)) } },
                    label = { Text(density.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                )
            }
        }

        SwitchRow(stringResource(R.string.settings_animations), settings.appearance.animations) { checked ->
            viewModel.update { it.copy(appearance = it.appearance.copy(animations = checked)) }
        }
        Spacer(Modifier.height(spacing.sectionGap))
    }
}

@Composable
private fun ThemePreview(theme: FolioTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp),
            )
            .background(theme.background)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.surface),
        ) {
            Box(
                Modifier
                    .padding(8.dp)
                    .size(width = 46.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(theme.onSurface.copy(alpha = 0.7f)),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(theme.accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = theme.name(),
                style = MaterialTheme.typography.labelMedium,
                color = theme.onSurface,
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    FolioIcons.Check,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderDefaultsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_reader), onBack) {
        SettingsSectionHeader(stringResource(R.string.setting_view_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PdfViewMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.pdf.viewMode == mode,
                    onClick = { viewModel.update { it.copy(pdf = it.pdf.copy(viewMode = mode)) } },
                    label = { Text(mode.label()) },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.setting_direction))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ComicDirection.entries.forEach { direction ->
                FilterChip(
                    selected = settings.comic.direction == direction,
                    onClick = { viewModel.update { it.copy(comic = it.comic.copy(direction = direction)) } },
                    label = {
                        Text(
                            stringResource(
                                when (direction) {
                                    ComicDirection.LTR -> R.string.setting_ltr
                                    ComicDirection.RTL -> R.string.setting_rtl
                                    ComicDirection.VERTICAL -> R.string.setting_vertical
                                },
                            ),
                        )
                    },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.setting_tap_zones))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TapZoneStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.behavior.tapZones == style,
                    onClick = { viewModel.update { it.copy(behavior = it.behavior.copy(tapZones = style)) } },
                    label = {
                        Text(
                            stringResource(
                                when (style) {
                                    TapZoneStyle.SIDES -> R.string.setting_tap_sides
                                    TapZoneStyle.EDGES -> R.string.setting_tap_edges
                                    TapZoneStyle.RIGHT_ONLY -> R.string.setting_tap_right
                                    TapZoneStyle.DISABLED -> R.string.setting_tap_disabled
                                },
                            ),
                        )
                    },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.setting_orientation))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrientationLock.entries.forEach { lock ->
                FilterChip(
                    selected = settings.behavior.orientationLock == lock,
                    onClick = { viewModel.update { it.copy(behavior = it.behavior.copy(orientationLock = lock)) } },
                    label = {
                        Text(
                            stringResource(
                                when (lock) {
                                    OrientationLock.AUTO -> R.string.setting_orientation_auto
                                    OrientationLock.PORTRAIT -> R.string.setting_orientation_portrait
                                    OrientationLock.LANDSCAPE -> R.string.setting_orientation_landscape
                                },
                            ),
                        )
                    },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.settings_section_behavior))
        SwitchRow(stringResource(R.string.setting_keep_screen_on), settings.behavior.keepScreenOn) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(keepScreenOn = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_fullscreen), settings.behavior.fullscreen) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(fullscreen = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_invert_tap), settings.behavior.invertTapZones) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(invertTapZones = checked)) }
        }
        SwitchRow(stringResource(R.string.reader_focus_mode), settings.behavior.focusModeByDefault) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(focusModeByDefault = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_reading_ruler), settings.behavior.readingRuler) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(readingRuler = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_page_turn_sound), settings.behavior.pageTurnSound) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(pageTurnSound = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_page_turn_haptic), settings.behavior.pageTurnHaptic) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(pageTurnHaptic = checked)) }
        }

        SettingsSectionHeader(stringResource(R.string.settings_section_zoom))
        SwitchRow(stringResource(R.string.setting_zoom_lock_button), settings.behavior.zoomLockButton) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(zoomLockButton = checked)) }
        }
        SwitchRow(
            stringResource(R.string.setting_zoom_lock_always),
            settings.behavior.zoomLockAlwaysVisible,
        ) { checked ->
            viewModel.update { it.copy(behavior = it.behavior.copy(zoomLockAlwaysVisible = checked)) }
        }
        SliderRow(
            label = stringResource(R.string.setting_auto_hide),
            value = settings.behavior.autoHideControlsMs / 1000f,
            range = 0f..15f,
            display = "${settings.behavior.autoHideControlsMs / 1000}s",
        ) { value ->
            viewModel.update {
                it.copy(behavior = it.behavior.copy(autoHideControlsMs = (value * 1000).toInt()))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScaffold(stringResource(R.string.settings_library), onBack) {
        SettingsSectionHeader(stringResource(R.string.home_style))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.folio.data.settings.HomeStyle.entries.forEach { style ->
                FilterChip(
                    selected = settings.home.style == style,
                    onClick = { viewModel.update { it.copy(home = it.home.copy(style = style)) } },
                    label = {
                        Text(
                            stringResource(
                                when (style) {
                                    app.folio.data.settings.HomeStyle.BOOKSHELF -> R.string.home_style_bookshelf
                                    app.folio.data.settings.HomeStyle.CLASSIC -> R.string.home_style_classic
                                },
                            ),
                        )
                    },
                )
            }
        }

        if (settings.home.style == app.folio.data.settings.HomeStyle.CLASSIC) {
            SettingsSectionHeader(stringResource(R.string.settings_home_sections))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeSection.entries.forEach { section ->
                    val enabled = section in settings.home.sections
                    FolioChip(
                        label = section.label(),
                        selected = enabled,
                        onClick = {
                            viewModel.update {
                                val sections = if (enabled) {
                                    it.home.sections - section
                                } else {
                                    it.home.sections + section
                                }
                                it.copy(home = it.home.copy(sections = sections))
                            }
                        },
                    )
                }
            }
        }

        SettingsSectionHeader(stringResource(R.string.library_layout))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryLayout.entries.forEach { layout ->
                FilterChip(
                    selected = settings.library.layout == layout,
                    onClick = { viewModel.update { it.copy(library = it.library.copy(layout = layout)) } },
                    label = { Text(layout.label()) },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.library_sort))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BookSort.entries.forEach { sort ->
                FilterChip(
                    selected = settings.library.sort == sort,
                    onClick = { viewModel.update { it.copy(library = it.library.copy(sort = sort)) } },
                    label = { Text(sort.label()) },
                )
            }
        }

        SwitchRow(stringResource(R.string.settings_show_progress), settings.library.showProgress) { checked ->
            viewModel.update { it.copy(library = it.library.copy(showProgress = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_show_tags), settings.library.showTags) { checked ->
            viewModel.update { it.copy(library = it.library.copy(showTags = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_show_file_type), settings.library.showFileType) { checked ->
            viewModel.update { it.copy(library = it.library.copy(showFileType = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_show_author), settings.library.showAuthor) { checked ->
            viewModel.update { it.copy(library = it.library.copy(showAuthor = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_show_titles), settings.library.showTitles) { checked ->
            viewModel.update { it.copy(library = it.library.copy(showTitles = checked)) }
        }
    }
}

@Composable
fun PomodoroSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pomodoro = settings.pomodoro

    SettingsScaffold(stringResource(R.string.settings_pomodoro), onBack) {
        SliderRow(
            label = stringResource(R.string.settings_focus_duration),
            value = pomodoro.focusMinutes.toFloat(),
            range = 1f..90f,
            display = "${pomodoro.focusMinutes} min",
        ) { value ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(focusMinutes = value.toInt())) }
        }
        SliderRow(
            label = stringResource(R.string.settings_short_break),
            value = pomodoro.shortBreakMinutes.toFloat(),
            range = 1f..30f,
            display = "${pomodoro.shortBreakMinutes} min",
        ) { value ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(shortBreakMinutes = value.toInt())) }
        }
        SliderRow(
            label = stringResource(R.string.settings_long_break),
            value = pomodoro.longBreakMinutes.toFloat(),
            range = 5f..60f,
            display = "${pomodoro.longBreakMinutes} min",
        ) { value ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(longBreakMinutes = value.toInt())) }
        }
        SliderRow(
            label = stringResource(R.string.settings_sessions_before_long),
            value = pomodoro.sessionsBeforeLongBreak.toFloat(),
            range = 2f..8f,
            display = pomodoro.sessionsBeforeLongBreak.toString(),
        ) { value ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(sessionsBeforeLongBreak = value.toInt())) }
        }
        SwitchRow(stringResource(R.string.settings_auto_start_breaks), pomodoro.autoStartBreaks) { checked ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(autoStartBreaks = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_auto_start_focus), pomodoro.autoStartNextFocus) { checked ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(autoStartNextFocus = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_sound), pomodoro.sound) { checked ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(sound = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_vibration), pomodoro.vibration) { checked ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(vibration = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_pause_music_on_breaks), pomodoro.pauseMusicOnBreaks) { checked ->
            viewModel.update { it.copy(pomodoro = it.pomodoro.copy(pauseMusicOnBreaks = checked)) }
        }
    }
}

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = settings.notifications

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SettingsScaffold(stringResource(R.string.settings_notifications), onBack) {
        SwitchRow(stringResource(R.string.settings_daily_reminder), notifications.dailyReminder) { checked ->
            if (checked && android.os.Build.VERSION.SDK_INT >= 33) {
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.update { it.copy(notifications = it.notifications.copy(dailyReminder = checked)) }
            viewModel.scheduleReminder(context)
        }
        SliderRow(
            label = stringResource(R.string.settings_reminder_time),
            value = notifications.reminderHour.toFloat(),
            range = 0f..23f,
            display = String.format("%02d:00", notifications.reminderHour),
        ) { value ->
            viewModel.update { it.copy(notifications = it.notifications.copy(reminderHour = value.toInt())) }
            viewModel.scheduleReminder(context)
        }
        SwitchRow(stringResource(R.string.settings_goal_reminder), notifications.goalReminder) { checked ->
            viewModel.update { it.copy(notifications = it.notifications.copy(goalReminder = checked)) }
            viewModel.scheduleReminder(context)
        }
        SwitchRow(stringResource(R.string.settings_streak_reminder), notifications.streakReminder) { checked ->
            viewModel.update { it.copy(notifications = it.notifications.copy(streakReminder = checked)) }
            viewModel.scheduleReminder(context)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val librarySize by viewModel.librarySize.collectAsStateWithLifecycle()
    val indexed by viewModel.indexedBooks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScaffold(stringResource(R.string.settings_storage), onBack) {
        SettingsSectionHeader(stringResource(R.string.settings_section_storage_usage))
        StorageRow(stringResource(R.string.library_title), Format.fileSize(librarySize))
        StorageRow(stringResource(R.string.action_change_cover), Format.fileSize(storage.covers))
        StorageRow(stringResource(R.string.reader_thumbnails), Format.fileSize(storage.renderedPages))
        StorageRow(stringResource(R.string.settings_full_text), "$indexed books")
        StorageRow(stringResource(R.string.import_skipped), Format.fileSize(storage.temporary))
        StorageRow(stringResource(R.string.settings_import_copy), Format.fileSize(storage.importedFiles))

        SettingsSectionHeader(stringResource(R.string.settings_import_mode))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.storage.importMode == mode,
                    onClick = { viewModel.update { it.copy(storage = it.storage.copy(importMode = mode)) } },
                    label = {
                        Text(
                            stringResource(
                                if (mode == ImportMode.LINK) {
                                    R.string.settings_import_link
                                } else {
                                    R.string.settings_import_copy
                                },
                            ),
                        )
                    },
                )
            }
        }

        SettingsSectionHeader(stringResource(R.string.settings_section_indexing))
        SwitchRow(stringResource(R.string.settings_full_text), settings.storage.fullTextIndexing) { checked ->
            viewModel.update { it.copy(storage = it.storage.copy(fullTextIndexing = checked)) }
            if (checked) viewModel.reindex(context) else viewModel.clearTextIndex()
        }
        SliderRow(
            label = stringResource(R.string.settings_trash_days, settings.storage.trashRetentionDays),
            value = settings.storage.trashRetentionDays.toFloat(),
            range = 1f..90f,
            display = "${settings.storage.trashRetentionDays}",
        ) { value ->
            viewModel.update { it.copy(storage = it.storage.copy(trashRetentionDays = value.toInt())) }
        }
        TextButton(onClick = viewModel::clearCache) { Text(stringResource(R.string.settings_clear_cache)) }
        TextButton(onClick = { viewModel.reindex(context) }) { Text(stringResource(R.string.search_indexing)) }
    }
}

@Composable
private fun nightLightSummary(night: app.folio.data.settings.NightLightSettings): String = when (night.mode) {
    NightLightMode.OFF -> stringResource(R.string.night_light_off)
    NightLightMode.ALWAYS -> stringResource(R.string.night_light_always)
    NightLightMode.SCHEDULED -> stringResource(
        R.string.night_light_schedule_summary,
        NightLight.clock(night.startMinutes),
        NightLight.clock(night.endMinutes),
    )
}

/**
 * The warm night filter: an amber tint over all of Folio in the evening, so reading late does not
 * keep you up, with optional extra dimming for a dark bedroom.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NightLightScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val night = settings.nightLight
    // Which time is being picked: 0 night light start, 1 its end, 2 bedtime.
    var editing by remember { mutableStateOf<Int?>(null) }

    fun update(transform: (app.folio.data.settings.NightLightSettings) -> app.folio.data.settings.NightLightSettings) =
        viewModel.update { it.copy(nightLight = transform(it.nightLight)) }

    SettingsScaffold(stringResource(R.string.settings_night_light), onBack) {
        Text(
            text = stringResource(R.string.night_light_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                NightLightMode.OFF to R.string.night_light_off,
                NightLightMode.ALWAYS to R.string.night_light_always,
                NightLightMode.SCHEDULED to R.string.night_light_scheduled,
            ).forEach { (mode, label) ->
                FolioChip(stringResource(label), night.mode == mode, onClick = { update { it.copy(mode = mode) } })
            }
        }

        if (night.mode == NightLightMode.SCHEDULED) {
            SettingsSectionHeader(stringResource(R.string.night_light_schedule))
            NavigationRow(stringResource(R.string.night_light_start), NightLight.clock(night.startMinutes)) { editing = 0 }
            NavigationRow(stringResource(R.string.night_light_end), NightLight.clock(night.endMinutes)) { editing = 1 }
        }

        SettingsSectionHeader(stringResource(R.string.night_light_strength))
        SliderRow(
            label = stringResource(R.string.night_light_warmth),
            value = night.warmth,
            range = 0f..1f,
            display = "${(night.warmth * 100).toInt()}%",
        ) { value -> update { it.copy(warmth = value) } }
        SliderRow(
            label = stringResource(R.string.night_light_dim),
            value = night.dim,
            range = 0f..1f,
            display = "${(night.dim * 100).toInt()}%",
        ) { value -> update { it.copy(dim = value) } }
        Text(
            text = stringResource(R.string.night_light_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSectionHeader(stringResource(R.string.bedtime_section))
        SwitchRow(stringResource(R.string.bedtime_reminder), night.bedtimeReminder) { on ->
            update { it.copy(bedtimeReminder = on) }
        }
        if (night.bedtimeReminder) {
            NavigationRow(stringResource(R.string.bedtime_time), NightLight.clock(night.bedtimeMinutes)) { editing = 2 }
        }
        Text(
            text = stringResource(R.string.bedtime_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    editing?.let { which ->
        val minutes = when (which) {
            0 -> night.startMinutes
            1 -> night.endMinutes
            else -> night.bedtimeMinutes
        }
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(
                    stringResource(
                        when (which) {
                            0 -> R.string.night_light_start
                            1 -> R.string.night_light_end
                            else -> R.string.bedtime_time
                        },
                    ),
                )
            },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    update {
                        when (which) {
                            0 -> it.copy(startMinutes = picked)
                            1 -> it.copy(endMinutes = picked)
                            else -> it.copy(bedtimeMinutes = picked)
                        }
                    }
                    editing = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
fun AccessibilityScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accessibility = settings.accessibility

    SettingsScaffold(stringResource(R.string.settings_accessibility), onBack) {
        SliderRow(
            label = stringResource(R.string.settings_text_scale),
            value = accessibility.textScale,
            range = 0.8f..1.6f,
            display = String.format("%.1f×", accessibility.textScale),
        ) { value ->
            viewModel.update { it.copy(accessibility = it.accessibility.copy(textScale = value)) }
        }
        SwitchRow(stringResource(R.string.settings_high_contrast), accessibility.highContrast) { checked ->
            viewModel.update { it.copy(accessibility = it.accessibility.copy(highContrast = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_reduce_motion), accessibility.reduceMotion) { checked ->
            viewModel.update { it.copy(accessibility = it.accessibility.copy(reduceMotion = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_large_targets), accessibility.largeTouchTargets) { checked ->
            viewModel.update { it.copy(accessibility = it.accessibility.copy(largeTouchTargets = checked)) }
        }
    }
}

@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val privacy = settings.privacy
    var pinEntry by remember { mutableStateOf<String?>(null) }
    var confirmEntry by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    SettingsScaffold(stringResource(R.string.settings_privacy), onBack) {
        SwitchRow(stringResource(R.string.settings_pin_lock), privacy.lockEnabled) { checked ->
            if (checked) pinEntry = "" else viewModel.removePin()
        }
        if (privacy.lockEnabled) {
            SwitchRow(stringResource(R.string.settings_biometric), privacy.biometricEnabled) { checked ->
                viewModel.update { it.copy(privacy = it.privacy.copy(biometricEnabled = checked)) }
            }
            SliderRow(
                label = stringResource(R.string.settings_lock_timeout),
                value = privacy.lockTimeoutSeconds.toFloat(),
                range = 0f..300f,
                display = "${privacy.lockTimeoutSeconds}s",
            ) { value ->
                viewModel.update { it.copy(privacy = it.privacy.copy(lockTimeoutSeconds = value.toInt())) }
            }
        }
        SwitchRow(stringResource(R.string.settings_hide_recents), privacy.hideInRecents) { checked ->
            viewModel.update { it.copy(privacy = it.privacy.copy(hideInRecents = checked)) }
        }

        SettingsSectionHeader(stringResource(R.string.settings_section_browsing))
        Text(
            text = stringResource(R.string.settings_browsing_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(stringResource(R.string.settings_incognito_keyboard), privacy.incognitoKeyboard) { checked ->
            viewModel.update { it.copy(privacy = it.privacy.copy(incognitoKeyboard = checked)) }
        }
        SwitchRow(stringResource(R.string.settings_forget_browsing), privacy.forgetBrowsing) { checked ->
            viewModel.update { it.copy(privacy = it.privacy.copy(forgetBrowsing = checked)) }
        }
        TextButton(onClick = { confirmClear = true }) {
            Text(stringResource(R.string.settings_clear_browsing))
        }
        if (cleared) {
            Text(
                text = stringResource(R.string.settings_browsing_cleared),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (confirmClear) {
        var includeSiteData by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_browsing)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_clear_browsing_message))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clickable { includeSiteData = !includeSiteData },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = includeSiteData, onCheckedChange = null)
                        Text(
                            text = stringResource(R.string.settings_clear_site_data),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearBrowsingData(includeSiteData) { cleared = true }
                }) { Text(stringResource(R.string.settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    pinEntry?.let { value ->
        app.folio.ui.components.TextInputDialog(
            title = stringResource(R.string.lock_set_pin),
            label = stringResource(R.string.lock_enter_pin),
            initialValue = value,
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { pin ->
                pinEntry = null
                confirmEntry = pin
            },
            onDismiss = { pinEntry = null },
        )
    }

    confirmEntry?.let { first ->
        app.folio.ui.components.TextInputDialog(
            title = stringResource(R.string.lock_confirm_pin),
            label = stringResource(R.string.lock_enter_pin),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { second ->
                if (first == second && second.length >= 4) viewModel.setPin(second)
                confirmEntry = null
            },
            onDismiss = { confirmEntry = null },
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_about), onBack) {
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(text = stringResource(R.string.about_offline), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Text(text = stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = LICENSES,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val LICENSES =
    "Readium Kotlin Toolkit (BSD-3-Clause) · PdfiumAndroid (Apache-2.0) · Apache Commons Compress " +
        "(Apache-2.0) · junrar (UnRAR license) · jsoup (MIT) · commonmark-java (BSD-2-Clause) · " +
        "Coil (Apache-2.0) · AndroidX (Apache-2.0) · Literata, Lora, Source Serif 4, Inter, Fraunces, " +
        "Atkinson Hyperlegible and JetBrains Mono (SIL Open Font License 1.1)"

// ---- Shared pieces -------------------------------------------------------------
// SettingsScaffold, NavigationRow, SwitchRow and SliderRow live in
// app.folio.ui.components.Common now — they're used outside this package too.

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FolioTheme.name(): String = stringResource(nameRes)

@Composable
private fun ThemeGroup.label(): String = stringResource(
    when (this) {
        ThemeGroup.LIGHT -> R.string.theme_group_light
        ThemeGroup.DARK -> R.string.theme_group_dark
        ThemeGroup.READING -> R.string.theme_group_reading
        ThemeGroup.EXPERIMENTAL -> R.string.theme_group_experimental
    },
)

@Composable
private fun HomeSection.label(): String = stringResource(
    when (this) {
        HomeSection.CONTINUE_READING -> R.string.home_continue_reading
        HomeSection.READING_GOAL -> R.string.home_goal
        HomeSection.STREAK -> R.string.home_streak
        HomeSection.RECENTLY_ADDED -> R.string.home_recently_added
        HomeSection.COLLECTIONS -> R.string.home_collections
        HomeSection.ACTIVITY -> R.string.home_activity
        HomeSection.POMODORO -> R.string.home_pomodoro
        HomeSection.QUEUE -> R.string.home_queue
    },
)
