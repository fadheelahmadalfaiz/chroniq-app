package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.CloudBackupRecordEntity
import com.example.data.model.GoogleAccountEntity
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncedCalendarEntity

@Database(
    entities = [
        AlarmEntity::class,
        AlarmDateEntity::class,
        SmartReminderEntity::class,
        ReminderTemplateEntity::class,
        AlarmHistoryEntity::class,
        CloudBackupRecordEntity::class,
        SyncedCalendarEntity::class,
        GoogleAccountEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ChronoDateDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: ChronoDateDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE alarms ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN max_snooze_attempts INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE alarms ADD COLUMN alarm_duration_minutes INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE alarms ADD COLUMN missed_alarm_behavior TEXT NOT NULL DEFAULT 'NOTIFY_ONLY'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN is_smart_reminder_enabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN deleted_at INTEGER")
                db.execSQL("ALTER TABLE alarms ADD COLUMN record_version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN device_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE alarms SET updated_at = created_at WHERE updated_at = 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS smart_reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        parent_alarm_id TEXT NOT NULL,
                        offset_minutes INTEGER NOT NULL,
                        custom_title TEXT NOT NULL,
                        custom_message TEXT NOT NULL,
                        sound_uri TEXT,
                        sound_name TEXT NOT NULL,
                        vibrate INTEGER NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        snooze_minutes INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(parent_alarm_id) REFERENCES alarms(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_reminders_parent_alarm_id ON smart_reminders(parent_alarm_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        offsets_csv TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS alarm_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        alarm_id TEXT NOT NULL,
                        alarm_label TEXT NOT NULL,
                        source_type TEXT NOT NULL,
                        reminder_title TEXT,
                        outcome TEXT NOT NULL,
                        triggered_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_history_triggered_at ON alarm_history(triggered_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_alarm_history_alarm_id ON alarm_history(alarm_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_backup_records (
                        backupId TEXT NOT NULL PRIMARY KEY,
                        user_email TEXT NOT NULL,
                        device_id TEXT NOT NULL,
                        backup_version INTEGER NOT NULL,
                        alarm_count INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        payload_json TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): ChronoDateDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChronoDateDatabase::class.java,
                    "chronodate_alarm.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
