# WABox — Research

_Last updated: 2026-11-10_

This document records what was investigated before any code was written, what was
verified against primary sources, and what remains unverifiable without a physical
device. Every architectural decision in `ARCHITECTURE.md` traces back to a finding here.

Legend: **[Verified]** = confirmed against official docs or AOSP source.
**[Reported]** = consistent across multiple secondary sources, not officially documented.
**[Unverified]** = no reliable source; WABox is designed so it does not depend on it.

---

## 1. Where WhatsApp keeps statuses

WhatsApp downloads the media of statuses you view (and, depending on version and
network, statuses it pre-fetches) into a hidden `.Statuses` directory. The directory
contains a `.nomedia` file, so the system media scanner does **not** index it, and
`MediaStore` queries never return status files.

| Era | WhatsApp Messenger | WhatsApp Business |
| --- | --- | --- |
| Before 2021 (legacy external storage) | `/sdcard/WhatsApp/Media/.Statuses` | `/sdcard/WhatsApp Business/Media/.Statuses` |
| Scoped storage (WhatsApp 2.21+, all current versions) | `/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses` | `/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses` |

- **[Reported]** The move to `Android/media/<package>/` happened when WhatsApp adopted
  scoped storage in 2021. Multiple independent sources and open-source status savers
  use exactly these paths ([anexplorer.io][anex-wa], [XDA][xda-a11],
  [SultanAyubi360/WhatsApp-Status-Saver][gh-ss1], [DMegatron/WhatsAppStatusSaver][gh-ss2]).
- **[Verified]** `Android/media/` is a *shared* storage location. Unlike
  `Android/data/` and `Android/obb/`, it is not hidden from the Storage Access
  Framework (see §3).
- Files are plain JPEG/MP4 (occasionally WebP/GIF/3GP). They are **not** encrypted on
  disk. WABox reads only these plain media files; it never touches WhatsApp's private
  databases (`/data/data/com.whatsapp`) or encrypted backups.
- Status files disappear roughly 24 hours after posting, when WhatsApp cleans the folder.
  The folder is a **cache**. WABox therefore copies saved media into the user's own
  gallery.

**Design consequence:** don't hard-code the inner path. WABox asks for access to the
package folder (`Android/media/com.whatsapp`) and **searches** it for `.Statuses`
directories. If WhatsApp changes its inner layout, discovery keeps working.

## 2. Multiple accounts: what can actually be distinguished

This is the critical requirement. There are four different "multi-account" setups on
Android. Each behaves differently.

### 2a. WhatsApp Messenger vs WhatsApp Business: **distinguishable** [Verified]
Different packages (`com.whatsapp`, `com.whatsapp.w4b`), so they use different
`Android/media/<package>` folders. WABox can separate them reliably.

### 2b. WhatsApp's native in-app second account ("Add account", rolled out from Oct 2023)
- **[Reported]** Announced 19 Oct 2023. It allows two accounts (two numbers) inside one
  WhatsApp installation ([9to5Mac][9to5], [TechCrunch][tc], [Business Insider][bi]).
- **[Unverified]** No official documentation describes how the second account's media
  is laid out on disk. One vendor guide claims the user picks a separate folder during
  setup. Another claims media is stored independently. No primary source confirms
  either, and no open-source status saver handles a distinct second-account `.Statuses`
  path ([search of GitHub status savers][gh-topic]).
- WhatsApp's private database records which account a status came from. That data
  lives in `/data/data/com.whatsapp`, which is private, encrypted, and inaccessible
  without root. **WABox will not attempt it.**

**Conclusion:** WABox cannot know which phone number a status belongs to. It can,
however, reliably distinguish **status folders**. Discovery finds every `.Statuses`
directory inside each granted tree:

- If the device's WhatsApp version keeps a second account in its own folder, discovery
  finds two folders, and each becomes a separate, user-labellable source
  ("Personal", "Work").
