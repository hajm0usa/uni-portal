# University Portal — پرتال دانشگاه

Personal, read-only Android client for a Moodle-based university portal. Persian / RTL, Android 8+ (API 26). See [PLAN.md](PLAN.md) for the slice boundaries and [AGENTS.md](AGENTS.md) for the project rules.

## Home redesign — approved, native implementation awaiting verification

[Open the browser prototype](docs/design/home/index.html) or see its
[preview instructions](docs/design/home/README.md). Persian RTL, light/dark
themes, real variable font weights, and six sample data states. This is a design
reference using fictional data. The owner approved it, and native Home now follows
that layout: transparent logo, nearest deadline without duplication, grouped
events, course shortcuts, and a compact account menu. Existing authentication,
cache clearing, sync and diagnostics are preserved. Native layout/device checks
and the APK build remain unverified; the local build still cannot resolve AGP.
All app Kotlin sources, including Compose, compile in an isolated Android-API
harness with the Compose compiler and generated R/BuildConfig stand-ins. That
does not verify Android resources, KSP output, manifest merging, packaging or
runtime behavior; the app's build repositories and dependencies are unchanged.

Home adds no UI event count cap. Fetching **all** live events still needs confirmed
pagination; the source currently requests six and native Home explicitly says the
feed is incomplete. Design approval does not change that server-data limitation.

## Current scope: Slice 3 — course/folder screens (downloads pending)

