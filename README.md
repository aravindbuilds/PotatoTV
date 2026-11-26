# PotatoTV

An Android media browser and player built with Kotlin, Jetpack Compose and Media3/ExoPlayer.
PotatoTV opens third-party sites in a built-in browser, blocks ads and pop-ups, detects HLS/DASH/progressive streams and can play them in a native player. It does **not** host, store, index or distribute any video.

> PotatoTV is an independent app. It is not FMHYPlay and does not ship FMHYPlay's code or native libraries.

## Downloads

| Build | Orientation | Release |
| --- | --- | --- |
| Mobile | Phone portrait (browser rotates for fullscreen site video) | [v0.01-mobile](https://github.com/aravindbuilds/PotatoTV/releases/tag/v0.01-mobile) |
| TV | Android TV landscape / Leanback | [v0.01-tv](https://github.com/aravindbuilds/PotatoTV/releases/tag/v0.01-tv) |

## Features

- Cinemeta metadata (free, no API key): posters, backdrops, ratings, episodes, continue watching, My List.
- FMHY wiki sources imported on demand from the public `Streaming.md` page; title page templates from the baked local template list. Only sites that can open the exact title (TMDB id) are offered for playback.
- Site ranking: your favourites and frequently opened sites first, then FMHY 🌟 picks and 10/10 graded sites, with wiki order as the tie-breaker.
- Built-in ad blocker: 158k-domain blocklist, pop-up/redirect handling, ad-container hiding, dark pages and a D-pad cursor mode.
- Live TV via iptv-org, plus your own M3U / Xtream playlists. No 18+ playlists are bundled.
- TV intelligence layer: channel identity/deduplication, field-level metadata provenance, XMLTV EPG (India enabled by default), Now/Next, full TV guide, programme search, language/genre filters and locally tracked popularity sorting. See [docs/IPTV_INTELLIGENCE.md](docs/IPTV_INTELLIGENCE.md).
- Native HLS / DASH / progressive player with quality, speed, subtitles, audio and track selection. Detected streams are manual — the site's own player keeps playing unless you choose ours.
- Simkl scrobbling, OpenSubtitles, GitHub update checker, and an honest HTTP/SOCKS5 proxy option (no bundled WireGuard helper).

## Build

```bash
./gradlew assembleMobileDebug   # app/build/outputs/apk/mobile/debug/app-mobile-debug.apk
./gradlew assembleTvDebug       # app/build/outputs/apk/tv/debug/app-tv-debug.apk
```

Requires Android SDK 35 and JDK 17.

## Legal

PotatoTV is a browser and player only. Sites, playlists and the content they serve are provided by third parties. Use it only for content you have the right to watch.
