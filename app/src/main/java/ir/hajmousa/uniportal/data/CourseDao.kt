package ir.hajmousa.uniportal.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ir.hajmousa.uniportal.core.PortalException

@Entity(tableName = "course", primaryKeys = ["providerId", "remoteId"])
data class CourseEntity(
    val providerId: String,
    val remoteId: String,
    val name: String?,
    val shortName: String?,
    val position: Int,
)

@Entity(tableName = "sync_state", primaryKeys = ["providerId", "section"])
data class SyncStateEntity(
    val providerId: String,
    val section: String,
    val lastSuccessEpochSeconds: Long?,
    val lastError: String?,
)

@Dao
abstract class CourseDao {
    @Query("SELECT * FROM course WHERE providerId = :providerId ORDER BY position")
    abstract fun courses(providerId: String): List<CourseEntity>

    @Query("SELECT * FROM sync_state WHERE providerId = :providerId AND section = :section")
    abstract fun syncState(providerId: String, section: String): SyncStateEntity?

    @Query("DELETE FROM course WHERE providerId = :providerId")
    abstract fun deleteCourses(providerId: String)

    @Query("DELETE FROM agenda_item WHERE providerId = :providerId")
    abstract fun deleteAgenda(providerId: String)

    @Query("DELETE FROM agenda_observed WHERE providerId = :providerId")
    abstract fun deleteObservedAgenda(providerId: String)

    @Query("DELETE FROM course_content_page WHERE providerId = :providerId")
    abstract fun deleteContent(providerId: String)

    @Query("DELETE FROM sync_state WHERE providerId = :providerId")
    abstract fun deleteAllSyncState(providerId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveSyncState(state: SyncStateEntity)

    @Transaction
    open fun readPage(providerId: String): CachedCoursePage? {
        val state = syncState(providerId, SECTION) ?: return null
        val epoch = state.lastSuccessEpochSeconds ?: return null
        require(epoch in 0..Long.MAX_VALUE / 1000)
        val error = PortalException.Reason.entries.firstOrNull { it.name == state.lastError }
        return CachedCoursePage(courses(providerId).map { Course(it.providerId, it.remoteId, it.name, it.shortName) }, epoch, error)
    }

    @Transaction
    open fun replacePage(providerId: String, courses: List<Course>, epochSeconds: Long) {
        require(epochSeconds in 0..Long.MAX_VALUE / 1000)
        require(courses.all { it.providerId == providerId })
        require(courses.map { it.remoteId }.distinct().size == courses.size)
        // shortcut: this table holds only the first-page snapshot, expand sync after pagination is verified.
        deleteCourses(providerId)
        insertCourses(courses.mapIndexed { index, course ->
            CourseEntity(course.providerId, course.remoteId, course.name, course.shortName, index)
        })
        saveSyncState(SyncStateEntity(providerId, SECTION, epochSeconds, null))
    }

    @Transaction
    open fun recordFailure(providerId: String, reason: PortalException.Reason) {
        val previous = syncState(providerId, SECTION)
        saveSyncState(SyncStateEntity(providerId, SECTION, previous?.lastSuccessEpochSeconds, reason.name))
    }

    @Transaction
    open fun clearPage(providerId: String) {
        // This is the account-transition boundary: clear all cached sections in one transaction.
        deleteAgenda(providerId)
        deleteObservedAgenda(providerId)
        deleteContent(providerId)
        deleteCourses(providerId)
        deleteAllSyncState(providerId)
    }

    companion object {
        const val SECTION = "courses_first_page"
    }
}
