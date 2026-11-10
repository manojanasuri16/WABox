# WABox — Implementation Plan

_Last updated: 2026-11-10_

Phases follow the requested sequence. Each phase ends with a green `./gradlew assembleDebug`
(and `testDebugUnitTest` once tests exist) before the next begins.

| # | Phase | Deliverables | Exit check |
| --- | --- | --- | --- |
| 1 | Research | `RESEARCH.md` | ✅ |
| 2 | Feasibility | `TECHNICAL_FEASIBILITY.md` | ✅ |
| 3 | Architecture | `ARCHITECTURE.md`, this plan | ✅ |
| 4 | Apple-design review | Principles mapped to Android (below) | ✅ |
| 5 | Design system | Tokens, theme, core components | ✅ |
| 6 | Navigation and screens | Scaffold, bottom bar, routes, onboarding shell | ✅ |
| 7 | Media discovery | Room, locator, scanner, repository, live observer | ✅ |
| 8 | Sources/accounts | Grant manager, switcher sheet, labels, health, reconnect | ✅ |
| 9 | Viewer and saving | Pager, zoom, Media3, MediaSaver, share, dedupe | ✅ |
| 10 | Saved gallery | Tabs, sort, selection, delete, reconciliation | ✅ |
| 11 | Widgets | Three Glance widgets, config activity, workers, thumbnail cache | ✅ |
| 12 | Settings | Theme, save location, sources, refresh, about | ✅ |
| 13 | Test and polish | Unit tests, emulator end-to-end, light/dark screenshots, release APK | ✅ |

| 14 | App Lock (requested later) | Biometric gate, timeouts, widget privacy | ✅ |

Results: see [TESTING.md](TESTING.md).

## Apple-design principles → WABox (phase 4)

| Principle (apple-design skill) | Applied as |
| --- | --- |
| Response: feedback on press-down | `PressableScale` scales to 0.97 on press with a critically damped spring. Haptic tick on save commit. |
| Springs, not durations | `WaMotion.standard` (damping 1.0, response 0.35 s), `WaMotion.snappy` (1.0 / 0.25), `WaMotion.momentum` (0.8 / 0.35) used only after flings (viewer dismiss) |
| Interruptibility | Compose `Animatable`/`animate*AsState` retarget from the current value. No input locks during transitions. |
| Momentum projection | Viewer swipe-down-to-dismiss uses projected endpoint `y + (v/1000)·d/(1−d)`, `d = 0.998`. Rubber-banding on upward overscroll. |
| Spatial consistency | The source sheet rises from the bottom and dismisses down. The viewer fades and scales from the grid and returns the same way. |
| Materials and depth | Translucent bottom bar and viewer chrome (surface at 0.86–0.92 alpha + hairline), with content scrolling beneath. A scrim only for modal sheets. |
| Typography | System font. Large title 32 sp with −0.4 sp tracking. Body 16 sp with 0 tracking. Captions with positive tracking. Leading set per size. Respects font scale. |
| Restraint / simplicity | Three tabs. No cards around grids. One accent colour. App-kind tints only as small badges. |
| Reduced motion | `ANIMATOR_DURATION_SCALE == 0` turns springs into cross-fades |
| Wayfinding | Large titles name each screen. The source pill always shows what is being viewed. The viewer always has a close affordance. |
| Agency and forgiveness | Delete confirms (irreversible). Save never asks. Undo is unnecessary because saving is non-destructive. |
| Direct labels | Tabs: Home, Saved, Settings (as requested). Actions: "Save", "Saved", "Share", "Delete". |

## Test plan

### Automated (JVM unit tests)
- Path classification (`AppKind` from tree/doc IDs, `.Statuses` detection, legacy paths)
- Media filtering (mime/extension, `.nomedia`, temp files)
- Discovery diff (insert/update/delete sets)
- Duplicate fingerprinting
- Filename sanitising for MediaStore
- Spring parameter conversion
- Formatting (sizes, durations, relative times)

### Emulator end-to-end (API 34 AVD)
Simulate the real layouts by pushing real JPEG/MP4 files with `adb`:
```
/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/
/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses/
```
Then verify: onboarding grant via the real DocumentsUI picker, discovery counts, live
update when a file is pushed while the app is open, save (MediaStore row appears in
`Pictures/WABox`), duplicate prevention, share sheet, delete, theme switching, widget
placement and resize, widget refresh/save, and reboot (`adb reboot`), after which
grants and widgets persist.

### Physical device (required, cannot be emulated)
See *Features that require physical-device verification* in
[TECHNICAL_FEASIBILITY.md](TECHNICAL_FEASIBILITY.md).