- Native Today, Courses and Calendar agenda tabs; Grades/Messages are coming-soon cards. Teal light/dark fallback and wallpaper colors on Android 12+.
- Bundled Vazirmatn font, including its SIL Open Font License in APK assets.
- **Long-press the app title** to open local diagnostics. Use the refresh/clear buttons; system Back returns to the selected tab.
- Logs are a 200-entry, process-memory-only buffer. Only fixed event IDs and exception class/stack frames are accepted; exception messages, causes and suppressed exceptions are not recorded. No logcat or disk logging.
- Slice 0 is owner-confirmed complete. Slice 1 adds sign-in and a native first-page course list, now backed by Room for offline access.
- Slice 1 now has a tolerant course-page parser, shared course models and a sesskey extractor, tested against the owner's real sanitized samples. Parse errors return fixed typed reasons without retaining response text or exception causes. DTOs stay inside the Moodle provider.
- A host-scoped OkHttp cookie jar encrypts persisted cookie records with AES-GCM and an Android Keystore key. The WebView import helper accepts only HTTPS cookies and conservatively assigns host-only/root-path/session scope because `CookieManager` does not expose original attributes.
- The visible WebView allows only `elearn.ut.ac.ir` and `sso1.ut.ac.ir`; external top-level links open outside the WebView. It recognizes the observed callback path without reading/logging query values, imports cookies only after return to Moodle, and requires the known Moodle session-cookie name. The owner confirmed successful in-app login and real course names appearing in the native list, verifying cookie import and authenticated OkHttp course fetching.
- **Owner-requested automatic sign-in (decision #26):** enter credentials in the Persian native form and choose “save and sign in”. Username/password are stored only as AES-GCM ciphertext under a separate Android Keystore key; backup/device-transfer rules explicitly exclude this storage. No fingerprint is required. Credentials are never saved in activity state or logs, and login screens prevent screenshots, autofill and WebView debugging. Android plaintext strings necessarily exist briefly in memory; encryption is not protection against a compromised/rooted device.
- On a fresh app entry with saved credentials, the app checks the saved native session once. A `NeedsLogin` result opens one automatic recovery flow. It uses the existing visible WebView and the live SSO form's anti-forgery token, cookies and ReturnUrl, never captured values. Both native code and the script check the exact HTTPS SSO origin; the script also checks the observed form action/fields before filling anything. One automatic submission per flow, no background attempts/retry loop. Wrong credentials, challenges, TLS problems or changed forms require manual intervention; editing credentials explicitly permits another attempt. Rotation during submission does not silently resubmit.
- After login, OkHttp fetches the main-page sesskey and the observed course-list AJAX method with the encrypted cookie jar and an honest User-Agent. Requests are serialized/spaced, responses are bounded, off-origin redirects are rejected, and session-key failures refresh/retry at most once. Session expiry shows a sign-in prompt; successful re-login reloads courses. The happy path is owner-confirmed; expiry/re-login and saved-cookie reuse across process restart remain unverified.
- Only the observed **first page** (offset 0, limit 24) is requested, explicitly labeled in the UI. Room stores that snapshot with provider-scoped keys, server order and an epoch-seconds timestamp. Startup shows saved data first, before the optional saved-credential session check; refresh/session errors keep it visible with a saved-data warning until a new login flow clears it. Last successful fetch time is shown in Jalali/Persian for Tehran. Pagination termination remains unknown; this is not a complete course inventory.
- The Moodle cache is cleared on logout and **before opening any login WebView**, because no verified account ID is available to isolate users. Cancelling login therefore also discards the old cache. A local clear failure blocks the transition rather than showing another account old data. Error/empty/HTML fixtures remain explicitly **synthetic**, not confirmed server responses.
- Native/WebView cookies are also cleared before a fresh login, avoiding an old account's browser session bypassing newly entered credentials. Logout deletes the credential key/ciphertext and cookies even before attempting Room cleanup; failures are reported rather than claiming deletion succeeded. “Manual login without saving” and editing credentials forget the previous saved login first. The native Moodle client, request pacing and educational-data read-only rules are unchanged; only authentication-form submission is authorized.

Production application ID: `ir.hajmousa.uniportal`; debug: `ir.hajmousa.uniportal.debug`.

## Build and install (no Android Studio needed)

1. Push this repository to your GitHub repository. No remote is configured automatically.
2. Open **Actions → Build APK**. Every push and pull request runs unit tests and builds a debug APK; it can also be started manually.
3. Download the `uni-portal-debug` artifact, unzip it and install `app-debug.apk` on your phone.
4. Load courses online, close the app, enable airplane mode and reopen it: saved courses should appear without pressing refresh. Refresh offline should show an error without removing the saved list. Check that logout clears it, including after reopening. Test expired-session re-login separately; report only safe error labels/event names, never URLs, cookies or page contents.

Locally, with JDK 17+ and Android SDK platform 37 installed:

```sh
node scripts/test-sso-login.cjs
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
- Vazirmatn regular/medium/semibold/bold, unmodified, from [upstream v33.003](https://github.com/rastikerdar/vazirmatn/tree/v33.003), license in `app/src/main/assets/licenses/vazirmatn-OFL.txt`. Medium and semibold were added for the approved Home hierarchy; their SHA-256 values are `b986623e4ddef10755e04be39f8ea7bcb1dc08bfe8dd0aa6af395736f256ad4a` and `3f239d8364c14ee32c46b6839fa197c28958358b82a9673d2c6acc8506d78bc5` respectively.

Latest Home regression check: **127 lightweight JVM tests** and the Node SSO
checks pass. Six new tests cover deadline selection, grouping, provider identity,
no UI truncation and Tehran date boundaries. There are **146 test declarations**;
19 Android-backed tests, including the new Persian Home date/time test, remain
unverified. Earlier counts below describe the previous slices' verification.

`MoodleProvider` registers the course `DataSource`, which returns only shared models. Each provider has its own session StateFlow: Unknown until a successful fetch, Active after success, NeedsLogin on session expiry. Other errors do not reset it or sign other providers out. The repository restores persisted login requirements (including before any successful cache), and uses a coroutine Mutex to serialize refreshes with account-change clearing. The ViewModel still offers re-login if saving the expiry error fails.

Verified here: **121 lightweight JVM tests** pass via the standalone Kotlin 2.4.21 compiler, including course/folder parsing, HTTP reads, detail navigation/state/cancellation and browser-link validation, calendar mapping/HTTP/provider/cache, foreground sync coordination and shared PortalViewModel state/cancellation coverage, credential encryption/tamper/invalid-input checks, exact-origin/one-attempt/escaping checks and the existing course/cache/parser/HTTP/ViewModel tests. The dependency-free Node test exercises the actual SSO script against the valid form model and 27 unsafe/changed form cases; CI runs it before Gradle. A local Chromium check confirms the real sanitized nested-form excerpt produces the owner-observed field order, including both RememberLogin fields. Android auth/data/factory sources also pass syntax/type checks against minimal API stubs, which are **not** an Android build or Keystore verification.

Slice 2 now wires the confirmed calendar method, a six-event seven-day preview DataSource and provider-scoped Room caching into Today/Calendar agenda screens. Both share one PortalViewModel and the Courses tab shares its refresh/session lifecycle. Foreground entry checks independently stale sections (30-minute threshold); explicit pull/button refresh forces both. A per-provider mutex serializes multi-section sync and clearing; requests share one paced Moodle client. Failed refreshes preserve old data, session expiry stops the next section without a loop, and leaving the foreground/content cancels refresh. Login/logout clears both cached sections; logout waits for in-flight refresh cancellation before removing cookies. All screens retain Persian/RTL theming and safe diagnostics. This is not a complete calendar: pagination and quiz/exam mapping remain unknown. Compose compilation, gestures and live device behavior still need verification.

Slice 3 now connects sanitized course/folder fixtures, Jsoup parsers and selected-page GETs to native nested detail screens. Open course content from the Courses tab, then a folder to list files; Back returns to the parent without refetching its recent in-memory content. Pull/button refresh retains old content on error. Unsupported activities open only validated read-only Moodle views in the external browser, which has a separate session. Detail requests share the dashboard provider/client/session/lock, and detail expiry reaches the existing foreground login flow. Navigation/background cancellation drops obsolete results; login/logout hides detail memory. Content is explicitly memory-only, not an offline Room cache. Native downloads/opening, SAF storage and download workers are not implemented; use the folder's browser fallback for now. Jsoup 1.24.1 was checked against Maven Central before addition. Decision #27 authorizes ETag/Last-Modified refresh policy, still unimplemented. Slice 2 CI/device verification was explicitly skipped, not passed.

Folder detail screens also offer a persistent SAF download-folder picker with Persian Android 11+ restriction guidance. The tree URI is stored in DataStore after verifying persisted read/write access and directory create support; access is rechecked on resume. Missing/revoked/unwritable folders prompt selection again. Cancelling or failing a new selection preserves the existing choice. This prepares storage only: no file download/open action is enabled yet. DataStore Preferences 1.3.0 was checked against the official AndroidX stable release page before addition.

Room version 2 adds `agenda_item` with an explicit non-destructive 1→2 migration. Room/KSP schema generation and generated Kotlin compilation pass in isolation with minimal Android API stubs; a SQLite self-check confirms migration SQL matches the generated schema and preserves old rows. Eighteen Android-backed tests need CI: six database, two ICU date, four credential-storage and six new folder-storage tests. The folder store/ViewModel and new tests compile in an isolated Android-API JVM harness, but its tests hit dependency-download and SDK-runtime-classpath failures, not a passing run. The full Android/Compose build remains blocked resolving AGP; CI must run all **139 JVM test declarations**, the Node checks and produce an APK. Actual Android migration, Keystore, SAF picker/grants/restart behavior, UI lifecycle and live calendar/content behavior are not proven by these local checks. The owner confirmed automatic sign-in works and accepted Slice 1; that confirmation does not separately prove every failure/recovery path.

The owner confirms Slice 0, successful in-app SSO login and real course names appearing in the native list before the cache/provider increments. On-device offline caching/clearing was explicitly skipped, not verified. Cookie reuse across restart, session-expiry/re-login, pagination completeness and release signing also remain unverified. Site facts in `docs/DISCOVERY.md` come from owner observations/sanitized responses; local tests alone do not establish live-site behavior.