- If both accounts share one folder (the likely case), WABox shows **one** WhatsApp
  source and says so plainly. It never invents a split.

### 2c. OEM "Dual Apps" / "Dual Messenger" / app clones (Samsung, Xiaomi, OnePlus, …)
- **[Verified]** The clone runs as a separate Android **user/profile** (for example
  user 95 or 999). Its external storage is `/storage/emulated/<userId>`. AOSP's
  `ExternalStorageProvider` builds its root from `UserHandle.myUserId()`
  ([AOSP ExternalStorageProvider][aosp-esp]), so the primary user's picker shows the
  primary user's storage only.
- **[Reported]** Some OEMs expose clone media through vendor-specific folders
  (`/DualApp/…`) or bind mounts, and the behaviour varies by OEM and version
  ([XDA dual app thread][xda-dual], [Samsung community][samsung]).
- **WABox approach:** no OEM-specific hacks. "Add another folder" lets the user grant
  **any** folder the system picker exposes. If an OEM surfaces the clone's
  `Android/media/com.whatsapp`, WABox discovers its `.Statuses` folder as a separate
  source. Otherwise the clone is out of reach, and the app explains this.

### 2d. Work profile / Secure Folder / Private Space
Separate Android user, separate storage. Not reachable from the personal profile, by
design. Installing WABox inside that profile is the legitimate route.

## 3. Android storage rules (Android 10 → 16)

| Android | Relevant behaviour | Source |
| --- | --- | --- |
| 10 (API 29) | Scoped storage introduced; apps targeting 29 can opt out with `requestLegacyExternalStorage`. SAF has no tree restrictions. | [Android 11 storage][a11] |
| 11 (API 30) | Scoped storage enforced for apps targeting 30+. SAF `ACTION_OPEN_DOCUMENT_TREE` **cannot** select: storage root, SD-card roots, `Download/`. Cannot select files in `Android/data/` or `Android/obb/`. | [Android 11 storage][a11], [SAF guide][saf] |
| 11+ | `MANAGE_EXTERNAL_STORAGE` ("All files access") exists but is restricted by Google Play policy to file managers, backup apps, and similar. A status saver does not qualify. | [Manage all files][mef], [Play policy][play-mef] |
| 13 (API 33) | `READ_EXTERNAL_STORAGE` replaced by granular `READ_MEDIA_IMAGES/VIDEO`. These go through MediaStore, which **does not index `.nomedia` folders**, so they cannot see statuses. | [Android 13 changes][a13] |
| 14 (API 34) | Partial photo access ("Selected photos"). MediaStore-based, so again irrelevant to `.Statuses`. | [Android 14 changes][a14] |

**Exactly what the picker blocks (verified in AOSP source).**
`ExternalStorageProvider.shouldBlockDirectoryFromTree()` blocks:
1. the volume root (empty path),
2. `Download/` and **`Android/` itself**,
3. anything under `Android/data`, `Android/obb`, `Android/sandbox` (`isRestrictedPath`).

`Android/media/com.whatsapp` is none of these, so it **can be granted as a tree**
([AOSP ExternalStorageProvider.java][aosp-esp]). DocumentsUI also refuses
`EXTRA_INITIAL_URI` values inside restricted paths ([DocumentsUI commit][aosp-dui]).
`Android/media/...` is allowed as an initial location.

**Persisting access.** `takePersistableUriPermission()` keeps the grant across reboots.
The grant is lost if the granted folder is moved or deleted, for example when WhatsApp
is uninstalled ([SAF guide][saf]). WABox must detect lost access and offer to
reconnect. It must never fail silently.

**Performance.** The SAF guide warns that iterating many files through a tree URI is
slow. `DocumentFile` issues one IPC query per property. WABox uses
`DocumentsContract.buildChildDocumentsUriUsingTree` with a single projection query per
directory, and bounds the discovery walk.

## 4. Permissions WABox needs

