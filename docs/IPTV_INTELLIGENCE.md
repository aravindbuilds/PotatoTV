# IPTV Metadata & EPG Intelligence Layer

## 1. Architecture assessment (what was actually in the repo)

PotatoTV is an **offline-first Android TV / mobile client**, not a server application:

- Kotlin + Jetpack Compose, two product flavours (`tv`, `mobile`), Media3/ExoPlayer playback.
- Persistence is `DataStore<Preferences>` plus JSON files under `filesDir`; **no SQL database and no backend API existed**.
- IPTV ingestion was `M3uParser` (basic `tvg-id`/`tvg-name`/`tvg-logo`/`group-title`), `XtreamClient`, and `IptvRepository` (iptv-org channels + streams, 24 h cache).
- Favourites are `Set<String>` of channel ids; stream health is a "working channels" set.
- UI used focus-aware pills/cards and `isCompactScreen()` for phone portrait.

Therefore the intelligence layer was added as a **modular local Kotlin layer**, not a new framework or a server. The "unified API" is the repository interface `TvIntelligenceRepository`, consumed by the Compose UI. Nothing private (playlist URLs, Xtream credentials) leaves the device through this layer.

## 2. What was implemented

### Ingestion (`data/tv/M3uPlaylistParser.kt`)
- `#EXTM3U x-tvg-url`, `#EXTINF`, `#EXTGRP`, `#EXTVLCOPT`, `#KODIPROP`.
- Parses `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`, `tvg-language`, `tvg-country`, `tvg-url`, `tvg-chno`, quality, and preserves all provider-specific attributes.
- Every entry records original name, normalized name, stream URL, provider reference, import time and headers.
- Duplicate URLs are collapsed; different URLs are kept as alternate sources. A stream URL is never a channel identity.
- All playlist kinds (M3U URL, pasted M3U, Xtream, single stream) now flow into the intelligence repository.

### Identity & matching (`ChannelIdentity.kt`, `ChannelMatcher.kt`)
- Unicode NFKC/NFD normalisation, punctuation/quality-suffix stripping, abbreviations, country/language prefixes.
- Priority: manual override → exact external/tvg id → exact name → alias → broadcaster → fuzzy text; logo is supporting evidence only.
- Country/language/quality-feed conflicts reduce confidence; ambiguous HD/SD or regional matches are **not** auto-merged, they go to review.
- Thresholds are configurable via `MatchConfig`; overrides and separations are persisted.

### Metadata (`IptvOrgProvider.kt`, `TvIntelligenceRepository.kt`)
- Adapter interface `MetadataProvider`; iptv-org adapter consumes channels, feeds, logos, categories, languages, countries and timezones with a bounded, conditional, 24 h disk cache.
- Field-level provenance records source, source id, retrieval time, verification and confidence. Verified provider fields are not overwritten by weaker playlist claims.
- Canonical channels keep feeds, alternative names, logos, descriptions, websites, broadcaster/owners, language/region/timezone, quality **claims**, all original names, provider streams and every playlist ref.
- Uncertain matches are stored in `reviews.json`; manual mapping/merge/separate APIs exist.

### EPG (`EpgXmlParser.kt`, `EpgRepository.kt`, `EpgQueries.kt`)
- Streaming, pull-based XMLTV tokenizer: no DOM, no whole-file string, bounded element size. DTD/ENTITY declarations are rejected (XXE-safe).
- Handles channel ids/display names/icons and programme titles, subtitles, descriptions, categories, credits, `xmltv_ns` and on-screen episode numbers, ratings, artwork, `previously-shown` and `live`.
- Timestamps (compact `yyyyMMddHHmmss ±HHMM` and ISO 8601) are normalised to UTC while the original offset is retained.
- Quality checks: invalid timestamps, negative durations, duplicates, overlaps, gaps > 6 h, stale sources. Missing programmes are never fabricated.
- Conditional fetches (ETag / Last-Modified), gzip streamed decompression, failure counters and stale detection. Snapshot JSON is written atomically.
- Indexed Now/Next with binary search, short-lived cache, date-range schedules and programme search/classification from real EPG metadata only.

