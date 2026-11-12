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

- [x] [owner] Accept Slice 1 as complete. Owner confirmed automatic sign-in works on the device and explicitly declared the slice complete. Remaining unchecked verification items above are retained as evidence gaps, not blockers to owner acceptance or claims that those checks passed.

**Status: complete, owner-accepted.** Native course fetching and automatic SSO sign-in are owner-confirmed. Room/provider integration is implemented. At acceptance, 65 lightweight JVM tests and Node script checks passed; the latest CI run, nine Android-backed tests and full APK compilation were not independently verified (74 JVM test declarations then). Local Android builds remain blocked by AGP resolution. Detailed restart, challenge, logout-deletion and real server-expiry checks are not separately confirmed. Offline-device verification was explicitly skipped, not passed; pagination completeness remains unknown. The owner subsequently authorized Slice 2; progress is tracked below.

---

## Slice 2 — Today screen, assignments, calendar

- [ ] [owner] Fill `docs/DISCOVERY.md` sections "Calendar / action events" (and assignments if they use a different method).
  - Confirmed `core_calendar_get_action_events_by_timesort`, observed arguments and a real assignment-deadline response. Sanitized `calendar_ok.json` added; both fixture-privacy tests pass locally. Exam/quiz event shape, server time-boundary and pagination semantics, and real empty/error/HTML responses remain unknown. Parser, preview HTTP/DataSource, Room caching and screen integration are implemented; CI/device verification is pending.
- [ ] [agent] Shared `AgendaItem` model and mapping from Moodle events (deadlines, quiz close times as exam events).
  - [x] Shared epoch-seconds model and fixture-tested calendar parser. Confirmed `mod_assign` / `assign` / `due` maps to DEADLINE; unconfirmed types remain OTHER, never guessed exams. Optional fields are tolerated; malformed/incomplete/duplicate events fail the section safely. Shared envelope/session-error parsing is reused by courses. Only plain HTTPS Moodle assignment-view links survive mapping; action/edit/delete/credential URLs are excluded.
  - [x] `MoodleProvider.agendaPreview` registers the shared-model DataSource. It requests the observed six-event limit for today's Tehran midnight through midnight seven days later, calculated from an injected `Clock`. Calendar and courses share one HTTP semaphore, pacing, cookie handling and bounded sesskey recovery. No new request is made merely by registering the source. This is a preview, not a complete calendar or a verified server-side interval-boundary rule.
  - [x] Provider-scoped Room agenda-preview cache and repository, including server order, epoch timestamps, successful empty snapshots and safe per-section errors. Failed refreshes retain the previous snapshot. Account-transition clearing deletes both course and agenda rows/metadata transactionally, using the same refresh/clear mutex so an in-flight agenda refresh cannot repopulate a cleared cache. The existing login/logout caller therefore also clears agenda data. Room version 2 adds only `agenda_item`; explicit 1→2 migration preserves courses and sync state. Schema 2 is exported from KSP, not hand-invented.
  - 100 lightweight JVM tests pass locally, including sync freshness/force/concurrency, section-error isolation, shared-session expiry without retry loops, cache-first PortalViewModel state, refresh cancellation and account-change failure handling. Existing provider/cache/auth tests still pass. Node SSO checks pass for the valid model and 27 unsafe/changed forms. Isolated Room/KSP generation and generated Kotlin compilation previously passed using minimal Android API stubs; a SQLite migration/schema self-check passed. Twelve Android-backed tests need CI (six database, two date, four credential store); all 112 test declarations and the APK build still need CI because local AGP 9.5.1 resolution is blocked. Compose UI, device migration, live preview, quiz/exam mapping and pagination completeness remain unverified.
- [ ] [agent] `SyncCoordinator` (foreground trigger, ~30 min threshold, per-provider mutex, pull-to-refresh), "last updated" label.
  - [x] Implemented per-provider multi-section sync mutex, cache-first restoration and independently stale course/agenda checks (30 minutes, missing cache or clock rollback). Refreshes are sequential through one MoodleProvider/client/session. Ordinary section failures do not block the other section; session expiry stops further requests and requires login. Account clearing serializes with the entire sync. Foreground STARTED triggers stale checks; leaving the content/foreground cancels refresh, and explicit pull/button refresh forces it. No periodic/background worker. Logout waits for in-flight refresh cancellation before clearing cookies; explicit logout suppresses automatic foreground reload until a new entry/login. Absolute Jalali last-updated labels and stale agenda day counts are shown.
  - [ ] CI/phone verification of lifecycle cancellation, pull gestures, expiry recovery and the 30-minute policy.
- [ ] [agent] `NavigationBar` shell (Today, Courses, Calendar, Grades, Messages; unfinished tabs show a "coming soon" card).
  - [x] Five Persian tabs, theme-tinted bundled vector icons, saved selection and RTL layout; Grades/Messages show coming-soon cards. Existing title-long-press diagnostics and saved-credential/manual login remain reachable. UI needs CI/device verification.
- [ ] [agent] Today screen with empty, stale ("last updated 3 days ago") and `NeedsLogin` states.
  - [x] Shared cache-first PortalViewModel and Today preview with Tehran day grouping, today/upcoming events, explicit no-event/no-cache/loading/error/stale/login states, epoch-based Jalali/Persian display and safe agenda diagnostics. Refresh errors retain saved content; account clearing immediately hides it and blocks login when local deletion fails. State previews added; Compose rendering/device behavior unverified.
