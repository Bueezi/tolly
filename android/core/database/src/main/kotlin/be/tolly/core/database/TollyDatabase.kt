package be.tolly.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        CourseEntity::class,
        ContentEntity::class,
        AttachmentEntity::class,
        AnnouncementEntity::class,
        DownloadEntity::class,
        SyncStateEntity::class,
        CalendarItemEntity::class,
        GradeEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Keep downloads across upgrades: dropping that table would orphan the files on disk.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class TollyDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun contentDao(): ContentDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun downloadDao(): DownloadDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun calendarDao(): CalendarDao
    abstract fun gradeDao(): GradeDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TollyDatabase =
        Room.databaseBuilder(context, TollyDatabase::class.java, "tolly.db")
            // The database is only a cache of server data; rebuilding it is always safe.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun courseDao(db: TollyDatabase) = db.courseDao()
    @Provides fun contentDao(db: TollyDatabase) = db.contentDao()
    @Provides fun announcementDao(db: TollyDatabase) = db.announcementDao()
    @Provides fun downloadDao(db: TollyDatabase) = db.downloadDao()
    @Provides fun syncStateDao(db: TollyDatabase) = db.syncStateDao()
    @Provides fun maintenanceDao(db: TollyDatabase) = db.maintenanceDao()
    @Provides fun calendarDao(db: TollyDatabase) = db.calendarDao()
    @Provides fun gradeDao(db: TollyDatabase) = db.gradeDao()
}
