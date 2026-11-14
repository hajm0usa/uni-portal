package ir.hajmousa.uniportal.data

internal class MemoryAgendaDao(private val courses: MemoryCourseDao) : AgendaDao() {
    private fun checkStorage() { check(!courses.failStorage) { "private-example-input" } }
    override fun items(providerId: String): List<AgendaEntity> {
        checkStorage()
        return courses.agendaRows.filter { it.providerId == providerId }.sortedBy { it.position }
    }
    override fun deleteItems(providerId: String) = courses.deleteAgenda(providerId)
    override fun insertItems(items: List<AgendaEntity>) {
        checkStorage()
        courses.agendaRows.addAll(items)
    }
    override fun syncState(providerId: String, section: String) = courses.syncState(providerId, section)
    override fun widgetSyncStates() = courses.allSyncStates()
    override fun saveSyncState(state: SyncStateEntity) = courses.saveSyncState(state)
}
