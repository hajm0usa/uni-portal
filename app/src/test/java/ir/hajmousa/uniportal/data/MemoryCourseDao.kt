package ir.hajmousa.uniportal.data

internal class MemoryCourseDao : CourseDao() {
    private val rows = mutableListOf<CourseEntity>()
    private val states = mutableMapOf<Pair<String, String>, SyncStateEntity>()
    val agendaRows = mutableListOf<AgendaEntity>()
    var failStorage = false

    private fun checkStorage() { check(!failStorage) { "private-example-input" } }
    override fun courses(providerId: String): List<CourseEntity> {
        checkStorage()
        return rows.filter { it.providerId == providerId }.sortedBy { it.position }
    }
    override fun syncState(providerId: String, section: String): SyncStateEntity? {
        checkStorage()
        return states[providerId to section]
    }
    override fun deleteCourses(providerId: String) {
        checkStorage()
        rows.removeAll { it.providerId == providerId }
    }
    override fun deleteAgenda(providerId: String) {
        checkStorage()
        agendaRows.removeAll { it.providerId == providerId }
    }
    override fun deleteSyncState(providerId: String, section: String) {
        checkStorage()
        states.remove(providerId to section)
    }
    override fun insertCourses(courses: List<CourseEntity>) {
        checkStorage()
        rows.addAll(courses)
    }
    override fun saveSyncState(state: SyncStateEntity) {
        checkStorage()
        states[state.providerId to state.section] = state
    }
}
