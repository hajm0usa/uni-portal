# PLAN.md

Work strictly in order. Do not start a slice before the previous one is confirmed by the owner. Update the checkboxes as you go.

Legend: `[owner]` = needs input or action from the owner; `[agent]` = agent work.

---

## Slice 0 — Project skeleton and CI

Goal: an empty but real app that builds in GitHub Actions and installs on a phone.

- [x] [owner] Choose app name, `applicationId` (e.g. `ir.example.uniportal`), and a base color for the fixed palette. Confirmed: پرتال دانشگاه, `ir.hajmousa.uniportal`, teal `#006A60`.
- [x] [agent] Gradle project (Kotlin, Compose BOM, Material 3), `minSdk 26`, `libs.versions.toml` with current stable versions, `supportsRtl=true`, `allowBackup=false`, no cleartext traffic. Owner reports GitHub Actions succeeds; the run has not been independently inspected.
- [x] [agent] Package layout: `core`, `auth`, `data`, `provider.moodle`, `feature`, `widget`, `notify`.
- [x] [agent] `AppLog` wrapper + hidden in-app log screen (ring buffer, no URLs with query, no tokens/cookies/bodies). Long-press the title; unit tests cover privacy, eviction, snapshots and concurrent writes.
- [x] [agent] Theme: Material You dynamic color on API 31+, fixed palette below that; Persian font bundled; RTL. Owner confirmed the Persian RTL screen works on the phone; API/theme variants are not independently tested.
- [x] [agent] `.github/workflows/build-apk.yml` (see skill `github-actions-apk`): debug APK artifact on every push; release APK on tag. Written; signed tag builds create a draft release for owner review.
- [ ] [owner] Add signing secrets to GitHub (see skill) — can wait until the first release.

Done when: CI is green, the debug APK installs, shows a Persian RTL placeholder screen and the hidden log screen.

**Status: complete, owner-confirmed.** The owner reports successful GitHub Actions, APK installation, Persian RTL layout and working hidden logs, and previously requested Slice 1. The run/artifact and phone behavior have not been independently inspected. Gradle wrapper and six JVM unit tests pass locally; the last local Android build stopped at Google repository plugin resolution and produced no local APK. Release signing can wait until the first release. Slice 1 implementation may now begin; do not start Slice 2 without completing Slice 1 and obtaining owner confirmation.

---

## Slice 1 — Thin vertical slice (the risk-killer)

Goal: prove the four risky assumptions with real code, in the smallest possible app.

Assumptions to verify:
1. [x] Cookies from the WebView after SSO are enough for OkHttp — owner confirmed real course names appear in the native list.
2. [x] `sesskey` can be extracted from the main page HTML — exercised by the same successful native course fetch.
3. [x] `service.php` answers with that cookie + `sesskey` without errors — exercised by the same successful native course fetch.
4. [ ] After the session expires, the re-login flow works.

- [ ] [owner] Fill the "SSO" and "Course list" sections of `docs/DISCOVERY.md` (hosts, cookie names, the exact AJAX method for the course list, sanitized sample response).
  - Discovery in progress: owner-confirmed hosts, Moodle cookie attributes, SSO cookie-name observations, callback path/query-parameter names, sesskey presence/syntax in `M.cfg`, course-list method/arguments/batch envelope and sanitized real course/config fixtures are recorded. SSO cookie attributes/authentication role, exact main-frame redirect chain, pagination termination and real error fixtures remain unverified. Slice 0 is owner-confirmed complete.
  - 65 lightweight JVM tests pass via the standalone Kotlin compiler, including credential encryption, exact-origin/one-attempt script gating/escaping, provider/session, repository/cache and ViewModel coverage. Node checks cover the valid SSO form model and 27 unsafe/changed cases; local Chromium confirms the sanitized real nested form's submitted control order. Nine Android-backed tests need CI (including four new credential-store tests); the earlier Robolectric SDK download timed out. CI must run all 74 JVM test declarations, Node checks and build the APK. Local API-stub auth compilation is not Android/Keystore verification. Real-site facts still require owner verification.
