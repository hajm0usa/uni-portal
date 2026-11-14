# DISCOVERY — facts about the real site

The owner fills this while developing, using the browser DevTools (F12). The agent must not invent anything in this file; unknowns stay as `TODO`.

**Never write real cookies, `sesskey` values, tokens, names, emails, student numbers or message text here.** Replace values with placeholders (`XXXX`, `<name>`).

## How to collect a method

1. Open the page in a logged-in browser. F12 -> Network -> filter `service.php` (or Fetch/XHR). Check "Preserve log".
2. Reload the page. Click the request.
3. Copy: the `info=` value (method name), the request body (`methodname` + `args`), and the response JSON.
4. Sanitize, then paste into the section below and save the response as a fixture (see `fixtures/README.md`).
5. If the page makes **no** `service.php` request, write "HTML only" and note which URL serves it.

---

## SSO

- Moodle base URL: `https://elearn.ut.ac.ir/` (confirmed by owner)
- Login entry URL: `https://elearn.ut.ac.ir/login/index.php` (confirmed by owner). The app now opens this URL in the login WebView instead of the site root; native sesskey fetching and cookie import still use the root URL. The changed entry point awaits device verification.
- SSO host(s): `sso1.ut.ac.ir` (confirmed by owner; only this SSO hostname appeared in the supplied document-request list, but completeness of the capture remains unverified).
- Main-frame redirect chain after login (hosts only): exact sequence `TODO`. The owner confirmed that browser sign-in successfully reaches the course dashboard with `elearn.ut.ac.ir` in the address bar. The supplied Network Doc list ends in SSO but is not verified as a chronological main-frame redirect chain; it must not be treated as the final top-level navigation.
- Observed document-request domains in the owner's supplied order (full hostnames confirmed by owner): `elearn.ut.ac.ir` -> `sso1.ut.ac.ir` -> `elearn.ut.ac.ir` -> `elearn.ut.ac.ir` -> `sso1.ut.ac.ir` -> `elearn.ut.ac.ir` -> `elearn.ut.ac.ir` -> `sso1.ut.ac.ir` -> `sso1.ut.ac.ir`. The owner confirmed that the abbreviated `eleaen` entry also meant `elearn.ut.ac.ir`. Document requests may include frames; this list alone does not establish top-level navigation order.
- Login form: plain HTML `<form>`; owner supplied the page HTML and actual browser-submitted payload. POST target confirmed: `https://sso1.ut.ac.ir/ApiContainer.SSO.RCL1/Account/login`. Payload field order: `ReturnUrl`, `captcha` (empty in the supplied successful capture), `Username`, `Password`, `RememberLogin=true`, `button=login`, `__RequestVerificationToken`, `RememberLogin=false`. Preserve the duplicate checkbox/hidden field; do not collapse these into a map or infer checkbox semantics from its label.
  - The source contains nested forms; browser form ownership, fresh hidden tokens and JavaScript are best preserved by submitting the live form through its observed login button. Its `formaction` is `/ApiContainer.SSO.RCL1/Account/login`; the outer form has an empty action. The actual GET URL serving the form and the scripts' behavior are not independently verified. A hidden `captcha` field exists: the earlier no-captcha report does not establish that challenges can never appear.
  - `ReturnUrl` is a local authorization-callback path with an encoded Moodle callback and nested sensitive state. `__RequestVerificationToken` is dynamic anti-forgery material. The owner-supplied values are sensitive and are deliberately not retained. Every attempt must use the live page's values/cookies; never replay the supplied capture. Failed-login page shape remains unknown. Saved-credential automation is owner-authorized in decision #26 but is not yet device-verified.
