---
name: parser-fixtures
description: Use when writing parser tests, adding or sanitizing fixtures (real Moodle responses), or handling HTML-instead-of-JSON and error cases in the university app.
---

# Parser fixtures and tests

Read `fixtures/README.md` for the sanitizing checklist. The owner supplies real responses; the agent never makes up a "realistic" response and presents it as real.

## Location and naming

`app/src/test/resources/fixtures/<provider>/<section>_<case>.json|html`, e.g. `moodle/courses_ok.json`, `moodle/courses_empty.json`, `moodle/courses_error.json`, `moodle/courses_login_page.html`.

Every section needs at least: ok, empty, error object from the server, and HTML returned instead of JSON.

## When a fixture is missing

Stop and ask the owner for it (with the steps from `docs/DISCOVERY.md`). Until then you may write a **clearly labeled synthetic** fixture (`*_synthetic.json`) to unblock compilation, and the DataSource must be marked unverified in `PLAN.md`.

## Sanitizing rules (summary)

Replace names, emails, student numbers, tokens, `sesskey`, cookie values, ticket parameters, message bodies. Keep keys, nesting, types, nulls and empty arrays. Search the file for `@`, `sesskey`, `token`, `ticket`, `MoodleSession` before committing.

## Test pattern

- Load the fixture as text; run the parser/DTO decoding; assert on the mapped app model (counts, a few fields, epoch values), not on the raw DTO.
- Tolerance tests: add an unknown key and remove an optional key; parsing must still succeed.
- Error tests: server `error` object, HTML login page, malformed JSON, empty body. Each must produce `Result.failure` with a typed reason (`SessionExpired`, `ServerError`, `ParseError`), never an exception that escapes.
- For HTML parsers (Jsoup): test the selectors against the fixture and add one test with a slightly altered structure that must fail gracefully with `ParseError`, not return wrong data silently.

## Privacy checks in CI

A test scans `src/test/resources/fixtures` for forbidden patterns (`@`, long digit runs that look like student numbers, `sesskey":"` followed by anything other than `XXXX`, `MoodleSession=`) and fails the build if found.
