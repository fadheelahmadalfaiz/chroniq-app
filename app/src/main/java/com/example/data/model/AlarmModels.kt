package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class AlarmSourceType {
    MANUAL,
    GOOGLE_CALENDAR,
}

enum class RecurrenceType {
    SINGLE_DATE,
    MULTI_DATE,
    MONTHLY_DATE,
    YEARLY_DATE,
    INTERVAL_DAYS,
}

enum class PostAlarmAction {
    AUTO_ARCHIVE,
    TURN_OFF,
    AUTO_DELETE,
}

enum class AlarmDateStatus {
    SCHEDULED,
    TRIGGERED,
    SNOOZED,
    DISMISSED,
    MISSED,
}

enum class SyncStatus {
    LOCAL,
    SYNCED,
    UPDATED,
    DELETED_REMOTE,
    CONFLICT,
    ERROR,
}

enum class EventFilterMode {
    ALL_EVENTS,
    KEYWORD_FILTER,
}

enum class DeletedEventBehavior {
    DISABLE,
    DELETE,
}

enum class CalendarAuthMethod {
    GOOGLE_OAUTH,
    DEVICE_CALENDAR,
}

enum class MissedAlarmBehavior {
    NOTIFY_ONLY,
    AUTO_ARCHIVE,
    AUTO_DISMISS,
}

enum class AlarmHistoryOutcome {
    COMPLETED,
    DISMISSED,
    MISSED,
    SNOOZED,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentColorPreset(val displayNameId: String, val displayNameEn: String, val seedHex: Long) {
    BLUE("Biru (Default Chroniq)", "Blue (Default Chroniq)", 0xFF5B6BFF),
    INDIGO("Indigo", "Indigo", 0xFF4F46E5),
    PURPLE("Ungu", "Purple", 0xFF8B5CF6),
    TEAL("Teal", "Teal", 0xFF0D9488),
    GREEN("Hijau", "Green", 0xFF16A34A),
    ORANGE("Oranye", "Orange", 0xFFEA580C),
    ROSE("Mawar", "Rose", 0xFFE11D48),
}

enum class TimeFormatMode {
    HOUR_24,
    HOUR_12,
}

enum class FirstDayOfWeekPref(val dayOfWeek: DayOfWeek) {
    MONDAY(DayOfWeek.MONDAY),
    SUNDAY(DayOfWeek.SUNDAY),
    SATURDAY(DayOfWeek.SATURDAY),
}

enum class AppLanguage(val tag: String) {
    INDONESIAN("id"),
    ENGLISH("en"),
}

@Entity(
    tableName = "alarms",
    indices = [
        Index(value = ["gcal_event_id", "gcal_calendar_id"]),
    ],
)
data class AlarmEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "source_type") val sourceType: String = AlarmSourceType.MANUAL.name,
    @ColumnInfo(name = "gcal_event_id") val gcalEventId: String? = null,
    @ColumnInfo(name = "gcal_calendar_id") val gcalCalendarId: String? = null,
    @ColumnInfo(name = "gcal_calendar_name") val gcalCalendarName: String? = null,
    @ColumnInfo(name = "gcal_account_email") val gcalAccountEmail: String? = null,
    @ColumnInfo(name = "event_location") val eventLocation: String? = null,
    @ColumnInfo(name = "event_start_time_millis") val eventStartTimeMillis: Long? = null,
    val label: String,
    val description: String = "",
    val category: String = "General",
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "recurrence_type") val recurrenceType: String = RecurrenceType.SINGLE_DATE.name,
    @ColumnInfo(name = "interval_days") val intervalDays: Int = 0,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "sound_uri") val soundUri: String? = null,
    @ColumnInfo(name = "sound_name") val soundName: String = "Default Alarm",
    val vibrate: Boolean = true,
    @ColumnInfo(name = "volume_level") val volumeLevel: Int = 90,
    @ColumnInfo(name = "fade_in_seconds") val fadeInSeconds: Int = 15,
    @ColumnInfo(name = "snooze_duration_minutes") val snoozeDurationMinutes: Int = 10,
    @ColumnInfo(name = "max_snooze_attempts") val maxSnoozeAttempts: Int = 3,
    @ColumnInfo(name = "alarm_duration_minutes") val alarmDurationMinutes: Int = 10,
    @ColumnInfo(name = "missed_alarm_behavior") val missedAlarmBehavior: String = MissedAlarmBehavior.NOTIFY_ONLY.name,
    @ColumnInfo(name = "post_alarm_action") val postAlarmAction: String = PostAlarmAction.AUTO_ARCHIVE.name,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "is_smart_reminder_enabled") val isSmartReminderEnabled: Boolean = false,
    @ColumnInfo(name = "offset_minutes_before") val offsetMinutesBefore: Int = 0,
    @ColumnInfo(name = "sync_status") val syncStatus: String = SyncStatus.LOCAL.name,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "record_version") val recordVersion: Int = 1,
    @ColumnInfo(name = "device_id") val deviceId: String = "",
) {
    val sourceTypeEnum: AlarmSourceType
        get() = runCatching { AlarmSourceType.valueOf(sourceType) }.getOrDefault(AlarmSourceType.MANUAL)

    val recurrenceTypeEnum: RecurrenceType
        get() = runCatching { RecurrenceType.valueOf(recurrenceType) }.getOrDefault(RecurrenceType.SINGLE_DATE)

    val postAlarmActionEnum: PostAlarmAction
        get() = runCatching { PostAlarmAction.valueOf(postAlarmAction) }.getOrDefault(PostAlarmAction.AUTO_ARCHIVE)

    val missedAlarmBehaviorEnum: MissedAlarmBehavior
        get() = runCatching { MissedAlarmBehavior.valueOf(missedAlarmBehavior) }.getOrDefault(MissedAlarmBehavior.NOTIFY_ONLY)

    val syncStatusEnum: SyncStatus
        get() = runCatching { SyncStatus.valueOf(syncStatus) }.getOrDefault(SyncStatus.LOCAL)

    val isGoogleCalendar: Boolean
        get() = sourceTypeEnum == AlarmSourceType.GOOGLE_CALENDAR

    val isSoftDeleted: Boolean
        get() = deletedAt != null

    val formattedTime: String
        get() = formatAlarmTime(hour, minute, TimeFormatMode.HOUR_24)

    fun formattedTime(mode: TimeFormatMode): String = formatAlarmTime(hour, minute, mode)
}

