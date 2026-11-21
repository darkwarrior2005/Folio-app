# Folio 1.6.0

Everything below landed on this branch as a series of incremental commits; see the git log for the
exact order and each commit's own detailed notes. This file is the short version.

## Manga

Folio can now load the same manga extension APKs Mihon/Tachiyomi uses, unmodified. Off by default
(Settings → Manga sources); nothing in this area touches the network until that switch is on.

- The `eu.kanade.tachiyomi.*` extension API (source types, network helpers, Cloudflare bypass).
- Extension loading, signing-key trust, repositories (`index.pb`/`index.min.json`/`repo.json`),
  install/uninstall through the system installer.
- A manga library: browse, search, library tab inside Books, chapter list with read/bookmark
  state, a reader, chapter downloads for offline reading.
- "Open the site" lets you pass a Cloudflare check, login or captcha once in a real WebView when
  it can't be solved automatically.

**Simplified from the original plan, by choice:**
- `SourceSettingsScreen` (a source's own preferences) is a stub - it needs a `PreferenceFragmentCompat`
  bridged into this Compose nav graph, left as a follow-up.
- Folio's own icon set was not built (no source icon-sheet art was available); manga and web
  screens use Material icons instead.
- The Library screen's FAB is not yet a multi-action menu; "Find manga online" and "From the web"
  are reachable from the Manga tab's own FAB and from Settings respectively, for now.
- EPUB/text annotations were **not** removed in favor of PDF-only, unlike the original plan - that
  would have deleted working functionality without being asked to.

## From the web

Settings → From the web, off by default. Save a page, follow an RSS/Atom feed, or read a web
novel chapter by chapter - each becomes an ordinary offline HTML book.

## New document formats

MOBI, AZW3, FB2, DOCX, ODT and RTF are converted to HTML on import and open in the existing text
reader, so highlights, search, and the TOC all work on them unchanged. See the README's "Known
limitations" for what each converter doesn't handle.

## Reading comfort and night light

- A reading ruler, a synthesized page-turn tick with haptics, dictionary look-up, and shareable
  quote cards, all in the reader's selection toolbar or Settings → Reader → Behavior.
- A scheduled night-light tint with warmth/dim sliders and a bedtime reminder card
  (Settings → Night light).
- A "Welcome back" card on the home screen resumes where you left off, with a "Surprise me" option,
  plus a Continue Reading home-screen widget.

## Privacy

- Manga browsing you never favorited (nothing downloaded or read) is forgotten automatically on
  each app start, with network caches and cookies optionally cleared too
  (Settings → Privacy → Searching and browsing, including "Clear browsing data now").
- An incognito-keyboard composition local is in place; wiring every text field to honor it (ideally
  via Compose's `InterceptPlatformTextInput`) is a follow-up rather than something guessed at
  without a way to verify it compiles and works here.

## Project-wide

- Version 1.6.0 (versionCode 16).
- Room database version 4 → 5 (manga tables), by auto-migration.
- New dependencies: OkHttp, RxJava 1, Injekt, kotlinx-serialization json-okio/protobuf, QuickJS
  (via a JitPack allowlist scoped to just that group), an AVIF/JPEG XL/HEIF image decoder, AndroidX
  Preference, Coil's OkHttp network fetcher.
- `AndroidManifest.xml`: INTERNET/ACCESS_NETWORK_STATE granted (gated by settings, see above),
  extension install/uninstall permissions, new Open-with/Share MIME types, a FileProvider for quote
  cards, a widget receiver.
- CI (`release.yml`): a manual run can now create a tag at the built commit and publish a release
  there, via a `release_tag` input.

## Not verified by a build in this environment

This branch's Gradle build could not be run end-to-end here: the sandbox's network couldn't resolve
the Android Gradle Plugin artifact (a pre-existing environment limitation, not something this work
introduced). Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` before
relying on this branch, and expect to fix compile errors - a port this size, written without a
working build loop to check against, will have some.
