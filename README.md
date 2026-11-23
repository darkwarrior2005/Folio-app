# Folio

An offline-first personal reading library for Android: e-books, PDFs, research papers, manga and
comics in one place, with reading progress, annotations, organisation, statistics and a Pomodoro
timer — all stored on the device.

**Offline by default.** Books, PDFs, comics, music, notes and statistics never touch the network.
The only parts that do are the optional manga sources and "From the web" features (below), which
both start switched off; until you turn one on Folio opens no connection. No account, no server, no analytics, no crash
reporting, and Android backup is disabled.

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
| Manga sources | Mihon/Tachiyomi extension API, OkHttp, Injekt, QuickJS | Runs the same extension APKs Mihon does, from the same repositories |
| Manga pages | Android's decoder, then [`ca.mpreg:imagedecoder`](https://github.com/mpreg-ca/imagedecoder) | AVIF, JPEG XL and HEIF pages that phones often cannot decode themselves |

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
├── manga/            Source registry, extension loader/installer/repositories, downloads,
│                     global search, chapter loader for streaming
├── pomodoro/         Timer engine, foreground service, notifications, reminders
└── ui/               Compose theme, components, screens, navigation

app/src/main/java/eu/kanade/tachiyomi/
                      The extension API (source, network, util), under the package names
                      extensions are compiled against, so Mihon extensions load unchanged
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

## Manga sources and Mihon compatibility

Folio can browse, search, read and download manga from online sources using **Mihon extensions**:
the same APKs, from the same extension repositories (both `index.min.json` and Mihon 0.20+
`index.pb` indexes). Extensions are written for Mihon's runtime, so Folio reproduces that runtime
rather than inventing its own, and where Mihon decides what to do with a source's data, Folio uses
Mihon's own code.

**What Folio does exactly as Mihon does**

| Step | How |
| --- | --- |
| Loading extensions | Extension lib 1.4 and 1.6, signature trust, the extension's own classes first (delegate-last class loader), source factories |
| What extensions are given | Injekt with `Application`, `NetworkHelper`, the lenient `Json` Mihon provides (`explicitNulls = false`), `ProtoBuf`, `JavaScriptEngine` (QuickJS), `AppInfo`, per-source `SharedPreferences` |
| Network | Same timeouts, 5 MiB response cache, cookie jar shared with WebView, user-agent interceptor, Cloudflare interceptor with matching WebView client hints |
| Chapter lists | Mihon's sync rules: an empty list is refused rather than wiping your chapters, duplicate URLs are dropped, names are cleaned (`ChapterSanitizer`), missing chapter numbers are recognised from the names (`ChapterRecognition`, ported with Mihon's 47 tests), undated chapters get a sensible date, a chapter back under a new URL keeps its read and bookmark marks, and `prepareNewChapter` is called |
| Manga details | Empty fields never overwrite stored ones; the source's `memo` is stored and handed back |
| Pages | Image links resolved when missing or empty, several pages fetched ahead in parallel |
| Covers | Fetched through the source's own client and headers, so sites that block outside image requests still show them |
| Source settings | The extension's own settings screen (`ConfigurableSource`), opened from Browse, the catalogue or the installed extension |
| Sites that need a person | "Open the site" opens the source's website with the same user agent and cookies, so a Cloudflare check, a captcha or a login done there carries over |

**What Folio adds on top**, none of which changes how extension data is handled:

- One search across every installed source from the Library's Manga tab, with sources you can
  leave out, a per-source "Try again" and "Open the site"
- A language picker that filters extensions, Browse and that search
- An on/off switch per installed extension, and a fingerprint or screen-lock check before the
  Extensions screen opens
- Chapter multi-select and downloads by rule (next N unread, unread, a number range)
- Readable errors: a failing page or chapter says why, with Try again

**Privacy**

- Search terms are never saved, and search fields are not kept in saved app state.
- An incognito keyboard (on by default) asks the keyboard not to learn from anything typed in Folio.
- On each start (on by default) Folio forgets the browsing trail: manga only looked at, cached site
  responses and cached covers. Your library and downloads stay.
- The "Open the site" browser clears its cache, history, form data and site storage when it
  closes, keeping only cookies (they hold a passed Cloudflare check). Settings → Privacy and lock →
  "Clear browsing data now" can also sign you out of every source website.

**When a source fails**

1. Read the message: Folio shows the source's own error.
2. If it mentions WebView, cookies, a captcha or a login, or the site refused the request, tap
   **Open the site**, complete what it asks, and go back. The source tries again by itself.
3. Check the source's settings (the gear icon), and update the extension from the Extensions
   screen: sites change, and extension updates follow.
4. If it still fails, report the full error text.

## From the web

