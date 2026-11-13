# Decisions (locked)

Each entry: decision, then why. Change one only by writing a new entry that supersedes it.

1. **Moodle with SSO, no captcha, official mobile web service disabled.**
   Facts reported by the owner: `service.php` answers (`Invalid json` on empty body), while `login/token.php`/launch reports that web services must be enabled. So the official token route is closed.

2. **Scrape through internal AJAX (`/lib/ajax/service.php`) with session cookie + `sesskey`.**
   JSON endpoints are far less fragile than HTML parsing and behave like an unofficial API. HTML + Jsoup only when a section has no AJAX method.

3. **Sign in in a visible WebView once.**
   The SSO redirect chain (separate domain, plain HTML form, token in URL as a one-time ticket) is handled by the WebView with no custom code, and we never see or store the password.

4. **Session cookies stored encrypted in our own cookie jar.**
   WebView cookie persistence is not reliable across Android versions and app restarts.

5. **Offline-first with Room; widgets and reminders read Room only.**
   Background sessions may be dead, so nothing in the background may depend on a live session.

6. **No periodic background sync.** Foreground sync (>~30 min stale) + pull-to-refresh.
   Avoids pointless load on the server and failing silent jobs. Revisit only if the session-lifetime test shows the SSO cookie lives for days.

7. **Local notifications only.** Deadline/exam reminders from Room; "sign in again" alarm after ~3 days without sync; "new items" is an in-app badge.
   "New assignment" push is impossible without a live session; the badge is the honest version.

8. **Site announcements/forum are not part of app notifications.** The app derives its own notifications from assignments and exams it sees.

9. **Scope v1:** courses, course content + files, assignments, calendar, grades, messages. Excluded: quizzes (stateful), assignment upload (write), forum.
   Writes through unofficial scraping can corrupt real submissions.

10. **Exams come from calendar events**, not from a quiz module.

11. **Messages are never cached**, never logged, never in bug reports.
   Most sensitive data; little offline value.

12. **Files: OkHttp + WorkManager into a user-chosen SAF folder.**
   `pluginfile.php` needs our cookies, so `DownloadManager` cannot be used. The user chose SAF over the app-private folder.

13. **Download state lives in Room keyed by Moodle ids.**
   The user can rename/move/delete files; scanning the folder via SAF is slow and fragile. Cost accepted: a moved file shows "not downloaded".

14. **Dates: Jalali, Tehran time, Persian digits; epoch in storage and logic.**
   Switching or adding Gregorian later is a formatter change.

15. **UI: Kotlin + Jetpack Compose + Material 3 (Material You), Glance widgets, minSdk 26.**
   Dynamic color needs API 31+; older versions use a fixed palette.

16. **Home screen = "Today"**, bottom NavigationBar: Today, Courses, Calendar, Grades, Messages; course detail is a nested screen.

17. **Unsupported sections: a card with an "open in browser" button.** The WebView is for login only.
   Smaller WebView attack surface. Known cost: Chrome does not share the app's cookies, so the user may pass SSO again.

18. **Audience: the owner and a few friends; direct APK; no backend.**
   Lowers legal/disciplinary risk and keeps user data on the device. Polite client: rate limit, honest User-Agent, cache first.

19. **Updates via GitHub Releases + `latest.json` banner; fixed signing key; manual bug report button; no crash-reporting service.**

20. **Parsing is tolerant; section-level errors only.** Fixtures of real responses drive tests.

21. **Build only on GitHub Actions (owner has no Android Studio).** Hidden in-app log screen is part of the app from Slice 0.

22. **Build order: thin vertical slice first (Slice 1)** to kill the four risky assumptions before any polished UI.

23. **Extensible to other university systems:** read-only `Provider` interface, one shared `SsoSession`, host-keyed cookie jar, per-provider `NeedsLogin`, shared `AgendaItem`, `providerId` in Room. No generic plugin framework.
   All other systems are on the same SSO and read-only (owner's answer).

24. **Writes (reserve/register/submit) are out of scope everywhere**; if ever needed, a separate `ActionProvider` with explicit confirmation and state re-read.

25. **App identity confirmed by the owner:** Persian name **پرتال دانشگاه**, production `applicationId` **`ir.hajmousa.uniportal`**, fixed-palette base **teal `#006A60`**. Debug uses `.debug` so it can coexist with production. Android 12+ follows the wallpaper's dynamic colors.

26. **Owner-requested saved credentials and foreground automatic SSO login (supersedes #3).**
    The owner explicitly requested storing the username/password securely on the phone and automatically signing in, without fingerprint protection. Store them only in app-private AES-GCM ciphertext under a separate Android Keystore key, excluded from backup. Never log credentials, form values or generated scripts, and never put credentials in saved-instance state. Logout deletes the credentials/key as well as cookies. Encryption protects storage, not a rooted/compromised device or secrets while they are in use.
    Use the existing visible, host-restricted login WebView and its fresh live form/token/cookie/ReturnUrl state, not a replay of captured requests. Release credentials only to the exact HTTPS SSO origin and verified login form action. One automatic submission per login flow; a failed or changed/challenged form falls back to visible/manual login. Automatic recovery is bounded to one flow per app entry, with explicit retry available. No background login, fingerprint dependency, generic form automation or university-data writes. Submitting this authentication form is the sole exception to the earlier no-form-submission rule; all educational data remains read-only.

27. **Owner-approved HTTP validators for files without Moodle modification metadata.**
    Where the observed HTML file listing provides no `timemodified`, use the file response's ETag (preferred) or Last-Modified as HTTP cache validators, not as invented Moodle metadata. This supersedes the `timemodified`-only re-download rule for these files. Retain exact validator values privately, never in logs or bug reports. On a requested refresh of a complete, still-existing local copy, use a conditional GET (`If-None-Match`, otherwise `If-Modified-Since`); a 304 keeps the existing document. A successful validated 200 response may supply a replacement, but the last good copy must not be destroyed before the replacement succeeds. Missing/partial local files require a full download, never treating a 304 as local-file success. If neither validator is available, keep an existing local copy until the user explicitly requests re-download; do not infer changes from filenames, sizes, cache max-age or guessed timestamps. No automatic background file polling or archive extraction. Conditional-request/304 behavior is a client policy to test, not yet a confirmed university-server capability; a server may ignore conditions and send a full response.

28. **Owner-approved Home design, implemented natively rather than as content in a WebView.**
    The browser prototype in `docs/design/home/` is the approved visual reference: transparent university logo; restrained Material 3 text hierarchy with real medium/semibold weights; nearest future deadline; remaining events grouped by Tehran date without repeating the featured item; course shortcuts; and the existing five-tab navigation. Keep sign-in/out and long-press diagnostics accessible without the old Home welcome panel. Respect light/dark and Android dynamic colors, Persian resources and accessibility font scaling. The owner wants all events in the seven-day view, not a six-item product limit. That is a target, not new server evidence: the current source still requests six events, so a complete feed remains blocked on confirmed pagination/termination; native UI must not conceal that data limitation. Authentication, cache clearing, sync timing and read-only rules are unchanged.
