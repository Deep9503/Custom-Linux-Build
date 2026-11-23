# Auto Browser (experimental)

A web browser that runs on the Android Auto screen. For experiments only — sideloaded, not for Play Store.

## How it works
Android Auto doesn't allow browsers, so the app registers as a *navigation* app, which gets a raw
drawing surface on the car display. A WebView is rendered onto that surface through a private
VirtualDisplay, and car touch input (tap, pan, fling, pinch) is forwarded to it.

## What's new in 0.2
- **On-screen keyboard** drawn by the app on the car screen (works even when Android Auto's keyboard is locked).
  Tap 🔍 to search, or tap any text box on a page and the keyboard opens for it.
- **Ad & tracker blocking** using the filter lists Brave's shields are built on (EasyList, EasyPrivacy,
  Peter Lowe's list): blocks ad/tracker requests and hides ad elements. Lists are bundled and auto-update weekly.
- **YouTube ad blocking**: strips ad data from the player, hides ad cards, auto-skips anything left.
- **Send to car** (phone app): type on the phone keyboard, open the result on the car screen.

## What's new in 0.4
- **Tabs** on the car screen: tabs button (next to ▲▼) → new tab, switch, close. Up to 10 tabs,
  remembered between drives. Links that open in a new window open in a new tab.
- **Scrolling fixed**: ▲/▼ and pan-drag scroll the page directly (works on YouTube, no accidental
  taps or pull-to-refresh).

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
