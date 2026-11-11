package ir.hajmousa.uniportal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CourseEntity::class, SyncStateEntity::class], version = 1, exportSchema = true)
abstract class PortalDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile private var instance: PortalDatabase? = null

        fun get(context: Context): PortalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PortalDatabase::class.java, "portal.db")
                .build().also { instance = it }
        }
    }
}
