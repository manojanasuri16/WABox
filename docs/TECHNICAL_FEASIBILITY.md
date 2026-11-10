# WABox — Technical Feasibility

_Last updated: 2026-11-10 · Based on findings in [RESEARCH.md](RESEARCH.md)_

Each requested capability is assessed as **Feasible**, **Feasible with limits**, or
**Not feasible**. Any "Not feasible" item states the honest alternative that WABox ships
instead.

## Summary matrix

| Capability | Verdict | Mechanism / limitation |
| --- | --- | --- |
| WhatsApp status discovery | **Feasible** | SAF tree grant on `Android/media/com.whatsapp`, then search for `.Statuses` |
| WhatsApp Business discovery | **Feasible** | Same, on `Android/media/com.whatsapp.w4b` |
| Legacy (pre-2021) WhatsApp paths | **Feasible on Android 10** | `/WhatsApp/...` is grantable on API 29. On 30+ the storage root is blocked, but `WhatsApp/` itself is a normal folder and can still be granted. Handled through *Add another folder*. |
| Distinguish Messenger vs Business | **Feasible** | Separate packages, separate folders |
| Distinguish two accounts inside one WhatsApp install | **Feasible with limits** | Only if WhatsApp keeps them in separate `.Statuses` folders. Discovery detects that automatically. If they share a folder, they cannot be separated without WhatsApp's private database, which is inaccessible. |
| Identify *which phone number* a status belongs to | **Not feasible** | That data is only in `/data/data/com.whatsapp` (private, encrypted). **Alternative:** the user labels each discovered folder ("Personal", "Work"). |
| OEM clones (Dual Messenger, etc.) | **Feasible with limits** | Only where the OEM exposes the clone's folder in the system picker. *Add another folder* handles it. Otherwise it lives in another Android user and is out of reach. |
| Work profile / Secure Folder | **Not feasible from the personal profile** | Install WABox inside that profile. |
| Save images and videos | **Feasible** | MediaStore insert with no permission, `IS_PENDING` while copying |
| Unified saved gallery | **Feasible** | Room index of WABox-saved items, reconciled against MediaStore |
| Delete saved items | **Feasible** | Direct delete for owned rows. `createDeleteRequest` (API 30+) or `RecoverableSecurityException` (API 29) after reinstall. |
| Share | **Feasible** | Saved items: MediaStore URI with a read grant. Unsaved statuses: copied to the app cache, served by `FileProvider`. |
| Edge-to-edge viewer, zoom, video | **Feasible** | Compose `HorizontalPager`, transform gestures, Media3 ExoPlayer |
| Live updates while the app is open | **Feasible** | `DirectoryCursor` + `ContentObserver` (verified in AOSP) |
| Real-time updates in the background | **Not feasible (by policy)** | No legitimate API. WABox offers optional periodic refresh (≥ 1 h) and refreshes widgets after each discovery. |
| Widgets: recent statuses, account switcher, quick access | **Feasible** | Glance 1.1, `SizeMode.Responsive` |
| Widget quick save | **Feasible** | `ActionCallback` → expedited `SaveWorker` → widget update |
| Widget manual refresh | **Feasible** | `ActionCallback` → `RefreshWorker` |
| Per-widget configuration | **Feasible** | Glance per-widget preferences state + configuration activity. Reconfiguration on API 31+. |
| Widget reboot survival | **Feasible** | Launcher restores `RemoteViews`, Glance state is in DataStore, WorkManager reschedules itself |
| No root, no private data | **Satisfied** | Only plain files in shared storage, through user-granted SAF access |

## Platform support

| | Value | Rationale |
| --- | --- | --- |
| `minSdk` | **29 (Android 10)** | Requested baseline. MediaStore `RELATIVE_PATH`/`IS_PENDING` needs 29. Covers >90% of active devices in 2026. |
| `targetSdk` / `compileSdk` | **36 (Android 16)** | Current Play requirement |
| Architectures | Any (pure Kotlin, no native code) | |

### Behaviour by Android version

| Version | Status access | Notes |
| --- | --- | --- |
| Android 10 (29) | SAF, unrestricted picker | Legacy `/WhatsApp` folder also grantable |
| Android 11–12L (30–32) | SAF; `Android/media/...` grantable | Widgets on 29–30 use drawable rounded backgrounds. No widget reconfiguration on 30. |
| Android 13–14 (33–34) | Same; granular media permissions irrelevant | Predictive back supported (opt-in) |
| Android 15–16 (35–36) | Same | Edge-to-edge enforced, which WABox is built for |

## Key risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| WhatsApp changes its inner folder layout | Medium | Recursive `.Statuses` search instead of hard-coded inner paths |
| WhatsApp moves out of `Android/media` | Low | *Add another folder*. Grant-loss detection prompts a reconnect. |
| An OEM picker hides dot-folders | Medium | WABox grants the package folder, not `.Statuses` directly, so the user never has to see a hidden folder |
| The user picks the wrong folder | High | Validation: if the picked tree has no `.Statuses` within depth 4, WABox explains this and offers to retry. The grant is released. |
| The user grants the wrong app's folder (WA vs WAB) | Medium | Classification is by path. WABox accepts it and labels it correctly. |
| Google Play policy on status savers | Medium | No `MANAGE_EXTERNAL_STORAGE`, no network, no WhatsApp branding or logos, a clear statement of non-affiliation, and saving positioned as a personal-use convenience |
| Very large videos (≥100 MB) | Low | Streamed copy, free-space precheck, progress state, cancellation-safe cleanup of pending rows |

## Features that require physical-device verification

These cannot be fully proven on an emulator because they depend on real WhatsApp
installations or OEM behaviour:

1. Real WhatsApp and WhatsApp Business status downloads, filenames, and `.nomedia` presence.
2. Native in-app second account: whether a second `.Statuses` folder appears.
3. OEM dual-app folder exposure (Samsung, Xiaomi, OnePlus).
4. OEM file pickers that differ from AOSP DocumentsUI (some ship custom pickers).
5. Launcher-specific widget rendering, resizing, and corner radii (Pixel Launcher, One UI, MIUI).
6. Aggressive OEM battery managers deferring the periodic worker.
7. Reboot persistence of SAF grants and widgets on OEM builds.

The emulator test plan (see [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)) simulates
the WhatsApp folder layouts with real media files pushed through `adb`. That exercises
the full SAF grant, discovery, save, widget, and reboot pipeline end to end.
