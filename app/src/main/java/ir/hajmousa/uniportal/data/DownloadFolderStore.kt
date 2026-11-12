package ir.hajmousa.uniportal.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.core.PortalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.downloadFolders by preferencesDataStore(name = "download_folder")

class DownloadFolderStore internal constructor(
    private val preferences: DataStore<Preferences>,
    private val resolver: ContentResolver,
) {
    constructor(context: Context) : this(context.applicationContext.downloadFolders, context.applicationContext.contentResolver)

    suspend fun current(): Result<Uri?> = safely {
        preferences.data.first()[TREE]?.let(Uri::parse)?.takeIf { usable(it) }
    }

    suspend fun select(uri: Uri): Result<Unit> = safely {
        require(uri.scheme == "content" && DocumentsContract.isTreeUri(uri))
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(usable(uri))
        // Older tree grants may still be needed to open previously downloaded documents.
        preferences.edit { it[TREE] = uri.toString() }
    }

    private fun usable(uri: Uri): Boolean {
        if (uri.scheme != "content" || !DocumentsContract.isTreeUri(uri)) return false
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission && it.isWritePermission }) return false
        return try {
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR &&
                    cursor.getLong(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong() != 0L
            } ?: false
        } catch (_: SecurityException) { false } catch (_: java.io.FileNotFoundException) { false }
    }

    private suspend fun <T> safely(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try { Result.success(block()) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            AppLog.record(AppLog.Event.DOWNLOAD_FOLDER_FAILED, error)
            Result.failure(PortalException(PortalException.Reason.STORAGE_ERROR))
        }
    }

    private companion object { val TREE = stringPreferencesKey("tree_uri") }
}
