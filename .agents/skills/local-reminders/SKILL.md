---
name: local-reminders
description: Use when implementing local reminders, AlarmManager scheduling, notification channels and permission, boot rescheduling, or the "sign in again" alarm in the university app.
---

# Local reminders and notifications

All notifications are **local**, built from Room data. No push, no background network (decisions #6, #7).

## What exists

1. **Deadline/exam reminders**: 24 h and 3 h before each `AgendaItem` of kind DEADLINE/EXAM.
2. **"Sign in again" alarm**: ~3 days after the last successful sync; every successful sync pushes it back. If it fires, notify: "لاگین دوباره لازم است تا یادآوری‌ها به‌روز بمانند".
3. **"New items" badge**: in-app only, computed when the app opens and a sync finishes. Never a notification.

## Scheduling

- A single `ReminderScheduler` recomputes the whole set from Room after every successful sync and on boot/time change: cancel obsolete alarms (deleted/changed deadlines), schedule new ones. Request codes derive from a stable hash of `(providerId, remoteId, offset)`.
- Use `AlarmManager.setAndAllowWhileIdle` (inexact is fine). Do **not** request `SCHEDULE_EXACT_ALARM` unless the owner asks.
- `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`) re-runs the scheduler from Room.
- Skip reminders whose trigger time is already in the past.

## Notifications

- Android 13+: runtime `POST_NOTIFICATIONS` permission; ask in context (e.g. when the first reminder would be scheduled), and degrade gracefully if denied.
- Channels: `deadlines`, `session`. Persian names and descriptions.
- Tapping a notification opens the matching screen via a deep link.
- Notification text contains title and time only; never message content, never personal data beyond the item title.

## Stale data honesty

Reminders are only as fresh as the last sync. The "sign in again" alarm and the "last updated" label on Today/widget exist so the user is never misled by old data.

## Tests

Scheduler diffing (add/change/delete), past-time skip, boot rescheduling, alarm push-back after a sync, notification content contains no sensitive fields.
