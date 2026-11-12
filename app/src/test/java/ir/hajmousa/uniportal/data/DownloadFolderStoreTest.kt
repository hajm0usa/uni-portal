package ir.hajmousa.uniportal.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract.Document
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import ir.hajmousa.uniportal.core.AppLog
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DownloadFolderStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver
    private val tree = Uri.parse("content://folder.test/tree/sample")
    private lateinit var preferences: DataStore<Preferences>
    private lateinit var store: DownloadFolderStore
    private lateinit var provider: FolderProvider

    @Before
    fun start() {
        provider = Robolectric.buildContentProvider(FolderProvider::class.java)
            .create(ProviderInfo().apply { authority = "folder.test" }).get()
        preferences = PreferenceDataStoreFactory.create(scope = scope) { temporary.root.resolve("folder.preferences_pb") }
        store = DownloadFolderStore(preferences, resolver)
        AppLog.clear()
    }

    @After
    fun stop() = runBlocking { job.cancelAndJoin(); AppLog.clear() }

    @Test
    fun `selection survives reopening preferences and retains read and write permission`() = runBlocking {
        assertNull(store.current().getOrThrow())
        store.select(tree).getOrThrow()
        val permission = resolver.persistedUriPermissions.single()
        assertTrue(permission.isReadPermission && permission.isWritePermission)
        job.cancelAndJoin()
        val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = reopenedScope) { temporary.root.resolve("folder.preferences_pb") }
            assertEquals(tree, DownloadFolderStore(reopened, resolver).current().getOrThrow())
        } finally { reopenedScope.cancel() }
    }

    @Test
    fun `revoked write permission requires selection again`() = runBlocking {
        store.select(tree).getOrThrow()
        resolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertNull(store.current().getOrThrow())
        store.select(tree).getOrThrow()
        assertEquals(tree, store.current().getOrThrow())
    }

    @Test
    fun `deleted or unwritable directory is not usable and only its own document is queried`() = runBlocking {
        store.select(tree).getOrThrow()
        assertEquals("content://folder.test/tree/sample/document/sample", provider.lastQuery.toString())
        provider.exists = false
        assertNull(store.current().getOrThrow())
        provider.exists = true
        provider.flags = 0
        assertNull(store.current().getOrThrow())
        provider.flags = Document.FLAG_DIR_SUPPORTS_CREATE
        provider.mime = "application/pdf"
        assertNull(store.current().getOrThrow())
    }

    @Test
    fun `invalid selection keeps the previously chosen folder and no private URI is logged`() = runBlocking {
        store.select(tree).getOrThrow()
        assertTrue(store.select(Uri.parse("file:///private-example-input")).isFailure)
        assertTrue(store.select(Uri.parse("content://folder.test/document/not-a-tree")).isFailure)
        assertEquals(tree, store.current().getOrThrow())
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
        assertFalse(AppLog.snapshot().toString().contains(tree.toString()))
    }

    @Test
    fun `failed persistence reports failure without replacing the existing selection`() = runBlocking {
        store.select(tree).getOrThrow()
        val failing = object : DataStore<Preferences> by preferences {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("private-example-input")
        }
        assertTrue(DownloadFolderStore(failing, resolver).select(Uri.parse("content://folder.test/tree/second")).isFailure)
        assertEquals(tree, store.current().getOrThrow())
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
    }

    @Test
    fun `provider security loss requires selection again without exposing provider errors`() = runBlocking {
        store.select(tree).getOrThrow()
        provider.denied = true
        assertNull(store.current().getOrThrow())
        assertTrue(store.select(tree).isFailure)
        assertFalse(AppLog.snapshot().toString().contains("private-example-input"))
    }

    class FolderProvider : ContentProvider() {
        var exists = true
        var denied = false
        var mime = Document.MIME_TYPE_DIR
        var flags = Document.FLAG_DIR_SUPPORTS_CREATE
        var lastQuery: Uri? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            lastQuery = uri
            if (denied) throw SecurityException("private-example-input")
            return MatrixCursor(projection!!).apply { if (exists) addRow(arrayOf<Any>(mime, flags)) }
        }
        override fun getType(uri: Uri) = mime
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes expected")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("No writes expected")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("No writes expected")
    }
}