- [x] [agent] `SsoSession` + WebView login screen restricted to the observed university and SSO hosts (external top-level links open in the system browser; no `addJavascriptInterface`). Callback recognition uses exact host/path without reading query values; HTTPS/host policy has tests. Owner confirmed on-device login closes the WebView, shows success and loads real course names through OkHttp.
- [x] [agent] `PersistentCookieJar` keyed by response host, AES-GCM with Android Keystore key. Cookie attribute/path/expiry and host-only/domain matching logic, AES-GCM tamper detection and browser-cookie import parsing have JVM tests. Owner-confirmed native course loading exercises WebView import, encrypted jar write/read and authenticated OkHttp requests; process-restart persistence remains unverified. CookieManager exposes only name/value; imported cookies use Secure host-only root-path session scope and may need refinement once actual attributes are verified.
- [x] [agent] `sesskey` extractor (regex on `M.cfg`) + `MoodleAjaxClient` with honest User-Agent, serialized requests with a 200 ms gap, bounded response size/timeouts and session-expiry detection. Redirects within the Moodle origin are followed; SSO/off-origin redirects are rejected before an outbound follow-up. Tests pass locally; owner confirmed real course names appear, exercising live extraction and AJAX parsing. The CI run/artifact has not been independently inspected.
  - [x] Pure `M.cfg` extraction plus one key refresh/retry on `invalidsesskey` / `servicerequireslogin`; a second failure requires login. A new key is fetched for every manual load and after WebView login, never persisted or logged.
  - Owner supplied the direct `/login/index.php` entry point; the login WebView now starts there. Native client base URL and cookie import are unchanged. Updated entry-point behavior needs a device check.
- [x] [agent] One real `DataSource`: course list -> Room (with `providerId`) -> a plain list screen.
  - [x] Shared `Course` model and tolerant parser are wired through `CourseRepository` and Room to the Persian first-page list. Version 1 has provider-scoped course keys, ordered rows and per-section sync metadata; successful replacement is transactional and failures preserve the prior snapshot. Startup restores saved rows without networking; loading/errors/NeedsLogin retain them. Last-fetch time uses epoch seconds/Jalali display. Cache clears before any login and on logout, blocking the transition if clearing fails. `MoodleProvider` registers the suspend course `DataSource`; the repository consumes shared models and uses the supplied provider ID for cache operations. A coroutine Mutex preserves refresh/clear serialization across suspension. No schema/dependency changes. Full Android build remains pending; the owner explicitly skipped on-device offline verification. Pagination termination is still unknown.
- [ ] [agent] `NeedsLogin` state and re-login flow.
  - [x] Each provider owns a `ProviderSession` StateFlow: Unknown until a fetch proves Active, NeedsLogin on bounded session failure, Active after recovery. Other errors preserve its state; one provider never changes another's session or shared SSO cookies. Startup restores a persisted login requirement even without a successful cached page; successful account-change clearing resets it to Unknown. The ViewModel uses this state even when saving the expiry error fails. The sign-in button opens the existing WebView, and successful login reloads courses. MockWebServer/ViewModel tests pass; actual server-expiry/re-login behavior remains unverified.
