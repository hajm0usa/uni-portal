package ir.hajmousa.uniportal.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ir.hajmousa.uniportal.core.PortalException
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "agenda_item", primaryKeys = ["providerId", "remoteId"])
data class AgendaEntity(
    val providerId: String,
    val remoteId: String,
    val title: String,
    val startEpoch: Long,
    val kind: String,
    val courseRemoteId: String?,
    val deepLink: String?,
    val position: Int,
)

@Dao
abstract class AgendaDao {
    @Query("SELECT * FROM agenda_item WHERE providerId = :providerId ORDER BY position")
    abstract fun items(providerId: String): List<AgendaEntity>

    @Query("SELECT * FROM sync_state WHERE providerId = :providerId AND section = :section")
    abstract fun syncState(providerId: String, section: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state ORDER BY providerId, section")
    abstract fun widgetSyncStates(): List<SyncStateEntity>

    @Query("SELECT MAX(lastSuccessEpochSeconds) FROM sync_state")
    abstract fun lastSuccessfulSync(): Long?

    @Query("SELECT * FROM agenda_observed WHERE providerId = :providerId")
    abstract fun observedItems(providerId: String): List<AgendaObservedEntity>

    @Query("SELECT * FROM agenda_observed WHERE providerId = :providerId AND isNew = 1")
    abstract fun observeNewItems(providerId: String): Flow<List<AgendaObservedEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insertObserved(items: List<AgendaObservedEntity>)

    @Query("UPDATE agenda_observed SET isNew = 0 WHERE providerId = :providerId AND remoteId = :remoteId")
    abstract fun markItemSeen(providerId: String, remoteId: String)

    @Transaction
    open fun readWidgetAgenda(): WidgetAgendaCache {
        val states = widgetSyncStates()
        val previews = states.filter { it.section == SECTION }.mapNotNull { readPreview(it.providerId) }
        return WidgetAgendaCache(
            items = previews.flatMap { it.items },
            lastSuccessEpochSeconds = previews.minOfOrNull { it.lastSuccessEpochSeconds },
            needsLogin = states.any { it.lastError == PortalException.Reason.SESSION_EXPIRED.name },
            error = previews.firstNotNullOfOrNull { it.lastError }
                ?: states.firstOrNull { it.section == SECTION && it.lastError != null }?.let { state ->
                    PortalException.Reason.entries.firstOrNull { it.name == state.lastError }
                },
        )
    }

    @Query("DELETE FROM agenda_item WHERE providerId = :providerId")
    abstract fun deleteItems(providerId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertItems(items: List<AgendaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveSyncState(state: SyncStateEntity)

    @Transaction
    open fun readPreview(providerId: String): CachedAgendaPreview? {
        val state = syncState(providerId, SECTION) ?: return null
        val epoch = state.lastSuccessEpochSeconds ?: return null
        require(epoch in 0..Long.MAX_VALUE / 1000)
        val items = items(providerId).map {
            require(it.startEpoch in 1..Long.MAX_VALUE / 1000)
            AgendaItem(it.providerId, it.remoteId, it.title, it.startEpoch,
                AgendaItem.Kind.entries.firstOrNull { kind -> kind.name == it.kind } ?: AgendaItem.Kind.OTHER,
                it.courseRemoteId, it.deepLink)
        }
        return CachedAgendaPreview(items, epoch, PortalException.Reason.entries.firstOrNull { it.name == state.lastError })
    }

    @Transaction
    open fun replacePreview(providerId: String, items: List<AgendaItem>, epochSeconds: Long) {
        require(epochSeconds in 0..Long.MAX_VALUE / 1000)
        require(items.all { it.providerId == providerId && it.startEpoch in 1..Long.MAX_VALUE / 1000 })
        require(items.map { it.remoteId }.distinct().size == items.size)
        val known = observedItems(providerId).map { it.remoteId }.toSet()
        val initialized = known.isNotEmpty() || syncState(providerId, SECTION)?.lastSuccessEpochSeconds != null
        insertObserved(items.filter { it.kind == AgendaItem.Kind.DEADLINE && it.remoteId !in known }
            .map { AgendaObservedEntity(providerId, it.remoteId, isNew = initialized) })
        // shortcut: store only the six-event preview, expand after calendar pagination is verified.
        deleteItems(providerId)
        insertItems(items.mapIndexed { index, item ->
            AgendaEntity(item.providerId, item.remoteId, item.title, item.startEpoch, item.kind.name,
                item.courseRemoteId, item.deepLink, index)
        })
        saveSyncState(SyncStateEntity(providerId, SECTION, epochSeconds, null))
    }

    @Transaction
    open fun recordFailure(providerId: String, reason: PortalException.Reason) {
        val previous = syncState(providerId, SECTION)
        saveSyncState(SyncStateEntity(providerId, SECTION, previous?.lastSuccessEpochSeconds, reason.name))
    }

    companion object {
        const val SECTION = "agenda_preview"
    }
}
