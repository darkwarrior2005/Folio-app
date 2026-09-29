# Folio

> 🤖 **Made with AI.** Folio was designed and written entirely with AI, using
> [Claude Code](https://claude.ai/code) (Anthropic). The code, the tests, the build setup, the release
> workflow and this README were all produced by AI from the owner's instructions.

Folio is an **offline-first personal reading library for Android**. It keeps your e-books, PDFs,
research papers, manga, comics and plain-text documents in one place, and adds the things a serious
reader wants around them: reading progress that is never lost, highlights and notes, drawing on
pages, organisation with collections and tags, reading statistics, a Pomodoro focus timer and even
background music that plays while you read — all stored on your own device.

## Purpose

Most reading apps handle one format well, need an account, or send your reading habits to a server.
Folio exists to be the opposite:

- **One app for everything you read** — novels (EPUB), papers and documents (PDF), manga and comics
  (CBZ/CBR/image folders) and notes (TXT/Markdown/HTML) share one library, one search and one set of
  annotations.
- **Private by construction** — no account, no cloud, no analytics, no ads. The Internet permission
  is removed from the app, so Android itself blocks any network access.
- **Built for focus** — a distraction-free reader, a Pomodoro timer, reading goals and streaks, and
  a per-book soundtrack help you actually sit down and read.
- **Your data stays yours** — everything can be backed up to a single ZIP file and restored on another
  device, and annotations can be exported as Markdown.

## Highlights

| | |
| --- | --- |
| 📚 **Many formats** | PDF, EPUB, CBZ, CBR, image folders, images, TXT, Markdown, HTML |
| 🗂️ **Organisation** | Categories, ordered collections, tags, smart collections, reading queue, favourites |
| 🏠 **Bookshelf home** | Your collections shown as real shelves with covers and spines |
| 🖍️ **Annotations** | Bookmarks, five highlight colours, notes, a notes hub, Markdown export |
| ✏️ **Scribble** | Pen, highlighter and eraser on PDF and comic pages, with undo/redo |
| 🔍 **Search** | Library metadata, full text inside books, and your notes |
| 🎵 **Reading music** | Offline music player; link playlists to books or whole collections |
| ⏱️ **Pomodoro** | Focus timer that survives the app being killed, with recorded sessions |
| 📊 **Statistics** | Reading sessions, streaks, goals and per-book stats |
| 🎨 **Themes** | 17 themes, custom accent colour, bundled reading fonts |
| 🔒 **Privacy** | No Internet, optional PIN/biometric lock, hide from recent apps |
| 💾 **Backup** | ZIP backup and restore, optionally including the book files |

## Requirements

### To use the app

- An Android phone, tablet or foldable running **Android 8.0 (API 26) or newer**
  (the app targets Android API 36).
- Enough free storage for your books, music and covers. Books are opened in place through Android's
  file picker, so they are not copied unless you choose to include them in a backup.
- **No Internet connection and no account** are needed — ever.

### Permissions it asks for

| Permission | Why |
| --- | --- |
| Notifications | Pomodoro timer, reading reminders and music playback controls |
| Foreground service (special use, media playback) | Keeps the timer and the music running when the app is in the background |
| Wake lock, vibrate | Timer alerts and uninterrupted playback |
| Biometric | Optional fingerprint/face unlock for the app lock |

Files are accessed only through the Storage Access Framework, i.e. only the files and folders you
pick yourself. `INTERNET` and `ACCESS_NETWORK_STATE` are explicitly **removed** from the manifest.

### To build it from source

- **JDK 17 or newer** (Android Studio's bundled JDK works; CI uses JDK 21).
- **Android SDK with API 37** (Readium and PDFium require compiling against it).
- A `local.properties` file pointing at the SDK (Android Studio creates it for you).
- Optional, for signed release builds: a `keystore.properties` file (git-ignored) with `storeFile`,
  `storePassword`, `keyAlias` and `keyPassword`.

## Getting started

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

Install it on a connected device:

```bash
./gradlew :app:installDebug
```

Then open Folio, tap import, and pick files or a whole folder. Covers and metadata are extracted in
the background; you can start reading straight away.

### Release builds

The **Release APK** GitHub Actions workflow (`.github/workflows/release.yml`) builds a signed APK:

- Run it manually from *Actions → Release APK → Run workflow*; the APK is attached to the run.
- Push a tag such as `v1.5.0` and the APK is also published as a GitHub release.
- Signing uses the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
  `KEY_PASSWORD`. Without them the workflow still builds a debug-signed APK. Use the same keystore
  as the copy already on your phone, or Android will refuse the update.

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

## Features in detail

- **Formats:** PDF, EPUB, CBZ, CBR, image folders, single images, TXT, Markdown, HTML
- **Import:** multi-file, folder scan, "Open with", share-to-app, duplicate detection by content
  hash, background metadata and cover extraction
- **Library:** 5 layouts, grouping, combinable filters, sorting, bulk editing, long-press actions,
  categories, collections (ordered, drag to reorder), tags with normalisation and autocomplete,
  smart collections, reading queue, favourites, recently removed (restorable)
- **Home:** a bookshelf built from your collections: a "Currently reading" shelf, one shelf per
  collection with its name set into the board, and at the bottom an "Other books" shelf holding the
  books that are in no collection. Books show their real covers and spines, thickness follows page
  count. A live clock opens the focus timer and the music player sits above the shelves. The
  original sectioned home is still available as "Classic" in Settings → Library.
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
  plays while you read it; give a whole book collection a soundtrack that its books play unless they
  have music of their own; set all of this, and make new playlists, from "Reading music" on the home
  player; pauses during Pomodoro breaks
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

## About this project

Folio is a personal project built **completely with AI** using Claude Code. Every feature above was
requested in plain language and then implemented, tested and documented by the AI. As with any
software, bugs are possible — please open an issue if you find one.
