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
- Login form: plain HTML `<form>` (confirmed by owner). Fields: `TODO`
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

- Method(s): `TODO`
- Request body: `TODO`
- How exam times appear (event type / name): `TODO`
- Fixture: `TODO`

## Assignments (if separate from calendar)

- Method(s): `TODO`

## Course content (sections, modules, files)

- AJAX or HTML only: `TODO`
- Method / URL: `TODO`
- What a file link looks like (`pluginfile.php/<contextid>/<component>/<filearea>/<itemid>/<name>`), sanitized: `TODO`
- Does a file URL work with the session cookie alone? `TODO`
- What the server returns for a file when the session is dead (redirect? HTML?): `TODO`

## Grades

- AJAX or HTML only: `TODO`
- Method / URL: `TODO`
- Sanitized sample: `TODO`

## Messages

- Methods: `TODO`
- Works with the session cookie: `TODO`

## Error shapes

- Expired session on `service.php`: `TODO`
- Bad `sesskey`: `TODO`
- Permission error: `TODO`
