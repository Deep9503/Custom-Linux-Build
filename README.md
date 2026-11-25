# CineDrive

A dark, cinema-style Android app for **legal** video, with an audio-only Android Auto mode.

## Sources
- **My Library** – your own movies, shows and music from a Jellyfin server (sign in under Settings).
- **Free Classics** – public-domain films, cartoons and TV from the Internet Archive.

New sources plug in through the `ContentProvider` interface (`providers/Models.kt`). Only add
sources you have the right to use.

## Features (v0.1)
- Home: featured banner, Continue Watching, rows per category
- Search (both sources, recent searches), details page, episodes
- Player (Media3): HLS/DASH/MP4, subtitles, audio tracks, speed, ±10 s, resume,
  next episode, full-screen landscape, picture-in-picture, auto-fallback to direct file
- My List and watch progress (Room)
- Android Auto: standard media UI – Continue (as audio), My Music (Jellyfin),
  Old-Time Radio, Audiobooks, voice search, steering-wheel controls. **No video in the car.**

## Build
GitHub Actions builds `CineDrive.apk` on every push to the `cinedrive` branch (see Releases).
Locally: open in Android Studio, or `gradle assembleDebug`.

## Privacy & security
- Only INTERNET + media-playback service permissions.
- Jellyfin password is never stored; the access token is kept in encrypted storage.
- No analytics, ads or trackers.
- Cleartext HTTP is allowed so home Jellyfin servers on `http://192.168.x.x` work.
