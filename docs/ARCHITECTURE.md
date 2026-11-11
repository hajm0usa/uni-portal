# Architecture

## Layers

```
feature.*  (Compose screens, ViewModels)      widget (Glance)     notify (alarms)
        \                |                      /                   /
         +------------- data (repositories, Room, SyncCoordinator) -+
                          |
                 provider.moodle (DataSources, parsers, MoodleAjaxClient)
                          |
                 auth (SsoSession, PersistentCookieJar)   core (time, AppLog, Result)
```

UI, widgets and notifications depend only on `data` and the shared models. They never import `provider.*`.

## Contracts

```kotlin
interface Provider {
    val id: String                       // "moodle"
    val session: ProviderSession         // per-provider session state (Active / NeedsLogin)
    val sources: List<DataSource<*>>
}

interface DataSource<T> {
    suspend fun fetch(): Result<List<T>> // returns clean app models, never raw JSON/HTML
}

data class AgendaItem(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val startEpoch: Long,                // epoch seconds or millis: pick one in core and keep it
    val kind: Kind,                      // DEADLINE, EXAM, EVENT, OTHER
    val deepLink: String?,
)
```

Rules:
- Adding a section to Moodle = a new `DataSource` + mapping. If a section only exists as HTML, only that `DataSource` uses Jsoup; nothing above it changes.
- A failure in one `DataSource` yields a section-level error state, not an app crash.
- `ProviderSession` state is per provider; `SsoSession` is shared by all.

## Auth and session

- `SsoSession` owns the WebView login. After login: read cookies for the SSO host and the provider host from `CookieManager`, store them in `PersistentCookieJar`.
- `PersistentCookieJar` keys cookies by host. Persistence: AES-GCM with an Android Keystore key (do not use the deprecated `EncryptedSharedPreferences`).
- Moodle session: cookie (`MoodleSession` or whatever DISCOVERY says) + `sesskey` read from `M.cfg` in the main page HTML.
- A second provider on the same SSO obtains its session silently: request its URL with OkHttp following redirects while the SSO cookie is alive. This is unverified until a second system exists.
- Session loss detection: redirect to the SSO host, `servicerequireslogin`, `invalidsesskey` (after one refresh retry).

## Moodle AJAX protocol (to be confirmed with DISCOVERY)

```
POST {base}/lib/ajax/service.php?sesskey={sesskey}&info={methodname}
Content-Type: application/json
[{"index":0,"methodname":"...","args":{...}}]
```
Response: a JSON array; each element has `error` (boolean or object) and `data`. Treat HTTP 200 with `error: true` as a failure with `exception.errorcode`.
Never assume a method exists because Moodle docs list it: only methods seen in the owner's Network tab are used.

## Data model (Room)

All shared tables have a composite key `(providerId, remoteId)`.

- `course(providerId, remoteId, name, shortName, ...)`
- `agenda_item(providerId, remoteId, title, startEpoch, kind, courseRemoteId?, deepLink?)`
- `file_entry(providerId, remoteId, courseRemoteId, name, mime?, timeModified, localUri?, state)` — `state`: NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED
- `sync_state(providerId, section, lastSuccessEpoch, lastError?)`
- No table for messages.

Migrations: use real Room migrations once Slice 1 ships to a friend's phone; before that, destructive migration is acceptable.

## Sync

`SyncCoordinator.syncIfStale(providerId)`: runs when the app is foregrounded and last success is older than ~30 min; pull-to-refresh forces it. One `Mutex` per provider. Requests are sequential with a small delay; courses are fetched one at a time, not in parallel. Show cached data first, replace on success.

## Downloads

OkHttp + WorkManager. Write via `ContentResolver.openOutputStream(uri)` on a SAF document. Check `Content-Type` (if it is `text/html` while a file was expected, treat as session loss, do not save). Use `DocumentsContract` for existence checks, not `DocumentFile` tree walking.

## Time

Epoch everywhere in logic and Room. Jalali formatting via `android.icu.util.PersianCalendar` at display time; zone `ZoneId.of("Asia/Tehran")`; Persian digits via `NumberFormat.getInstance(Locale("fa"))`.

## Security notes

- WebView login: allowed hosts = university + SSO only; other links open the system browser; JavaScript on (Moodle/SSO need it), no JS interfaces.
- `allowBackup=false`, no cleartext, R8 on in release.
- Logs: no query strings, cookies, `sesskey`, tokens, message content.
