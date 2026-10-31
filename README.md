# Island X

Native Android interaction layer built with Kotlin and Jetpack Compose. See [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) for verified implementation and release blockers.

## Build

Install JDK 17, Android SDK Platform 36, and Android Build Tools 36.0.0, then configure `ANDROID_HOME` or `ANDROID_SDK_ROOT`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:bundleRelease
```

## Edge Navigation

Edge navigation is optional and disabled by default. Enable it from Edge Control, then explicitly enable the Island X accessibility service in Android settings. The service observes foreground package names only to maintain a local history; it does not retrieve screen content. Edge strips use Android's accessibility-overlay window type and require the accessibility service, not draw-over-other-apps access.

Smart App Return uses eligible apps observed while the feature is enabled. It attempts to launch the prior app's launcher entry with task-reuse flags and opens system Recents when there is no eligible history or launch fails. Android does not expose a complete Recents task list to ordinary apps, so returning to an exact existing task cannot be guaranteed.

An intercepted edge swipe invokes Android's global Back action, but the platform's native predictive-back animation may not run for that custom gesture. Disable Island X edge navigation to restore native system edge handling. Other apps, system navigation, OEM gesture handlers, and secure surfaces can still take priority; edge interception is not universal.

The official Play target API requirements page was checked October 30, 2026: new apps and updates must target Android 16 (API 36) or higher beginning August 31, 2026. This project targets API 36; signing, policy disclosures, pre-launch testing, and Play approval remain outstanding publisher responsibilities.