- [ ] [agent] Calendar screen (month/agenda) with Jalali dates and Persian digits.
  - [x] Minimal agenda view reuses the same cached preview and groups it by Tehran date with Jalali headings, Persian digits and pull-to-refresh. Explicitly labels the six-event/seven-day preview as incomplete, not a full month/calendar or an exam feed. No month grid, guessed pagination or assignment-page scraping.

**Status: implemented preview, owner-authorized progression with verification skipped.** The owner explicitly skipped CI/device verification and authorized Slice 3. This is acceptance of moving on, not evidence that compilation, migration, gestures or live Today/Calendar behavior passed. The unchecked verification items above remain unverified; completeness/exam support remains deferred pending real evidence.

Done when: Today and Calendar work from real data and from Room offline.

---

## Slice 3 — Courses, content, file downloads

- [ ] [owner] Fill `docs/DISCOVERY.md` section "Course content" (AJAX or HTML only?) and "File URLs" (what `pluginfile.php` URLs look like, sanitized).
  - Owner authorized this slice after skipping Slice 2 verification. The owner observed no course `service.php` request and supplied weekly course HTML containing 20 sections/19 activities and seven module types. Safe selectors, ordinal-versus-database IDs, empty sections, inline labels and duplicate titles are recorded in DISCOVERY; a sanitized selected excerpt retains four sections/ten rows/all observed types. Owner also supplied folder HTML with a two-file tree (encoded Persian ZIP and ASCII PDF) and direct `mod_folder/content/0` links; sanitized `folder_ok.html` retains the tree and separate whole-folder form. Both captures omit original personal/session data. One ZIP's application/zip, size, Last-Modified and ETag presence are now confirmed, including a garbled attachment-name header. Status, cookie-only authentication, expired-file behavior and Moodle timemodified remain unknown. HTTP-validator fallback is owner-approved in decision #27, not yet implemented or server-verified. Parsing, selected-page fetching and nested UI are implemented but not device-verified; content Room caching and downloads remain pending. Do not invent metadata or key downloads by filename alone.
- [ ] [agent] Course detail screen (sections, modules, files) as a nested screen.
  - [x] Shared CourseSection/CourseActivity/CourseFile models and fixture-backed Jsoup course/folder parsers. Weekly course sections preserve database IDs, row order, empty weeks and distinct same-title activities; labels become plain text. Only validated HTTPS Moodle view links with matching module IDs survive; scripts/actions/credential links are never executed or exposed. Folder parsing checks course/module/context scope and keeps the full decoded context/component/area/item/path as remote identity, not just the filename. Malformed/ambiguous/unsafe folder content fails safely rather than silently becoming empty success. No file modification/MIME metadata is invented.
  - [x] Course/folder GET reads through the existing paced, cookie-authenticated Moodle client, exposed as scoped DataSources sharing its provider session. Numeric IDs are validated before networking; page GET queries contain only the selected ID, never sesskey/action parameters. Existing native main-page/session checking and bounded retry are reused. No file links are followed and no whole-folder form is submitted. Content sources are not part of dashboard foreground sync.
  - [x] Courses tab opens native course sections; folder activities open a nested file list. Both routes support Back, pull/button refresh, loading/empty/stale/error/login states and absolute Jalali last-fetch labels. Inline labels are plain text; unsupported activities have validated external-browser fallback. File lists explicitly say native download/open is not ready. Detail content is memory-only and labeled accordingly, not claimed as offline Room cache. No content WebView, scripts, auto-download or whole-folder form submission.
  - [x] Root and detail repositories share one MoodleProvider/client/session and the same per-provider sync/account-clear mutex. Root session observation catches detail expiry, reusing bounded foreground login recovery. Lifecycle-controlled page loading cancels obsolete responses; login/logout clears in-memory detail state, and logout waits for active detail work before deleting cookies. Root dashboard sync pauses while a detail route is visible.
  - 121 lightweight JVM tests pass, including six detail navigation/state/security tests on top of parser/client/cache/auth coverage. Tests cover folder back navigation retaining the parent, duplicate-load avoidance, refresh-error retention, shared expiry without further requests, rejected foreign/forged selections, late-response cancellation and safe browser URLs. SSO Node checks pass. Twelve Android-backed tests and APK/Compose compilation remain unverified (133 declarations total); local AGP resolution is still blocked. Data/factory syntax/type checks pass against minimal API stubs, not an Android build. Detail UI/device behavior, content Room caching and downloads remain pending verification/implementation respectively. Jsoup 1.24.1 was checked against Maven Central release metadata before addition.
- [ ] [agent] Folder picker flow (SAF) with persisted permission and the Android 11+ restrictions explained to the user.
- [ ] [agent] Downloader (OkHttp + WorkManager, foreground info for large files, `Content-Type` check, `timemodified` re-download rule), `FileEntry` state in Room.
  - Decision #27 authorizes ETag/Last-Modified conditional refresh when Moodle `timemodified` is unavailable. With no validators, retain a complete existing copy until explicit re-download; missing/partial local files require full download. No background file polling, and no replacement of the last good copy before a new download succeeds.
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
