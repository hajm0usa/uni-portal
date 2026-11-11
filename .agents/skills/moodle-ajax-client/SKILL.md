---
name: moodle-ajax-client
description: Use when writing or debugging requests to Moodle's /lib/ajax/service.php, sesskey extraction, interceptors, error handling or session-expiry detection in the university app.
---

# Moodle AJAX client

Read `docs/ARCHITECTURE.md` (AJAX protocol) and `docs/DISCOVERY.md` first. Only use method names that the owner has recorded in DISCOVERY. If a method is missing there, ask the owner; never invent one from Moodle documentation.

## Request shape

```
POST {base}/lib/ajax/service.php?sesskey={sesskey}&info={methodname}
Content-Type: application/json
[{"index":0,"methodname":"<method>","args":{...}}]
```

Response is a JSON array. For each element: `error` (false, true, or an object) and `data`. HTTP 200 with `error` set is a failure; read `exception.errorcode` / `message`.

## sesskey

- Fetch the main page with the session cookie, extract with a regex on the `M.cfg` block (`"sesskey":"..."`). Keep the regex in one place with a fixture test; a fallback is the `logout.php?sesskey=` link.
- `sesskey` is bound to the session: after every login it changes.
- Flow on failure: `invalidsesskey` -> refresh once -> retry once -> on a second failure set `NeedsLogin`. Never loop.

## Session expiry detection (all map to `NeedsLogin`)

- Redirect to the SSO host (check the final URL host after redirects).
- HTML instead of JSON (check `Content-Type` and the first non-space char).
- `servicerequireslogin`, or `invalidsesskey` after the one refresh.

## Client rules

- OkHttp with `PersistentCookieJar`; follow redirects (needed for SSO detection; inspect the final URL).
- Interceptors: honest `User-Agent` with app name and version; rate limiter (Semaphore + small delay); expiry detector.
- Requests for many courses are sequential with a delay, never parallel.
- Never log the query string, cookies, `sesskey` or bodies. Log method name, HTTP status and duration only.
- Message methods: responses are never logged and never stored.
- Read-only: never call a method that changes state, even if one exists.

## Parsing

- kotlinx.serialization, `ignoreUnknownKeys = true`, nullable fields with defaults.
- Parse into DTOs, then map to clean app models inside the `DataSource`. DTOs never leave `provider.moodle`.
- A parse error becomes `Result.failure` for that section only.

## Tests

`MockWebServer` plus fixtures (skill `parser-fixtures`). Required cases: ok, empty, `error` object, HTML login page, malformed JSON, redirect to SSO host, `invalidsesskey` then success after refresh, `invalidsesskey` twice -> `NeedsLogin`.
