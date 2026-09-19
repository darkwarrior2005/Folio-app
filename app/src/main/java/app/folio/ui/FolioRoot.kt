package app.folio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.folio.R
import app.folio.data.settings.AppSettings
import app.folio.ui.nav.Routes
import app.folio.ui.screens.details.BookDetailsScreen
import app.folio.ui.screens.details.EditBookScreen
import app.folio.ui.screens.home.HomeScreen
import app.folio.ui.screens.library.LibraryScreen
import app.folio.ui.screens.lock.LockScreen
import app.folio.ui.screens.notes.NotesScreen
import app.folio.ui.screens.organize.CategoriesScreen
import app.folio.ui.screens.organize.CollectionDetailScreen
import app.folio.ui.screens.organize.CollectionsScreen
import app.folio.ui.screens.organize.QueueScreen
import app.folio.ui.screens.organize.TagsScreen
import app.folio.ui.screens.organize.TrashScreen
import app.folio.ui.screens.pomodoro.PomodoroScreen
import app.folio.ui.screens.reader.ReaderScreen
import app.folio.ui.screens.search.SearchScreen
import app.folio.ui.screens.settings.AboutScreen
import app.folio.ui.screens.settings.AccessibilityScreen
import app.folio.ui.screens.settings.BackupScreen
import app.folio.ui.screens.settings.LibrarySettingsScreen
import app.folio.ui.screens.settings.NotificationSettingsScreen
import app.folio.ui.screens.settings.PomodoroSettingsScreen
import app.folio.ui.screens.settings.PrivacyScreen
import app.folio.ui.screens.settings.ReaderDefaultsScreen
import app.folio.ui.screens.settings.SettingsScreen
import app.folio.ui.screens.settings.StorageScreen
import app.folio.ui.screens.settings.ThemePickerScreen
import app.folio.ui.screens.stats.StatsScreen

private const val TABLET_WIDTH_DP = 840

data class BottomDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val destinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, Icons.Rounded.Home),
    BottomDestination(Routes.LIBRARY, R.string.nav_library, Icons.AutoMirrored.Rounded.MenuBook),
    BottomDestination(Routes.NOTES, R.string.nav_notes, Icons.Rounded.StickyNote2),
    BottomDestination(Routes.STATS, R.string.nav_stats, Icons.Rounded.Insights),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
)