| Need | Mechanism | Runtime permission? |
| --- | --- | --- |
| Read status files | SAF tree grant (user picks the folder) | **None.** The user grant is the permission. |
| Save copies to gallery | `MediaStore.Images/Video` insert with `IS_PENDING` (API 29+) | **None** for own files ([MediaStore guide][ms]) |
| Delete own saved copies | `ContentResolver.delete`. After a reinstall, ownership is lost and API 30+ needs `MediaStore.createDeleteRequest` | None (system confirmation dialog) |
| Check WhatsApp is installed | `<queries>` for the two packages ([package visibility][pv]) | None |
| Background refresh | WorkManager | None |
| Network | — | **WABox declares no `INTERNET` permission.** |

`READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, and `MANAGE_EXTERNAL_STORAGE` are **not**
requested. They either cannot see `.Statuses` or are Play-policy restricted.

## 5. Media discovery and refresh

- **Live updates are possible only in the foreground.** **[Verified]** In AOSP
  `FileSystemProvider` (the base of `ExternalStorageProvider`), a cursor returned by
  `queryChildDocuments` is a `DirectoryCursor`. While it stays open, the provider
  attaches a `FileObserver` (`CLOSE_WRITE | MOVED_TO | DELETE | …`) to the directory and
  notifies the cursor's notification URI
  ([AOSP FileSystemProvider.java][aosp-fsp]). WABox keeps that cursor open **only while
  the app is in the foreground**, registers a `ContentObserver`, and re-scans with a
  debounce. When WhatsApp downloads a status, it appears in WABox without a manual
  refresh.
- Outside the foreground, discovery is **pull-based**. It runs on app start,
  pull-to-refresh, the widget refresh button, and an **optional** periodic WorkManager
  job (off by default; WorkManager's minimum interval is 15 minutes, and WABox offers
  1 h or longer).
- Change detection is cheap. One `queryChildDocuments` returns name, size,
  `last_modified`, and MIME type for the whole folder in one cursor, and the result is
  diffed against Room.

## 6. Previews and playback

- **Images:** Coil 3 loads `content://` URIs directly, with memory and disk caches and
  size-aware decoding. No network module is needed.
- **Video thumbnails:** `coil-video` (`MediaMetadataRetriever` frame extraction) for
  grids. A single frame is extracted at about 1 s.
- **Video playback:** Media3 ExoPlayer plays `content://` URIs natively through
  `DataSource.Factory`, handles large files by streaming without loading them into
  memory, and is the actively maintained successor to ExoPlayer 2
  ([Media3][media3]).

## 7. Saving

- `MediaStore` insert into `Pictures/WABox` (images) and `Movies/WABox` (videos), or a
  user-selected alternative (`DCIM/WABox`). `IS_PENDING=1` is set while copying, so
  galleries never show partial files ([MediaStore guide][ms]).
- Copy uses a 256 KB buffered stream on `Dispatchers.IO`. Free space is checked first
  with `StatFs`.
- **Duplicate detection:** WhatsApp status filenames are content-derived hashes, so
  `(originalName, size)` identifies a status well. WABox stores that fingerprint plus a
  partial SHA-256 (first and last 64 KB). A re-save of the same media is detected even
  if the file was renamed.

## 8. Background execution

- Doze, App Standby Buckets, and OEM battery managers defer background work.
  WorkManager is the supported API that respects these constraints and survives reboots
  (it reschedules on `BOOT_COMPLETED` itself) ([WorkManager][wm],
  [background limits][bg]).
- WABox does no foreground services, wake locks, or exact alarms.

## 9. Home-screen widgets

| Option | Assessment |
| --- | --- |
| Raw `AppWidgetProvider` + `RemoteViews` XML | Maximum control and no extra dependency, but verbose. Responsive layouts need hand-written XML per size. |
| **Jetpack Glance** (`glance-appwidget`) | Compose-style API that compiles to `RemoteViews`. Built-in `SizeMode.Responsive`, per-widget state via `GlanceStateDefinition`, `ActionCallback` for interactions, `updateAll()` for refresh. **Chosen.** ([Glance][glance]) |

