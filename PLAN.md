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
1. Cookies from the WebView after SSO are enough for OkHttp.
2. `sesskey` can be extracted from the main page HTML.
3. `service.php` answers with that cookie + `sesskey` without errors.
4. After the session expires, the re-login flow works.

- [ ] [owner] Fill the "SSO" and "Course list" sections of `docs/DISCOVERY.md` (hosts, cookie names, the exact AJAX method for the course list, sanitized sample response).
  - Discovery in progress: owner-confirmed hosts, Moodle cookie attributes, SSO cookie-name observations, callback path/query-parameter names, sesskey presence/syntax in `M.cfg`, course-list method/arguments/batch envelope and sanitized real course/config fixtures are recorded. SSO cookie attributes/authentication role, exact main-frame redirect chain, pagination termination and real error fixtures remain unverified. Slice 0 is owner-confirmed complete.
  - Fixture JSON/structure/sanitization checks and all 26 JVM unit tests pass locally (four AppLog, two fixture privacy, twelve parser, five cookie-security, three SSO URL-policy tests). Parser tests use real sanitized success/config fixtures and clearly labeled synthetic error cases. No live network behavior is verified by these tests.
- [x] [agent] `SsoSession` + WebView login screen restricted to the observed university and SSO hosts (external top-level links open in the system browser; no `addJavascriptInterface`). Callback recognition uses exact host/path without reading query values; HTTPS/host policy has tests. Real SSO navigation and cookie handoff need a phone/CI test.
- [x] [agent] `PersistentCookieJar` keyed by response host, AES-GCM with Android Keystore key. Cookie attribute/path/expiry and host-only/domain matching logic, AES-GCM tamper detection and browser-cookie import parsing have JVM tests. Android Keystore/SharedPreferences round-trip and actual WebView cookie handoff remain device/CI-unverified. CookieManager exposes only name/value; imported cookies use Secure host-only root-path session scope and may need refinement once actual attributes are verified.
- [ ] [agent] `sesskey` extractor (regex on `M.cfg`) + `MoodleAjaxClient` with interceptors: honest User-Agent, rate limit, session-expiry detection.
  - [x] Pure `M.cfg` sesskey extraction, balanced JSON-string-aware object scanning and typed input-safe failures, tested against the real sanitized config fragment. HTTP client/retry and live extraction remain pending.
- [ ] [agent] One real `DataSource`: course list -> Room (with `providerId`) -> a plain list screen.
  - [x] Shared `Course` model with `providerId` and remote ID; tolerant course-page DTO/parser tested against the real sanitized response. `nextoffset` is retained without inventing end-of-pagination semantics. DataSource, Room and UI are not wired yet.
- [ ] [agent] `NeedsLogin` state and re-login flow.
- [ ] [agent] Parser tests with fixtures, including expired session, malformed JSON, HTML instead of JSON.
  - Local parser coverage implemented; missing real error/empty/HTML responses are represented only by explicitly synthetic fixtures. HTTP/redirect/retry tests and real-device verification remain pending.
- [ ] [owner] Test on a real phone; report which of the four assumptions held, with the in-app log.

Done when: sign in, see the real course list, kill the session on the server side (or wait), see the app move to `NeedsLogin`, sign in again, list reloads. Findings written to `docs/DISCOVERY.md`.

**Status:** Slice 1 now has a visible restricted-host SSO WebView, encrypted cookie jar, callback recognition, session-cookie presence check, fixture-backed course/sesskey parsers and shared course model. Owner-supplied CI output confirms Android compilation and APK packaging, but one cookie test fails. Inspection of OkHttp 5.5.1's Android artifact identifies the cause: domain-cookie parsing loads the public-suffix list from Android assets and requires an initialized application context, unavailable in plain JVM tests. Changing domains or using `Cookie.Builder` did not fix that dependency; the earlier public-suffix rejection diagnosis was incorrect. Unit-test compile/runtime classpaths now substitute the same-version JVM artifact, leaving the APK on the Android artifact. All 26 tests pass via the standalone compiler, including domain-cookie parsing and public-suffix rejection; an isolated Gradle resolution check confirms the test-only substitution. Full Android Gradle/CI verification remains pending because local AGP resolution is blocked. Sign-in/callback behavior is not confirmed in-app; there is no AJAX client, Room cache, course screen, or NeedsLogin state yet. Slice 1 is not complete.

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
