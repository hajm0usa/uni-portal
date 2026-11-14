# Open questions

Update this file when something is answered; move the answer to `DISCOVERY.md` or `DECISIONS.md`.

| # | Question | Who | Blocks | Status |
|---|---|---|---|---|
| 1 | App name, `applicationId`, base color for the fixed palette | owner | Slice 0 | answered — see decision 25 |
| 2 | SSO hosts, cookie names (SSO side and Moodle side) | owner | Slice 1 | open |
| 3 | Exact AJAX method names and sanitized sample responses: dashboard/course list, course page, calendar/action events, messages | owner | Slices 1-6 | open (owner will collect during development; Grades excluded by decision 29) |
| 4 | Session lifetime test: Moodle `MoodleSession` and the SSO cookie after 30 min, 2 h, 8 h idle (normal page / back to SSO / asks for password?) | owner | tuning of Slice 5; decides whether silent refresh is worth building | open |
| 5 | Do the four Slice-1 assumptions hold? (cookies enough for OkHttp, `sesskey` extractable, `service.php` accepts it, re-login works) | agent + owner | everything after Slice 1 | open |
| 6 | Is course content (sections/modules) available by AJAX, or only HTML (`course/view.php`)? | owner | Slice 3 | open |
| 7 | Are grades AJAX or HTML only? | owner | none | closed — Grades excluded by decision 29; no evidence needed |
| 8 | Which message methods exist and do they work with the session cookie? | owner | Slice 6 | open |
| 9 | Name of the first additional system on the same SSO (to test `AgendaItem`) and whether its SSO ticket works without a WebView | owner | later | open |
| 10 | University site terms of use: anything forbidding automated access? | owner | before sharing with friends | open |
