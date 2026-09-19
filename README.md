# Folio

An offline-first personal reading library for Android: e-books, PDFs, research papers, manga and
comics in one place, with reading progress, annotations, organisation, statistics and a Pomodoro
timer — all stored on the device.

**The app has no Internet permission at all.** It is stripped from the merged manifest, so Android
itself prevents the app from making a network request. No account, no server, no analytics.

## Building

```bash
./gradlew :app:assembleDebug
```

Requires a JDK 17+ (Android Studio's bundled JDK works) and an Android SDK with API 37.
`local.properties` points at the SDK.

Install on a connected device:

```bash
./gradlew :app:installDebug
```

## Technology

| Area | Choice | Why |
| --- | --- | --- |
| UI | Kotlin + Jetpack Compose, Material 3 | One toolkit for phone, tablet and foldable layouts |
| Database | Room (SQLite) with FTS4 | Transactional local storage plus full-text search in books |
| EPUB | [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) | Exact reading positions (locators), decorations for highlights, in-book search |
| PDF | PDFium (`io.legere:pdfiumandroid`) + a custom Compose renderer | Page rendering, text extraction, text selection geometry, margin detection |
| Comics | Apache Commons Compress (CBZ), junrar (CBR), SAF folders | Pages read on demand instead of unpacking whole archives |
| Text | Custom WebView reader with its own CSS/JS | Full control over typography, pagination, highlights |
| Background work | WorkManager | Resumable text indexing and reading reminders |
| Settings | DataStore (a single JSON document) | Easy to back up and to extend |

## Architecture

```
app/src/main/java/app/folio/
├── core/model/       Pure domain logic (no Android): metadata resolution, tag normalisation,
│                     library filtering/sorting, statistics, search-query building
├── data/
│   ├── db/           Room entities, DAOs, database
│   ├── files/        SAF access, hashing, cache directories
│   ├── repo/         Repositories (library, organization, annotations, reading, search)
│   ├── settings/     AppSettings + DataStore, per-book reader prefs, PIN hashing
│   └── backup/       ZIP + JSON backup, restore and Markdown annotation export
├── importer/         Format detection, import queue, duplicate handling, text indexer
├── reader/
│   ├── api/          ReaderEngine / ReaderHost / ReaderController contracts
│   ├── epub/  pdf/  comic/  text/    One engine per format
│   └── ReaderRegistry.kt             The only place that maps a format to an engine
├── pomodoro/         Timer engine, foreground service, notifications, reminders
└── ui/               Compose theme, components, screens, navigation
```

Adding a format means writing one `ReaderEngine` and registering it. Progress, bookmarks,
highlights, notes, search and statistics are shared services that every reader gets for free.

### Metadata: the file vs. the library

A book carries two sets of metadata: what extraction found in the file, and what the user typed.
What you see is `user override ?: imported value`. Re-reading a file only rewrites the imported
set, so a rescan can never undo your edits. Editing the title never touches the file; renaming the
file is a separate, explicit action.

### Reading positions

Every reader reports a shared `BookLocation` (page, offset, chapter href, total progression, and
the Readium locator for EPUB). It is written in one transaction together with the book row,
debounced while scrolling and flushed immediately when the reader leaves the foreground, so a
force close or a dead battery cannot lose your place.

## Implemented

- **Formats:** PDF, EPUB, CBZ, CBR, image folders, single images, TXT, Markdown, HTML
- **Import:** multi-file, folder scan, "Open with", share-to-app, duplicate detection by content
  hash, background metadata and cover extraction
- **Library:** 5 layouts, grouping, combinable filters, sorting, bulk editing, long-press actions,
  categories, collections (ordered, drag to reorder), tags with normalisation and autocomplete,
  smart collections, reading queue, favourites, recently removed (restorable)
- **Readers:** PDF (continuous / paged / two-page, zoom, night mode, margin cropping, text
  selection, highlights, in-book search, thumbnails), EPUB (pagination or scroll, fonts, spacing,
  themes, selection, highlights, search, TOC), manga (LTR/RTL/vertical, double page, fits, zoom,
  per-book direction, honours ComicInfo.xml), text (typography, pagination, highlights, search)
- **Zoom:** pinch in every reader — true zoom up to 5× for PDF, comics and fixed-layout EPUB (limited
  by available memory, with a message), live text size for reflowable EPUB and text; per-book zoom;
  a draggable translucent zoom-lock button that silently disables all zoom gestures
- **Music:** offline music library (MP3, M4A/AAC, FLAC, OGG, WAV) with library-only metadata edits,
  music tags and collections; background playback with notification and lock-screen controls; link
  collections or tracks to PDF/EPUB books (loop, shuffle or a chosen selection) so the book's music
  plays while you read it; pauses during Pomodoro breaks
- **Annotations:** bookmarks, five highlight colours, notes, a global notes hub, Markdown export
- **Scribble:** draw on PDF and comic pages with a pen, a see-through highlighter (adjustable
  opacity, 10–100 %) and a stroke eraser, with undo/redo. In EPUB and text books, Draw makes a
  drawing card pinned to your place; drawings appear in the Notes hub, backups and the Markdown
  export.
- **Search:** library metadata, full text inside books (FTS index built in the background),
  and notes
- **Reading data:** sessions, statistics, streaks, goals, per-book stats
- **Pomodoro:** configurable, survives process death, foreground notification, recorded sessions
- **Themes:** 17 themes in four groups, custom accent with a contrast guard, bundled fonts
- **Settings:** appearance, reader, library, Pomodoro, notifications, storage, privacy (PIN and
  biometric lock, hide in recents), accessibility, backup/restore/reset
- **Backup:** ZIP with `library.json` and covers, optional book files, restore with missing-file
  reporting

## Known limitations

- **CBR (RAR5):** junrar cannot decode RAR5 archives. RAR4 files work; RAR5 reports a clear error
  suggesting conversion to CBZ.
- **MOBI / AZW / AZW3, DOCX, RTF:** not implemented. DRM-protected files cannot be supported.
- **Scanned PDFs:** searching inside them needs OCR, which is not implemented, so image-only pages
  have no text to index or select.
- **Text reader:** documents are loaded whole and capped at 12 MB; images referenced by relative
  paths in a standalone HTML file will not resolve.
- **Text-to-speech and dictionary lookup:** not implemented.
- **Cloud sync:** deliberately absent.
- **Music formats:** ALAC, APE, WMA and DSD are not supported (no FFmpeg decoder bundled). Tags are
  never written back into audio files.

## Tests

- JVM unit tests: metadata override rules, tag normalisation and autocomplete ranking, statistics
  and streak logic — `./gradlew :app:testDebugUnitTest`
- Instrumented tests (real database on a device): position round-trip, overrides surviving a
  rescan, tags, annotations, remove/restore/purge, duplicate detection, collection ordering and a
  full backup/restore round trip — `./gradlew :app:connectedDebugAndroidTest`

  ## Note
- This is Application is fully made using AI (claude code).
