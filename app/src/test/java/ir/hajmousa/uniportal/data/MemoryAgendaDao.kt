package ir.hajmousa.uniportal.data

import kotlinx.coroutines.flow.map

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
    override fun lastSuccessfulSync() = courses.allSyncStates().mapNotNull { it.lastSuccessEpochSeconds }.maxOrNull()
    override fun observedItems(providerId: String): List<AgendaObservedEntity> {
        checkStorage()
        return courses.observedRows.value.filter { it.providerId == providerId }
    }
    override fun observeNewItems(providerId: String) = courses.observedRows.map { items ->
        checkStorage()
        items.filter { it.providerId == providerId && it.isNew }
    }
    override fun insertObserved(items: List<AgendaObservedEntity>) {
        checkStorage()
        val known = courses.observedRows.value.map { it.providerId to it.remoteId }.toSet()
        courses.observedRows.value += items.filterNot { (it.providerId to it.remoteId) in known }
    }
    override fun markItemSeen(providerId: String, remoteId: String) {
        checkStorage()
        courses.observedRows.value = courses.observedRows.value.map {
            if (it.providerId == providerId && it.remoteId == remoteId) it.copy(isNew = false) else it
        }
    }
    override fun saveSyncState(state: SyncStateEntity) = courses.saveSyncState(state)
}
