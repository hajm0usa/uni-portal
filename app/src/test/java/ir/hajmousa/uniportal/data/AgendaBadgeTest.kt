package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import ir.hajmousa.uniportal.core.PortalException.Reason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AgendaBadgeTest {
    private val courses = MemoryCourseDao()
    private val dao = MemoryAgendaDao(courses)
    private val item = AgendaItem("moodle", "1", "Sample deadline", 1700000000, AgendaItem.Kind.DEADLINE)
    private fun newIds(provider: String = "moodle") = dao.observedItems(provider).filter { it.isNew }.map { it.remoteId }.toSet()

    @Test
    fun `first successful preview establishes a baseline and only confirmed deadlines are tracked`() {
        dao.replacePreview("moodle", listOf(item, item.copy(remoteId = "2", kind = AgendaItem.Kind.OTHER),
            item.copy(remoteId = "3", kind = AgendaItem.Kind.EXAM)), 100)
        assertEquals(listOf(AgendaObservedEntity("moodle", "1", false)), dao.observedItems("moodle"))
    }

    @Test
    fun `later unknown ids are new but disappearing and reappearing preview entries stay known`() {
        dao.replacePreview("moodle", listOf(item), 100)
        val later = item.copy(remoteId = "2")
        dao.replacePreview("moodle", listOf(later), 200)
        assertEquals(setOf("2"), newIds())
        dao.markItemSeen("moodle", "2")
        dao.replacePreview("moodle", emptyList(), 300)
        dao.replacePreview("moodle", listOf(item, later), 400)
        assertTrue(newIds().isEmpty())
        assertEquals(2, dao.observedItems("moodle").size)
    }

    @Test
    fun `title deadline and ordering changes do not fabricate a new assignment`() {
        dao.replacePreview("moodle", listOf(item), 100)
        dao.replacePreview("moodle", listOf(item.copy(title = "Renamed", startEpoch = item.startEpoch + 1)), 200)
        assertTrue(newIds().isEmpty())
        dao.replacePreview("moodle", listOf(item, item.copy(remoteId = "2")), 300)
        assertEquals(setOf("2"), newIds())
    }

    @Test
    fun `empty successful baseline differs from a failed first fetch`() {
        dao.recordFailure("moodle", Reason.NETWORK_ERROR)
        dao.replacePreview("moodle", listOf(item), 100)
        assertTrue(newIds().isEmpty())
        courses.clearPage("moodle")
        dao.replacePreview("moodle", emptyList(), 100)
        dao.replacePreview("moodle", listOf(item), 200)
        assertEquals(setOf("1"), newIds())
    }

    @Test
    fun `acknowledgment is provider scoped and does not alter sync age errors or reminder input`() {
        listOf("moodle", "other").forEach { provider ->
            dao.replacePreview(provider, emptyList(), 100)
            dao.replacePreview(provider, listOf(item.copy(providerId = provider)), 200)
        }
        dao.recordFailure("moodle", Reason.SESSION_EXPIRED)
        val snapshot = dao.readWidgetAgenda()
        dao.markItemSeen("moodle", "1")
        assertTrue(newIds().isEmpty())
        assertEquals(setOf("1"), newIds("other"))
        assertEquals(200L, dao.lastSuccessfulSync())
        assertEquals(Reason.SESSION_EXPIRED, dao.readPreview("moodle")!!.lastError)
        assertEquals(snapshot, dao.readWidgetAgenda())
    }

    @Test
    fun `account clear removes observation history and the next account starts with no new badges`() {
        dao.replacePreview("moodle", emptyList(), 100)
        dao.replacePreview("moodle", listOf(item), 200)
        dao.replacePreview("other", listOf(item.copy(providerId = "other")), 100)
        courses.clearPage("moodle")
        assertTrue(dao.observedItems("moodle").isEmpty())
        assertEquals(1, dao.observedItems("other").size)
        dao.replacePreview("moodle", listOf(item), 300)
        assertTrue(newIds().isEmpty())
    }

    @Test
    fun `failed refresh cannot invent or acknowledge badges`() = runTest {
        dao.replacePreview("moodle", emptyList(), 100)
        dao.replacePreview("moodle", listOf(item), 200)
        val provider = object : Provider {
            override val id = "moodle"
            override val session = ProviderSession()
            override val sources = emptyList<DataSource<*>>()
        }
        val repository = AgendaRepository(provider, DataSource { Result.failure(PortalException(Reason.NETWORK_ERROR)) }, dao)
        assertTrue(repository.fetchPreview().isFailure)
        assertEquals(setOf("moodle" to "1"), repository.newItemKeys.first().getOrThrow())
        assertEquals(200L, dao.readPreview("moodle")!!.lastSuccessEpochSeconds)
    }

    @Test
    fun `badge storage errors are typed and do not clear pending flags`() = runTest {
        dao.replacePreview("moodle", emptyList(), 100)
        dao.replacePreview("moodle", listOf(item), 200)
        val provider = object : Provider {
            override val id = "moodle"
            override val session = ProviderSession()
            override val sources = emptyList<DataSource<*>>()
        }
        val repository = AgendaRepository(provider, DataSource { Result.success(emptyList()) }, dao)
        courses.failStorage = true
        assertEquals(Reason.STORAGE_ERROR, (repository.newItemKeys.first().exceptionOrNull() as PortalException).reason)
        assertEquals(Reason.STORAGE_ERROR, (repository.markSeen("1").exceptionOrNull() as PortalException).reason)
        courses.failStorage = false
        assertEquals(setOf("1"), newIds())
    }
}
