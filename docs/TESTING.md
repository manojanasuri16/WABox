# WABox — Testing

_Last run: 2026-11-10 · Build: 1.0.0 (1)_

## 1. Automated (JVM)

`./gradlew testDebugUnitTest`: **21 tests, 0 failures**

| Suite | Covers |
| --- | --- |
| `StatusPathsTest` (9) | App classification (scoped and legacy paths), picker targets, nesting and volumes, display paths, discovery skip list, media and mime filtering, default labels |
| `StatusDiffTest` (5) | First scan, no-op scan, deletions, changed files keep `firstSeenAt`, stable folder-scoped IDs, duplicate rows |
| `FormatsTest` (7) | Sizes, durations, relative times, fingerprint sensitivity, spring stiffness, momentum projection, rubber-banding |

## 2. Emulator end-to-end

**Environment:** Pixel 6 AVD, Android 14 (API 34, Google APIs, x86_64), Pixel Launcher, software
rendering. WhatsApp isn't installed on the emulator, so its exact on-disk layout was recreated with
real JPEG and H.264/AAC MP4 files pushed via `adb`:

```
Android/media/com.whatsapp/WhatsApp/Media/.Statuses/{*.jpg,*.mp4,.nomedia}
Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/…        (must be skipped)
Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses/{*.jpg,*.mp4,.nomedia}
```

| Area | Scenario | Result |
| --- | --- | --- |
| Onboarding | Welcome → Connect → system picker opens **directly at `Android/media/com.whatsapp`** (`EXTRA_INITIAL_URI`) → Use this folder → Allow | ✅ |
| Discovery | Both apps found; `.nomedia` ignored; `WhatsApp Images` not scanned; video durations probed | ✅ 7 + 2 statuses |
| First run | Everything shown as "Recent". No false "New" section after picker round-trips | ✅ (bug found and fixed) |
| Live updates | File pushed while the app is open appears within about 1 s, no refresh | ✅ |
| Viewer | Image zoom page, video playback from SAF URI, swipe between items, swipe-down dismiss, details sheet with dimensions | ✅ |
| Saving | Video → `Movies/WABox`, photo → `Pictures/WABox`, `is_pending=0`, size matches source | ✅ |
| Duplicates | Saved items show a ✓ on Home, in the viewer and in widgets. Save is disabled for them | ✅ |
| Sharing | Unsaved status shared through the FileProvider copy; chooser shows the image | ✅ |
| Saved gallery | Date sections, tabs, sort menu, item count and location subtitle | ✅ |
| Themes | System, light and dark switch instantly. Dark uses true black and elevated surfaces | ✅ |
| Source switching | Home sheet, Settings, and the switcher widget all share one selection | ✅ |
| Access loss | Business folder moved away → "access lost" banner → **Reconnect** reopens the picker at the same folder → restored | ✅ |
| Widgets: placement | All three appear in the picker with previews. Switcher and Recent placed on Pixel Launcher | ✅ |
| Widgets: interaction | Switcher row changes the app's source. Recent tile opens the viewer. Quick-save badge saves via `SaveWorker`. Refresh runs `RefreshWorker` | ✅ |
| Widgets: configuration | Reconfigure pencil → per-widget source → widget pinned to Business while the app stays on WhatsApp | ✅ |
| Widgets: sizing | Narrow-tall bucket fallback showed only 2 tiles in a wide strip → switched to `SizeMode.Exact` with fitted grid | ✅ (bug found and fixed) |
| Reboot | `adb reboot`: widgets restore with per-widget state; SAF grants persist; background refresh from a widget discovers a new file with the app not open | ✅ |
| App Lock | Enable requires PIN, then locks instantly after leaving → opaque lock screen → PIN → returns to the same screen | ✅ |
| Idle cost | `dumpsys gfxinfo` over 4 s idle on Home and Saved: **0 frames rendered** | ✅ (bug found and fixed) |
| Accessibility | Tiles expose "Video from WhatsApp Business, 18m ago" and Save/Saved labels | ✅ |
| Cold start | Release build, software-rendered emulator: ~1.9 s (debug ~5.2 s) | ✅ |

## 3. Requires a physical device

These depend on real WhatsApp installations, OEM software, or hardware the emulator can't provide:

1. Real WhatsApp and WhatsApp Business status downloads: file naming, timing, `.nomedia`.
2. **WhatsApp's in-app second account**: whether it writes a separate `.Statuses` folder. WABox
   handles both outcomes, but which one happens can only be observed on a device using the feature.
3. OEM Dual Apps / Dual Messenger folder exposure in the file picker (Samsung, Xiaomi, OnePlus).
4. OEM file pickers that differ from AOSP DocumentsUI.
5. Manual widget **resizing** on real launchers. Pixel Launcher's resize handles ignore synthetic
   adb drags. Layout uses the exact reported size and was verified at two sizes.
6. Launcher-specific widget corner radii, especially on Android 10–11 where drawables are used.
7. Fingerprint and face unlock for App Lock. The emulator test used the device PIN path.
8. Aggressive OEM battery managers deferring the optional periodic refresh.
9. Android 10 (API 29) behaviour, including the legacy `/WhatsApp` folder via *Add another folder*.
10. Very large videos (100 MB or more) for copy speed and free-space prompts.
