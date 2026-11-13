package ir.hajmousa.uniportal.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ir.hajmousa.uniportal.core.PortalException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Cache whole parsed pages so empty sections, nested activities and server ordering survive together.
@Entity(tableName = "course_content_page", primaryKeys = ["providerId", "remoteId"])
data class CourseContentEntity(val providerId: String, val remoteId: String, val payload: String)

@Dao
abstract class CourseContentDao {
    @Query("SELECT * FROM course_content_page WHERE providerId = :providerId AND remoteId = :remoteId")
    abstract fun page(providerId: String, remoteId: String): CourseContentEntity?

    @Query("SELECT * FROM sync_state WHERE providerId = :providerId AND section = :section")
    abstract fun syncState(providerId: String, section: String): SyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun savePage(page: CourseContentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveSyncState(state: SyncStateEntity)

    @Transaction
    open fun readSections(providerId: String, courseId: String): CachedContent<CourseSection>? = read(providerId, sectionsKey(courseId))

    @Transaction
    open fun readFiles(providerId: String, courseId: String, activityId: String): CachedContent<CourseFile>? =
        read(providerId, filesKey(courseId, activityId))

    @Transaction
    open fun replaceSections(providerId: String, courseId: String, items: List<CourseSection>, epoch: Long) {
        require(items.all { it.providerId == providerId && it.courseRemoteId == courseId &&
            it.activities.all { activity -> activity.providerId == providerId } })
        require(items.map { it.remoteId }.distinct().size == items.size)
        val activities = items.flatMap { it.activities }
        require(activities.map { it.remoteId }.distinct().size == activities.size)
        replace(providerId, sectionsKey(courseId), items, epoch)
    }

    @Transaction
    open fun replaceFiles(providerId: String, courseId: String, activityId: String, items: List<CourseFile>, epoch: Long) {
        require(items.all { it.providerId == providerId && it.courseRemoteId == courseId && it.activityRemoteId == activityId })
        require(items.map { it.remoteId }.distinct().size == items.size)
        replace(providerId, filesKey(courseId, activityId), items, epoch)
    }

    @Transaction
    open fun recordFailure(providerId: String, key: String, reason: PortalException.Reason) {
        val previous = syncState(providerId, key)
        saveSyncState(SyncStateEntity(providerId, key, previous?.lastSuccessEpochSeconds, reason.name))
    }

    private inline fun <reified T> read(providerId: String, key: String): CachedContent<T>? {
        val state = syncState(providerId, key) ?: return null
        val epoch = state.lastSuccessEpochSeconds ?: return null
        require(epoch in 0..Long.MAX_VALUE / 1000)
        val payload = requireNotNull(page(providerId, key)).payload
        return CachedContent(json.decodeFromString<List<T>>(payload), epoch,
            PortalException.Reason.entries.firstOrNull { it.name == state.lastError })
    }

    private inline fun <reified T> replace(providerId: String, key: String, items: List<T>, epoch: Long) {
        require(epoch in 0..Long.MAX_VALUE / 1000)
        savePage(CourseContentEntity(providerId, key, json.encodeToString(items)))
        saveSyncState(SyncStateEntity(providerId, key, epoch, null))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        internal fun sectionsKey(courseId: String) = "content:course:$courseId"
        internal fun filesKey(courseId: String, activityId: String) = "content:folder:$courseId:$activityId"
    }
}