Optional, off until turned on (Library → + → From the web). Everything it fetches becomes an
ordinary book in the library and is read offline afterwards.

- **Pages:** paste or share a link from the browser; Folio keeps the readable part (text,
  headings, pictures, embedded in the book) and drops menus, comments, share bars and adverts.
- **Feeds:** RSS and Atom, added by the feed's address or the site's; checked only when opened.
  "Save and read" turns an item into a book.
- **Web novels:** give a chapter's address; that chapter and the next ten become one book, found
  by following each chapter's "Next" link. "Check for new chapters" adds what has appeared since,
  at the end of the same book, so your place, highlights and notes stay.

It uses its own connection that keeps no cookies and no cache, separate from manga sources. Pages
built entirely by scripts, and sites behind a login or a Cloudflare check, cannot be read this way.

## Implemented

- **Formats:** PDF, EPUB, CBZ, CBR, image folders, single images, TXT, Markdown, HTML, and —
  converted on the phone for the text reader — Kindle MOBI / AZW / AZW3 without DRM, FB2 (also
  zipped), Word DOCX, OpenDocument ODT and RTF, with their pictures, headings (for the table of
  contents), title, author and cover
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
- **Annotations:** bookmarks, five highlight colours, notes, a global notes hub, Markdown export;
  any highlight can be shared as a quote card (a picture of the passage on a cream page, made on
  the phone)
- **Reading comfort:** a reading ruler that dims all but a band of lines (drag its handle to move
  it), an optional soft page-turn sound and gentle tick, and "Look up" on selected text, which
  hands the word to the dictionary or translation app already on the phone (EPUB and text books
  get the system's own Define and Translate)
- **Night light:** a warm amber filter over the whole app, always on or on a schedule, with
  warmth and extra dimming; an optional bedtime nudge that offers "10 more minutes" or "Good night"
- **Cozy home:** a welcome-back card with Continue and "Surprise me" (a book from your queue or one
  you have started), and a Continue reading home-screen widget
- **Icons:** Folio's own hand-drawn icon set (sources in `docs/icons`)
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
- **From the web (optional):** saved pages, RSS/Atom feeds and web novels, as books
- **Manga online (optional):** extension repositories, install/update/uninstall, per-extension
  on/off and source settings, Browse and per-source search, search across all sources, languages,
  details and chapter lists, streaming reader, downloads (queue, Wi-Fi only, CBZ with
  ComicInfo.xml, read-ahead, delete after reading), chapter multi-select

## Known limitations

- **CBR (RAR5):** junrar cannot decode RAR5 archives. RAR4 files work; RAR5 reports a clear error
  suggesting conversion to CBZ.
- **DRM:** books locked with DRM (most Kindle store purchases, Adobe-protected EPUBs) cannot be
  opened; Folio says so instead of failing silently.
- **Converted formats:** MOBI with HUFF/CDIC compression is not read. KF8 (AZW3) text is read in
  its stored order rather than reassembled, so some publisher layout is lost; links inside MOBI
  books do not jump. DOCX and ODT keep text structure, emphasis, lists, tables and pictures, not
  page layout; footnotes in ODT are left out. Pictures beyond 16 MB per book are left out.
- **Scanned PDFs:** searching inside them needs OCR, which is not implemented, so image-only pages
  have no text to index or select.
- **Text reader:** documents are loaded whole and capped at 12 MB; images referenced by relative
  paths in a standalone HTML file will not resolve.
- **Text-to-speech:** not implemented.
- **Cloud sync:** deliberately absent.
- **Music formats:** ALAC, APE, WMA and DSD are not supported (no FFmpeg decoder bundled). Tags are
  never written back into audio files.
- **Manga sources:** extensions are built for Mihon, so one that starts relying on something new in
  Mihon's runtime may fail in Folio until that piece is added. Sites also change; those fixes arrive
  as extension updates. Not implemented: trackers (AniList, MyAnimeList…), automatic library
  updates, DNS-over-HTTPS, Shizuku installs.

## Tests

- JVM unit tests: metadata override rules, tag normalisation and autocomplete ranking, statistics
  and streak logic, and for manga: Mihon's chapter-number recognition tests, chapter sync rules,
  chapter selection, source languages, the protobuf repository index and search across sources;
  the MOBI/AZW3, FB2, DOCX, ODT and RTF converters against sample books written by an independent
  generator (`app/src/test/resources/formats/make_samples.py`); finding the article in a web page,
  next-chapter links, and RSS/Atom feeds —
  `./gradlew :app:testDebugUnitTest`
- Instrumented tests (real database on a device): position round-trip, overrides surviving a
  rescan, tags, annotations, remove/restore/purge, duplicate detection, collection ordering and a
  full backup/restore round trip — `./gradlew :app:connectedDebugAndroidTest`
