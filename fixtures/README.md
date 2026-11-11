# Fixtures

Real responses from the site, used to test parsers. They live in `app/src/test/resources/fixtures/<provider>/` once the Gradle project exists; this folder is the place to keep the unsanitized originals' instructions only. **Never commit unsanitized originals.**

## Sanitizing checklist

Before a response becomes a fixture:

- [ ] Remove or replace: names, emails, student numbers, phone numbers, profile image URLs with tokens, message bodies.
- [ ] Replace `sesskey`, tokens, cookie values, authorization `code`, `state`, and `session_state` values with `XXXX`, including percent-encoded URL parameters. Redact the entire `state` value: it can contain an encoded URL with a nested `sesskey`.
- [ ] Keep the **structure**: all keys, nesting, types, nulls, empty arrays. Parser tests depend on shape, not values.
- [ ] Keep one realistic value per field type (a real-looking epoch for dates, a Persian course name is fine if it is not personal).
- [ ] Re-open the file and search for `@`, `sesskey`, `token`, `ticket`, `code`, `state`, `session_state`, `MoodleSession`; only placeholder credential values may remain.

## Naming

`<section>_<case>.json|html`, for example:
- `courses_ok.json`
- `courses_empty.json`
- `courses_expired_session.json` (error from the server)
- `courses_login_page.html` (HTML returned instead of JSON)
- `calendar_ok.json`, `grades_ok.html`

Every section needs at least: ok, empty, error, and "HTML instead of JSON".
