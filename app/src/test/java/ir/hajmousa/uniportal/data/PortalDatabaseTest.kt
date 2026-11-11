package ir.hajmousa.uniportal.data

import androidx.room.Room
import ir.hajmousa.uniportal.core.PortalException.Reason
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PortalDatabaseTest {
    private lateinit var db: PortalDatabase
    private val context get() = RuntimeEnvironment.getApplication()
    private val old = Course("moodle", "1", "synthetic saved course", null)

    @Before
    fun start() {
        context.deleteDatabase("course-test.db")
        db = open()
    }

    private fun open() = Room.databaseBuilder(context, PortalDatabase::class.java, "course-test.db")
        .allowMainThreadQueries().build()

    @After
    fun stop() {
        db.close()
        context.deleteDatabase("course-test.db")
    }

    @Test
    fun `snapshot survives database reopening with order epoch and fixed error intact`() {
        val courses = listOf(old.copy(remoteId = "9"), old.copy(remoteId = "2"))
        db.courseDao().replacePage("moodle", courses, 1700000000)
        db.courseDao().recordFailure("moodle", Reason.SESSION_EXPIRED)
        db.close()
        db = open()
        val page = db.courseDao().readPage("moodle")!!
        assertEquals(courses, page.courses)
        assertEquals(1700000000L, page.lastSuccessEpochSeconds)
        assertEquals(Reason.SESSION_EXPIRED, page.lastError)
    }

    @Test
    fun `composite keys isolate identical remote ids and empty success replaces only its provider`() {
        val sibling = old.copy(providerId = "other")
        db.courseDao().replacePage("moodle", listOf(old), 1)
        db.courseDao().replacePage("other", listOf(sibling), 2)
        db.courseDao().replacePage("moodle", emptyList(), 3)
        assertTrue(db.courseDao().readPage("moodle")!!.courses.isEmpty())
        assertEquals(listOf(sibling), db.courseDao().readPage("other")!!.courses)
        db.courseDao().clearPage("moodle")
        assertNull(db.courseDao().readPage("moodle"))
        assertNotNull(db.courseDao().readPage("other"))
    }

    @Test
    fun `metadata write failure rolls back replacement rows and preserves last successful snapshot`() {
        db.courseDao().replacePage("moodle", listOf(old), 1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_sync BEFORE INSERT ON sync_state BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
        )
        try {
            db.courseDao().replacePage("moodle", listOf(old.copy(name = "synthetic replacement")), 2)
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            // The transaction must restore rows deleted before the metadata failure.
        }
        val page = db.courseDao().readPage("moodle")!!
        assertEquals(listOf(old), page.courses)
        assertEquals(1L, page.lastSuccessEpochSeconds)
    }
}
