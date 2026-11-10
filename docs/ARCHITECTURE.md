# WABox — Architecture

_Last updated: 2026-11-10_

## 1. Principles

1. **One source of truth.** Room holds the index of sources, statuses and saved items.
   UI and widgets observe Room. Only the discovery engine talks to SAF.
2. **Honest data model.** WABox models what it can *observe*: granted folders and the
   status folders found inside them. It never models phone numbers it cannot see.
3. **Pull with care.** Discovery is cheap (one cursor per status folder) and runs only
   when it has a reason to.
4. **No silent failure.** Every source has an explicit health state. Every action
   returns a typed result that the UI renders.
5. **Few dependencies.** Platform APIs first.

## 2. Module and package layout

Single Gradle module `:app`, organised by layer then feature:

```
com.wabox.app
├── WABoxApp.kt              Application: builds AppContainer, schedules work
├── AppContainer.kt          Manual DI graph (singletons)
├── MainActivity.kt          Single activity, edge-to-edge, widget deep links
├── core/
│   ├── model/               Pure Kotlin domain models (Source, StatusItem, SavedItem, …)
│   ├── util/                Formatting, hashing, path classification
├── data/
│   ├── db/                  Room: entities, DAOs, WABoxDatabase
│   ├── prefs/               DataStore-backed SettingsRepository
│   ├── saf/                 StatusFolderLocator, SafScanner, SourceGrantManager
│   ├── media/               MediaSaver (MediaStore), ShareHelper, ThumbnailCache
│   └── repo/                SourceRepository, StatusRepository, SavedRepository
├── work/                    RefreshWorker, SaveWorker, WorkScheduler
├── security/                AppLock state machine + BiometricPrompt helper
├── widget/                  Glance widgets, receivers, actions, config activity, theme
└── ui/
    ├── designsystem/        Tokens (color, type, spacing, shape, motion) + components
    ├── navigation/          Routes, NavHost, bottom bar
    ├── onboarding/          Welcome + connect flow
    ├── home/                Status grid, source switcher
    ├── viewer/              Full-screen pager (status + saved modes)
    ├── saved/               Saved gallery
    └── settings/            Settings, sources management, about
```

## 3. Data model

```
SourceEntity             one SAF tree grant chosen by the user
  id, treeUri, appKind(WHATSAPP|BUSINESS|OTHER), displayPath,
  health(OK|NO_STATUS_FOLDER|ACCESS_LOST), addedAt
        │ 1..n
StatusFolderEntity       one discovered ".Statuses" directory = one switchable "account"
  id, sourceId, documentId, relativePath, label (user-editable), position
        │ 1..n
StatusEntity             one media file currently present in a status folder
  id (= folderId:documentId hash), folderId, documentUri, name, mime, isVideo,
  size, lastModified, durationMs?, width?, height?, firstSeenAt
SavedEntity              one copy WABox wrote into MediaStore
  id, mediaUri, displayName, mime, isVideo, size, savedAt,
  originName, originSize, partialHash, originFolderLabel, originAppKind
```

- The **account switcher** lists `StatusFolderEntity` rows, grouped by app kind. An
  "All" option sits on top. The active selection is a DataStore preference, so the app
  and the Account Switcher widget share it.
- **Saved state of a status** is a join: `StatusEntity(name,size)` against `SavedEntity(originName, originSize)`.
- Statuses that disappear from disk are deleted from Room on the next scan. Saved items
  are independent copies and remain.

## 4. Discovery engine

```
SourceGrantManager   picker intents (EXTRA_INITIAL_URI), takePersistableUriPermission,
                     validation, release, health check via persistedUriPermissions
StatusFolderLocator  bounded BFS from the tree root (depth ≤ 4, skips heavy media folders,
                     stops descending inside ".Statuses") → list of status folder docIds
SafScanner           one DocumentsContract child query per folder; filters media mime
                     types / extensions; ignores ".nomedia"
StatusRepository     diff(scanned, room) → inserts/updates/deletes in one transaction;
                     extracts video duration + widget thumbnails only for *new* items
```

**Triggers**
| Trigger | Path |
| --- | --- |
| App comes to the foreground | `StatusRepository.refresh()` (throttled to 20 s) |
| Folder changes while in the foreground | `LiveStatusObserver` holds `DirectoryCursor`s, debounces 750 ms → `refresh()` |
| Pull to refresh / toolbar button | `refresh(force = true)` |
| Widget refresh button | `RefreshWorker` (expedited one-time work) |
| Optional background | `PeriodicWorkRequest` (1 h / 6 h / 12 h / 24 h, battery-not-low) |
| After any successful refresh | `WidgetUpdater.updateAll()` |

Concurrency: a `Mutex` serialises refreshes, so concurrent triggers coalesce.

## 5. Saving pipeline

`MediaSaver.save(status)`:
1. Duplicate check: `(name, size)` → partial SHA-256 (first + last 64 KB).
2. Free-space check (`StatFs`, size + 10 MB margin).
3. MediaStore insert with `RELATIVE_PATH` from settings, `IS_PENDING = 1`.
4. Streamed copy (256 KB buffer, cooperative cancellation).
5. `IS_PENDING = 0`, then a Room `SavedEntity` row.
6. On any failure: delete the pending row and return a typed `SaveResult.Error`.

Result types: `Saved`, `AlreadySaved`, `SourceMissing`, `NoSpace`, `Failed(reason)`.

## 6. Presentation

- MVVM with `StateFlow<UiState>` per screen. Events go through plain functions. One-off
  feedback goes through a `SharedFlow<UserMessage>` rendered by a shared toast host.