- Return credential parameter: `code`, observed in the actual Moodle callback Request URL supplied by the owner. The owner previously described the return credential as single-use; consumption/expiry behavior has not been independently tested. No credential values are retained here.
- Query-parameter names on one inspected request to `sso1.ut.ac.ir` (hostname and names confirmed by owner; values omitted): `client_id`, `response_type`, `redirect_uri`, `state`, `scope`. The path and exact phase in the login flow remain unconfirmed. These names resemble an authorization request, but do not establish the protocol or identify the ticket returned to Moodle.
- Callback target: `https://elearn.ut.ac.ir/admin/oauth2callback.php` — confirmed both by the requested `redirect_uri` and, subsequently, the owner's actual callback Request URL. The selected request's method was reported as `GET`. Actual callback query-parameter names: `code`, `scope`, `state`, `session_state`; values intentionally omitted. Browser sign-in reaches the Moodle dashboard. The owner also confirmed successful in-app sign-in (WebView closes and the app shows success); callback HTTP status remains unverified.
- The earlier reports of a queryless Moodle GET and repeated authorization-request parameter names were not tied to a verified callback URL; they must not override the later actual callback URL observation.
- Callback privacy: `state` can contain an encoded URL with a nested `sesskey`. Treat the entire `state` value, `code` and `session_state` as sensitive. Do not log, copy into tracked fixtures, or retain their values (including percent-encoded forms); parameter names alone are sufficient for discovery.
- Cookies set on the SSO host (name, Expires column, HttpOnly/Secure): `TODO` (owner previously noted the Expires column says "Session"). After sign-in, the owner reports that `sso1.ut.ac.ir` is not listed under Application Cookies; the `.ut.ac.ir` cookies they inspected are `__arcsjs` and `__arcsjsc`. These match the parent-domain cookies already observed on the Moodle page, but their purpose/origin and any SSO authentication cookies remain unverified. Parent-domain scope alone does not identify an authentication cookie.
- Cookies observed on one `sso1.ut.ac.ir` Network request (names only, supplied by owner):
  - Request Cookies: `__arcsjs`, `__arcsjsc`.
  - Response Cookies: `.AspNetCore.Antiforgery.q2tjTUgKrLM`, `.AspNetCore.Mvc.CookieTempDataProvider`.
  - The owner checked the other SSO responses in the same login capture and reported no additional response-cookie names. This is limited to that capture; it does not prove that no other SSO cookies exist or that either named cookie carries authenticated state.
  - Attributes for both named response cookies (Domain, Path, Expires/Max-Age, HttpOnly and Secure): unknown; the owner could not provide them. The earlier general "Session" observation has not been tied to either cookie. Do not infer attributes from framework defaults.
  - The original request's phase in the sign-in flow remains `TODO`. Which cookies, if any, carry authenticated SSO state remains unverified.
- Cookies observed on the Moodle page (owner supplied; values intentionally omitted):
  - `MoodleSession`: domain `elearn.ut.ac.ir`, path `/`, expiry `Session`, HttpOnly unchecked, Secure checked (flags confirmed by owner).
  - `__arcsjs` and `__arcsjsc`: domain `.ut.ac.ir`, path `/`, explicit expiration (persistent cookies). Purpose and originating host are unverified.
  - HttpOnly / Secure flags for `__arcsjs` and `__arcsjsc`: `TODO` (supplied rows had no headers).