@Entity(
    tableName = "alarm_dates",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarm_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["alarm_id"]), Index(value = ["target_date"])],
)
data class AlarmDateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "alarm_id") val alarmId: String,
    @ColumnInfo(name = "target_date") val targetDate: String, // ISO YYYY-MM-DD
    val status: String = AlarmDateStatus.SCHEDULED.name,
) {
    val localDate: LocalDate
        get() = LocalDate.parse(targetDate)

    val statusEnum: AlarmDateStatus
        get() = runCatching { AlarmDateStatus.valueOf(status) }.getOrDefault(AlarmDateStatus.SCHEDULED)
}

@Entity(
    tableName = "smart_reminders",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_alarm_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["parent_alarm_id"])],
)
data class SmartReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "parent_alarm_id") val parentAlarmId: String,
    @ColumnInfo(name = "offset_minutes") val offsetMinutes: Int, // 0 = exact event time
    @ColumnInfo(name = "custom_title") val customTitle: String = "",
    @ColumnInfo(name = "custom_message") val customMessage: String = "",
    @ColumnInfo(name = "sound_uri") val soundUri: String? = null,
    @ColumnInfo(name = "sound_name") val soundName: String = "Default Alarm",
    val vibrate: Boolean = true,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = true,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int = 5,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reminder_templates")
data class ReminderTemplateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    @ColumnInfo(name = "offsets_csv") val offsetsCsv: String, // e.g. "180,120,60,30,0"
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    fun parsedOffsets(): List<Int> = offsetsCsv
        .split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .distinct()
        .sortedDescending()
}

@Entity(
    tableName = "alarm_history",
    indices = [Index(value = ["triggered_at"]), Index(value = ["alarm_id"])],
)
data class AlarmHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "alarm_id") val alarmId: String,
    @ColumnInfo(name = "alarm_label") val alarmLabel: String,
    @ColumnInfo(name = "source_type") val sourceType: String = AlarmSourceType.MANUAL.name,
    @ColumnInfo(name = "reminder_title") val reminderTitle: String? = null,
    @ColumnInfo(name = "outcome") val outcome: String, // COMPLETED, DISMISSED, MISSED, SNOOZED
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long = System.currentTimeMillis(),
) {
    val outcomeEnum: AlarmHistoryOutcome
        get() = runCatching { AlarmHistoryOutcome.valueOf(outcome) }.getOrDefault(AlarmHistoryOutcome.DISMISSED)
}

