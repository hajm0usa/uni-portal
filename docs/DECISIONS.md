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
