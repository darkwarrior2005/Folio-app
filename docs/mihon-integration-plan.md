# Mihon-style features for Folio: study and plan

Reference: https://github.com/mihonapp/mihon (commit ab7ee00, Apache License 2.0).
The source API, manga library, extension loader and downloads are now implemented;
see the git history on this branch. The UI screens are not.

**None of it has been compiled.** The cloud session it was written in cannot reach
`dl.google.com`, so the Android Gradle Plugin cannot be downloaded and no Gradle task
can run there. The pure chapter-sync logic was compiled and its 8 unit tests pass;
everything that touches Android, Room, OkHttp or Compose is unverified. Run
`./gradlew :app:assembleDebug test` locally first and expect to fix compile errors.

Also note: `app/schemas/app.folio.data.db.FolioDatabase/4.json` is missing from git, which
auto-migration 4 -> 5 and the existing MigrationTest both need. If a build complains about a
missing schema for version 4, generate it by building the commit before this work and commit
the file.

## 1. Licensing
Mihon is Apache-2.0, so code can be adapted into Folio with these obligations:
- Keep the license text and add a `NOTICE`/credits entry for Mihon (and Tachiyomi, its origin).
- Keep copyright headers on copied files and mark files you changed.
- Folio's own licence can differ, but Apache-2.0 terms stay on the copied parts.
- 
## 2. How Mihon is built (what we studied)
| Area | Mihon location | Key idea |
|---|---|---|
| Source API | `source-api/` | `Source` / `CatalogueSource` / `HttpSource` / `ParsedHttpSource`. Suspend methods: `getPopularManga`, `getLatestUpdates`, `getSearchManga`, `getMangaUpdate`, `getPageList`. Models `SManga`, `SChapter`, `Page`, `FilterList`. |
| Extensions | `app/.../extension/` | Extensions are separate APKs declaring the feature `tachiyomi.extension`. `ExtensionLoader` reads them via PackageManager, checks the lib version (1.4 or 1.6), checks the signing key against a trust list (`TrustExtension`), then instantiates the source classes with a ClassLoader. Installers: PackageInstaller and Shizuku. |
| Extension repos | `mihon/domain/extension` | User-added extension stores (repo URLs) that index available APKs. |
| Local source | `source-local/` | Reads CBZ/ZIP/RAR/EPUB/folders from disk as a source. |
| Downloads | `data/download/` | `DownloadManager` (API), `Downloader` (queue, per-source jobs, `Semaphore` for parallel pages, `.tmp` files then rename, optional CBZ archiving, `ComicInfo.xml`), `DownloadStore` (persisted queue), `DownloadCache` (index of what is on disk), `DownloadProvider` (directory naming), `DownloadWorker` (WorkManager, network constraints). |
| Offline reading | reader | The reader first checks for a downloaded chapter dir/CBZ and falls back to the source's `getPageList`. |

## 3. Mapping onto Folio
Folio today: formats EPUB/PDF/comic/text under `reader/*` behind `reader.api.ReaderApi`; importer for local files; Room DB `FolioDatabase` (v3); settings in `data.settings`; WorkManager already used (`IndexWorker`, `ReminderWorker`).

Proposed new packages:
- `source/api` : trimmed copy of the source API (suspend only, no RxJava).
- `source/runtime` : `SourceManager`, `ExtensionLoader`, trust list, repo index fetcher.
- `data/download` : `DownloadQueue`, `Downloader`, `DownloadWorker`, store/cache. Downloads land in the same layout the comic engine already reads (CBZ), so offline reading reuses `reader.comic.ComicEngine`.
- UI: Browse (sources/extensions), Downloads queue.

## 4. Suggested order
1. Attribution + docs (this file, NOTICE).
2. Source API + one built-in source (local) + SourceManager.
3. Downloads + offline (works against any source, testable with a fake source).
4. External extensions loader (highest risk, do last).

## 5. Decisions
- **Package names (decided):** keep Mihon's `eu.kanade.tachiyomi.source` API so existing Mihon extensions load.
- **Extension repos (decided):** no default repo. Folio ships empty and users add their own repo URLs. Distribution is outside the Play Store (sideloaded APK), so Play policy does not apply.
- **Tracking (AniList, MAL, etc.):** removed from scope entirely at the user's request.

## 6. Separate Manga library (decided)
Manga lives in its own library, apart from the existing book library (EPUB/PDF/text). It holds everything manga-related:
- manga saved from sources (title, cover, chapters, read state), whether or not downloaded;
- downloaded chapters (CBZ on disk) for offline reading, plus the download queue;
- local manga imported from files.
Implementation: new Room tables (`manga`, `manga_chapter`, `manga_download`) in a DB migration 3 to 4, a `MangaRepository`, and its own Manga tab/screens. Existing book tables and screens stay untouched, and manga is read through the existing comic reader.

## 7. Risks
- **Extension security:** loading third-party APKs runs arbitrary code in Folio's process. Keep signature trust checks and a clear warning before first use.
- **Reuse vs rewrite:** Mihon uses Injekt/Koin-style DI, RxJava remnants and Voyager; Folio uses a manual `AppContainer`. Port logic, not wiring.
