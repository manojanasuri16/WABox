# WABox

**Every status. Every account. One place.**

WABox is an Android app that keeps the WhatsApp and WhatsApp Business statuses you've viewed in one
calm, fast gallery, so you can save the ones that matter before they expire. It has home-screen
widgets, an optional App Lock, and no internet permission.

> WABox is an independent app. It isn't affiliated with, endorsed by, or sponsored by WhatsApp or Meta.

## Download

Grab the latest signed APK from **[Releases](../../releases)** and install it. You may need to allow
"Install unknown apps" for your browser or file manager.

| | |
| --- | --- |
| Android | **10 (API 29) – 16 (API 36)** |
| Size | ~5.5 MB |
| Permissions | **No runtime prompts and no `INTERNET`.** Folder access is granted through Android's file picker. Install-time permissions come only from AndroidX: WorkManager (wake lock, boot completed, network state, foreground service) and Biometric. |

## Features

- **WhatsApp and WhatsApp Business discovery.** WABox reads the `.Statuses` folders WhatsApp writes to
  `Android/media/…`, using the Storage Access Framework. No root, no "All files access", no private data.
- **Honest multi-account support.** Every distinguishable status folder becomes a source you can rename
  ("Personal", "Work") and switch between. WhatsApp and WhatsApp Business are always separate. For two
  accounts inside one WhatsApp, see [Known limitations](#known-limitations).
- **One-tap saving** to `Pictures/WABox` and `Movies/WABox` (or `DCIM/WABox`), with duplicate detection
  (filename + size + partial content hash).
- **Viewer.** Edge-to-edge, pinch and double-tap zoom, swipe between statuses, swipe down to dismiss,
  Media3 video with a scrubber and mute, share, and a details sheet.
- **Saved gallery.** Photo and video tabs, newest or oldest first, date sections, multi-select share and delete.
- **Live updates while open.** New statuses appear without refreshing, using a FileObserver-backed SAF cursor.
- **Widgets.** Recent statuses (per-widget source, quick save), Account switcher, and Quick access
  (counts plus refresh). All are resizable, follow light and dark mode, and survive reboot.
- **App Lock (optional).** Fingerprint, face, or your screen lock. Re-locks after leaving (instantly to 15 min),
  hides the recents thumbnail (Android 13+), and the Recent widget hides its previews while locked.
- **Settings.** System, light, or dark theme; save location; refresh on open, live updates, and optional
  background refresh; source and permission management; about.

## First run

1. Tap **Get started**, then **Connect** next to WhatsApp (and/or WhatsApp Business).
2. Android's file picker opens at `Android › media › com.whatsapp`. Tap **Use this folder**, then **Allow**.
3. That's it. View statuses in WhatsApp and they appear in WABox, live while it's open.

If a folder is later moved or deleted (for example, WhatsApp is reinstalled), WABox shows an
**Access lost** banner with a **Reconnect** button. It never fails silently.

## Build from source

Requirements: JDK 17+ (21 tested), Android SDK with platform 36, about 4 GB of free RAM for Gradle.

```bash
git clone https://github.com/manojanasuri16/WABox.git
cd WABox
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # path to your SDK
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk (id: com.wabox.app.debug)
./gradlew testDebugUnitTest       # 21 JVM unit tests
```

Release builds are minified (R8) and signed when these Gradle properties exist (for example in
`~/.gradle/gradle.properties`, never in the repo). Otherwise they fall back to debug signing:

```properties
WABOX_STORE_FILE=/path/to/release.jks
WABOX_STORE_PASSWORD=…
WABOX_KEY_ALIAS=wabox
WABOX_KEY_PASSWORD=…
```

```bash
./gradlew assembleRelease         # app/build/outputs/apk/release/app-release.apk
```

Open the project in Android Studio (Ladybug or newer) as an ordinary Gradle project.

## Tech

Kotlin 2.1 · Jetpack Compose + Material 3 (custom design system) · MVVM + repositories · Coroutines/Flow ·
Room · DataStore · WorkManager · Jetpack Glance · Media3 ExoPlayer · Coil 3 · AndroidX Biometric.
No DI framework, no network stack.

## Documentation

- [docs/RESEARCH.md](docs/RESEARCH.md): storage, SAF, and multi-account research with sources
- [docs/TECHNICAL_FEASIBILITY.md](docs/TECHNICAL_FEASIBILITY.md): what is and isn't possible, and why
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): layers, data model, discovery engine, widgets
- [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md): phases and design principles
- [docs/TESTING.md](docs/TESTING.md): what was tested where, and what still needs a physical device

## Known limitations

- **Two accounts inside one WhatsApp app.** WhatsApp doesn't reveal which account a status belongs to;
  that lives in its private, encrypted database. WABox separates them only if WhatsApp stores them in
  different `.Statuses` folders. If they share a folder, their statuses appear together.
- **OEM "Dual Apps" / clones and work profiles** run as separate Android users. Their storage is
  invisible to WABox unless the phone's file picker exposes the clone's folder (use *Add another folder*).
- **Only statuses WhatsApp has downloaded** can be shown. That's usually the ones you've viewed.
- **No real-time background updates.** Updates are live while WABox is open. Otherwise refresh is
  manual, on open, from a widget, or periodic (1 h or longer, optional), and Android may defer it.
- **App Lock** is an app-level gate, not encryption. Saved copies are normal gallery files.
  The recents thumbnail is hidden only on Android 13+.
- **Widget rounding** uses drawables on Android 10–11, so launcher-specific radii may differ slightly.

## License

© 2026 Manoj Anasuri. All rights reserved.
