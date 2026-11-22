package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.example.alarm.AlarmNotificationHelper
import com.example.alarm.AlarmRingingService
import com.example.alarm.AlarmScheduler
import com.example.backup.BackupJsonManager
import com.example.backup.BackupPreviewResult
import com.example.backup.ChroniqBackupPayload
import com.example.backup.CloudBackupManager
import com.example.backup.CloudOperationResult
import com.example.backup.RestoreMode
import com.example.backup.RestoreReport
import com.example.backup.toAlarmWithDates
import com.example.backup.toEntity
import com.example.data.local.AlarmDao
import com.example.data.local.PersonalizationStore
import com.example.data.local.SyncPreferencesStore
import com.example.data.model.AccentColorPreset
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmDateStatus
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.AlarmHistoryOutcome
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.CloudBackupRecordEntity
import com.example.data.model.DeletedEventBehavior
import com.example.data.model.EventFilterMode
import com.example.data.model.FirstDayOfWeekPref
import com.example.data.model.GoogleAccountEntity
import com.example.data.model.MissedAlarmBehavior
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncPreferences
import com.example.data.model.SyncedCalendarEntity
import com.example.data.model.ThemeMode
import com.example.data.model.TimeFormatMode
import com.example.sync.CalendarSyncEngine
import com.example.sync.CalendarSyncWorker
import com.example.sync.GoogleCalendarAuthManager
import com.example.sync.SyncSummary
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class ChronoDateRepository(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: AlarmNotificationHelper,
    private val syncPreferencesStore: SyncPreferencesStore,
    private val personalizationStore: PersonalizationStore,
    private val syncEngine: CalendarSyncEngine,
    private val authManager: GoogleCalendarAuthManager,
    private val backupJsonManager: BackupJsonManager,
    private val cloudBackupManager: CloudBackupManager,
) {

    val allAlarms: Flow<List<AlarmWithDates>> = alarmDao.observeAllAlarmsWithDates()
    val syncedCalendars: Flow<List<SyncedCalendarEntity>> = alarmDao.observeSyncedCalendars()
    val googleAccounts: Flow<List<GoogleAccountEntity>> = alarmDao.observeGoogleAccounts()
    val syncPreferences: Flow<SyncPreferences> = syncPreferencesStore.preferencesFlow
    val personalizationPrefs: Flow<PersonalizationPrefs> = personalizationStore.prefsFlow
    val reminderTemplates: Flow<List<ReminderTemplateEntity>> = alarmDao.observeReminderTemplates()
    val alarmHistory: Flow<List<AlarmHistoryEntity>> = alarmDao.observeAlarmHistory()
    val cloudBackups: Flow<List<CloudBackupRecordEntity>> = alarmDao.observeCloudBackups()

    fun canScheduleExactAlarms(): Boolean = alarmScheduler.canScheduleExactAlarms()
    fun hasCloudBearerToken(): Boolean = cloudBackupManager.hasBearerToken()
    fun isOnline(): Boolean = cloudBackupManager.isOnline()

    suspend fun getAlarmById(alarmId: String): AlarmWithDates? = alarmDao.getAlarmWithDatesById(alarmId)

    suspend fun seedDefaultTemplatesIfEmpty() {
        if (alarmDao.getAllReminderTemplates().isEmpty()) {
            val defaults = listOf(
                ReminderTemplateEntity(
                    id = "tpl_flight_default",
                    name = "Flight / Penerbangan",
                    offsetsCsv = "1440,180,60,0",
                ),
                ReminderTemplateEntity(
                    id = "tpl_exam_default",
                    name = "Exam / Ujian",
                    offsetsCsv = "1440,120,30,10",
                ),
                ReminderTemplateEntity(
                    id = "tpl_meeting_default",
                    name = "Important Meeting / Rapat",
                    offsetsCsv = "60,15,5",
                ),
            )
            defaults.forEach { alarmDao.upsertReminderTemplate(it) }
        }
    }

    suspend fun saveAlarm(
        alarm: AlarmEntity,
        targetDates: List<LocalDate>,
        reminders: List<SmartReminderEntity>? = null,
    ): Long? {
        val existing = alarmDao.getAlarmWithDatesById(alarm.id)
        if (existing != null) {
            alarmScheduler.cancelAlarmAndReminders(existing.alarm.id, existing.reminders)
        }

        val deviceId = personalizationStore.getOrCreateDeviceId()
        val now = System.currentTimeMillis()
        val resolvedReminders = (reminders ?: existing?.reminders.orEmpty())
            .map { it.copy(parentAlarmId = alarm.id, updatedAt = now) }

        val updatedAlarm = alarm.copy(
            isSmartReminderEnabled = alarm.isSmartReminderEnabled && resolvedReminders.isNotEmpty(),
            updatedAt = now,
            deletedAt = null,
            recordVersion = (existing?.alarm?.recordVersion ?: 0) + 1,
            deviceId = deviceId,
        )

        val sortedUniqueDates = targetDates.distinct().sorted()
        val dateEntities = sortedUniqueDates.map { date ->
            AlarmDateEntity(
                alarmId = updatedAlarm.id,
                targetDate = date.toString(),
                status = AlarmDateStatus.SCHEDULED.name,
            )
        }
        alarmDao.saveAlarmComplete(updatedAlarm, dateEntities, resolvedReminders)

        val saved = AlarmWithDates(updatedAlarm, dateEntities, resolvedReminders)
        val nextMillis = if (updatedAlarm.isEnabled && !updatedAlarm.isArchived) {
            alarmScheduler.scheduleNextOccurrence(saved)
        } else {
            alarmScheduler.cancelAlarmAndReminders(updatedAlarm.id, resolvedReminders)
            null
        }
        WidgetUpdater.refreshAll(context)
        return nextMillis
    }

    suspend fun toggleSmartReminder(alarmId: String, reminderId: String, enabled: Boolean) {
        alarmDao.setReminderEnabled(reminderId, enabled)
        val updated = alarmDao.getAlarmWithDatesById(alarmId) ?: return
        if (updated.alarm.isEnabled && !updated.alarm.isArchived) {
            alarmScheduler.scheduleNextOccurrence(updated)
        } else {
            alarmScheduler.cancelReminder(reminderId)
        }
        WidgetUpdater.refreshAll(context)
    }

    suspend fun saveReminderTemplate(name: String, offsets: List<Int>) {
        val cleaned = offsets.distinct().sortedDescending()
        if (name.isBlank() || cleaned.isEmpty()) return
        alarmDao.upsertReminderTemplate(
            ReminderTemplateEntity(
                name = name.trim(),
                offsetsCsv = cleaned.joinToString(","),
            ),
        )
    }

    suspend fun deleteReminderTemplate(templateId: String) {
        alarmDao.deleteReminderTemplate(templateId)
    }

    suspend fun setAlarmEnabled(alarmId: String, enabled: Boolean): Long? {
        alarmDao.setAlarmEnabled(alarmId, enabled)
        val item = alarmDao.getAlarmWithDatesById(alarmId) ?: return null
        val triggerMillis = if (enabled && !item.alarm.isArchived) {
            alarmScheduler.scheduleNextOccurrence(item)
        } else {
            alarmScheduler.cancelAlarmAndReminders(alarmId, item.reminders)
            null
        }
        WidgetUpdater.refreshAll(context)
        return triggerMillis
    }

    suspend fun setAlarmArchived(alarmId: String, archived: Boolean) {
        val existing = alarmDao.getAlarmWithDatesById(alarmId)
        alarmDao.setAlarmArchived(alarmId, archived)
        if (archived) {
            alarmScheduler.cancelAlarmAndReminders(alarmId, existing?.reminders.orEmpty())
        } else {
            val item = alarmDao.getAlarmWithDatesById(alarmId) ?: return
            if (item.alarm.isEnabled) {
                alarmScheduler.scheduleNextOccurrence(item)
            }
        }
        WidgetUpdater.refreshAll(context)
    }

    suspend fun deleteAlarm(alarmId: String) {
        val existing = alarmDao.getAlarmWithDatesById(alarmId)
        alarmScheduler.cancelAlarmAndReminders(alarmId, existing?.reminders.orEmpty())
        // Soft-delete preserves sync tombstones for multi-device PostgreSQL sync
        alarmDao.softDeleteAlarmById(alarmId)
        WidgetUpdater.refreshAll(context)
    }

    suspend fun rescheduleAllActiveAlarms() {
        val active = alarmDao.getAllActiveAlarmsWithDates()
        alarmScheduler.rescheduleAllEnabledAlarms(active)
        WidgetUpdater.refreshAll(context)
    }

    fun launchPreviewRinging(alarmId: String) {
        val intent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, true)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    suspend fun handleAlarmSnoozed(
        alarmId: String,
        alarmDateId: String?,
        reminderId: String? = null,
        currentSnoozeCount: Int = 0,
    ) {
        val item = alarmDao.getAlarmWithDatesById(alarmId) ?: return
        val reminder = reminderId?.let { rid -> item.reminders.firstOrNull { it.id == rid } }

        alarmDao.insertAlarmHistory(
            AlarmHistoryEntity(
                alarmId = alarmId,
                alarmLabel = item.alarm.label,
                sourceType = item.alarm.sourceType,
                reminderTitle = reminder?.customTitle?.takeIf { it.isNotBlank() },
                outcome = AlarmHistoryOutcome.SNOOZED.name,
            ),
        )

        val maxAttempts = item.alarm.maxSnoozeAttempts
        if (maxAttempts > 0 && currentSnoozeCount >= maxAttempts) {
            // Exceeded max snooze attempts -> dismiss cleanly
            handleAlarmDismissed(alarmId, alarmDateId, reminderId)
            return
        }

        if (reminder == null && alarmDateId != null) {
            alarmDao.updateAlarmDateStatus(alarmDateId, AlarmDateStatus.SNOOZED.name)
        }
        val snoozeMins = reminder?.snoozeMinutes ?: item.alarm.snoozeDurationMinutes
        alarmScheduler.scheduleSnooze(
            alarmId = alarmId,
            alarmDateId = alarmDateId,
            snoozeMinutes = snoozeMins,
            reminderId = reminderId,
            nextSnoozeCount = currentSnoozeCount + 1,
        )
        WidgetUpdater.refreshAll(context)
    }

    suspend fun handleAlarmMissed(
        alarmId: String,
        alarmDateId: String?,
        reminderId: String? = null,
    ) {
        val item = alarmDao.getAlarmWithDatesById(alarmId) ?: return
        val reminder = reminderId?.let { rid -> item.reminders.firstOrNull { it.id == rid } }
        val prefs = personalizationStore.getCurrent()

        alarmDao.insertAlarmHistory(
            AlarmHistoryEntity(
                alarmId = alarmId,
                alarmLabel = item.alarm.label,
                sourceType = item.alarm.sourceType,
                reminderTitle = reminder?.customTitle?.takeIf { it.isNotBlank() },
                outcome = AlarmHistoryOutcome.MISSED.name,
            ),
        )

        notificationHelper.showMissedAlarmNotification(
            alarmLabel = reminder?.customTitle?.takeIf { it.isNotBlank() } ?: item.alarm.label,
            scheduledTimeText = item.alarm.formattedTime(prefs.timeFormat),
            language = prefs.language,
        )

        if (reminder == null) {
            if (alarmDateId != null) {
                alarmDao.updateAlarmDateStatus(alarmDateId, AlarmDateStatus.MISSED.name)
            }
            when (item.alarm.missedAlarmBehaviorEnum) {
                MissedAlarmBehavior.AUTO_ARCHIVE -> alarmDao.setAlarmArchived(alarmId, true)
                MissedAlarmBehavior.AUTO_DISMISS -> advanceOrFinishParentAlarm(alarmId)
                MissedAlarmBehavior.NOTIFY_ONLY -> advanceOrFinishParentAlarm(alarmId)
            }
        }
        WidgetUpdater.refreshAll(context)
    }

    suspend fun handleAlarmDismissed(
        alarmId: String,
        alarmDateId: String?,
        reminderId: String? = null,
    ) {
        val item = alarmDao.getAlarmWithDatesById(alarmId) ?: return
        val reminder = reminderId?.let { rid -> item.reminders.firstOrNull { it.id == rid } }

        alarmDao.insertAlarmHistory(
            AlarmHistoryEntity(
                alarmId = alarmId,
                alarmLabel = item.alarm.label,
                sourceType = item.alarm.sourceType,
                reminderTitle = reminder?.customTitle?.takeIf { it.isNotBlank() },
                outcome = if (reminder == null) AlarmHistoryOutcome.COMPLETED.name else AlarmHistoryOutcome.DISMISSED.name,
            ),
        )

        // If a child smart reminder was dismissed, DO NOT complete/archive the parent alarm!
        if (reminder != null) {
            WidgetUpdater.refreshAll(context)
            return
        }

        if (alarmDateId != null) {
            alarmDao.updateAlarmDateStatus(alarmDateId, AlarmDateStatus.DISMISSED.name)
        }
        advanceOrFinishParentAlarm(alarmId)
        WidgetUpdater.refreshAll(context)
    }

    private suspend fun advanceOrFinishParentAlarm(alarmId: String) {
        val updated = alarmDao.getAlarmWithDatesById(alarmId) ?: return
        val nextScheduled = updated.nextScheduledDate()

        if (nextScheduled != null) {
            alarmScheduler.scheduleNextOccurrence(updated)
            return
        }

        val lastDate = updated.sortedDates.lastOrNull()?.localDate
        val nextRecurringDate: LocalDate? = if (lastDate != null) {
            when (updated.alarm.recurrenceTypeEnum) {
                RecurrenceType.MONTHLY_DATE -> lastDate.plusMonths(1)
                RecurrenceType.YEARLY_DATE -> lastDate.plusYears(1)
                RecurrenceType.INTERVAL_DAYS -> {
                    val interval = updated.alarm.intervalDays.coerceAtLeast(1)
                    lastDate.plusDays(interval.toLong())
                }
                else -> null
            }
        } else {
            null
        }

        if (nextRecurringDate != null) {
            val newDateEntity = AlarmDateEntity(
                alarmId = alarmId,
                targetDate = nextRecurringDate.toString(),
                status = AlarmDateStatus.SCHEDULED.name,
            )
            alarmDao.insertAlarmDates(listOf(newDateEntity))
            val refreshed = alarmDao.getAlarmWithDatesById(alarmId)
            if (refreshed != null) {
                alarmScheduler.scheduleNextOccurrence(refreshed)
            }
            return
        }

        alarmScheduler.cancelAlarmAndReminders(alarmId, updated.reminders)
        when (updated.alarm.postAlarmActionEnum) {
            PostAlarmAction.AUTO_ARCHIVE -> alarmDao.setAlarmArchived(alarmId, true)
            PostAlarmAction.TURN_OFF -> alarmDao.setAlarmEnabled(alarmId, false)
            PostAlarmAction.AUTO_DELETE -> alarmDao.softDeleteAlarmById(alarmId)
        }
    }

    // ==================== BACKUP & RESTORE (LOCAL JSON) ====================

    suspend fun buildLocalBackupJson(): String {
        val alarms = alarmDao.getAllNonDeletedAlarmsWithDates()
        val templates = alarmDao.getAllReminderTemplates()
        val deviceId = personalizationStore.getOrCreateDeviceId()
        val payload = backupJsonManager.buildPayload(alarms, templates, deviceId)
        return backupJsonManager.serializeToJson(payload)
    }

    suspend fun exportLocalBackupToUri(uri: Uri): Result<Int> {
        val alarms = alarmDao.getAllNonDeletedAlarmsWithDates()
        val json = buildLocalBackupJson()
        return backupJsonManager.writeJsonToUri(uri, json).map {
            personalizationStore.updateLastLocalBackupAt(System.currentTimeMillis())
            alarms.size
        }
    }

    suspend fun createShareableLocalBackupUri(): Result<Uri> {
        val json = buildLocalBackupJson()
        return backupJsonManager.createShareableBackupFile(json).also { result ->
            if (result.isSuccess) {
                personalizationStore.updateLastLocalBackupAt(System.currentTimeMillis())
            }
        }
    }

    suspend fun previewLocalBackupFromUri(uri: Uri): BackupPreviewResult {
        val raw = backupJsonManager.readJsonFromUri(uri).getOrElse { exc ->
            return BackupPreviewResult(
                isValid = false,
                errorMessage = exc.localizedMessage ?: "Gagal membaca file cadangan.",
            )
        }
        val existing = alarmDao.getAllNonDeletedAlarmsWithDates()
        return backupJsonManager.parseAndValidate(raw, existing)
    }

    suspend fun restoreFromBackupPayload(
        payload: ChroniqBackupPayload,
        mode: RestoreMode,
    ): RestoreReport {
        val existingAll = alarmDao.getAllNonDeletedAlarmsWithDates()

        val report = when (mode) {
            RestoreMode.REPLACE -> {
                existingAll.forEach { alarmScheduler.cancelAlarmAndReminders(it.alarm.id, it.reminders) }
                val incomingItems = payload.alarms.map { dto ->
                    val converted = dto.toAlarmWithDates()
                    converted.copy(alarm = converted.alarm.copy(deletedAt = null))
                }
                alarmDao.replaceAllAlarms(incomingItems)
                payload.reminderTemplates.forEach { alarmDao.upsertReminderTemplate(it.toEntity()) }
                RestoreReport(
                    success = true,
                    restoredCount = incomingItems.size,
                    skippedDuplicates = 0,
                    smartRemindersRestored = incomingItems.sumOf { it.reminders.size },
                    message = "Pemulihan Ganti Semua selesai: ${incomingItems.size} alarm dipulihkan.",
                )
            }

            RestoreMode.MERGE -> {
                val (toSave, skipped) = backupJsonManager.computeMergeItems(payload, existingAll)
                toSave.forEach { item ->
                    alarmDao.saveAlarmComplete(item.alarm, item.dates, item.reminders)
                }
                payload.reminderTemplates.forEach { alarmDao.upsertReminderTemplate(it.toEntity()) }
                RestoreReport(
                    success = true,
                    restoredCount = toSave.size,
                    skippedDuplicates = skipped,
                    smartRemindersRestored = toSave.sumOf { it.reminders.size },
                    message = "Pemulihan Gabung selesai: ${toSave.size} alarm ditambahkan, $skipped duplikat dilewati.",
                )
            }
        }

        rescheduleAllActiveAlarms()
        return report
    }

    // ==================== CLOUD BACKUP (POSTGRESQL REST API) ====================

    suspend fun saveCloudConfiguration(baseUrl: String, userEmail: String, bearerToken: String): Result<Unit> {
        val validated = com.example.backup.CloudBackupApi.validateHttpsEndpoint(baseUrl)
            .getOrElse { return Result.failure(it) }
        personalizationStore.updateCloudConfig(validated, userEmail)
        if (bearerToken.isNotBlank()) {
            cloudBackupManager.saveBearerToken(bearerToken)
        }
        return Result.success(Unit)
    }

    suspend fun uploadCloudBackupNow(): CloudOperationResult = cloudBackupManager.uploadBackupNow()

    suspend fun refreshCloudHistoryNow(): CloudOperationResult = cloudBackupManager.refreshBackupHistory()

    suspend fun syncCloudBidirectionalNow(): CloudOperationResult {
        val result = cloudBackupManager.syncBidirectionalNow()
        if (result.success) {
            rescheduleAllActiveAlarms()
        }
        return result
    }

    suspend fun restoreCloudBackupVersion(backupId: String, mode: RestoreMode): RestoreReport {
        val payload = cloudBackupManager.downloadBackupPayload(backupId).getOrElse { exc ->
            return RestoreReport(
                success = false,
                message = exc.localizedMessage ?: "Gagal mengunduh versi cadangan Cloud.",
            )
        }
        return restoreFromBackupPayload(payload, mode)
    }

    // ==================== DATA MANAGEMENT & HISTORY ====================

    suspend fun clearAlarmHistory() {
        alarmDao.clearAlarmHistory()
    }

    suspend fun resetAllAppData() {
        val existing = alarmDao.getAllNonDeletedAlarmsWithDates()
        existing.forEach { alarmScheduler.cancelAlarmAndReminders(it.alarm.id, it.reminders) }
        alarmDao.deleteAllAlarms()
        alarmDao.clearAlarmHistory()
        alarmDao.clearCloudBackups()
        alarmDao.deleteAllCalendars()
        alarmDao.deleteAllGoogleAccounts()
        personalizationStore.resetAllPreferences()
        syncPreferencesStore.resetAll()
        seedDefaultTemplatesIfEmpty()
        WidgetUpdater.refreshAll(context)
    }

    // ==================== PERSONALIZATION & SETTINGS MUTATORS ====================

    suspend fun updateThemeMode(mode: ThemeMode) = personalizationStore.updateThemeMode(mode)
    suspend fun updateAccentColor(preset: AccentColorPreset) = personalizationStore.updateAccentColor(preset)
    suspend fun updateCustomAccent(argb: Long) = personalizationStore.updateCustomAccent(argb)

    suspend fun updateTimeFormat(format: TimeFormatMode) {
        personalizationStore.updateTimeFormat(format)
        WidgetUpdater.refreshAll(context)
    }

    suspend fun updateFirstDayOfWeek(pref: FirstDayOfWeekPref) = personalizationStore.updateFirstDayOfWeek(pref)

    suspend fun updateLanguage(language: AppLanguage) {
        personalizationStore.updateLanguage(language)
        WidgetUpdater.refreshAll(context)
    }

    suspend fun updateDefaultSound(uri: String?, name: String) = personalizationStore.updateDefaultSound(uri, name)
    suspend fun updateDefaultVolume(volume: Int) = personalizationStore.updateDefaultVolume(volume)
    suspend fun updateDefaultVibrate(vibrate: Boolean) = personalizationStore.updateDefaultVibrate(vibrate)
    suspend fun updateDefaultFadeIn(seconds: Int) = personalizationStore.updateDefaultFadeIn(seconds)
    suspend fun updateDefaultSnooze(minutes: Int) = personalizationStore.updateDefaultSnooze(minutes)
    suspend fun updateDefaultMaxSnooze(attempts: Int) = personalizationStore.updateDefaultMaxSnooze(attempts)
    suspend fun updateDefaultAlarmDuration(minutes: Int) = personalizationStore.updateDefaultAlarmDuration(minutes)
    suspend fun updateDefaultRecurrence(recurrence: RecurrenceType) = personalizationStore.updateDefaultRecurrence(recurrence)
    suspend fun updateDefaultPostAlarmAction(action: PostAlarmAction) = personalizationStore.updateDefaultPostAlarmAction(action)
    suspend fun updateDefaultMissedBehavior(behavior: MissedAlarmBehavior) = personalizationStore.updateDefaultMissedBehavior(behavior)
    suspend fun updateDefaultSmartRemindersEnabled(enabled: Boolean) = personalizationStore.updateDefaultSmartRemindersEnabled(enabled)
    suspend fun updateAutoCloudSync(enabled: Boolean) = personalizationStore.updateAutoCloudSync(enabled)

    suspend fun resetPersonalizationToDefaults() {
        personalizationStore.resetPersonalizationToDefaults()
        WidgetUpdater.refreshAll(context)
    }

    // ==================== GOOGLE CALENDAR SYNC ====================

    suspend fun connectGoogleAccount(accessToken: String): SyncSummary {
        val summary = syncEngine.connectGoogleAccountWithToken(accessToken)
        val prefs = syncPreferencesStore.getCurrent()
        CalendarSyncWorker.schedulePeriodicSync(context, prefs.syncIntervalHours)
        WidgetUpdater.refreshAll(context)
        return summary
    }

    suspend fun connectDeviceCalendars(): SyncSummary {
        val summary = syncEngine.connectDeviceCalendars()
        val prefs = syncPreferencesStore.getCurrent()
        CalendarSyncWorker.schedulePeriodicSync(context, prefs.syncIntervalHours)
        WidgetUpdater.refreshAll(context)
        return summary
    }

    suspend fun syncCalendarsNow(): SyncSummary {
        val summary = syncEngine.syncAllConnectedCalendars()
        WidgetUpdater.refreshAll(context)
        return summary
    }

    suspend fun setCalendarSelected(calendarId: String, selected: Boolean) {
        alarmDao.setCalendarSelected(calendarId, selected)
        if (!selected) {
            val alarms = alarmDao.getAlarmsByCalendarId(calendarId)
            alarms.forEach {
                alarmScheduler.cancelAlarmAndReminders(it.alarm.id, it.reminders)
                alarmDao.setAlarmEnabled(it.alarm.id, false)
            }
        }
        WidgetUpdater.refreshAll(context)
    }

    suspend fun disconnectAccount(email: String) {
        val calendars = alarmDao.getAllSyncedCalendars().filter { it.accountEmail == email }
        calendars.forEach { cal ->
            alarmDao.getAlarmsByCalendarId(cal.calendarId).forEach {
                alarmScheduler.cancelAlarmAndReminders(it.alarm.id, it.reminders)
            }
        }
        authManager.clearToken(email)
        alarmDao.deleteAlarmsByAccount(email)
        alarmDao.deleteCalendarsByAccount(email)
        alarmDao.deleteGoogleAccount(email)
        WidgetUpdater.refreshAll(context)
    }

    suspend fun updateOffsetMinutes(minutes: Int) = syncPreferencesStore.updateOffsetMinutes(minutes)
    suspend fun updateFilterMode(mode: EventFilterMode) = syncPreferencesStore.updateFilterMode(mode)
    suspend fun updateKeywords(keywords: List<String>) = syncPreferencesStore.updateKeywords(keywords)
    suspend fun updateIgnoreAllDay(ignore: Boolean) = syncPreferencesStore.updateIgnoreAllDay(ignore)
    suspend fun updateAllDayTime(hour: Int, minute: Int) = syncPreferencesStore.updateAllDayTime(hour, minute)
    suspend fun updateDeletedEventBehavior(behavior: DeletedEventBehavior) =
        syncPreferencesStore.updateDeletedEventBehavior(behavior)
    suspend fun updateEnableGcalSmartReminders(enabled: Boolean) =
        syncPreferencesStore.updateEnableGcalSmartReminders(enabled)
    suspend fun updateGcalSmartReminderOffsets(offsets: List<Int>) =
        syncPreferencesStore.updateGcalSmartReminderOffsets(offsets)

    suspend fun updateSyncIntervalHours(hours: Int) {
        syncPreferencesStore.updateSyncIntervalHours(hours)
        CalendarSyncWorker.schedulePeriodicSync(context, hours)
    }
}