- WebView cookie handoff limitation: Android `CookieManager.getCookie(url)` exposes applicable name/value pairs, not original Domain, Path, Expires, HttpOnly, Secure or SameSite attributes. The current bridge imports them as Secure, host-only, root-path session cookies scoped to the queried HTTPS hostname, and drops ambiguous duplicate names. This preserves least-privilege host scope, but exact path/parent-domain/session semantics need real-device verification before claiming SSO and Moodle cookies interoperate.
- WebView allow-list currently contains only the two observed HTTPS hosts, `elearn.ut.ac.ir` and `sso1.ut.ac.ir`. Top-level navigation to other hosts opens the system browser; off-list subframe navigation is blocked. The owner-observed `redirect_uri` and callback use the allowed Moodle host. Additional redirect hosts, if the live WebView needs any, are unknown and must be confirmed before adding.
- App sign-in is owner-confirmed on a real phone: after login, the WebView closes and the app shows sign-in success. The owner subsequently confirmed that real course names appear in the native list. Together these exercise callback recognition, CookieManager import, encrypted jar write/read, authenticated OkHttp requests, live sesskey extraction and the course AJAX request. Persistence across process restart and session-expiry/re-login remain unverified.
- Where `sesskey` appears in the HTML: inside the `M.cfg` block of the logged-in dashboard page source (confirmed by owner). The owner's saved assignment was validated locally: `M.cfg` is assigned a JSON-compatible object with a nonempty, double-quoted string `sesskey` field and a semicolon terminator. The extractor passes fixture tests and is exercised by owner-confirmed native course loading. No original sesskey value has been recorded in tracked files.
- Config fixture: `app/src/test/resources/fixtures/moodle/moodle_config_ok.html` — sanitized real assignment with an explicitly added script wrapper (not a complete captured HTML page). Original assignment syntax, keys, nesting and types are preserved; nonempty strings are `XXXX`, numbers are zero. Session-timeout fields exist as strings in the sample, but their original values are not retained and actual session lifetime remains untested.
- Native client implementation: fetches the Moodle root with the imported cookie jar, extracts `M.cfg.sesskey`, then requests the confirmed course method. The owner confirmed real course names appear, verifying this happy path against the live site. The client follows only same-origin redirects; SSO/off-origin/downgrade redirects are rejected before following. `invalidsesskey` / `servicerequireslogin` cause one fresh main-page fetch and one AJAX retry; a second session failure requires visible login. HTML responses, authentication HTTP failures and off-origin redirects also require login. Error/retry behavior is MockWebServer-tested, not yet verified against actual university session expiry.

## Session lifetime tests

| Idle time | Moodle page loads? | Redirected to SSO? | SSO asked for password? | `service.php` error |
|---|---|---|---|---|
| 30 min | TODO | TODO | TODO | TODO |
| 2 h | TODO | TODO | TODO | TODO |
| 8 h | TODO | TODO | TODO | TODO |

## Course list (dashboard)

- Method (`methodname` in request payload): `core_course_get_enrolled_courses_by_timeline_classification` (confirmed by owner from the dashboard Network request).
- Observed request `args` (owner supplied; `sort` was visible in the earlier preview):
  ```json
  {
    "offset": 0,
    "limit": 24,
    "classification": "all",
    "sort": "ul.timeaccess desc",
    "customfieldname": "",
    "customfieldvalue": ""
  }
  ```
- Request batch envelope: an array containing exactly one object with only `index`, `methodname` and `args`; `index` is numeric `0` (all confirmed by owner from Payload). Method and arguments are recorded above.
- Saved course-list response validated locally: a one-item array containing only boolean `error` (`false`) and `data`. The `data` object contains only `courses` (21 objects) and numeric `nextoffset` (`21`). Pagination/end-of-list semantics remain unverified; a short page alone is not proof of completion.
- Observed course fields (structure/types only; real course names, identifiers and image content omitted):
  - Numbers: `id`, `startdate`, `enddate`, `progress`, `summaryformat`.
  - Strings: `fullname`, `shortname`, `idnumber`, `fullnamedisplay`, `coursecategory`, `courseimage`, `pdfexportfont`, `summary`, `viewurl`.
  - Booleans: `hasprogress`, `hidden`, `isfavourite`, `showactivitydates`, `showcompletionconditions`, `showshortname`, `visible`.
  - The earlier Preview image was truncated; the saved response is complete. Original images have not been decoded or copied into the sanitized fixture.
- Fixture: `app/src/test/resources/fixtures/moodle/courses_ok.json` — sanitized from the owner's complete saved response; preserves all 21 course objects and field types. Names, identifiers, dates, links and images are sample replacements, and progress/favourites are neutralized. See the adjacent fixture README for provenance. JSON/structure/privacy, parser, MockWebServer and ViewModel tests pass locally. The owner confirmed real course names appear through the native first-page request/repository/UI path; the live count and completeness were not reported. First-page Room caching is now implemented but awaits full CI/device verification; the UI still labels offset 0 / limit 24 and no pagination termination rule is assumed. A cached first page is not evidence that all enrolled courses were fetched.