Constraints verified in the docs ([Glance][glance], [App widgets][aw]):
- Widgets run in the launcher process. The `GlanceAppWidget` must be stateless and read
  from the app's data layer.
- `updatePeriodMillis` has a 30-minute minimum. WABox sets it to `0` and updates
  **after successful discovery** instead.
- `RemoteViews` bitmaps are bounded (roughly screen width × height × 4 × 1.5 bytes in
  total). WABox pre-renders small JPEG thumbnails (≤ 320 px) into a widget thumbnail
  cache during discovery and decodes them with `inSampleSize`.
- Rounded corners: `system_app_widget_background_radius` on API 31+. Drawable
  backgrounds on API 29–30.
- Reconfiguration (`widgetFeatures="reconfigurable"`) is available on API 31+. On older
  APIs, the configuration activity runs only at placement.
- Widgets survive reboot: the launcher restores `RemoteViews`, Glance state lives in
  DataStore, and the next update re-renders from Room.

## 10. Technology stack research

| Concern | Choice | Why |
| --- | --- | --- |
| Language | Kotlin 2.1 | First-class Android language. Coroutines and Flow. |
| UI | Jetpack Compose + Material 3, heavily customised | Modern, declarative. M3 supplies accessible primitives; WABox tokens restyle them. |
| Architecture | MVVM + repositories, unidirectional data flow | Testable without the ceremony of a full use-case layer for a single-module app. |
| DI | Manual `AppContainer` | A dozen singletons does not justify Hilt's compile-time cost. |
| Persistence | Room (index of statuses, accounts, saved items) + DataStore (preferences) | Widgets and fast startup read from Room without touching SAF. |
| Images | Coil 3 (`coil-compose`, `coil-video`) | Small, Compose-native, handles `content://`. |
| Video | Media3 ExoPlayer + UI | Official and robust. |
| Background | WorkManager | Only correct choice under modern background limits. |
| Widgets | Glance 1.1 | See §9. |
| Navigation | Navigation Compose (type-safe routes) | Back-stack correctness and deep links from widgets. |

---

## Sources

- [a11]: Android 11 storage updates — https://developer.android.com/about/versions/11/privacy/storage
- [saf]: Access documents and other files from shared storage (SAF) — https://developer.android.com/training/data-storage/shared/documents-files
- [ms]: Access media files from shared storage (MediaStore) — https://developer.android.com/training/data-storage/shared/media
- [mef]: Manage all files on a storage device — https://developer.android.com/training/data-storage/manage-all-files
- [play-mef]: Google Play — Use of All files access permission — https://support.google.com/googleplay/android-developer/answer/10467955
- [a13]: Android 13 behavior changes (granular media permissions) — https://developer.android.com/about/versions/13/behavior-changes-13#granular-media-permissions
- [a14]: Android 14 — partial photo and video access — https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- [pv]: Package visibility filtering — https://developer.android.com/training/package-visibility
- [aosp-esp]: AOSP `ExternalStorageProvider.java` (`shouldBlockDirectoryFromTree`, `isRestrictedPath`) — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java
- [aosp-dui]: AOSP DocumentsUI — "Restrict initial location for ACTION_OPEN_DOCUMENT/_TREE" — https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/db3f1075f%5E
- [aosp-fsp]: AOSP `FileSystemProvider.java` (`DirectoryCursor`, `DirectoryObserver`) — https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/content/FileSystemProvider.java
- [glance]: Jetpack Glance — manage and update GlanceAppWidget — https://developer.android.com/develop/ui/compose/glance/glance-app-widget
- [aw]: App widgets overview — https://developer.android.com/develop/ui/views/appwidgets/overview
- [wm]: WorkManager — https://developer.android.com/topic/libraries/architecture/workmanager
- [bg]: Background execution limits — https://developer.android.com/about/versions/oreo/background
- [media3]: Media3 ExoPlayer — https://developer.android.com/media/media3/exoplayer
- Coil — https://coil-kt.github.io/coil/
- [9to5]: 9to5Mac — two WhatsApp accounts on one phone (2023-10-19) — https://9to5mac.com/2023/10/19/two-whatsapp-accounts-on-one-phone/
- [tc]: TechCrunch — WhatsApp multiple accounts — https://techcrunch.com/?p=2616965
- [bi]: Business Insider — WhatsApp allows two accounts on one phone — https://www.businessinsider.in/tech/apps/news/whatsapp-now-allows-users-to-have-two-accounts-on-one-phone-within-the-app/amp_articleshow/104583151.cms
- [anex-wa]: anexplorer.io — Manage WhatsApp media files on Android — https://anexplorer.io/solve/manage-whatsapp-media
- [xda-a11]: XDA — Where are the WhatsApp files on Android 11? — https://xdaforums.com/t/where-are-the-whatsapp-files-on-android-11.4251285/
- [xda-dual]: XDA — Dual App WhatsApp media location — https://xdaforums.com/t/dual-app-whatsapp-media-location-after-whatsapp-beta-update.4285861/
- [samsung]: Samsung Community — Dual-app WhatsApp media folder — https://eu.community.samsung.com/t5/mobile-apps-services/dual-app-whatsapp-media-folder-is-deleted-on-samsung-galaxy-a31/td-p/3573976
- [gh-ss1]: https://github.com/SultanAyubi360/WhatsApp-Status-Saver
- [gh-ss2]: https://github.com/DMegatron/WhatsAppStatusSaver
- [gh-topic]: https://github.com/topics/whatsapp-status-saver

