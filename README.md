# University Portal — پرتال دانشگاه

Personal, read-only Android client for a Moodle-based university portal. Persian / RTL, Android 8+ (API 26). See [PLAN.md](PLAN.md) for the slice boundaries and [AGENTS.md](AGENTS.md) for the project rules.

## Current scope: Slice 1 — native course-request verification

- Compose placeholder screen; teal light/dark fallback and wallpaper colors on Android 12+.
- Bundled Vazirmatn font, including its SIL Open Font License in APK assets.
- **Long-press the app title** to open local diagnostics. Use the refresh/clear buttons; system Back returns to the placeholder.
- Logs are a 200-entry, process-memory-only buffer. Only fixed event IDs and exception class/stack frames are accepted; exception messages, causes and suppressed exceptions are not recorded. No logcat or disk logging.
- Slice 0 is owner-confirmed complete. Slice 1 adds sign-in and a native first-page course list; Room caching remains pending.
- Slice 1 now has a tolerant course-page parser, shared course models and a sesskey extractor, tested against the owner's real sanitized samples. Parse errors return fixed typed reasons without retaining response text or exception causes. DTOs stay inside the Moodle provider.
- A host-scoped OkHttp cookie jar encrypts persisted cookie records with AES-GCM and an Android Keystore key. The WebView import helper accepts only HTTPS cookies and conservatively assigns host-only/root-path/session scope because `CookieManager` does not expose original attributes.
- The visible WebView allows only `elearn.ut.ac.ir` and `sso1.ut.ac.ir`; external top-level links open outside the WebView. It recognizes the observed callback path without reading/logging query values, imports cookies only after return to Moodle, and requires the known Moodle session-cookie name. The owner confirmed successful in-app login and real course names appearing in the native list, verifying cookie import and authenticated OkHttp course fetching.
- After login, OkHttp fetches the main-page sesskey and the observed course-list AJAX method with the encrypted cookie jar and an honest User-Agent. Requests are serialized/spaced, responses are bounded, off-origin redirects are rejected, and session-key failures refresh/retry at most once. Session expiry shows a sign-in prompt; successful re-login reloads courses. The happy path is owner-confirmed; expiry/re-login and saved-cookie reuse across process restart remain unverified.
- Only the observed **first page** (offset 0, limit 24) is requested, explicitly labeled in the UI; courses are held in memory, not cached offline. Pagination termination and Room remain pending. Error/empty/HTML fixtures are explicitly **synthetic**, not confirmed server responses.

Production application ID: `ir.hajmousa.uniportal`; debug: `ir.hajmousa.uniportal.debug`.

## Build and install (no Android Studio needed)

1. Push this repository to your GitHub repository. No remote is configured automatically.
2. Open **Actions → Build APK**. Every push and pull request runs unit tests and builds a debug APK; it can also be started manually.
3. Download the `uni-portal-debug` artifact, unzip it and install `app-debug.apk` on your phone.
4. Sign in and check whether real course names appear. Then close/reopen the app and use the saved-session course button. Test expired-session re-login separately; report only safe error labels/event names from the hidden logs, never URLs, cookies or page contents.

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
- kotlinx.serialization JSON `1.12.0`: [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) and [official release notes](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0), checked before adding it for Slice 1. The release targets Kotlin 2.4.10 and is used with the project's Kotlin/compiler plugin 2.4.21.
- Vazirmatn regular/bold, unmodified, from [upstream v33.003](https://github.com/rastikerdar/vazirmatn/tree/v33.003), license in `app/src/main/assets/licenses/vazirmatn-OFL.txt`.

Verified here: all **44 JVM tests** pass (four AppLog, two fixture privacy, twelve parsers, five cookie-security, three SSO URL-policy, fifteen MockWebServer HTTP and three ViewModel tests), compiled directly with Kotlin 2.4.21 and its serialization compiler plugin. Coverage includes exact read-only payload/cookies/User-Agent, refresh/retry limits, redirects, HTML and malformed responses, response-size limits, network/storage failures, request serialization/spacing and UI-state transitions. ViewModel tests use the Android Lifecycle artifact with the test Main dispatcher. Unit tests use OkHttp's JVM artifact because its Android variant needs application assets for public-suffix parsing. The owner confirmed real course names appear on-device; the CI run/artifact has not been independently inspected. The complete Android build remains unverified locally because Gradle cannot resolve AGP from Google's repository and there is no local Android SDK.

The owner confirms Slice 0, successful in-app SSO login and real course names appearing in the native list. Cookie persistence across restart, session-expiry/re-login, pagination completeness and release signing remain unverified. Site facts in `docs/DISCOVERY.md` come from owner observations/sanitized responses; local HTTP tests alone do not establish live-site behavior.
