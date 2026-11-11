---
name: github-actions-apk
description: Use when setting up or changing the Gradle build, signing, GitHub Actions workflow, release APKs, latest.json or the update checker for the university app (no Android Studio on the owner's machine).
---

# Build and release on GitHub Actions

The owner has no Android Studio; CI is the only build. Keep the loop short: every push produces a debug APK artifact.

## Gradle

- Single module `app`, Kotlin DSL, version catalog `gradle/libs.versions.toml`. Look up the current stable versions of AGP, Kotlin, Compose BOM, Room, OkHttp, WorkManager, Glance, kotlinx.serialization and Jsoup when you add them; do not trust memory.
- `minSdk 26`, `targetSdk` = current stable API level, `compileSdk` the same.
- Commit the Gradle wrapper. Release build: R8 minify + shrink with keep rules for kotlinx.serialization and Room.
- `versionName` from the git tag (`v0.1.0` -> `0.1.0`), `versionCode` from a CI run number or tag math; document the rule.

## Workflow `.github/workflows/build-apk.yml` (shape)

- Triggers: `push`, `pull_request`, and tags `v*`.
- Steps: checkout, JDK 17 (temurin), `gradle/actions/setup-gradle`, `./gradlew testDebugUnitTest assembleDebug`, upload `app-debug.apk` as an artifact.
- On tag `v*`: decode the keystore from the secret, `./gradlew assembleRelease`, create a GitHub Release and attach the signed APK and `latest.json`.
- Pin third-party actions to a major version or commit SHA; do not use unmaintained actions.

## Signing

- Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Decode to a temp file in the runner; never echo them, never commit the keystore.
- Use one fixed key forever; otherwise friends must uninstall and lose data. Tell the owner to back up the keystore somewhere safe (offline).
- Debug builds use the default debug key and a different `applicationIdSuffix` (`.debug`) so both can coexist.

## `latest.json` (update checker)

Published as a release asset (or a file in the repo):

```json
{ "versionName": "0.1.0", "versionCode": 1, "apkUrl": "https://github.com/<owner>/<repo>/releases/download/v0.1.0/app-release.apk", "notes": "..." }
```

The app reads it on open (plain HTTPS GET, no cookies), compares `versionCode`, and shows a banner with a link. No silent install, no `REQUEST_INSTALL_PACKAGES`.

## Debug loop helpers

- The hidden in-app log screen (Slice 0) is the main debugging tool; make sure it shows exceptions with stack traces (no URLs with query, no cookies, no bodies).
- Upload test reports as an artifact when tests fail.
- Keep CI under a few minutes: Gradle cache on, no unnecessary matrix.

## Do not

- Do not store secrets in the repo, in logs or in artifacts.
- Do not publish the APK anywhere public without the owner's say (the audience is the owner and a few friends).
