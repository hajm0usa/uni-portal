---
name: saf-downloads
description: Use when implementing file download, the SAF folder picker, download state in Room, opening/sharing files, or WorkManager download workers in the university app.
---

# Downloads with SAF

## Why not DownloadManager

Moodle `pluginfile.php` URLs need our session cookies; `DownloadManager` does not have them. Download with OkHttp (our client and cookie jar) inside a WorkManager worker.

## Folder choice

- First download (or settings): `ACTION_OPEN_DOCUMENT_TREE`, then `takePersistableUriPermission(READ|WRITE)`. Store the tree URI in DataStore.
- Android 11+ forbids picking the storage root and the Downloads folder: tell the user to create/pick a dedicated folder (e.g. "دانشگاه").
- Layout inside the chosen folder: `course/section/file` created lazily with `DocumentsContract.createDocument`.
- If the permission is lost (folder deleted/revoked), the next download asks the user to pick a folder again.

## Writing

- `ContentResolver.openOutputStream(uri)`; stream OkHttp's body straight into it (no full buffering). Write to the document, and on failure delete the partial document.
- For existence checks use `DocumentsContract`/`ContentResolver.query` on the single URI. Do not walk the tree with `DocumentFile` (each call is an IPC; it is slow).

## Safety checks

- Check HTTP status and `Content-Type`: if a file was expected and the response is `text/html`, or the final URL is on the SSO host, treat it as session loss (`NeedsLogin`) and **save nothing**.
- Verify size/`Content-Length` when present; mark FAILED on mismatch.
- Never log the URL query string or cookies.

## Room state (`file_entry`)

Key `(providerId, remoteId)` built from Moodle ids (context/component/area/item/name), never from the on-disk file name. States: NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED. Store `timeModified` and `localUri`.

- Re-download only when the server's `timemodified` changed.
- On open/display, verify the single `localUri` still exists; if not, reset to NOT_DOWNLOADED and offer to download again. If the user moved/renamed the file, we cannot find it; that is accepted (decision #13).

## Worker

- `WorkManager` with network constraint, unique work per file, backoff for network errors but **no** retry loop on `NeedsLogin`.
- Foreground info (notification) for large files; progress updates throttled.
- Rate-limit: one download at a time per provider by default.

## Opening and sharing

- Open with `ACTION_VIEW` on the content URI with `FLAG_GRANT_READ_URI_PERMISSION`; share with `ACTION_SEND`. Use `FileProvider` only for files we create ourselves in app storage.

## Tests

Content-Type guard (HTML instead of file), partial file cleanup, state transitions, `timemodified` rule, missing-file reset.
