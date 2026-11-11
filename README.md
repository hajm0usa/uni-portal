# University Portal — پرتال دانشگاه

Personal, read-only Android client for a Moodle-based university portal. Persian / RTL, Android 8+ (API 26). See [PLAN.md](PLAN.md) for the slice boundaries and [AGENTS.md](AGENTS.md) for the project rules.

## Current scope: Slice 1 — native course-request verification

- Compose placeholder screen; teal light/dark fallback and wallpaper colors on Android 12+.
- Bundled Vazirmatn font, including its SIL Open Font License in APK assets.
- **Long-press the app title** to open local diagnostics. Use the refresh/clear buttons; system Back returns to the placeholder.
- Logs are a 200-entry, process-memory-only buffer. Only fixed event IDs and exception class/stack frames are accepted; exception messages, causes and suppressed exceptions are not recorded. No logcat or disk logging.
- Slice 0 is owner-confirmed complete. Slice 1 adds sign-in and a native first-page course list, now backed by Room for offline access.
- Slice 1 now has a tolerant course-page parser, shared course models and a sesskey extractor, tested against the owner's real sanitized samples. Parse errors return fixed typed reasons without retaining response text or exception causes. DTOs stay inside the Moodle provider.
- A host-scoped OkHttp cookie jar encrypts persisted cookie records with AES-GCM and an Android Keystore key. The WebView import helper accepts only HTTPS cookies and conservatively assigns host-only/root-path/session scope because `CookieManager` does not expose original attributes.
- The visible WebView allows only `elearn.ut.ac.ir` and `sso1.ut.ac.ir`; external top-level links open outside the WebView. It recognizes the observed callback path without reading/logging query values, imports cookies only after return to Moodle, and requires the known Moodle session-cookie name. The owner confirmed successful in-app login and real course names appearing in the native list, verifying cookie import and authenticated OkHttp course fetching.
- After login, OkHttp fetches the main-page sesskey and the observed course-list AJAX method with the encrypted cookie jar and an honest User-Agent. Requests are serialized/spaced, responses are bounded, off-origin redirects are rejected, and session-key failures refresh/retry at most once. Session expiry shows a sign-in prompt; successful re-login reloads courses. The happy path is owner-confirmed; expiry/re-login and saved-cookie reuse across process restart remain unverified.
- Only the observed **first page** (offset 0, limit 24) is requested, explicitly labeled in the UI. Room stores that snapshot with provider-scoped keys, server order and an epoch-seconds timestamp. Startup shows saved data without a network request; refresh/session errors keep it visible with a saved-data warning. Last successful fetch time is shown in Jalali/Persian for Tehran. Pagination termination remains unknown; this is not a complete course inventory.
- The Moodle cache is cleared on logout and **before opening any login WebView**, because no verified account ID is available to isolate users. Cancelling login therefore also discards the old cache. A local clear failure blocks the transition rather than showing another account old data. Error/empty/HTML fixtures remain explicitly **synthetic**, not confirmed server responses.

Production application ID: `ir.hajmousa.uniportal`; debug: `ir.hajmousa.uniportal.debug`.

## Build and install (no Android Studio needed)

1. Push this repository to your GitHub repository. No remote is configured automatically.
2. Open **Actions → Build APK**. Every push and pull request runs unit tests and builds a debug APK; it can also be started manually.
3. Download the `uni-portal-debug` artifact, unzip it and install `app-debug.apk` on your phone.
4. Load courses online, close the app, enable airplane mode and reopen it: saved courses should appear without pressing refresh. Refresh offline should show an error without removing the saved list. Check that logout clears it, including after reopening. Test expired-session re-login separately; report only safe error labels/event names, never URLs, cookies or page contents.

Locally, with JDK 17+ and Android SDK platform 37 installed:

```sh
./gradlew testDebugUnitTest assembleDebug
```

The checked-in wrapper verifies the Gradle distribution checksum. GitHub Actions uses JDK 17. Gradle uses AGP's built-in Kotlin, upgraded to the version in the catalog to match the Compose compiler.

## Releases (only when ready)

Add repository Actions secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. `KEYSTORE_BASE64` is the base64 encoding of your signing keystore, not a password. **Back up the keystore and passwords offline: every future release must use the same key.** Do not put secrets in issues, commits or command output.

Push a tag such as `v0.1.0` once ready. The workflow first tests/builds debug, then builds an R8-minified, resource-shrunk signed release and creates a **draft** GitHub Release with `app-release.apk` and `latest.json`. Review it and publish manually. Unsigned release builds are never uploaded by CI; missing secrets fail the release job without affecting ordinary debug builds.

