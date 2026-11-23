# Auto Browser (experimental)

A web browser that runs on the Android Auto screen. For experiments only — sideloaded, not for Play Store.

## How it works
Android Auto doesn't allow browsers, so the app registers as a *navigation* app, which gets a raw
drawing surface on the car display. A WebView is rendered onto that surface through a private
VirtualDisplay, and car touch input (tap, pan, fling, pinch) is forwarded to it.

## Car controls
- 🔍 Search / URL — car keyboard (only while parked on most cars). Recent searches + quick links are tappable rows.
- ◀ Back, ⟳ Reload, ☰ Menu (search engine, type into page field, forward, home, desktop site, set home)
- Map buttons: ✥ Pan (enables drag-to-scroll), ▲/▼ page up/down. Tap directly on the page to click links.
- Search engines: Google, DuckDuckGo, Bing, Brave, YouTube, Wikipedia.

## Build
- GitHub: push this folder to a repo → Actions → "Build APK" → download `AutoBrowser.apk` from Releases.
- Android Studio: open the folder → Build → Build APK(s).

## Install on phone + enable in Android Auto
1. Install the APK (allow "install unknown apps" for your browser/file manager).
2. Android Auto settings on phone → tap "Version" 10× → ⋮ → Developer settings → enable **Unknown sources**.
3. Connect to the car (or the Desktop Head Unit). "Auto Browser" appears in the app launcher.

Use only while parked.