- ViewModels are created via `viewModelFactory { initializer { … } }` from `AppContainer`.
- Navigation: type-safe Navigation Compose routes: `Onboarding`, `Main` (three tabs held
  in a `SaveableStateHolder`), `Viewer(mode, startId)`, `Sources`, `About`.
- Widget taps deep-link into `MainActivity` with an extra, which navigates to
  `Viewer(STATUS, id)`.

## 7. Design system

`ui/designsystem` defines tokens before screens are built:
- `WaColors`: semantic roles (background, groupedBackground, surface, elevated, label,
  secondaryLabel, tertiaryLabel, separator, accent, success, destructive, scrim, plus
  app-kind tints), with light and dark sets.
- `WaType`: an Apple-style scale (largeTitle → caption2) with size-specific tracking
  and leading on the system font.
- `WaSpacing`, `WaRadius`, `WaMotion`: springs from Apple's damping/response
  parameters, converted with `stiffness = (2π / response)²`, plus a reduced-motion
  check.
- Components: `PressableScale`, `SegmentedControl`, `SourceSwitcherPill` and sheet,
  `MediaTile`, `GroupedSection`/`GroupedRow`, `WaButton` variants, `EmptyState`,
  `ToastHost`, `WaBottomBar`, `LargeTitleHeader`, `AppKindBadge`.

These map onto Material 3's `ColorScheme`/`Typography`, so stock M3 components (sheets,
switches, dialogs) inherit the look.

## 8. Widgets

| Widget | Sizing | Data | Interactions |
| --- | --- | --- | --- |
| **Recent Statuses** | `SizeMode.Exact`: ~72 dp tiles fitted to the real size, 1–4 rows | latest statuses for the configured source ("Follow app" or a fixed folder) | tile → viewer; save badge → `SaveWorker`; header → app |
| **Sources** (account switcher) | `SizeMode.Exact`: rows fitted to height | status folders + counts + active marker | row → set active source + update all widgets; open app |
| **Quick Access** | `SizeMode.Exact`: compact row under 100 dp tall, card otherwise | counts (new / photos / videos), last refresh | body → app; refresh glyph → `RefreshWorker` |

- Thumbnails come from `ThumbnailCache` (`cacheDir/widget_thumbs/<statusId>.jpg`, ≤ 320 px,
  JPEG q85). It is written during discovery for the newest 24 items and pruned as
  statuses disappear.
- `GlanceTheme` uses WABox colour providers (day/night). On API 31+ backgrounds use
  `cornerRadius`; on API 29–30 they use rounded drawables.
- Per-widget state: `PreferencesGlanceStateDefinition` (source selection).
- `updatePeriodMillis = 0`: updates are event-driven only.
- `SizeMode.Exact` rather than `Responsive`. Testing showed that when a launcher reports a size shorter
  than every responsive bucket, Glance falls back to the smallest bucket and leaves most of the widget empty.
- With App Lock on, the Recent widget shows "Protected by App Lock" instead of thumbnails.

## 9. App Lock

- `AppLock` observes the process lifecycle. It locks on cold start and when the app returns from the
  background after more than the chosen timeout (instantly, 1, 5 or 15 min).
- `MainActivity` (a `FragmentActivity`, for BiometricPrompt) draws an opaque `LockScreen` **over** the nav
  host instead of removing it. Pending activity results (folder picker, delete consent) still land, and
  nothing behind the lock is visible or touchable.
- Authenticators: `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on API 30+, `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`
  on API 29 (STRONG + credential is unsupported there). Turning the lock on or off requires authenticating.
- `setRecentsScreenshotEnabled(false)` hides the recents thumbnail on API 33+.
- If the device's screen lock is removed, App Lock turns itself off with an explicit message rather than
  trapping the user.

## 10. Error and permission model

`SourceHealth` is recomputed on every refresh:
- `ACCESS_LOST`: the URI is no longer in `persistedUriPermissions`, or the query throws
  `SecurityException`/`FileNotFoundException`. The UI shows a banner with a *Reconnect*
  button, which launches the picker pre-pointed at the same folder.
- `NO_STATUS_FOLDER`: the tree is valid but contains no `.Statuses` folder. The UI shows
  "Open WhatsApp and view a status first".
- The viewer handles a status deleted while open with the "No longer available on this
  device" state.

## 11. Technology decisions

| Decision | Choice | Alternatives considered |
| --- | --- | --- |
| Build | AGP 8.7.3, Gradle 8.11.1, Kotlin 2.1.0, KSP | Newer AGP: no feature WABox needs. This set is proven on this machine. |
| UI | Compose BOM 2024.12.01 (Material 3 1.3.1) | — |
| DI | Manual container | Hilt/Koin: unnecessary for this graph size |
| DB | Room 2.6.1 | SQLDelight: Room integrates with Flow/KSP and is standard |
| Prefs | DataStore Preferences 1.1.1 | SharedPreferences: no Flow, main-thread I/O |
| Images | Coil 3.0.4 (+ video) | Glide: heavier, View-centric |
| Video | Media3 1.5.1 | ExoPlayer 2: deprecated |
| Widgets | Glance 1.1.1 | RemoteViews XML: far more code for responsive layouts |
| Background | WorkManager 2.10.0 | AlarmManager/JobScheduler direct: reinvents WorkManager |
| Navigation | Navigation Compose 2.8.5 type-safe | Hand-rolled: loses back stack and deep links |
| Auth | AndroidX Biometric 1.1.0 | Custom PIN: weaker, and duplicates the device lock |

No `INTERNET` permission. No analytics. No ads SDK.
