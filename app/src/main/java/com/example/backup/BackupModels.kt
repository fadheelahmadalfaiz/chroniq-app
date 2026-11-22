package com.example.backup

import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmDateStatus
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmSourceType
import com.example.data.model.AlarmWithDates
import com.example.data.model.MissedAlarmBehavior
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncStatus
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val CURRENT_BACKUP_VERSION = 2

enum class RestoreMode {
    MERGE,
    REPLACE,
}

@JsonClass(generateAdapter = true)
data class ChroniqBackupPayload(
    val app: String = "Chroniq",
    @Json(name = "backup_version") val backupVersion: Int = CURRENT_BACKUP_VERSION,
    @Json(name = "created_at") val createdAt: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
    @Json(name = "device_id") val deviceId: String = "",
    val alarms: List<AlarmBackupDto> = emptyList(),
    @Json(name = "reminder_templates") val reminderTemplates: List<ReminderTemplateBackupDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlarmBackupDto(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "General",
    @Json(name = "source_type") val sourceType: String = AlarmSourceType.MANUAL.name,
    @Json(name = "gcal_event_id") val gcalEventId: String? = null,
    @Json(name = "gcal_calendar_id") val gcalCalendarId: String? = null,
    @Json(name = "gcal_calendar_name") val gcalCalendarName: String? = null,
    @Json(name = "gcal_account_email") val gcalAccountEmail: String? = null,
    @Json(name = "event_location") val eventLocation: String? = null,
    @Json(name = "event_start_time_millis") val eventStartTimeMillis: Long? = null,
    val hour: Int,
    val minute: Int,
    val dates: List<String> = emptyList(),
    @Json(name = "recurrence_type") val recurrenceType: String = RecurrenceType.SINGLE_DATE.name,
    @Json(name = "interval_days") val intervalDays: Int = 0,
    val enabled: Boolean = true,
    @Json(name = "is_archived") val isArchived: Boolean = false,
    @Json(name = "sound_uri") val soundUri: String? = null,
    @Json(name = "sound_name") val soundName: String = "Default Alarm",
    val vibrate: Boolean = true,
    @Json(name = "volume_level") val volumeLevel: Int = 90,
    @Json(name = "fade_in_seconds") val fadeInSeconds: Int = 15,
    @Json(name = "snooze_duration_minutes") val snoozeDurationMinutes: Int = 10,
    @Json(name = "max_snooze_attempts") val maxSnoozeAttempts: Int = 3,
    @Json(name = "alarm_duration_minutes") val alarmDurationMinutes: Int = 10,
    @Json(name = "missed_alarm_behavior") val missedAlarmBehavior: String = MissedAlarmBehavior.NOTIFY_ONLY.name,
    @Json(name = "post_alarm_action") val postAlarmAction: String = PostAlarmAction.AUTO_ARCHIVE.name,
    @Json(name = "is_smart_reminder_enabled") val isSmartReminderEnabled: Boolean = false,
    @Json(name = "offset_minutes_before") val offsetMinutesBefore: Int = 0,
    @Json(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @Json(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @Json(name = "deleted_at") val deletedAt: Long? = null,
    @Json(name = "record_version") val recordVersion: Int = 1,
    @Json(name = "device_id") val deviceId: String = "",
    @Json(name = "smart_reminders") val smartReminders: List<SmartReminderBackupDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SmartReminderBackupDto(
    val id: String,
    @Json(name = "offset_minutes") val offsetMinutes: Int,
    @Json(name = "custom_title") val customTitle: String = "",
    @Json(name = "custom_message") val customMessage: String = "",
    @Json(name = "sound_uri") val soundUri: String? = null,
    @Json(name = "sound_name") val soundName: String = "Default Alarm",
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
    @Json(name = "snooze_minutes") val snoozeMinutes: Int = 5,
    @Json(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class ReminderTemplateBackupDto(
    val id: String,
    val name: String,
    @Json(name = "offsets_csv") val offsetsCsv: String,
    @Json(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

data class BackupPreviewResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val payload: ChroniqBackupPayload? = null,
    val totalAlarms: Int = 0,
    val newAlarmsCount: Int = 0,
    val duplicateAlarmsCount: Int = 0,
    val totalSmartReminders: Int = 0,
    val totalTemplates: Int = 0,
    val createdAtDisplay: String = "",
    val backupVersion: Int = 0,
)

data class RestoreReport(
    val success: Boolean,
    val restoredCount: Int = 0,
    val skippedDuplicates: Int = 0,
    val smartRemindersRestored: Int = 0,
    val message: String,
)

// Cloud REST API DTOs (PostgreSQL Backend Service)
@JsonClass(generateAdapter = true)
data class CloudBackupUploadRequest(
    @Json(name = "user_email") val userEmail: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "backup_version") val backupVersion: Int = CURRENT_BACKUP_VERSION,
    val payload: ChroniqBackupPayload,
)

@JsonClass(generateAdapter = true)
data class CloudBackupSummaryDto(
    @Json(name = "backup_id") val backupId: String,
    @Json(name = "user_email") val userEmail: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "backup_version") val backupVersion: Int = CURRENT_BACKUP_VERSION,
    @Json(name = "alarm_count") val alarmCount: Int = 0,
    @Json(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class CloudBackupDetailResponse(
    @Json(name = "backup_id") val backupId: String,
    @Json(name = "created_at") val createdAt: Long,
    val payload: ChroniqBackupPayload,
)

@JsonClass(generateAdapter = true)
data class CloudBackupListResponse(
    val backups: List<CloudBackupSummaryDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CloudSyncRequest(
    @Json(name = "user_email") val userEmail: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "last_synced_at") val lastSyncedAt: Long,
    val alarms: List<AlarmBackupDto>,
    @Json(name = "reminder_templates") val reminderTemplates: List<ReminderTemplateBackupDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CloudSyncResponse(
    @Json(name = "server_time") val serverTime: Long = System.currentTimeMillis(),
    @Json(name = "backup_id") val backupId: String? = null,
    val alarms: List<AlarmBackupDto> = emptyList(),
    @Json(name = "reminder_templates") val reminderTemplates: List<ReminderTemplateBackupDto> = emptyList(),
)

fun AlarmWithDates.toBackupDto(): AlarmBackupDto = AlarmBackupDto(
    id = alarm.id,
    title = alarm.label,
    description = alarm.description,
    category = alarm.category,
    sourceType = alarm.sourceType,
    gcalEventId = alarm.gcalEventId,
    gcalCalendarId = alarm.gcalCalendarId,
    gcalCalendarName = alarm.gcalCalendarName,
    gcalAccountEmail = alarm.gcalAccountEmail,
    eventLocation = alarm.eventLocation,
    eventStartTimeMillis = alarm.eventStartTimeMillis,
    hour = alarm.hour,
    minute = alarm.minute,
    dates = sortedDates.map { it.targetDate },
    recurrenceType = alarm.recurrenceType,
    intervalDays = alarm.intervalDays,
    enabled = alarm.isEnabled,
    isArchived = alarm.isArchived,
    soundUri = alarm.soundUri,
    soundName = alarm.soundName,
    vibrate = alarm.vibrate,
    volumeLevel = alarm.volumeLevel,
    fadeInSeconds = alarm.fadeInSeconds,
    snoozeDurationMinutes = alarm.snoozeDurationMinutes,
    maxSnoozeAttempts = alarm.maxSnoozeAttempts,
    alarmDurationMinutes = alarm.alarmDurationMinutes,
    missedAlarmBehavior = alarm.missedAlarmBehavior,
    postAlarmAction = alarm.postAlarmAction,
    isSmartReminderEnabled = alarm.isSmartReminderEnabled,
    offsetMinutesBefore = alarm.offsetMinutesBefore,
    createdAt = alarm.createdAt,
    updatedAt = alarm.updatedAt,
    deletedAt = alarm.deletedAt,
    recordVersion = alarm.recordVersion,
    deviceId = alarm.deviceId,
    smartReminders = sortedReminders.map { r ->
        SmartReminderBackupDto(
            id = r.id,
            offsetMinutes = r.offsetMinutes,
            customTitle = r.customTitle,
            customMessage = r.customMessage,
            soundUri = r.soundUri,
            soundName = r.soundName,
            vibrate = r.vibrate,
            enabled = r.isEnabled,
            snoozeMinutes = r.snoozeMinutes,
            updatedAt = r.updatedAt,
        )
    },
)

fun AlarmBackupDto.toAlarmWithDates(): AlarmWithDates {
    val entity = AlarmEntity(
        id = id,
        sourceType = runCatching { AlarmSourceType.valueOf(sourceType).name }
            .getOrDefault(AlarmSourceType.MANUAL.name),
        gcalEventId = gcalEventId,
        gcalCalendarId = gcalCalendarId,
        gcalCalendarName = gcalCalendarName,
        gcalAccountEmail = gcalAccountEmail,
        eventLocation = eventLocation,
        eventStartTimeMillis = eventStartTimeMillis,
        label = title.ifBlank { "Chroniq Alarm" },
        description = description,
        category = category.ifBlank { "General" },
        hour = hour.coerceIn(0, 23),
        minute = minute.coerceIn(0, 59),
        recurrenceType = runCatching { RecurrenceType.valueOf(recurrenceType).name }
            .getOrDefault(RecurrenceType.SINGLE_DATE.name),
        intervalDays = intervalDays.coerceAtLeast(0),
        isEnabled = enabled,
        soundUri = soundUri,
        soundName = soundName.ifBlank { "Default Alarm" },
        vibrate = vibrate,
        volumeLevel = volumeLevel.coerceIn(10, 100),
        fadeInSeconds = fadeInSeconds.coerceAtLeast(0),
        snoozeDurationMinutes = snoozeDurationMinutes.coerceIn(1, 60),
        maxSnoozeAttempts = maxSnoozeAttempts.coerceIn(0, 10),
        alarmDurationMinutes = alarmDurationMinutes.coerceIn(1, 30),
        missedAlarmBehavior = runCatching { MissedAlarmBehavior.valueOf(missedAlarmBehavior).name }
            .getOrDefault(MissedAlarmBehavior.NOTIFY_ONLY.name),
        postAlarmAction = runCatching { PostAlarmAction.valueOf(postAlarmAction).name }
            .getOrDefault(PostAlarmAction.AUTO_ARCHIVE.name),
        isArchived = isArchived,
        isSmartReminderEnabled = isSmartReminderEnabled || smartReminders.isNotEmpty(),
        offsetMinutesBefore = offsetMinutesBefore,
        syncStatus = if (sourceType == AlarmSourceType.GOOGLE_CALENDAR.name) {
            SyncStatus.SYNCED.name
        } else {
            SyncStatus.LOCAL.name
        },
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        recordVersion = recordVersion.coerceAtLeast(1),
        deviceId = deviceId,
    )

    val validDates = dates.mapNotNull { iso ->
        runCatching { LocalDate.parse(iso.trim()).toString() }.getOrNull()
    }.distinct().sorted().map { iso ->
        AlarmDateEntity(
            alarmId = entity.id,
            targetDate = iso,
            status = AlarmDateStatus.SCHEDULED.name,
        )
    }

    val childReminders = smartReminders.map { r ->
        SmartReminderEntity(
            id = r.id,
            parentAlarmId = entity.id,
            offsetMinutes = r.offsetMinutes.coerceAtLeast(0),
            customTitle = r.customTitle,
            customMessage = r.customMessage,
            soundUri = r.soundUri,
            soundName = r.soundName.ifBlank { "Default Alarm" },
            vibrate = r.vibrate,
            isEnabled = r.enabled,
            snoozeMinutes = r.snoozeMinutes.coerceIn(1, 60),
            updatedAt = r.updatedAt,
        )
    }

    return AlarmWithDates(alarm = entity, dates = validDates, reminders = childReminders)
}

fun ReminderTemplateEntity.toBackupDto() = ReminderTemplateBackupDto(
    id = id,
    name = name,
    offsetsCsv = offsetsCsv,
    createdAt = createdAt,
)

fun ReminderTemplateBackupDto.toEntity() = ReminderTemplateEntity(
    id = id,
    name = name,
    offsetsCsv = offsetsCsv,
    createdAt = createdAt,
)
