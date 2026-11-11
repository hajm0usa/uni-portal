# AGENTS.md — University Portal Android App

Read this file fully before doing anything. Then read `PLAN.md` (current slice) and the skills in `.claude/skills/` that match your task.

## What this project is

A personal Android app (Kotlin, Jetpack Compose, Material 3 / Material You) for a university's Moodle-based education site. The university gives **no official API** and the official Moodle mobile web service is **disabled** (`webservice` returns "must be enabled"). So the app signs in through the site's SSO in a WebView, takes over the session cookies, and then talks to Moodle's internal AJAX endpoint `/lib/ajax/service.php` with OkHttp. Data is cached in Room and shown as native Compose screens and Glance home-screen widgets.

Audience: the owner and a few friends, installed as a direct APK. No Play Store, no backend server of ours.

The UI language is **Persian (RTL)**. Code, comments, commit messages, docs: **English**.

## Locked decisions (do not re-litigate; see `docs/DECISIONS.md` for reasons)

- **Read-only app.** Never send a write request (no POST that changes state, no submitting forms, no assignment upload, no quiz attempts). Reads through `service.php` use POST by Moodle's protocol; that is allowed. If a write is ever needed it goes behind a separate `ActionProvider` interface, with explicit user confirmation and re-reading state afterwards. Not now.
- **Sign-in:** SSO via a visible WebView once. SSO is ticket based (token in URL is single use; the main site then sets a session cookie). Never store the password. Never automate the login form.
- **Session:** cookies are copied from `CookieManager` into our own `PersistentCookieJar` (keyed by host, encrypted with AES-GCM and an Android Keystore key). `sesskey` is read from the `M.cfg` block of the main page HTML and refreshed once on `invalidsesskey` / `servicerequireslogin`; a second failure sets the state to `NeedsLogin`. No retry loops.
- **No background network sync.** Sync runs when the app comes to the foreground (if data is older than ~30 min) and on pull-to-refresh. Reminders and widgets read Room only.
- **Notifications are local:** deadline/exam reminders scheduled from Room data, plus a local "sign in again" alarm ~3 days after the last successful sync. "New assignment" is only an in-app badge on open.
- **Messages are never cached** in Room, and never appear in logs or bug reports.
- **Files:** downloaded with OkHttp + WorkManager (never `DownloadManager`, it has no cookies) into a user-chosen folder via SAF (`ACTION_OPEN_DOCUMENT_TREE`, persisted permission). Room is the source of truth for download state, keyed by Moodle ids, never by file name. Check `Content-Type` so a login HTML page is never saved as a file. Re-download only when `timemodified` changed.
- **Dates:** store epoch only; format as Jalali with `ZoneId.of("Asia/Tehran")` and Persian digits at display time only. Never hardcode the +03:30 offset.
- **Unsupported sections** show a card with an "open in browser" button (external browser). The WebView is used for login only, never for content.
- **Extensibility:** every university system is a `Provider` (read-only). All systems share one `SsoSession`. UI, widgets and reminders only know the shared `AgendaItem` model, never Moodle types. Room tables carry `providerId`. Do **not** build a generic plugin framework; Moodle is the first implementation, the second real system will correct the interface.
- **Parsing is tolerant:** `ignoreUnknownKeys = true`, nullable fields, and a failure in one section only breaks that section's card. The app must never crash on a parse error.
- **Politeness toward the server:** honest `User-Agent` containing the app name, a rate limiter (Semaphore + small delay between course requests), cache first. No third-party analytics, no crash reporting services, no intermediate server.
- **Update channel:** APK in GitHub Releases + `latest.json`; the app shows a banner with a link. No silent install. APK is always signed with the same key.

## Architecture in one paragraph

Single Gradle module `app`, package layers: `core` (time, result types, logging), `auth` (`SsoSession`, cookie jar, WebView login), `data` (Room, repositories, sync), `provider.moodle` (AJAX client, `DataSource` implementations, parsers), `feature.*` (Compose screens), `widget` (Glance), `notify` (alarms). See `docs/ARCHITECTURE.md` for the contracts and the data model.

## Rules for working in this repo

1. **Work one slice at a time** from `PLAN.md`. Do not start the next slice until the current slice's "Done when" list is checked and the owner confirms.
2. **Ask before guessing real-world facts.** Moodle method names, response shapes, cookie names, SSO hosts and session timeouts are unknown until the owner fills `docs/DISCOVERY.md`. If a task needs a missing fact, stop and ask for it. Do not invent method names from Moodle docs and ship them as if verified.
3. **Fixtures:** parsers are tested against real sample responses in `app/src/test/resources/fixtures/`. The owner supplies them. Sanitize before committing (see `fixtures/README.md`). Never commit cookies, `sesskey`, tokens, names, emails, student numbers or message text.
4. **Never log** URLs with query strings, cookies, `sesskey`, tokens, request/response bodies of messages. The in-app log screen has the same rule. Use the `AppLog` wrapper, not `Log` directly.
5. **Secrets:** signing keystore and passwords live only in GitHub Secrets / local untracked files. Never print them, never commit them.
6. **Dependencies:** versions go through `gradle/libs.versions.toml`. Look up current stable versions when adding (do not trust memory). Keep dependencies minimal; prefer the platform (`android.icu`, `java.time`) over libraries.
7. **Build is on GitHub Actions** (the owner has no Android Studio). Keep the debug loop short: after each slice make sure the workflow builds a debug APK artifact, and that the hidden in-app log screen can show errors on the device.
8. **Compile-safety:** you may not be able to run the Android SDK locally. When you cannot compile, say so explicitly and list what you could not verify. Do not claim "builds fine" without a CI run or a local build.
9. **Small commits,** one concern each, message format `area: what` (e.g. `auth: persist cookies with Keystore`).
10. **RTL/Persian:** all user-visible strings in `res/values/strings.xml` (Persian), no hardcoded strings in Compose. Use `Locale("fa")` number formatting. Never use `Gravity.LEFT/RIGHT`, use start/end.
11. **When something in these docs conflicts with reality** (the site behaves differently), stop, report the conflict, and propose a change to the docs. Do not silently work around it.

## Definition of done (every slice)

- Compiles in CI, debug APK artifact produced.
- Parsers have tests against fixtures; error paths (expired session, malformed JSON, HTML instead of JSON) have tests.
- No secret or personal data in the diff (check fixtures and logs).
- `PLAN.md` checkboxes updated; new facts written to `docs/DISCOVERY.md` / `docs/DECISIONS.md`.
- A short summary for the owner: what works, what was not verified, what is needed next.

## Skills

Project skills live in `.claude/skills/`. Load the matching one before working in that area:

| Skill | Use for |
|---|---|
| `moodle-ajax-client` | `service.php` requests, `sesskey`, session expiry |
| `sso-session-cookies` | WebView login, cookie jar, re-login flow, `SsoSession` |
| `provider-architecture` | `Provider`, `DataSource`, `AgendaItem`, Room schema |
| `compose-persian-material3` | Compose UI, RTL, Persian digits/fonts, Material You, Glance |
| `persian-dates` | Jalali formatting, epoch rules, time zone |
| `saf-downloads` | file download, SAF, download state |
| `local-reminders` | alarms, notifications, boot rescheduling, "sign in again" alarm |
| `parser-fixtures` | sanitizing and using fixtures, parser tests |
| `github-actions-apk` | Gradle/CI build, signing, releases, `latest.json` |
