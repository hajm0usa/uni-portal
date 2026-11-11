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
- SSO host(s): `sso1.ut.ac.ir` (confirmed by owner; additional redirect hosts not yet checked)
- Redirect chain after login (hosts only): `TODO`
- Login form: plain HTML `<form>` (confirmed by owner). Fields: `TODO`
- After login the URL carries a one-time ticket (confirmed by owner): parameter name `TODO`
- Cookies set on the SSO host (name, Expires column, HttpOnly/Secure): `TODO` (owner noted the Expires column says "Session")
- Cookies observed on the Moodle page (owner supplied; values intentionally omitted):
  - `MoodleSession`: domain `elearn.ut.ac.ir`, path `/`, expiry `Session`, HttpOnly unchecked, Secure checked (flags confirmed by owner).
  - `__arcsjs` and `__arcsjsc`: domain `.ut.ac.ir`, path `/`, explicit expiration (persistent cookies). Purpose and originating host are unverified.
  - HttpOnly / Secure flags for `__arcsjs` and `__arcsjsc`: `TODO` (supplied rows had no headers).
- Where `sesskey` appears in the HTML (`M.cfg`, logout link): `TODO`

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
- Full request batch envelope: `TODO`
- Saved course-list response validated locally: a one-item array containing only boolean `error` (`false`) and `data`. The `data` object contains only `courses` (21 objects) and numeric `nextoffset` (`21`). Pagination/end-of-list semantics remain unverified; a short page alone is not proof of completion.
- Observed course fields (structure/types only; real course names, identifiers and image content omitted):
  - Numbers: `id`, `startdate`, `enddate`, `progress`, `summaryformat`.
  - Strings: `fullname`, `shortname`, `idnumber`, `fullnamedisplay`, `coursecategory`, `courseimage`, `pdfexportfont`, `summary`, `viewurl`.
  - Booleans: `hasprogress`, `hidden`, `isfavourite`, `showactivitydates`, `showcompletionconditions`, `showshortname`, `visible`.
  - The earlier Preview image was truncated; the saved response is complete. Original images have not been decoded or copied into the sanitized fixture.
- Fixture: `app/src/test/resources/fixtures/moodle/courses_ok.json` — sanitized from the owner's complete saved response; preserves all 21 course objects and field types. Names, identifiers, dates, links and images are sample replacements, and progress/favourites are neutralized. See the adjacent fixture README for provenance. JSON/structure/privacy checks passed locally; Kotlin parser tests are still pending.

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
