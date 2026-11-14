package ir.hajmousa.uniportal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CourseEntity::class, SyncStateEntity::class, AgendaEntity::class, CourseContentEntity::class, AgendaObservedEntity::class], version = 4, exportSchema = true)
abstract class PortalDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun agendaDao(): AgendaDao
    abstract fun courseContentDao(): CourseContentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `agenda_item` (`providerId` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `startEpoch` INTEGER NOT NULL, `kind` TEXT NOT NULL, `courseRemoteId` TEXT, `deepLink` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `remoteId`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `course_content_page` (`providerId` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`providerId`, `remoteId`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `agenda_observed` (`providerId` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `isNew` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `remoteId`))")
                db.execSQL("INSERT OR IGNORE INTO agenda_observed (providerId, remoteId, isNew) SELECT providerId, remoteId, 0 FROM agenda_item WHERE kind = 'DEADLINE'")
            }
        }

        @Volatile private var instance: PortalDatabase? = null

        fun get(context: Context): PortalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PortalDatabase::class.java, "portal.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