@Composable
fun FolioRoot(
    settings: AppSettings,
    locked: Boolean,
    onUnlocked: () -> Unit,
    openBookId: Long?,
    onOpenHandled: () -> Unit,
) {
    if (locked) {
        Surface(Modifier.fillMaxSize()) {
            LockScreen(privacy = settings.privacy, onUnlocked = onUnlocked)
        }
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(openBookId) {
        if (openBookId != null) {
            navController.navigate(Routes.reader(openBookId))
            onOpenHandled()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth.value >= TABLET_WIDTH_DP
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showNav = currentRoute in destinations.map { it.route }

        Row(Modifier.fillMaxSize()) {
            if (wide && showNav) {
                NavigationRail {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTop(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }

            Scaffold(
                bottomBar = {
                    if (!wide && showNav) {
                        NavigationBar {
                            destinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { navController.navigateTop(destination.route) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    label = { Text(stringResource(destination.labelRes)) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                FolioNavHost(
                    navController = navController,
                    wide = wide,
                    modifier = Modifier.padding(
                        bottom = padding.calculateBottomPadding(),
                    ),
                )
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FolioNavHost(
    navController: NavHostController,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenBook = { navController.navigate(Routes.reader(it)) },
                onBookDetails = { navController.navigate(Routes.bookDetails(it)) },
                onSeeLibrary = { navController.navigateTop(Routes.LIBRARY) },
                onCollections = { navController.navigate(Routes.COLLECTIONS) },
                onCollection = { navController.navigate(Routes.collection(it)) },
                onStats = { navController.navigateTop(Routes.STATS) },
                onQueue = { navController.navigate(Routes.QUEUE) },
                onPomodoro = { navController.navigate(Routes.POMODORO) },
            )
        }

        composable(Routes.LIBRARY) {
            if (wide) {
                // Tablets keep the library in view while a book's details sit beside it.
                var selected by remember { mutableStateOf<Long?>(null) }
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(0.42f)) {
                        LibraryScreen(
                            onOpenBook = { navController.navigate(Routes.reader(it)) },
                            onBookDetails = { selected = it },
                            onSearch = { navController.navigate(Routes.SEARCH) },
                    onMusic = { navController.navigate(Routes.MUSIC) },
                            onCollections = { navController.navigate(Routes.COLLECTIONS) },
                        )
                    }
                    Box(Modifier.weight(0.58f)) {
                        selected?.let { id ->
                            BookDetailsScreen(
                                bookId = id,
                                onBack = { selected = null },
                                onRead = { navController.navigate(Routes.reader(it)) },
                                onEdit = { navController.navigate(Routes.editBook(it)) },
                                onOpenLocation = { bookId, location ->
                                    navController.navigate(Routes.readerAt(bookId, location))
                                },
                            )
                        }
                    }
                }
            } else {
                LibraryScreen(
                    onOpenBook = { navController.navigate(Routes.reader(it)) },
                    onBookDetails = { navController.navigate(Routes.bookDetails(it)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onMusic = { navController.navigate(Routes.MUSIC) },
                    onCollections = { navController.navigate(Routes.COLLECTIONS) },
                )
            }
        }

        composable(Routes.NOTES) {
            NotesScreen(
                onOpenLocation = { bookId, location ->
                    navController.navigate(Routes.readerAt(bookId, location))
                },
                onOpenDrawing = { navController.navigate(Routes.drawnNote(it)) },
            )
        }

        composable(Routes.STATS) { StatsScreen() }

        composable(Routes.SETTINGS) {
            SettingsScreen(onNavigate = { navController.navigate(it) })
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.bookDetails(it)) },
                onOpenLocation = { bookId, location ->
                    navController.navigate(Routes.readerAt(bookId, location))
                },
            )
        }

        composable(
            route = Routes.BOOK_DETAILS,
            arguments = listOf(navArgument(Routes.BOOK_ID) { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong(Routes.BOOK_ID) ?: return@composable
            BookDetailsScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onRead = { navController.navigate(Routes.reader(it)) },
                onEdit = { navController.navigate(Routes.editBook(it)) },
                onOpenLocation = { id, location -> navController.navigate(Routes.readerAt(id, location)) },
            )
        }

        composable(
            route = Routes.EDIT_BOOK,
            arguments = listOf(navArgument(Routes.BOOK_ID) { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong(Routes.BOOK_ID) ?: return@composable
            EditBookScreen(bookId = bookId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument(Routes.BOOK_ID) { type = NavType.LongType },
                navArgument("location") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val bookId = entry.arguments?.getLong(Routes.BOOK_ID) ?: return@composable
            val jump by entry.savedStateHandle.getStateFlow<String?>(Routes.READER_JUMP, null)
                .collectAsStateWithLifecycle()
            ReaderScreen(
                bookId = bookId,
                startLocation = entry.arguments?.getString("location"),
                onBack = { navController.popBackStack() },
                onOpenDrawnNote = { navController.navigate(Routes.drawnNote(it)) },
                jumpTo = jump,
                onJumpHandled = { entry.savedStateHandle[Routes.READER_JUMP] = null },
            )
        }

        composable(
            Routes.DRAWN_NOTE,
            arguments = listOf(navArgument(Routes.NOTE_ID) { type = NavType.LongType }),
        ) { entry ->
            app.folio.ui.screens.drawing.DrawnNoteScreen(
                noteId = entry.arguments?.getLong(Routes.NOTE_ID) ?: return@composable,
                onBack = { navController.popBackStack() },
                onOpenInBook = { bookId, location ->
                    // Opened from that book's reader: hand it the drawing's place and go back to it.
                    if (navController.previousBackStackEntry?.destination?.route == Routes.READER) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.READER_JUMP, location)
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.readerAt(bookId, location))
                    }
                },
            )
        }

        composable(Routes.MUSIC) {
            app.folio.ui.screens.music.MusicLibraryScreen(
                onBack = { navController.popBackStack() },
                onEditTrack = { navController.navigate(Routes.trackEdit(it)) },
                onOpenCollection = { navController.navigate(Routes.musicCollection(it)) },
            )
        }

        composable(
            Routes.TRACK_EDIT,
            arguments = listOf(navArgument(Routes.TRACK_ID) { type = NavType.LongType }),
        ) { entry ->
            app.folio.ui.screens.music.TrackEditScreen(
                trackId = entry.arguments?.getLong(Routes.TRACK_ID) ?: return@composable,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.MUSIC_COLLECTION,
            arguments = listOf(navArgument(Routes.COLLECTION_ID) { type = NavType.LongType }),
        ) { entry ->
            app.folio.ui.screens.music.MusicCollectionScreen(
                collectionId = entry.arguments?.getLong(Routes.COLLECTION_ID) ?: return@composable,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.COLLECTIONS) {
            CollectionsScreen(
                onBack = { navController.popBackStack() },
                onOpenCollection = { navController.navigate(Routes.collection(it)) },
                onOpenSmart = { navController.navigateTop(Routes.LIBRARY) },
                onCategories = { navController.navigate(Routes.CATEGORIES) },
                onTags = { navController.navigate(Routes.TAGS) },
                onQueue = { navController.navigate(Routes.QUEUE) },
                onTrash = { navController.navigate(Routes.TRASH) },
            )
        }

        composable(
            route = Routes.COLLECTION_DETAIL,
            arguments = listOf(navArgument(Routes.COLLECTION_ID) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.COLLECTION_ID) ?: return@composable
            CollectionDetailScreen(
                collectionId = id,
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.reader(it)) },
            )
        }

        composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.TAGS) {
            TagsScreen(
                onBack = { navController.popBackStack() },
                onOpenTag = { navController.navigateTop(Routes.LIBRARY) },
            )
        }

        composable(Routes.QUEUE) {
            QueueScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(Routes.reader(it)) },
            )
        }

        composable(Routes.TRASH) { TrashScreen(onBack = { navController.popBackStack() }) }

        composable(Routes.POMODORO) {
            PomodoroScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Routes.POMODORO_SETTINGS) },
            )
        }

        composable(Routes.THEMES) { ThemePickerScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.READER_SETTINGS) { ReaderDefaultsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.LIBRARY_SETTINGS) { LibrarySettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.POMODORO_SETTINGS) { PomodoroSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) { NotificationSettingsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.STORAGE) { StorageScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ACCESSIBILITY) { AccessibilityScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.BACKUP) {
            BackupScreen(
                onBack = { navController.popBackStack() },
                onReset = { navController.navigateTop(Routes.HOME) },
            )
        }
    }
}
