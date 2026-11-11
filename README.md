# Auxio Companion (Wear OS)

A Wear OS remote for [Auxio](https://github.com/OxygenCobalt/Auxio), styled after Spotify's watch app.
Auxio itself is **not modified**.

```
 Wear OS watch                    Android phone
┌──────────────┐  Data Layer   ┌────────────────────┐  MediaBrowser /   ┌───────┐
│  :wear       │ ────────────▶ │  :phone (bridge)   │  MediaController  │ Auxio │
│  Compose UI  │ ◀──────────── │  Play Services     │ ────────────────▶ │       │
└──────────────┘   :protocol   └────────────────────┘ ◀──────────────── └───────┘
```

Why a bridge: Auxio's `AuxioService` is a `MediaBrowserService` with a full library tree, search, queue and a
`MediaSession` (that's what Android Auto uses), but a watch is a different device and can't bind to it. The Wear
Data Layer needs Google Play Services, which a FOSS app like Auxio shouldn't take on, so the phone half lives in
its own tiny app that talks to Auxio exactly like Android Auto does.

## Build
Open this folder in Android Studio. It mirrors Auxio's AGP/Kotlin versions.

- `:phone` → install on the phone
- `:wear`  → install on the watch
- Both share `applicationId org.oxycblt.auxio.companion` and must be signed with the **same key**
  (debug builds on one machine are). The Data Layer ignores apps that differ.

Then: install Auxio, open it once, open **Auxio Companion** on the phone (diagnostics screen), open **Auxio** on the watch.
Works with Auxio release and its `.debug` build.

## Screens
- **Home**: Search + Library pills, Shuffle all, now-playing row, playlists (or albums)
- **Library / lists**: tabs → albums/artists/genres/playlists → songs, paged, with covers; Play button on containers
- **Player** (vertical pager): cover + bezel progress + clock → controls + seek + crown volume → queue with shuffle/repeat/sleep timer
- **Search**: watch keyboard/voice → Auxio's own search engine

## Not verified: read this
Written without an Android SDK, Kotlin compiler or network, so **it has never been compiled or run**. Expect a few
compile errors to fix on first build (most likely: Wear Compose / icon import names, library version pins in the root
`build.gradle`). Things I'm least sure of at runtime:
1. Whether Auxio starts playback when the bridge triggers it while Auxio's process is cold on Android 12+
   (foreground-service background-start rules). If play does nothing on a cold start, open Auxio once first.
2. `RemoteInputIntentHelper` (watch search input) API shape in `wear-input` 1.1.0.
3. Sleep timer uses an inexact alarm: can fire a few minutes late in Doze.
4. The bridge parses Auxio's compact ids (`mct*`) for tab icons only; if Auxio changes them, icons degrade to generic.

## Known gaps / ideas
No tile or complication, no ambient mode, no previous-queue items, library changes aren't live-pushed to the watch.
Accent colour: `Palette.Accent` in `wear/.../ui/Theme.kt`.

## CI
`.github/workflows/companion.yml` builds both debug APKs on every push/PR (and on
demand via *Actions → Wear OS Companion Build → Run workflow*). Download `Auxio_Companion_Phone` and
`Auxio_Companion_Wear` from the run's artifacts. Install both from the **same run**: each run generates a fresh debug
key, and the Data Layer only links apps signed with the same key. On compile failure the logs show the errors and
the Gradle reports are uploaded.

## Theme sync
Auxio stores its accent in private preferences no other app can read, so the colour is chosen once in the phone
app ("Watch accent color", same 16 accents + Dynamic as Auxio). The watch picks it up within a few seconds and
remembers it. "Dynamic" follows the phone's Material You colour live. Making this fully automatic would need a small
patch to Auxio itself (e.g. publishing its accent in the media browser root extras).