### UI (`ui/iptv/`)
- New **Guide** tab: TV grid with channels vertical, proportional programme blocks, current programme highlighted, shared horizontal/vertical scrolling, programme and channel details; phone portrait uses a now/next list.
- Channel cards now show current programme, progress, next programme, language and a details action.
- **Language** and **Genre** filters are only shown when data exists.
- Sort modes: **Most watched** (local view counts), **Recently watched**, and **A–Z**. Popularity is explicitly local: no provider supplies it.
- Channel detail dialog: description, broadcaster, languages, feeds, quality claims with verification caveat, upcoming schedule, favourite/play.
- Programme detail dialog: times, duration, category, season/episode, artwork, live/previously-shown, watch channel.

## 3. Provider evaluation (verified, not assumed)

| Source | Status | What is integrated | Notes |
| --- | --- | --- | --- |
| iptv-org API (`channels`, `feeds`, `logos`, `categories`, `languages`, `countries`, `timezones`) | **Live, integrated** | Channel identity, aliases, network/owners, catalogues, logo, region/language/timezone, categories | Public CC0-style catalogue; 24 h cache; no auth. |
| iptv-org `guides.json` | **Live dataset, unusable for schedules today** | Adapter-ready; read but not scheduled | 180,680 channel→site mappings were downloaded and inspected: **all `sources` arrays were empty**, so no XMLTV URLs are currently published. No endpoints were invented. |
| iptv-org/epg repository | Investigated | Not integrated | It is a Node grabber toolkit, not a hosted guide. Its `GUIDES.md` currently lists hosts with red status / 0 channels. Its per-site channel configs use `xmltv_id=""` for Indian channels such as JioTV, so it cannot supply matching IDs here. |
| iptv-epg.org per-country XMLTV | **Live, integrated by default (India)** | Full schedules | `https://iptv-epg.org/files/epg-in.xml.gz` verified: ~2.95 MB gzip / ~39.7 MB XML, channel ids such as `Asianet.in` match the iptv-org catalogue. Updated several times daily. gz fetched with `Accept-Encoding: identity` and streamed. |
| epgshare01.online | Available, optional | Disabled source template | `epg_ripper_IN1.xml.gz` verified reachable (~3.7 MB). IDs differ from iptv-org; enable only if a playlist uses its IDs. FAQ states free EPG for legal use only. |
| mitthu786/tvepg (JioTV/Tata/ZEE5/etc.) | Available, not enabled | User-configurable URL only | Public XMLTV links exist but are educational/community resources behind short links; no default integration to avoid unstable third-party dependencies. |
| Broadcaster websites | Investigated, not integrated | None | No stable public, authorised, licence-clear XMLTV API found for Indian regional broadcasters. No scraping or auth bypass attempted. |

Custom EPG URLs can be added through `EpgRepository.setSources` (UI entry point is the next iteration).

## 4. Persistence model (adapted to the existing app)

No Room/PostgreSQL server was introduced. Files under `filesDir/tvintel/`:

- `entries.json` — original playlist entries (source of truth for reprocessing).
- `channels.json` — canonical channels with feeds, streams, provenance and EPG ids.
- `overrides.json` — manual mappings and separations.
- `reviews.json` — uncertain matches for review.
- `epg/snapshot-<source>.json`, `epg/status.json` — normalised schedules and source health.

DataStore holds favourites, working flags and the new local channel view counts. Writes are atomic (temp file + rename); all repository mutations are mutex-protected.

## 5. Testing

JVM tests live in `app/src/test/java/app/potatotv/tv/`:

- `M3uPlaylistParserTest` — attribute ordering, missing metadata, duplicates, malformed/unsafe URLs, 5,000-entry playlist.
- `ChannelMatcherTest` — exact id/name/alias, HD/SD ambiguity, language-feed separation, manual override, unrelated channels, normalisation.
- `XmlTvParserTest` — field extraction, CDATA/entities, UTC conversion, midnight, invalid/negative/duplicate programmes, XXE rejection, Now/Next, classification.
- `SafeUrlsTest` — SSRF/loopback/private blocking, player schemes, credential redaction.

Run: `gradlew testTvDebugUnitTest`.

## 6. Limitations / next priorities

1. EPG source management UI and an admin coverage/match-review screen are not yet surfaced (repository methods exist).
2. iptv-org `guides.json` has no published source URLs at present; no schedule can be fetched from it until the upstream dataset changes.
3. Popularity is local viewing count only; no cross-device or provider popularity data is invented.
4. Stream health checks reuse the existing conservative "Check working" action; health is not yet written back into `StreamHealth` per source.
5. Programme artwork/cast enrichment beyond XMLTV is not integrated; the schema is ready for it.
6. The EPG grid currently renders up to 80 channels and a 6-hour window; pagination/time-window navigation is a next step.