@Entity(tableName = "cloud_backup_records")
data class CloudBackupRecordEntity(
    @PrimaryKey val backupId: String,
    @ColumnInfo(name = "user_email") val userEmail: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "backup_version") val backupVersion: Int = 2,
    @ColumnInfo(name = "alarm_count") val alarmCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "payload_json") val payloadJson: String? = null,
)

data class AlarmWithDates(
    @Embedded val alarm: AlarmEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "alarm_id",
    )
    val dates: List<AlarmDateEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "parent_alarm_id",
    )
    val reminders: List<SmartReminderEntity> = emptyList(),
) {
    val sortedDates: List<AlarmDateEntity>
        get() = dates.sortedBy { it.targetDate }

    val sortedReminders: List<SmartReminderEntity>
        get() = reminders.sortedByDescending { it.offsetMinutes }

    fun nextScheduledDate(nowMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): AlarmDateEntity? {
        if (!alarm.isEnabled || alarm.isArchived || alarm.isSoftDeleted) return null
        return sortedDates.firstOrNull { dateEntity ->
            val st = dateEntity.statusEnum
            (st == AlarmDateStatus.SCHEDULED || st == AlarmDateStatus.SNOOZED) &&
                triggerMillisForDate(dateEntity.localDate, alarm.hour, alarm.minute, zoneId) > nowMillis
        }
    }

    fun nextTriggerMillis(nowMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val next = nextScheduledDate(nowMillis, zoneId) ?: return null
        return triggerMillisForDate(next.localDate, alarm.hour, alarm.minute, zoneId)
    }

    fun allLocalDates(): Set<LocalDate> = dates.mapNotNull { runCatching { it.localDate }.getOrNull() }.toSet()
}

data class ScheduledReminderOccurrence(
    val reminder: SmartReminderEntity,
    val parentDate: LocalDate,
    val triggerDateTime: LocalDateTime,
    val triggerMillis: Long,
    val isInPast: Boolean,
)

@Entity(tableName = "synced_calendars")
data class SyncedCalendarEntity(
    @PrimaryKey @ColumnInfo(name = "calendar_id") val calendarId: String,
    @ColumnInfo(name = "account_email") val accountEmail: String,
    val summary: String,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#5B6BFF",
    @ColumnInfo(name = "is_selected") val isSelected: Boolean = true,
    @ColumnInfo(name = "sync_token") val syncToken: String? = null,
    @ColumnInfo(name = "source_kind") val sourceKind: String = CalendarAuthMethod.GOOGLE_OAUTH.name,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
) {
    val sourceKindEnum: CalendarAuthMethod
        get() = runCatching { CalendarAuthMethod.valueOf(sourceKind) }.getOrDefault(CalendarAuthMethod.GOOGLE_OAUTH)
}

@Entity(tableName = "google_accounts")
data class GoogleAccountEntity(
    @PrimaryKey val email: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "auth_method") val authMethod: String = CalendarAuthMethod.GOOGLE_OAUTH.name,
    @ColumnInfo(name = "token_expired") val tokenExpired: Boolean = false,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long? = null,
) {
    val authMethodEnum: CalendarAuthMethod
        get() = runCatching { CalendarAuthMethod.valueOf(authMethod) }.getOrDefault(CalendarAuthMethod.GOOGLE_OAUTH)
}

data class SyncPreferences(
    val defaultOffsetMinutes: Int = 30,
    val filterMode: EventFilterMode = EventFilterMode.ALL_EVENTS,
    val keywords: List<String> = listOf("#alarm", "[ALARM]", "Shift", "Ujian", "Flight"),
    val ignoreAllDayEvents: Boolean = true,
    val allDayAlarmHour: Int = 7,
    val allDayAlarmMinute: Int = 0,
    val deletedEventBehavior: DeletedEventBehavior = DeletedEventBehavior.DISABLE,
    val syncIntervalHours: Int = 6,
    val lastGlobalSyncAt: Long = 0L,
    val enableGcalSmartReminders: Boolean = false,
    val gcalSmartReminderOffsets: List<Int> = listOf(60, 30, 10),
)

