package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.AlarmWithDates
import com.example.data.model.CloudBackupRecordEntity
import com.example.data.model.GoogleAccountEntity
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncedCalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Transaction
    @Query("SELECT * FROM alarms WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllAlarmsWithDates(): Flow<List<AlarmWithDates>>

    @Transaction
    @Query("SELECT * FROM alarms WHERE deleted_at IS NULL")
    suspend fun getAllNonDeletedAlarmsWithDates(): List<AlarmWithDates>

    @Transaction
    @Query("SELECT * FROM alarms")
    suspend fun getAllAlarmsIncludingDeleted(): List<AlarmWithDates>

    @Transaction
    @Query("SELECT * FROM alarms WHERE is_enabled = 1 AND is_archived = 0 AND deleted_at IS NULL")
    suspend fun getAllActiveAlarmsWithDates(): List<AlarmWithDates>

    @Transaction
    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun getAlarmWithDatesById(alarmId: String): AlarmWithDates?

    @Transaction
    @Query("SELECT * FROM alarms WHERE gcal_event_id = :eventId AND gcal_calendar_id = :calendarId LIMIT 1")
    suspend fun findByGcalEvent(eventId: String, calendarId: String): AlarmWithDates?

    @Transaction
    @Query("SELECT * FROM alarms WHERE gcal_calendar_id = :calendarId AND deleted_at IS NULL")
    suspend fun getAlarmsByCalendarId(calendarId: String): List<AlarmWithDates>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarm(alarm: AlarmEntity)

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmDates(dates: List<AlarmDateEntity>)

    @Query("DELETE FROM alarm_dates WHERE alarm_id = :alarmId")
    suspend fun deleteDatesForAlarm(alarmId: String)

    @Query("UPDATE alarm_dates SET status = :status WHERE id = :dateId")
    suspend fun updateAlarmDateStatus(dateId: String, status: String)

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun deleteAlarmById(alarmId: String)

    @Query("UPDATE alarms SET deleted_at = :deletedAt, updated_at = :deletedAt, is_enabled = 0, record_version = record_version + 1 WHERE id = :alarmId")
    suspend fun softDeleteAlarmById(alarmId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE alarms SET is_enabled = :enabled, updated_at = :updatedAt, record_version = record_version + 1 WHERE id = :alarmId")
    suspend fun setAlarmEnabled(alarmId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE alarms SET is_archived = :archived, is_enabled = CASE WHEN :archived = 1 THEN 0 ELSE is_enabled END, updated_at = :updatedAt, record_version = record_version + 1 WHERE id = :alarmId")
    suspend fun setAlarmArchived(alarmId: String, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    // Smart Reminders (Parent-Child)
    @Query("SELECT * FROM smart_reminders WHERE parent_alarm_id = :alarmId ORDER BY offset_minutes DESC")
    suspend fun getRemindersForAlarm(alarmId: String): List<SmartReminderEntity>

    @Query("SELECT * FROM smart_reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: String): SmartReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmartReminders(reminders: List<SmartReminderEntity>)

    @Query("DELETE FROM smart_reminders WHERE parent_alarm_id = :alarmId")
    suspend fun deleteRemindersForAlarm(alarmId: String)

    @Query("UPDATE smart_reminders SET is_enabled = :enabled, updated_at = :updatedAt WHERE id = :reminderId")
    suspend fun setReminderEnabled(reminderId: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM smart_reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Transaction
    suspend fun saveAlarmWithDates(alarm: AlarmEntity, dates: List<AlarmDateEntity>) {
        upsertAlarm(alarm)
        deleteDatesForAlarm(alarm.id)
        if (dates.isNotEmpty()) {
            insertAlarmDates(dates)
        }
    }

    @Transaction
    suspend fun saveAlarmComplete(
        alarm: AlarmEntity,
        dates: List<AlarmDateEntity>,
        reminders: List<SmartReminderEntity>,
    ) {
        upsertAlarm(alarm)
        deleteDatesForAlarm(alarm.id)
        if (dates.isNotEmpty()) {
            insertAlarmDates(dates)
        }
        deleteRemindersForAlarm(alarm.id)
        if (reminders.isNotEmpty()) {
            insertSmartReminders(reminders)
        }
    }

    @Transaction
    suspend fun replaceAllAlarms(items: List<AlarmWithDates>) {
        deleteAllAlarms()
        items.forEach { item ->
            saveAlarmComplete(item.alarm, item.dates, item.reminders)
        }
    }

    @Query("DELETE FROM alarms")
    suspend fun deleteAllAlarms()

    // Reminder Templates
    @Query("SELECT * FROM reminder_templates ORDER BY created_at DESC")
    fun observeReminderTemplates(): Flow<List<ReminderTemplateEntity>>

    @Query("SELECT * FROM reminder_templates ORDER BY created_at DESC")
    suspend fun getAllReminderTemplates(): List<ReminderTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderTemplate(template: ReminderTemplateEntity)

    @Query("DELETE FROM reminder_templates WHERE id = :templateId")
    suspend fun deleteReminderTemplate(templateId: String)

    // Alarm History & Statistics
    @Query("SELECT * FROM alarm_history ORDER BY triggered_at DESC LIMIT 300")
    fun observeAlarmHistory(): Flow<List<AlarmHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarmHistory(entry: AlarmHistoryEntity)

    @Query("DELETE FROM alarm_history")
    suspend fun clearAlarmHistory()

    // Cloud Backup History Cache
    @Query("SELECT * FROM cloud_backup_records ORDER BY created_at DESC")
    fun observeCloudBackups(): Flow<List<CloudBackupRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudBackups(records: List<CloudBackupRecordEntity>)

    @Query("DELETE FROM cloud_backup_records")
    suspend fun clearCloudBackups()

    // Synced Calendars
    @Query("SELECT * FROM synced_calendars ORDER BY account_email ASC, summary ASC")
    fun observeSyncedCalendars(): Flow<List<SyncedCalendarEntity>>

    @Query("SELECT * FROM synced_calendars")
    suspend fun getAllSyncedCalendars(): List<SyncedCalendarEntity>

    @Query("SELECT * FROM synced_calendars WHERE is_selected = 1")
    suspend fun getSelectedSyncedCalendars(): List<SyncedCalendarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendars(calendars: List<SyncedCalendarEntity>)

    @Query("UPDATE synced_calendars SET is_selected = :selected WHERE calendar_id = :calendarId")
    suspend fun setCalendarSelected(calendarId: String, selected: Boolean)

    @Query("UPDATE synced_calendars SET sync_token = :syncToken, last_synced_at = :syncedAt WHERE calendar_id = :calendarId")
    suspend fun updateCalendarSyncToken(calendarId: String, syncToken: String?, syncedAt: Long)

    @Query("DELETE FROM synced_calendars WHERE account_email = :email")
    suspend fun deleteCalendarsByAccount(email: String)

    @Query("DELETE FROM synced_calendars")
    suspend fun deleteAllCalendars()

    // Google Accounts
    @Query("SELECT * FROM google_accounts ORDER BY email ASC")
    fun observeGoogleAccounts(): Flow<List<GoogleAccountEntity>>

    @Query("SELECT * FROM google_accounts")
    suspend fun getAllGoogleAccounts(): List<GoogleAccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoogleAccount(account: GoogleAccountEntity)

    @Query("UPDATE google_accounts SET token_expired = :expired WHERE email = :email")
    suspend fun setAccountTokenExpired(email: String, expired: Boolean)

    @Query("UPDATE google_accounts SET last_synced_at = :syncedAt, token_expired = 0 WHERE email = :email")
    suspend fun markAccountSynced(email: String, syncedAt: Long)

    @Query("DELETE FROM google_accounts WHERE email = :email")
    suspend fun deleteGoogleAccount(email: String)

    @Query("DELETE FROM google_accounts")
    suspend fun deleteAllGoogleAccounts()

    @Query("DELETE FROM alarms WHERE gcal_account_email = :email")
    suspend fun deleteAlarmsByAccount(email: String)
}