- [ ] [agent/owner] Owner-requested saved credentials and automatic SSO sign-in (decision #26 supersedes the previous manual-only/password prohibition).
  - [x] Native Persian credential entry, separate Keystore/AES-GCM credential storage, explicit backup exclusions and screenshot/debug/autofill protection; no credential fields in saved-instance state or logs. Logout destroys the key/ciphertext, clears cookies and attempts cache cleanup; failures are visible.
  - [x] Fresh live-form automation on the confirmed SSO origin and observed POST action; preserves browser form ownership, live ReturnUrl/anti-forgery token and duplicate RememberLogin fields. One automatic submission per flow, foreground only; changed/challenged forms fall back to manual login. One automatic recovery flow per app entry; explicit retry/edit remains possible. Fresh login clears old native/browser cookies so an old account cannot bypass newly entered credentials. Disposed WebViews cannot import late cookies after a subsequent clear.
  - [ ] CI APK and Android-backed tests; device verification of secure storage/restart reuse, successful automatic SSO, wrong-password/challenge fallback, logout deletion and server-expiry recovery. No real credentials or captured tokens were used in local tests.
- [ ] [agent] Parser tests with fixtures, including expired session, malformed JSON, HTML instead of JSON.
  - Parser and HTTP/redirect/retry coverage implemented; missing real error/empty/HTML responses are represented only by explicitly synthetic fixtures. Happy-path native course fetching is owner-confirmed; real-device error/retry verification remains pending.
- [ ] [owner] Test on a real phone; report which of the four assumptions held, with the in-app log.
  - Owner confirmed successful in-app SSO login and real course names in the native list, verifying assumptions 1–3. Process-restart cookie reuse and server-expiry/re-login remain unverified; no device log was supplied.

Done when: sign in, see the real course list, kill the session on the server side (or wait), see the app move to `NeedsLogin`, sign in again, list reloads. Findings written to `docs/DISCOVERY.md`.

**Status:** Native course fetching is owner-confirmed, verifying assumptions 1–3. Room/provider integration and owner-authorized saved-credential automatic SSO are implemented. There are 65 passing lightweight JVM tests plus Node script checks; nine Android-backed tests and full APK compilation need CI (74 JVM test declarations total). Local Android builds remain blocked by AGP resolution. Automatic sign-in, Android Keystore operation and expired-session recovery require device verification. Offline-device verification was explicitly skipped, not passed; pagination completeness remains unknown. Slice 1 is not complete.

---

## Slice 2 — Today screen, assignments, calendar

- [ ] [owner] Fill `docs/DISCOVERY.md` sections "Calendar / action events" (and assignments if they use a different method).
- [ ] [agent] Shared `AgendaItem` model and mapping from Moodle events (deadlines, quiz close times as exam events).
- [ ] [agent] `SyncCoordinator` (foreground trigger, ~30 min threshold, per-provider mutex, pull-to-refresh), "last updated" label.
- [ ] [agent] `NavigationBar` shell (Today, Courses, Calendar, Grades, Messages; unfinished tabs show a "coming soon" card).
- [ ] [agent] Today screen with empty, stale ("last updated 3 days ago") and `NeedsLogin` states.
- [ ] [agent] Calendar screen (month/agenda) with Jalali dates and Persian digits.

Done when: Today and Calendar work from real data and from Room offline.

---

## Slice 3 — Courses, content, file downloads

- [ ] [owner] Fill `docs/DISCOVERY.md` section "Course content" (AJAX or HTML only?) and "File URLs" (what `pluginfile.php` URLs look like, sanitized).
- [ ] [agent] Course detail screen (sections, modules, files) as a nested screen.
- [ ] [agent] Folder picker flow (SAF) with persisted permission and the Android 11+ restrictions explained to the user.
- [ ] [agent] Downloader (OkHttp + WorkManager, foreground info for large files, `Content-Type` check, `timemodified` re-download rule), `FileEntry` state in Room.
- [ ] [agent] Open file via `FileProvider`/content URI, "share" action, handle "file no longer exists" -> state reset.

Done when: a PDF downloads into the chosen folder, opens, survives app restart, and a deleted file is detected when opened.

---

## Slice 4 — Grades

- [ ] [owner] Fill `docs/DISCOVERY.md` section "Grades" (AJAX or HTML). Provide a sanitized sample.
- [ ] [agent] `GradesSource` (Jsoup if HTML, with a fixture-tested parser), grades screen.
- [ ] [agent] If parsing proves too fragile, fall back to the "open in browser" card and record why.

---

## Slice 5 — Widget and reminders

- [ ] [agent] Glance widget "upcoming deadlines", reads Room only, shows "last updated" and a button that opens the app.
- [ ] [agent] Reminders (24 h and 3 h before deadlines/exams) from Room with `setAndAllowWhileIdle`; reschedule after each successful sync and on boot; POST_NOTIFICATIONS permission flow.
- [ ] [agent] Local "sign in again" alarm (~3 days after the last successful sync).
- [ ] [agent] "New items" badge on app open.

---

## Slice 6 — Messages (last)

- [ ] [owner] Fill `docs/DISCOVERY.md` section "Messages".
- [ ] [agent] Messages screen, fetched live only; never in Room, logs or bug reports (add a test that scans the log buffer).

---

## Slice 7 — Release tooling

- [ ] [agent] Update checker (`latest.json` from GitHub), banner with a link, no silent install.
- [ ] [agent] Manual "copy bug report" button (method name, error code, response structure with values removed; no messages).
- [ ] [owner] Create the signing key, add secrets, tag `v0.1.0`, send the APK to friends.

---

## Later (not scheduled)

- Second provider on the same SSO (validates `Provider` and `AgendaItem`).
- Silent session refresh in the background, only if the session-lifetime test shows the SSO cookie lives for days.
- Optional Gregorian display setting.
- `ActionProvider` for write operations, only with explicit user confirmation.