data class PersonalizationPrefs(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentColor: AccentColorPreset = AccentColorPreset.BLUE,
    val customAccentArgb: Long? = null,
    val timeFormat: TimeFormatMode = TimeFormatMode.HOUR_24,
    val firstDayOfWeek: FirstDayOfWeekPref = FirstDayOfWeekPref.MONDAY,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    // Alarm & Sound defaults
    val defaultSoundUri: String? = null,
    val defaultSoundName: String = "Default Alarm",
    val defaultVolumeLevel: Int = 90,
    val defaultVibrate: Boolean = true,
    val defaultFadeInSeconds: Int = 15,
    val defaultSnoozeMinutes: Int = 10,
    val defaultMaxSnoozeAttempts: Int = 3,
    // Scheduling defaults
    val defaultAlarmDurationMinutes: Int = 10,
    val defaultRecurrence: RecurrenceType = RecurrenceType.SINGLE_DATE,
    val defaultPostAlarmAction: PostAlarmAction = PostAlarmAction.AUTO_ARCHIVE,
    val defaultMissedBehavior: MissedAlarmBehavior = MissedAlarmBehavior.NOTIFY_ONLY,
    val defaultSmartRemindersEnabled: Boolean = false,
    // Backup & Cloud metadata
    val deviceId: String = "",
    val lastLocalBackupAt: Long = 0L,
    val cloudApiBaseUrl: String = "",
    val cloudUserEmail: String = "",
    val lastCloudSyncAt: Long = 0L,
    val autoCloudSyncEnabled: Boolean = false,
)