- Release `versionName` is the tag without `v`; only `vMAJOR.MINOR.PATCH` tags are accepted.
- CI `versionCode` is `github.run_number` for this workflow. Local builds default to code `1` and name `0.0.1`. Keep this workflow's version-code sequence increasing; moving releases to a new repository/workflow needs an explicit higher starting code.
- Debug and production installs coexist. CI's default debug key is disposable, so a later debug APK may require uninstalling the previous debug install. Production releases use your fixed key.
- The app-side update checker remains scheduled for Slice 7. `latest.json` is metadata only for now.

## Dependency and asset provenance

Versions were checked on 2026-11-11, adding only dependencies needed for this slice:

- AGP `9.5.1`, Activity Compose `1.13.0`, stable Compose BOM `2026.09.00`: Maven metadata ([AGP](https://maven.aliyun.com/repository/google/com/android/tools/build/gradle/maven-metadata.xml), [Activity](https://maven.aliyun.com/repository/google/androidx/activity/activity-compose/maven-metadata.xml), [BOM](https://maven.aliyun.com/repository/google/androidx/compose/compose-bom/maven-metadata.xml)). Google's documentation/metadata endpoints were unavailable here; this mirror was used for lookup only, not configured as a build repository.
- Kotlin `2.4.21`: [official release history](https://kotlinlang.org/docs/releases.html).
- Gradle `9.9.1`: [official release metadata](https://services.gradle.org/versions/current); wrapper JAR checked against its official SHA-256.
- API 37: [Google's current Compose sample configuration](https://github.com/android/compose-samples/blob/main/scripts/libs.versions.toml).
- JUnit `4.13.2`: [Maven Central metadata](https://repo.maven.apache.org/maven2/junit/junit/maven-metadata.xml).
- OkHttp `5.5.1`: current stable [Maven Central metadata](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml) and [official release tag](https://github.com/square/okhttp/releases/tag/parent-5.5.1), checked before adding it for the cookie jar. Browser cookie-attribute uncertainty is documented in `docs/DISCOVERY.md`.
- MockWebServer `5.5.1`: matching stable [Maven Central metadata](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/mockwebserver3/maven-metadata.xml), test-only.
- Lifecycle ViewModel/Compose `2.11.0`: latest non-prerelease in [Google metadata mirror](https://maven.aliyun.com/repository/google/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml). Coroutines Android/test `1.11.0`: stable [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml). These support lifecycle-owned off-main-thread loading and state collection.
- Room `2.8.5`: stable [Google metadata mirror](https://maven.aliyun.com/repository/google/androidx/room/room-runtime/maven-metadata.xml). KSP `2.3.13`: stable [Maven Central metadata](https://repo.maven.apache.org/maven2/com/google/devtools/ksp/symbol-processing-gradle-plugin/maven-metadata.xml). An isolated JVM build successfully generated and compiled the database/DAO with the same Kotlin/KSP/Room versions; schema version 1 is checked in. Robolectric `4.17`: latest stable [Maven Central metadata](https://repo.maven.apache.org/maven2/org/robolectric/robolectric/maven-metadata.xml), test-only for actual database and Android ICU checks.
- kotlinx.serialization JSON `1.12.0`: [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) and [official release notes](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0), checked before adding it for Slice 1. The release targets Kotlin 2.4.10 and is used with the project's Kotlin/compiler plugin 2.4.21.
- Vazirmatn regular/bold, unmodified, from [upstream v33.003](https://github.com/rastikerdar/vazirmatn/tree/v33.003), license in `app/src/main/assets/licenses/vazirmatn-OFL.txt`.

`MoodleProvider` registers the course `DataSource`, which returns only shared models. Each provider has its own session StateFlow: Unknown until a successful fetch, Active after success, NeedsLogin on session expiry. Other errors do not reset it or sign other providers out. The repository restores persisted login requirements (including before any successful cache), and uses a coroutine Mutex to serialize refreshes with account-change clearing. The ViewModel still offers re-login if saving the expiry error fails.

Verified here: **60 JVM tests** pass via the standalone Kotlin 2.4.21 compiler, including provider/session isolation and recovery, cache restoration without networking, preserved rows on failure, provider-scoped keys, empty snapshots, account-change clearing/failure, timestamp validation and clearing after an in-flight refresh. Room/KSP schema generation and generated-code compilation previously passed in an isolated JVM build; this increment changes no schema. Three actual-database Robolectric tests and two Android ICU date tests are added, but their local run was blocked downloading the Robolectric SDK jar (read timeout). Unit tests use OkHttp's JVM artifact because its Android variant needs application assets for public-suffix parsing. The full Android/Compose build remains unverified locally because AGP cannot be resolved from Google's repository; CI must run all 65 tests and produce an APK.

The owner confirms Slice 0, successful in-app SSO login and real course names appearing in the native list before the cache/provider increments. On-device offline caching/clearing was explicitly skipped, not verified. Cookie reuse across restart, session-expiry/re-login, pagination completeness and release signing also remain unverified. Site facts in `docs/DISCOVERY.md` come from owner observations/sanitized responses; local tests alone do not establish live-site behavior.
