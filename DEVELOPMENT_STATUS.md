# Development status

## Edge Navigation

- Implemented: independent left/right inward Back actions; configurable left/right hold actions (none, Smart App Return, Recents); hold duration; activation distance; sensitivity; and package exclusions.
- Implemented: opt-in accessibility service with full-height transparent edge zones, a temporary hold-progress marker, single-fire gesture handling, orientation-aware window placement, and a persisted bounded history of eligible foreground app package names.
- Implemented: Smart Return excludes Island X, launchers, the current keyboard, common transient/system UI packages, and user-excluded packages. It attempts to open the prior observed app with a launcher intent and task-reuse flags, then falls back to system Recents.
- Limitation: Android does not expose a complete Recents task list to ordinary apps. The attempted launch may not restore a particular existing task on every OEM/app. History only contains apps observed while the feature and accessibility service are enabled.
- Limitation: custom Back invokes the accessibility global Back action; the native predictive-back animation is not guaranteed. Disabling Island X edge navigation returns edge handling to Android.
- Limitation: system navigation and other full-screen/OEM gesture handlers can preempt edge overlays. Universal interception is not claimed.
- Privacy: the accessibility service requests no view-content access. It observes package names for local history and does not upload them.

## Verification

- `:app:compileDebugKotlin`: passed with JDK 17 and Android SDK Platform 36.
- `:app:testDebugUnitTest`: passed; three `RecentAppHistory` tests cover current-app exclusion, eligibility filtering, deduplication, and bounded restored history.
- `:app:assembleDebug`: passed; generated `app/build/outputs/apk/debug/app-debug.apk` (19 MB).
- `:app:bundleRelease`: passed; generated `app/build/outputs/bundle/release/app-release.aab` (3.3 MB). The AAB has no developer-owned upload-key signing configured and is not ready to upload as-is.
- APK manifest inspection: package `com.islandx.app`, min SDK 26, target SDK 36, and `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native ABI declarations.
- Android SDK tooling emitted an SDK XML version warning (tooling understands version 3 while installed SDK metadata is version 4); compilation and tests still passed.
- No `adb` executable or attached device was available. Physical-device gesture conflict testing, actual task-resume behavior, landscape interaction, and APK install/startup have not been performed.

## Release status

- Target SDK is 36, matching the Play requirement checked on October 30, 2026 for new apps and updates from August 31, 2026.
- Release AAB build configuration and unsigned bundle generation are verified. Developer upload-key signing, privacy-policy hosting, Play Data safety and accessibility disclosures, dependency review, and pre-launch checks remain outstanding.
- No Play approval or universal device compatibility is claimed.