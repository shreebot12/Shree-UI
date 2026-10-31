# Island X

Native Android interaction layer built with Kotlin and Jetpack Compose. See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) for verified implementation and release blockers.

## Build

Install JDK 17, Android SDK Platform 36, and Android Build Tools 36.0.0, then configure `ANDROID_HOME` or `ANDROID_SDK_ROOT`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease
```

## Island X Assistant

Open **Assistant** from the control room. Configure an OpenAI-compatible HTTPS endpoint, a model ID, and an optional API key. The key is encrypted with Android Keystore. Local compatible endpoints are supported at `localhost`, `127.0.0.1`, or the Android emulator host `10.0.2.2`; API keys are never sent over HTTP. Remote model providers receive the messages, selected local memories, and enabled skill instructions you submit.

The assistant supports on-demand Android speech recognition and system text-to-speech, encrypted chat/memory/skill storage, memory search, device/time information, and opening an installed app. Opening apps, disclosing device details, and saving a memory from a tool require individual approval. Skills are user-authored instructions, not executable scripts. Listening/processing status and short replies appear in the existing floating capsule while the overlay is enabled.

Island X does not include model weights or an Android inference runtime. For local inference, configure a compatible model server reachable from the device. Voice recognition is provided by Android's selected speech service and may use that service's network.

See [docs/openjarvis-android-mapping.md](docs/openjarvis-android-mapping.md) for the OpenJarvis architecture audit, feature mapping, license notes, and Android limitations.

## Download an APK from GitHub Actions

Every push to `main` automatically builds and tests the app. After the run succeeds, download `island-x-debug-apk` from the run's **Artifacts** section. The artifact contains the installable debug APK and is retained for 14 days. Pull requests also build and upload the APK.

## Edge Navigation

Edge navigation is optional and disabled by default. Enable it from Edge Control, then explicitly enable the Island X accessibility service in Android settings. The service observes foreground package names only to maintain a local history; it does not retrieve screen content. Edge strips use Android's accessibility-overlay window type and require the accessibility service, not draw-over-other-apps access.

Smart App Return uses eligible apps observed while the feature is enabled. It attempts to launch the prior app's launcher entry with task-reuse flags and opens system Recents when there is no eligible history or launch fails. Android does not expose a complete Recents task list to ordinary apps, so returning to an exact existing task cannot be guaranteed.

An intercepted edge swipe invokes Android's global Back action, but the platform's native predictive-back animation may not run for that custom gesture. Disable Island X edge navigation to restore native system edge handling. Other apps, system navigation, OEM gesture handlers, and secure surfaces can still take priority; edge interception is not universal.

Only assistant conversations sent to a user-configured model endpoint leave the device; other Island X features continue to process locally. The official Play target API requirements page was checked October 30, 2026: new apps and updates must target Android 16 (API 36) or higher beginning August 31, 2026. This project targets API 36; signing, policy disclosures, pre-launch testing, and Play approval remain outstanding publisher responsibilities.