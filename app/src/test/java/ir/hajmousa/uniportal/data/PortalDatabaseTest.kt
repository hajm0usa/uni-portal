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
        .addMigrations(PortalDatabase.MIGRATION_1_2, PortalDatabase.MIGRATION_2_3)
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

    @Test
    fun `agenda snapshot survives reopening and account clearing removes every section`() {
        val item = AgendaItem("moodle", "1", "Sample deadline", 1700006400, AgendaItem.Kind.DEADLINE)
        db.courseDao().replacePage("moodle", listOf(old), 1)
        db.agendaDao().replacePreview("moodle", listOf(item), 2)
        db.agendaDao().recordFailure("moodle", Reason.SERVER_ERROR)
        db.agendaDao().replacePreview("other", emptyList(), 3)
        db.close()
        db = open()
        val preview = db.agendaDao().readPreview("moodle")!!
        assertEquals(listOf(item), preview.items)
        assertEquals(2L, preview.lastSuccessEpochSeconds)
        assertEquals(Reason.SERVER_ERROR, preview.lastError)
        db.courseDao().clearPage("moodle")
        assertNull(db.agendaDao().readPreview("moodle"))
        assertTrue(db.agendaDao().items("moodle").isEmpty())
        assertNull(db.courseDao().readPage("moodle"))
        assertNotNull(db.agendaDao().readPreview("other"))
    }

    @Test
    fun `agenda metadata failure rolls back replacement and retains the previous preview`() {
        val item = AgendaItem("moodle", "1", "Sample deadline", 1700006400, AgendaItem.Kind.DEADLINE)
        db.agendaDao().replacePreview("moodle", listOf(item), 1)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_agenda_sync BEFORE INSERT ON sync_state BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
        )
        try {
            db.agendaDao().replacePreview("moodle", emptyList(), 2)
            fail("Expected transaction failure")
        } catch (_: android.database.sqlite.SQLiteException) {
            // Deleting the old rows must roll back with the failed metadata write.
        }
        assertEquals(listOf(item), db.agendaDao().readPreview("moodle")!!.items)
        assertEquals(1L, db.agendaDao().readPreview("moodle")!!.lastSuccessEpochSeconds)
    }

    @Test
    fun `version one migration preserves courses and metadata while adding usable agenda storage`() {
        db.close()
        context.deleteDatabase("course-test.db")
        val file = context.getDatabasePath("course-test.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE course (providerId TEXT NOT NULL, remoteId TEXT NOT NULL, name TEXT, shortName TEXT, position INTEGER NOT NULL, PRIMARY KEY(providerId, remoteId))")
            legacy.execSQL("CREATE TABLE sync_state (providerId TEXT NOT NULL, section TEXT NOT NULL, lastSuccessEpochSeconds INTEGER, lastError TEXT, PRIMARY KEY(providerId, section))")
            legacy.execSQL("INSERT INTO course VALUES ('moodle', '1', 'synthetic saved course', NULL, 0)")
            legacy.execSQL("INSERT INTO sync_state VALUES ('moodle', 'courses_first_page', 123, NULL)")
            legacy.version = 1
        }
        db = open()
        assertEquals(listOf(old), db.courseDao().readPage("moodle")!!.courses)
        assertEquals(123L, db.courseDao().readPage("moodle")!!.lastSuccessEpochSeconds)
        assertNull(db.agendaDao().readPreview("moodle"))
        db.agendaDao().replacePreview("moodle", emptyList(), 456)
        assertTrue(db.agendaDao().readPreview("moodle")!!.items.isEmpty())
    }

    @Test
    fun `content snapshots survive reopening including ordering duplicate titles scoped files and empty folders`() {
        val folder = CourseActivity("moodle", "200", "Sample", CourseActivity.Kind.FOLDER,
            "https://elearn.ut.ac.ir/mod/folder/view.php?id=200")
        val sections = listOf(CourseSection("moodle", "102", "1", "Same title", listOf(folder)),
            CourseSection("moodle", "100", "1", "Same title", emptyList()))
        val file = CourseFile("moodle", "300/mod_folder/content/0/sub/sample.pdf", "1", "200", "sample.pdf",
            "https://elearn.ut.ac.ir/pluginfile.php/300/mod_folder/content/0/sub/sample.pdf?forcedownload=1")
        db.courseContentDao().replaceSections("moodle", "1", sections, 1)
        db.courseContentDao().replaceFiles("moodle", "1", "200", listOf(file), 2)
        db.courseContentDao().replaceFiles("moodle", "1", "201", emptyList(), 3)
        db.courseContentDao().recordFailure("moodle", CourseContentDao.sectionsKey("1"), Reason.NETWORK_ERROR)
        db.close()
        db = open()
        assertEquals(CachedContent(sections, 1, Reason.NETWORK_ERROR), db.courseContentDao().readSections("moodle", "1"))
        assertEquals(CachedContent(listOf(file), 2), db.courseContentDao().readFiles("moodle", "1", "200"))
        assertEquals(CachedContent<CourseFile>(emptyList(), 3), db.courseContentDao().readFiles("moodle", "1", "201"))
    }

    @Test
    fun `content replacement and account clearing roll back completely when a transaction fails`() {
        val section = CourseSection("moodle", "100", "1", "Saved", emptyList())
        db.courseDao().replacePage("moodle", listOf(old), 1)
        db.courseContentDao().replaceSections("moodle", "1", listOf(section), 2)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_content_sync BEFORE INSERT ON sync_state BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try {
            db.courseContentDao().replaceSections("moodle", "1", emptyList(), 3)
            fail("Expected replacement failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(CachedContent(listOf(section), 2), db.courseContentDao().readSections("moodle", "1"))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_content_sync")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_content_clear BEFORE DELETE ON sync_state BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
        try {
            db.courseDao().clearPage("moodle")
            fail("Expected clearing failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(listOf(old), db.courseDao().readPage("moodle")!!.courses)
        assertEquals(CachedContent(listOf(section), 2), db.courseContentDao().readSections("moodle", "1"))
    }

    @Test
    fun `account clearing deletes content pages and orphan error metadata without touching another provider`() {
        db.courseContentDao().replaceSections("moodle", "1", emptyList(), 1)
        db.courseContentDao().replaceFiles("moodle", "1", "200", emptyList(), 2)
        db.courseContentDao().recordFailure("moodle", CourseContentDao.sectionsKey("9"), Reason.SESSION_EXPIRED)
        db.courseContentDao().replaceSections("other", "1", emptyList(), 3)
        db.courseDao().clearPage("moodle")
        assertNull(db.courseContentDao().readSections("moodle", "1"))
        assertNull(db.courseContentDao().page("moodle", CourseContentDao.sectionsKey("1")))
        assertNull(db.courseContentDao().readFiles("moodle", "1", "200"))
        assertNull(db.courseContentDao().syncState("moodle", CourseContentDao.sectionsKey("9")))
        assertNotNull(db.courseContentDao().readSections("other", "1"))
    }

    @Test
    fun `version two migration preserves course and agenda snapshots and adds usable content storage`() {
        db.close()
        context.deleteDatabase("course-test.db")
        val file = context.getDatabasePath("course-test.db")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE course (providerId TEXT NOT NULL, remoteId TEXT NOT NULL, name TEXT, shortName TEXT, position INTEGER NOT NULL, PRIMARY KEY(providerId, remoteId))")
            legacy.execSQL("CREATE TABLE sync_state (providerId TEXT NOT NULL, section TEXT NOT NULL, lastSuccessEpochSeconds INTEGER, lastError TEXT, PRIMARY KEY(providerId, section))")
            legacy.execSQL("CREATE TABLE agenda_item (providerId TEXT NOT NULL, remoteId TEXT NOT NULL, title TEXT NOT NULL, startEpoch INTEGER NOT NULL, kind TEXT NOT NULL, courseRemoteId TEXT, deepLink TEXT, position INTEGER NOT NULL, PRIMARY KEY(providerId, remoteId))")
            legacy.execSQL("INSERT INTO course VALUES ('moodle', '1', 'synthetic saved course', NULL, 0)")
            legacy.execSQL("INSERT INTO sync_state VALUES ('moodle', 'courses_first_page', 123, NULL)")
            legacy.execSQL("INSERT INTO sync_state VALUES ('moodle', 'agenda_preview', 456, 'NETWORK_ERROR')")
            legacy.execSQL("INSERT INTO agenda_item VALUES ('moodle', '1', 'Sample', 1700000000, 'DEADLINE', NULL, NULL, 0)")
            legacy.version = 2
        }
        db = open()
        assertEquals(listOf(old), db.courseDao().readPage("moodle")!!.courses)
        assertEquals(123L, db.courseDao().readPage("moodle")!!.lastSuccessEpochSeconds)
        assertEquals(Reason.NETWORK_ERROR, db.agendaDao().readPreview("moodle")!!.lastError)
        assertEquals("Sample", db.agendaDao().readPreview("moodle")!!.items.single().title)
        assertNull(db.courseContentDao().readSections("moodle", "1"))
        db.courseContentDao().replaceSections("moodle", "1", emptyList(), 789)
        assertEquals(CachedContent<CourseSection>(emptyList(), 789), db.courseContentDao().readSections("moodle", "1"))
    }
}
