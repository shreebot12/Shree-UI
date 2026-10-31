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
- `:app:testDebugUnitTest`: passed (12 tests): seven OpenAI-compatible client tests, two memory-search tests, and three `RecentAppHistory` tests.
- `:app:assembleDebug`: passed; generated `app/build/outputs/apk/debug/app-debug.apk` (19 MB), verified with APK Signature Scheme v2.
- `:app:lintDebug`: passed when run separately. A combined build/test/lint invocation lost its Gradle daemon during lint analysis, after tests and APK packaging had completed.
- `:app:bundleRelease`: not verified for the current source; attempts lost the Gradle daemon during R8 minification. The existing 3.5 MB bundle predates the latest source changes and must not be used as a current build. No developer-owned upload-key signing is configured.
- APK manifest inspection: package `com.islandx.app`, min SDK 26, target SDK 36, and `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` native ABI declarations.
- Android SDK tooling emitted an SDK XML version warning (tooling understands version 3 while installed SDK metadata is version 4); compilation and tests still passed.
- No `adb` executable or attached device was available. Physical-device gesture conflict testing, actual task-resume behavior, landscape interaction, and APK install/startup have not been performed.

## Release status

- Target SDK is 36, matching the Play requirement checked on October 30, 2026 for new apps and updates from August 31, 2026.
- Release AAB generation for the current source remains unverified. Developer upload-key signing, privacy-policy hosting, Play Data safety and accessibility disclosures, dependency review, and pre-launch checks remain outstanding.
- No Play approval or universal device compatibility is claimed.

## Personal assistant

- Audited the public `open-jarvis/OpenJarvis` repository at commit `570d75425d50592d931bac7a17ce6b2621561c06`. It is a Python/Rust framework with a React/Tauri desktop frontend, not an Android application. The root project declares Apache-2.0; this implementation is a Kotlin architecture port and does not copy OpenJarvis source. See [docs/openjarvis-android-mapping.md](docs/openjarvis-android-mapping.md) for module analysis, execution flow, dependencies, license notes, and feature mapping.
- Implemented native chat against configurable OpenAI-compatible `/chat/completions` endpoints. HTTPS endpoints support cloud providers; localhost/emulator HTTP endpoints work without API keys. Island X does not ship a model runtime or model weights, so a local inference server must be separately provided.
- Implemented a pluggable Kotlin `ChatCompletionProvider`, schema-based `AssistantTool` registry, multi-turn tool calls, and approved tools for opening launcher apps, disclosing device details, and saving memories. Time and local memory search are read-only tools.
- Implemented encrypted Room storage for conversation content, memories, and custom skill instructions; provider keys are encrypted with an AES-GCM key held in Android Keystore. Message roles/timestamps and skill names/descriptions remain unencrypted metadata. Android backup is disabled.
- Implemented explicit voice input using Android `RecognizerIntent` and reply playback using Android `TextToSpeech`. Recognition audio is not stored by Island X; the Android speech provider may process audio remotely.
- Integrated assistant activity/reply events into the existing Dynamic Island event hub and overlay. The overlay displays a shortened response and offers a route back to Assistant.
- User-authored skills are prompt instructions only; they cannot execute scripts. Tool extensions can be injected through the `AssistantTool` interface and `AssistantRuntime` constructor.
- Not implemented: packaged on-device inference, token streaming, offline speech recognition, automatic fact extraction, vector retrieval, imported/executable community skills, scheduled or process-surviving background agents, connector/channel integrations, OpenJarvis traces/telemetry, or training/evaluation systems.
- Android runtime and physical-device behavior (live provider request, microphone recognizer availability, API key encryption on device, overlay, and tool confirmation) were not tested on a device. No provider credentials or test endpoint were available.