fun triggerMillisForDate(
    date: LocalDate,
    hour: Int,
    minute: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    return LocalDateTime.of(date, LocalTime.of(hour, minute))
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

fun computeSmartReminderSchedule(
    dates: List<LocalDate>,
    hour: Int,
    minute: Int,
    reminders: List<SmartReminderEntity>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledReminderOccurrence> {
    val result = mutableListOf<ScheduledReminderOccurrence>()
    val uniqueDates = dates.distinct().sorted()
    for (date in uniqueDates) {
        val parentZdt = LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zoneId)
        for (reminder in reminders.sortedByDescending { it.offsetMinutes }) {
            val reminderZdt = parentZdt.minusMinutes(reminder.offsetMinutes.toLong())
            val triggerMillis = reminderZdt.toInstant().toEpochMilli()
            result.add(
                ScheduledReminderOccurrence(
                    reminder = reminder,
                    parentDate = date,
                    triggerDateTime = reminderZdt.toLocalDateTime(),
                    triggerMillis = triggerMillis,
                    isInPast = triggerMillis <= nowMillis,
                ),
            )
        }
    }
    return result.sortedBy { it.triggerMillis }
}

fun formatAlarmTime(hour: Int, minute: Int, mode: TimeFormatMode): String {
    val safeH = hour.coerceIn(0, 23)
    val safeM = minute.coerceIn(0, 59)
    return when (mode) {
        TimeFormatMode.HOUR_24 -> String.format(Locale.US, "%02d:%02d", safeH, safeM)
        TimeFormatMode.HOUR_12 -> {
            val amPm = if (safeH < 12) "AM" else "PM"
            val hour12 = when {
                safeH == 0 -> 12
                safeH > 12 -> safeH - 12
                else -> safeH
            }
            String.format(Locale.US, "%d:%02d %s", hour12, safeM, amPm)
        }
    }
}

fun formatOffsetLabel(offsetMinutes: Int, language: AppLanguage = AppLanguage.INDONESIAN): String {
    val isEn = language == AppLanguage.ENGLISH
    if (offsetMinutes <= 0) return if (isEn) "At event time" else "Tepat saat acara"
    val weeks = offsetMinutes / (7 * 24 * 60)
    val days = (offsetMinutes % (7 * 24 * 60)) / (24 * 60)
    val hours = (offsetMinutes % (24 * 60)) / 60
    val mins = offsetMinutes % 60
    val parts = mutableListOf<String>()
    if (weeks > 0) parts.add(if (isEn) "$weeks wk" else "$weeks mgg")
    if (days > 0) parts.add(if (isEn) "$days d" else "$days hr")
    if (hours > 0) parts.add(if (isEn) "$hours hr" else "$hours jam")
    if (mins > 0) parts.add(if (isEn) "$mins min" else "$mins mnt")
    val joined = parts.joinToString(" ")
    return if (isEn) "$joined before" else "$joined sebelum"
}

fun formatCountdownIndonesian(
    targetMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    language: AppLanguage = AppLanguage.INDONESIAN,
): String {
    val isEn = language == AppLanguage.ENGLISH
    val diffMs = targetMillis - nowMillis
    if (diffMs <= 0L) return if (isEn) "Ringing soon" else "Segera berbunyi"
    val duration = Duration.ofMillis(diffMs)
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return buildString {
        if (days > 0) append(if (isEn) "${days}d " else "${days}h ")
        if (hours > 0) append(if (isEn) "${hours}h " else "${hours}j ")
        if (days == 0L && hours == 0L && minutes == 0L) {
            append(if (isEn) "<1m" else "<1 mnt")
        } else {
            append("${minutes}m")
        }
    }.trim()
}

fun formatCountdownLongIndonesian(
    targetMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    language: AppLanguage = AppLanguage.INDONESIAN,
): String {
    val isEn = language == AppLanguage.ENGLISH
    val diffMs = targetMillis - nowMillis
    if (diffMs <= 0L) return if (isEn) "Less than 1 minute" else "Kurang dari 1 menit lagi"
    val duration = Duration.ofMillis(diffMs)
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add(if (isEn) "$days day${if (days > 1) "s" else ""}" else "$days hari")
    if (hours > 0) parts.add(if (isEn) "$hours hour${if (hours > 1) "s" else ""}" else "$hours jam")
    if (minutes > 0 || parts.isEmpty()) {
        val m = if (minutes == 0L) 1L else minutes
        parts.add(if (isEn) "$m min" else "$m menit")
    }
    return if (isEn) "In ${parts.joinToString(", ")}" else "Dalam ${parts.joinToString(" ")}"
}

fun formatRelativeSyncTime(
    lastSyncedAt: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    language: AppLanguage = AppLanguage.INDONESIAN,
): String {
    val isEn = language == AppLanguage.ENGLISH
    if (lastSyncedAt == null || lastSyncedAt <= 0L) {
        return if (isEn) "Never synced" else "Belum pernah disinkronkan"
    }
    val diffMin = ((nowMillis - lastSyncedAt) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMin < 1L -> if (isEn) "Synced just now" else "Baru saja disinkronkan"
        diffMin < 60L -> if (isEn) "Synced ${diffMin}m ago" else "Disinkronkan $diffMin mnt lalu"
        diffMin < 24 * 60L -> if (isEn) "Synced ${diffMin / 60}h ago" else "Disinkronkan ${diffMin / 60} jam lalu"
        else -> if (isEn) "Synced ${diffMin / (24 * 60)}d ago" else "Disinkronkan ${diffMin / (24 * 60)} hari lalu"
    }
}

private val LOCALE_ID: Locale = Locale.forLanguageTag("id-ID")
private val LOCALE_EN: Locale = Locale.US

fun LocalDate.formatShortId(language: AppLanguage = AppLanguage.INDONESIAN): String {
    val loc = if (language == AppLanguage.ENGLISH) LOCALE_EN else LOCALE_ID
    return this.format(DateTimeFormatter.ofPattern("d MMM", loc))
}

fun LocalDate.formatFullId(language: AppLanguage = AppLanguage.INDONESIAN): String {
    val loc = if (language == AppLanguage.ENGLISH) LOCALE_EN else LOCALE_ID
    return this.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", loc))
}

fun LocalDate.formatLongDate(language: AppLanguage = AppLanguage.INDONESIAN): String {
    val loc = if (language == AppLanguage.ENGLISH) LOCALE_EN else LOCALE_ID
    return this.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", loc))
}

fun Long.formatClockTime(
    mode: TimeFormatMode = TimeFormatMode.HOUR_24,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val zdt = Instant.ofEpochMilli(this).atZone(zoneId)
    return formatAlarmTime(zdt.hour, zdt.minute, mode)
}

fun orderedDaysOfWeek(firstDay: FirstDayOfWeekPref): List<DayOfWeek> {
    val start = firstDay.dayOfWeek
    return (0 until 7).map { offset -> start.plus(offset.toLong()) }
}

fun DayOfWeek.shortLabel(language: AppLanguage): String {
    return if (language == AppLanguage.ENGLISH) {
        when (this) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
        }
    } else {
        when (this) {
            DayOfWeek.MONDAY -> "Sen"
            DayOfWeek.TUESDAY -> "Sel"
            DayOfWeek.WEDNESDAY -> "Rab"
            DayOfWeek.THURSDAY -> "Kam"
            DayOfWeek.FRIDAY -> "Jum"
            DayOfWeek.SATURDAY -> "Sab"
            DayOfWeek.SUNDAY -> "Min"
        }
    }
}