## Calendar / action events (deadlines, exams)

- Method: `core_calendar_get_action_events_by_timesort` (owner-confirmed from the calendar/action-events Network request).
- Observed request `args` (owner supplied):
  ```json
  {
    "limitnum": 6,
    "limittononsuspendedevents": true,
    "timesortfrom": 1794342600,
    "timesortto": 1794947400
  }
  ```
  These are one captured request's values, not fixed app defaults. Time-boundary semantics and pagination/termination remain unverified. No `sesskey` value was retained.
- Owner-supplied success response: one-item array with `error: false`, `data.events` (one event), numeric `data.firstid` and `data.lastid` matching that event's ID. No total/has-more field was present. A short response is not proof of completeness; how to request subsequent pages remains unknown.
- Observed assignment deadline: `component: "mod_assign"`, `modulename: "assign"`, `eventtype: "due"`, with numeric `timestart`, `timesort`, `timeusermidnight`, `timemodified`, zero `timeduration`, boolean `overdue`, nested `course` and `action`, and a separate event `url`. `timestart` and `timesort` match in this sample only; do not assume they always match. Optional identity/group/category/repetition fields are null in this sample.
- `formattedtime` contains server-rendered HTML; native UI should format epoch values itself. `editurl` contains sensitive sesskey material, deliberately replaced in the fixture. Edit/delete URLs and `action.url` (a submission entry point) do not authorize writes; use only a validated read-only event/activity URL for browser navigation. No original credential value or raw response was saved.
- How exam times appear (event type / name): `TODO`
- Fixture: `app/src/test/resources/fixtures/moodle/calendar_ok.json` — sanitized real response; see the fixture README for replacements and limits. Assignment-deadline mapping into the shared `AgendaItem` model, preview HTTP/DataSource, cache repository, foreground sync and PortalViewModel logic are tested locally. Today/Calendar preview screens are wired but not yet compiled/device-verified. Room persistence/migration has isolated generated-code and SQLite checks; Android-backed tests remain pending. Real empty/error/HTML responses remain unavailable; clearly named synthetic cases exercise local parser failure paths.
- App preview policy (not a discovered server guarantee): one request with `limitnum: 6` and `limittononsuspendedevents: true`, for today's start of day in `Asia/Tehran` through the start of day seven dates later. Dates are recomputed from an injected clock for each fetch; captured timestamps are not hardcoded. It shares the existing cookie jar, request pacing and one-refresh/one-retry session handling with courses. No guessed pagination arguments or completeness claim; inclusive/exclusive endpoint semantics and live calendar fetching still require verification.

## Assignments (if separate from calendar)

- Method(s): `TODO`
- Owner supplied a rendered assignment-detail HTML page (`body#page-mod-assign-view`), distinct from the calendar AJAX response. It contains localized open/due labels under `[data-region="activity-dates"]`, an intro attachment under `#intro`, and a submission-status table. This proves HTML content exists, not that no read-only AJAX alternative exists. The original page includes personal identifiers, submission filenames and sesskey-bearing links and was not saved locally. Assignment-detail parsing is deferred; Slice 2 deadlines use the confirmed calendar JSON. This page is not evidence for quiz/exam mapping.

## Course content (sections, modules, files)

Latest client verification (2026-11-14): CI APK build passed for commit `4a4aaea` (run 43501216798). The owner confirmed the supplied device checklist, including live course/folder browsing, in-place upgrade preserving cached data, offline lists after force-stop/reopen, refresh failure/recovery, saved login, basic Home/navigation/themes, persistent SAF selection/cancellation and logout clearing. This supersedes earlier pending client/device notes below, but establishes no new server response shapes, download behavior or calendar pagination facts. Downloads remain deferred.

