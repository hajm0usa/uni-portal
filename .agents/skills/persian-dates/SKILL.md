---
name: persian-dates
description: Use when formatting, parsing, comparing or scheduling dates/times in the university app (Jalali display, epoch storage, Asia/Tehran).
---

# Persian dates

## Rules

- **Store epoch only** (pick seconds or millis once in `core` and keep it everywhere; Moodle sends Unix seconds). Room, logic, sorting, comparisons and alarms use epoch.
- **Format at display time only**: Jalali calendar, Persian digits, `ZoneId.of("Asia/Tehran")`. Never hardcode the +03:30 offset (the zone rules can change).
- Use `android.icu.util.PersianCalendar` (API 24+) with `android.icu.text.DateFormat`/`SimpleDateFormat` and a `fa` locale; no extra library.
- Persian digits through `NumberFormat.getInstance(Locale("fa"))` or the ICU formatter with a `fa` locale.

## Helpers to implement in `core.time`

- `formatDate(epoch)`, `formatTime(epoch)`, `formatDateTime(epoch)`, `formatRelative(epoch, now)` (e.g. "فردا ۱۴:۳۰", "۳ روز پیش").
- `startOfDay(epoch)`, `isSameDay(a, b)`, `daysBetween(a, b)` using the Tehran zone (calendar day boundaries are Tehran's, not UTC).
- A `Clock` interface injected everywhere so tests control "now".

## Pitfalls

- Week starts on Saturday in the Persian calendar; the calendar screen must follow it.
- Month lengths: first six months 31 days, next five 30, last 29 (30 in leap years). Do not hand-roll; use ICU.
- Day-boundary bugs: compare days in the Tehran zone, not by dividing epoch by 86400.
- Reminders: compute trigger epochs (deadline minus 24 h / 3 h) in epoch space; no calendar math needed.

## Tests

Known Jalali conversions (including year boundaries and a leap year), Persian digit output, `isSameDay` around midnight Tehran time, week-start handling, and relative formatting with a fixed `Clock`.
