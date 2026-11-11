---
name: provider-architecture
description: Use when adding or changing Provider, DataSource, AgendaItem, repositories, SyncCoordinator or the Room schema (providerId keys) in the university app.
---

# Provider architecture

Read `docs/ARCHITECTURE.md` (contracts and data model) first.

## Rules

- `Provider` is **read-only**: no write methods. Writes would be a separate `ActionProvider`, not now.
- UI, widgets and notifications import only `data` and shared models, never `provider.*`.
- A `DataSource<T>` returns clean app models (`Result<List<T>>`). DTOs and raw JSON/HTML stay inside `provider.moodle`.
- `AgendaItem` is the shared model for "Today", Calendar, the widget and reminders. Moodle events (deadlines, exam times) map into it inside the Moodle provider.
- Room tables carry `providerId`; composite primary key `(providerId, remoteId)`. Never key rows by display names.
- No generic plugin framework (no reflection loading, no config files that define providers). Register providers in one plain Kotlin list.
- Messages: no Room entity, no DAO, no cache. Fetched live and held in memory only.

## Adding a new section to Moodle

1. Ask the owner for the method name and a sanitized response in `docs/DISCOVERY.md` (or "HTML only").
2. Add the fixture(s) and parser tests first (skill `parser-fixtures`).
3. DTO + mapper + `DataSource` in `provider.moodle`.
4. Repository in `data` stores to Room and updates `sync_state`.
5. UI reads from the repository flow, with states: loading, content, empty, stale, `NeedsLogin`, section error.

## SyncCoordinator

- `syncIfStale(providerId)` on app foreground when the last success is older than ~30 min; pull-to-refresh forces it.
- One `Mutex` per provider; sequential requests with small delays.
- Show cached data first and replace it on success; failures set `lastError` for that section only.
- No periodic background sync (see `docs/DECISIONS.md` #6).

## Adding a second provider (later)

Same SSO, read-only. Implement `Provider`, map its data to `AgendaItem` or a new shared model, reuse `SsoSession`. If the interface turns out wrong, change the interface and write a new entry in `docs/DECISIONS.md`; do not add escape hatches.

## Room

Destructive migration is acceptable only before any friend installs the app; after that, write real migrations with tests.
