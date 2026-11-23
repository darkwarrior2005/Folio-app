# Folio 1.6.0: the manga library

Changes from `4cb4e7c` (the code released as `v1.5.0`) to the `v1.6.0` tag.

Note on numbering: the `v1.5.0` tag and release already pointed at the 1.4.0 code,
so this release takes the next free number rather than moving that tag.

Folio gains a manga library beside its book library: series fetched from sources,
downloaded for offline reading, through extensions written for Tachiyomi and Mihon.

## Summary

- **A separate manga library.** Manga is a series of chapters from a source, not a
  single file, so it has its own tables and its own screens. The book library,
  notes, music and reader are untouched.
- **Mihon-compatible sources.** The extensions-lib surface is ported from Mihon
  (Apache-2.0) under its original `eu.kanade.tachiyomi` package names, so
  extensions built for Tachiyomi and Mihon load unchanged.
- **Extensions the user chooses.** Folio ships with no repository. The user adds
  one, and an extension only runs once its signing key is accepted — per package,
  per version, so an update has to be accepted again.
- **Downloads and offline reading.** Chapters download in the background as CBZ
  with a `ComicInfo.xml`, and are read through Folio's existing comic reader.
  A chapter that is not downloaded is streamed from its source instead.
- **Offline by default, not by force.** Folio now holds the INTERNET permission,
  which it previously stripped, but every network path is gated on the "manga
  sources" setting, which starts off. With it off nothing opens a connection and
  no extension code is loaded.

## Why no Play Store

Finding installed extensions at all needs `QUERY_ALL_PACKAGES`, which Play
restricts. Folio is distributed as an APK.

## What is new

| Area | What it does |
| --- | --- |
| Manga library | Grid of added series with unread and downloaded counts, search, sort, filter |
| Browse | The installed sources, then each source's popular, latest and search listings |
| Extensions | Installed and available extensions, the trust prompt, install and uninstall |
| Repositories | Add and remove the repositories extensions come from |
| Details | Cover and details, favourite, refresh from the source, chapter list |
| Reader | A pager over a chapter's pages, downloaded or streamed, saving the page |
| Downloads | The queue, with progress and cancel |
| Settings → Manga | The sources switch, adult sources, and the download preferences |

## Database

Room goes from version 4 to 5. Five new tables, all additive, behind an
auto-migration: `manga`, `manga_chapters`, `manga_downloads`, `extension_repos`
and `trusted_extensions`. Nothing existing changes.

Version 4 shipped without its exported schema being committed, which the
auto-migration and `MigrationTest` both need. It was re-exported by rebuilding the
released code and is now in the repository.

## Known limits

- No Cloudflare bypass, so sources behind its interstitial fail.
- Covers from sources that only serve them to requests carrying their own headers
  do not load; a placeholder stands in.
- Extensions that ask Injekt for ProtoBuf, XML or a JavaScript engine fail to
  load, since only Context, Application, Json and NetworkHelper are exposed.
- Tracking (AniList, MyAnimeList and the rest) is deliberately not included.

## Credit

The source API, extension loading and download logic are adapted from
[Mihon](https://github.com/mihonapp/mihon) under the Apache License 2.0. See
`NOTICE` for the attribution and the list of adapted files.