- Course sections/activities are server-rendered HTML in the owner's supplied weekly course page (`body#page-course-view-weeks`, class `format-weeks`). The owner observed no `service.php` requests when reloading this course. This establishes an HTML read path for this sample, not proof that no AJAX alternative or other course format exists.
- Observed course link: `https://elearn.ut.ac.ir/course/view.php?id=<courseId>` (parameter shape confirmed by links within the supplied page; the actual Network request/status/redirect chain was not supplied). Section links use `/course/section.php?id=<sectionDatabaseId>`.
- Main content structure: `#region-main .course-content [data-for="course_sectionlist"]` contains `li[data-for="section"]`. `data-id` is the section database ID; `data-number` / `data-sectionid` and the `section-N` DOM ID are its ordinal (including zero), not interchangeable IDs. The supplied page has 20 section nodes, ordinals 0–19; only this captured page's visible content is established, not completeness of every course. Section names are in `data-sectionname` and `h3.sectionname`; one section has a `current` class. Empty sections have an empty `ul[data-for="cmlist"]` and must not be mistaken for a parse failure.
- Activity rows: `li[data-for="cmitem"]` under each section's `[data-for="cmlist"]`, with numeric `data-id` matching `module-<id>`. The source has 19 rows (16 in the general section, two same-title assignments in one week, one assignment in the current week). Type is exposed by `modtype_*` classes: `url`, `forum`, `label`, `folder`, `bigbluebuttonbn`, `adobeconnect`, `assign`. Display names occur at `[data-region="activity-card"][data-activityname]` and `.activityname .instancename`; the latter includes a hidden `.accesshide` type label. Names are not unique identifiers.
- Activity entry links are `.activityname a` with `/mod/<type>/view.php?id=<moduleId>`. Some URL modules also have onclick scripts opening `&redirect=1`; retain only validated hrefs, never execute scripts. Inline label activities have `.activity-altcontent` text and no activity-view link. Assignment dates are localized strings under `[data-region="activity-dates"]`; do not guess epoch values from these strings or calendar sidebar dates. Unknown formats/types and unsafe links must fail safely or use explicit browser fallback, never fabricated download actions.
- No direct downloadable teaching-file anchors or file-ID/mtime metadata are established by this course listing. Folder entries point to `/mod/folder/view.php?id=<moduleId>`; their file lists require a separate captured page. A section summary contains a `pluginfile.php/.../course/section/...` image, while site icons/logos and calendar/sidebar links also exist elsewhere; these must not be indiscriminately treated as course downloads.
- Fixture: `app/src/test/resources/fixtures/moodle/course_content_ok.html` — sanitized selected excerpt retaining four sections/ten rows/all observed types; see fixture README for deliberate omissions and replacements. No raw capture, original user identifiers or sesskey values were saved. Jsoup parsing, selected-page GETs and detail ViewModel logic are locally tested. Native nested course/folder screens compile in an isolated Kotlin/Compose harness but remain unverified in an APK/on-device. Root sesskey/session checks and bounded recovery are reused without adding sesskey to page GET queries. Only the observed weekly format is supported; other layouts fail safely rather than claiming empty content. Parsed course/folder lists now have provider-scoped Room page caching (schema 3), cache-first selected-page restoration and transactional account clearing; this is client behavior, not additional site evidence. File bytes are not cached/downloaded. Ten Room tests pass in the isolated Robolectric harness, covering persistence, migrations, isolation, rollback and clearing. On-device persistence and live native content fetching remain unverified.
- File links observed in the owner's earlier assignment-detail HTML (sanitized examples, not downloaded/tested): `https://elearn.ut.ac.ir/pluginfile.php/100/mod_assign/introattachment/0/sample.pdf?forcedownload=1` for an intro attachment, and `https://elearn.ut.ac.ir/pluginfile.php/100/assignsubmission_file/submission_files/200/sample.zip?forcedownload=1` for an existing submission file. IDs and names are sample replacements. Distinguish course/assignment materials from the user's submitted files; do not indiscriminately collect all `pluginfile.php` links from page navigation or submission areas. These examples do not establish a general course-content listing method, stable file identity, MIME type, modification timestamp or cookie-only download behavior. No original personal filename was saved.
- Folder detail HTML is now owner-supplied: `body#page-mod-folder-view`, body classes `cm-type-folder`, `cmid-<moduleId>` and `context-<contextId>`. Main content contains `.filemanager ul.foldertree[role="tree"]`; its root directory is a `li[role="treeitem"][aria-expanded="true"]` with nested `ul[role="group"]` containing two file rows. One link has a percent-encoded Persian ZIP filename and one an ASCII PDF filename. Subdirectory nesting, empty folders and other folder configurations remain unverified.
- Observed direct folder-file URL shape: `https://elearn.ut.ac.ir/pluginfile.php/<contextId>/mod_folder/content/0/<encodedFilename>?forcedownload=1`. The URL exposes a context/component/area/item/path tuple, but the page supplies no separate numeric file ID, `timemodified`, size or MIME metadata. Do not invent a modification timestamp, infer MIME from icons/extensions as if confirmed, or treat the shared item segment `0` as a unique file ID. A future download key must retain the provider plus full scoped identity, not just a display/local filename. Path spelling alone does not prove file content is unchanged.
- The folder also has a separate GET form to `/mod/folder/download_folder.php` with hidden `id=<moduleId>` and a whole-folder-download button. Its presence is not evidence that its response type/metadata is known; no request was made or form submitted by the agent. Initial scope can use individual file links, not automated form submission or archive extraction.
- Folder fixture: `app/src/test/resources/fixtures/moodle/folder_ok.html` — sanitized main-content excerpt, preserving both file rows, tree nesting, labels and form structure; IDs/names/URLs are sample replacements. All original user data, M.cfg and sesskey material are omitted. Jsoup parsing and folder-page GET integration return shared CourseFile models, validate course/module/context, preserve scoped decoded remote identity and reject unsafe names/URLs. Scoped sources share Moodle's client/session but are not dashboard sync sources. No file links are followed and no whole-folder form is submitted. The raw page and original file bytes were not saved, fetched or logged. Live file HTTP status, cookie-only authentication and expiry behavior still require evidence; one ZIP's response headers are confirmed below.
- Owner supplied response headers for the folder's Persian-named ZIP (not the PDF): `Content-Type: application/zip`, `Content-Length: 88336609`, `Accept-Ranges: bytes`, `Cache-Control: private, max-age=10, no-transform`, and `Last-Modified: Thu, 29 Oct 2026 21:40:39 GMT`. A quoted ETag is present, but its original value is intentionally not retained. Status code, HTTP method, request authentication and any preceding redirects were not supplied. These headers alone do not prove a complete download, range/resume correctness, conditional-GET behavior or cookie-only access. The agent did not fetch the URL or file bytes.
- `Content-Disposition` reports an attachment with a mojibake-looking Persian filename and no `filename*` parameter in the supplied capture. The folder anchor and percent-encoded URL carry readable Unicode names. Do not blindly use garbled header text as a local filename or guess a character-set repair; any eventual name handling must sanitize separators/control characters and use an established display-name fallback. Original filenames and unrelated request/correlation IDs were not retained.
- Metadata gap: HTTP `Last-Modified` and ETag are observed response validators, **not** evidence of Moodle's `timemodified` field or a numeric file ID. The owner approved using these validators when Moodle modification metadata is absent (decision #27), with explicit re-download only when an existing file has neither validator. This policy is not implemented yet; actual conditional-request/304 support remains unverified.
- Does a file URL work with the session cookie alone? `TODO`
- What the server returns for a file when the session is dead (redirect? HTML?): `TODO`

## Grades

- Removed from app scope by the owner on 2026-11-14 (decision #29), superseding the earlier discovery authorization. No capture or sample is needed; no grades method, URL or response shape is claimed as verified.

## Messages

- Methods: `TODO`
- Works with the session cookie: `TODO`

## Error shapes

- Expired session on `service.php`: `TODO`
- Bad `sesskey`: `TODO`
- Permission error: `TODO`
