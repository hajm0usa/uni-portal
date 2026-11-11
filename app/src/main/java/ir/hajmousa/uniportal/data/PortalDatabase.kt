package ir.hajmousa.uniportal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CourseEntity::class, SyncStateEntity::class, AgendaEntity::class], version = 2, exportSchema = true)
abstract class PortalDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun agendaDao(): AgendaDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `agenda_item` (`providerId` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `startEpoch` INTEGER NOT NULL, `kind` TEXT NOT NULL, `courseRemoteId` TEXT, `deepLink` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `remoteId`))")
            }
        }

        @Volatile private var instance: PortalDatabase? = null

        fun get(context: Context): PortalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PortalDatabase::class.java, "portal.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
