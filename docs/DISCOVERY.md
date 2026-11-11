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

- Moodle base URL: `TODO`
- SSO host(s): `TODO`
- Redirect chain after login (hosts only): `TODO`
- Login form: plain HTML `<form>` (confirmed by owner). Fields: `TODO`
- After login the URL carries a one-time ticket (confirmed by owner): parameter name `TODO`
- Cookies set on the SSO host (name, Expires column, HttpOnly/Secure): `TODO` (owner noted the Expires column says "Session")
- Cookies set on the Moodle host: `TODO`
- Where `sesskey` appears in the HTML (`M.cfg`, logout link): `TODO`

## Session lifetime tests

| Idle time | Moodle page loads? | Redirected to SSO? | SSO asked for password? | `service.php` error |
|---|---|---|---|---|
| 30 min | TODO | TODO | TODO | TODO |
| 2 h | TODO | TODO | TODO | TODO |
| 8 h | TODO | TODO | TODO | TODO |

## Course list (dashboard)

- Method (`info=`): `TODO` (owner confirmed `core_course_get_enrolled_courses_by_timeline_classification` appears in the requests)
- Request body: `TODO`
- Fixture: `app/src/test/resources/fixtures/moodle/courses_ok.json` `TODO`

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