[a11]: https://developer.android.com/about/versions/11/privacy/storage
[saf]: https://developer.android.com/training/data-storage/shared/documents-files
[ms]: https://developer.android.com/training/data-storage/shared/media
[mef]: https://developer.android.com/training/data-storage/manage-all-files
[play-mef]: https://support.google.com/googleplay/android-developer/answer/10467955
[a13]: https://developer.android.com/about/versions/13/behavior-changes-13#granular-media-permissions
[a14]: https://developer.android.com/about/versions/14/changes/partial-photo-video-access
[pv]: https://developer.android.com/training/package-visibility
[aosp-esp]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java
[aosp-dui]: https://android.googlesource.com/platform/packages/apps/DocumentsUI/+/db3f1075f%5E
[aosp-fsp]: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/content/FileSystemProvider.java
[glance]: https://developer.android.com/develop/ui/compose/glance/glance-app-widget
[aw]: https://developer.android.com/develop/ui/views/appwidgets/overview
[wm]: https://developer.android.com/topic/libraries/architecture/workmanager
[bg]: https://developer.android.com/about/versions/oreo/background
[media3]: https://developer.android.com/media/media3/exoplayer
[9to5]: https://9to5mac.com/2023/10/19/two-whatsapp-accounts-on-one-phone/
[tc]: https://techcrunch.com/?p=2616965
[bi]: https://www.businessinsider.in/tech/apps/news/whatsapp-now-allows-users-to-have-two-accounts-on-one-phone-within-the-app/amp_articleshow/104583151.cms
[anex-wa]: https://anexplorer.io/solve/manage-whatsapp-media
[xda-a11]: https://xdaforums.com/t/where-are-the-whatsapp-files-on-android-11.4251285/
[xda-dual]: https://xdaforums.com/t/dual-app-whatsapp-media-location-after-whatsapp-beta-update.4285861/
[samsung]: https://eu.community.samsung.com/t5/mobile-apps-services/dual-app-whatsapp-media-folder-is-deleted-on-samsung-galaxy-a31/td-p/3573976
[gh-ss1]: https://github.com/SultanAyubi360/WhatsApp-Status-Saver
[gh-ss2]: https://github.com/DMegatron/WhatsAppStatusSaver
[gh-topic]: https://github.com/topics/whatsapp-status-saver
