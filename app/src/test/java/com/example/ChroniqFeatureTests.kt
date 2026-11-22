package com.example

import com.example.backup.BackupJsonManager
import com.example.backup.CloudBackupApi
import com.example.backup.SyncConflictResolver
import com.example.backup.toBackupDto
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmDateStatus
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmSourceType
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.FirstDayOfWeekPref
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.TimeFormatMode
import com.example.data.model.computeSmartReminderSchedule
import com.example.data.model.formatAlarmTime
import com.example.data.model.formatLongDate
import com.example.data.model.formatOffsetLabel
import com.example.data.model.orderedDaysOfWeek
import com.example.data.model.triggerMillisForDate
import com.example.widget.NextAlarmSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ChroniqFeatureTests {

    private val jakartaZone = ZoneId.of("Asia/Jakarta")
    private val utcZone = ZoneId.of("UTC")

    private fun makeAlarm(
        id: String,
        label: String,
        dateIso: String,
        hour: Int,
        minute: Int,
        enabled: Boolean = true,
        archived: Boolean = false,
        deletedAt: Long? = null,
        status: AlarmDateStatus = AlarmDateStatus.SCHEDULED,
        source: AlarmSourceType = AlarmSourceType.MANUAL,
        calendarName: String? = null,
        updatedAt: Long = 1_000L,
        recordVersion: Int = 1,
        deviceId: String = "dev-A",
        reminders: List<SmartReminderEntity> = emptyList(),
    ): AlarmWithDates {
        val alarm = AlarmEntity(
            id = id,
            sourceType = source.name,
            gcalCalendarName = calendarName,
            label = label,
            hour = hour,
            minute = minute,
            isEnabled = enabled,
            isArchived = archived,
            isSmartReminderEnabled = reminders.isNotEmpty(),
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            recordVersion = recordVersion,
            deviceId = deviceId,
        )
        val dateEntity = AlarmDateEntity(
            id = "date-$id",
            alarmId = id,
            targetDate = dateIso,
            status = status.name,
        )
        return AlarmWithDates(alarm = alarm, dates = listOf(dateEntity), reminders = reminders)
    }

    // ---------------------------------------------------------
    // 1. HOME SCREEN WIDGET — NEXT ALARM ONLY
    // ---------------------------------------------------------

    @Test
    fun `next alarm selector chooses single nearest active alarm across manual and gcal`() {
        val now = triggerMillisForDate(LocalDate.of(2026, 12, 20), 8, 0, jakartaZone)

        val disabledSooner = makeAlarm("disabled", "Disabled", "2026-12-20", 9, 0, enabled = false)
        val archivedSooner = makeAlarm("archived", "Archived", "2026-12-20", 9, 30, archived = true)
        val deletedSooner = makeAlarm("deleted", "Deleted", "2026-12-20", 9, 45, deletedAt = now - 1000)
        val dismissedSooner = makeAlarm("dismissed", "Dismissed", "2026-12-20", 10, 0, status = AlarmDateStatus.DISMISSED)
        val expiredPast = makeAlarm("past", "Past", "2026-12-20", 7, 30)

        val gcalNearest = makeAlarm(
            id = "gcal-nearest",
            label = "Client Demo",
            dateIso = "2026-12-21",
            hour = 10,
            minute = 30,
            source = AlarmSourceType.GOOGLE_CALENDAR,
            calendarName = "Work",
        )
        val manualLater = makeAlarm("manual-later", "Flight Check-in", "2026-12-25", 6, 0)

        val chosen = NextAlarmSelector.findNextActiveAlarm(
            alarms = listOf(
                disabledSooner,
                archivedSooner,
                deletedSooner,
                dismissedSooner,
                expiredPast,
                manualLater,
                gcalNearest,
            ),
            nowMillis = now,
            zoneId = jakartaZone,
        )

        assertNotNull(chosen)
        assertEquals("gcal-nearest", chosen!!.alarmId)
        assertEquals("Client Demo", chosen.title)
        assertTrue(chosen.isGoogleCalendar)
        assertEquals("Work", chosen.calendarName)
        assertEquals(LocalDate.of(2026, 12, 21), chosen.targetDate)
    }

    @Test
    fun `next alarm selector returns null when no upcoming active alarms exist`() {
        val now = triggerMillisForDate(LocalDate.of(2026, 12, 20), 12, 0, jakartaZone)
        val items = listOf(
            makeAlarm("a1", "Expired", "2026-12-19", 9, 0),
            makeAlarm("a2", "Off", "2026-12-25", 9, 0, enabled = false),
            makeAlarm("a3", "Archived", "2026-12-25", 9, 0, archived = true),
        )
        assertNull(NextAlarmSelector.findNextActiveAlarm(items, now, jakartaZone))
    }

    // ---------------------------------------------------------
    // 2. SMART ALARM REMINDER ENGINE
    // ---------------------------------------------------------

    @Test
    fun `smart reminder schedule calculates offsets across midnight and marks past occurrences`() {
        // Parent alarm at 00:30 on 25 Dec 2026 (Asia/Jakarta)
        val parentDate = LocalDate.of(2026, 12, 25)
        // Current time is 24 Dec 2026 23:45 (so 60m-before at 23:30 is in the past, 30m-before at 00:00 is upcoming)
        val now = triggerMillisForDate(LocalDate.of(2026, 12, 24), 23, 45, jakartaZone)

        val reminders = listOf(
            SmartReminderEntity(id = "r60", parentAlarmId = "p1", offsetMinutes = 60, customTitle = "1h prep"),
            SmartReminderEntity(id = "r30", parentAlarmId = "p1", offsetMinutes = 30, customTitle = "30m prep"),
            SmartReminderEntity(id = "r0", parentAlarmId = "p1", offsetMinutes = 0, customTitle = "Exact"),
        )

        val schedule = computeSmartReminderSchedule(
            dates = listOf(parentDate),
            hour = 0,
            minute = 30,
            reminders = reminders,
            nowMillis = now,
            zoneId = jakartaZone,
        )

        assertEquals(3, schedule.size)
        // Sorted chronologically: 23:30 (24 Dec), 00:00 (25 Dec), 00:30 (25 Dec)
        assertEquals(LocalDateTime.of(2026, 12, 24, 23, 30), schedule[0].triggerDateTime)
        assertTrue(schedule[0].isInPast)

        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 0), schedule[1].triggerDateTime)
        assertFalse(schedule[1].isInPast)

        assertEquals(LocalDateTime.of(2026, 12, 25, 0, 30), schedule[2].triggerDateTime)
        assertFalse(schedule[2].isInPast)
    }

    @Test
    fun `smart reminder schedule respects timezone differences and recalculates on parent edit`() {
        val date = LocalDate.of(2026, 12, 25)
        val rem = listOf(SmartReminderEntity(id = "r120", parentAlarmId = "p1", offsetMinutes = 120))

        val jakartaSched = computeSmartReminderSchedule(listOf(date), 10, 0, rem, nowMillis = 0L, zoneId = jakartaZone)
        val utcSched = computeSmartReminderSchedule(listOf(date), 10, 0, rem, nowMillis = 0L, zoneId = utcZone)

        // Asia/Jakarta is UTC+7, so 08:00 WIB is 7 hours earlier in epoch millis than 08:00 UTC
        val diffHours = (utcSched.first().triggerMillis - jakartaSched.first().triggerMillis) / (60 * 60 * 1000L)
        assertEquals(7L, diffHours)

        // Recalculated after parent changes from 10:00 -> 14:30
        val editedSched = computeSmartReminderSchedule(listOf(date), 14, 30, rem, nowMillis = 0L, zoneId = jakartaZone)
        assertEquals(LocalDateTime.of(2026, 12, 25, 12, 30), editedSched.first().triggerDateTime)
    }

    // ---------------------------------------------------------
    // 3. LOCAL JSON BACKUP & RESTORE
    // ---------------------------------------------------------

    @Test
    fun `local json backup round-trip preserves parent-child reminders and detects duplicates on merge`() {
        val manager = BackupJsonManager(context = null)
        val rem1 = SmartReminderEntity(id = "rem-1", parentAlarmId = "a-1", offsetMinutes = 60, customTitle = "Pack bag")
        val rem2 = SmartReminderEntity(id = "rem-2", parentAlarmId = "a-1", offsetMinutes = 15, customTitle = "Leave now")

        val alarm1 = makeAlarm("a-1", "Flight to Tokyo", "2026-12-28", 8, 30, reminders = listOf(rem1, rem2))
        val alarm2 = makeAlarm("a-2", "Dentist", "2026-12-29", 15, 0)
        val template = ReminderTemplateEntity(id = "tpl-1", name = "Travel", offsetsCsv = "180,60,15")

        val payload = manager.buildPayload(listOf(alarm1, alarm2), listOf(template), deviceId = "dev-1")
        val json = manager.serializeToJson(payload)

        // Validate against existing dataset containing only alarm1
        val preview = manager.parseAndValidate(json, existingAlarms = listOf(alarm1))
        assertTrue(preview.isValid)
        assertEquals(2, preview.totalAlarms)
        assertEquals(1, preview.duplicateAlarmsCount)
        assertEquals(1, preview.newAlarmsCount)
        assertEquals(2, preview.totalSmartReminders)
        assertEquals(1, preview.totalTemplates)

        val payloadObj = preview.payload!!
        // Compute merge items: should add only alarm2 and skip alarm1
        val (toSave, skipped) = manager.computeMergeItems(payloadObj, existing = listOf(alarm1))
        assertEquals(1, toSave.size)
        assertEquals("a-2", toSave.first().alarm.id)
        assertEquals(1, skipped)

        // Verify smart reminders are preserved when importing into empty device
        val (fullImport, fullSkipped) = manager.computeMergeItems(payloadObj, existing = emptyList())
        assertEquals(2, fullImport.size)
        assertEquals(0, fullSkipped)
        val restoredFlight = fullImport.first { it.alarm.id == "a-1" }
        assertEquals(2, restoredFlight.reminders.size)
        assertEquals("Pack bag", restoredFlight.sortedReminders.first().customTitle)
    }

    @Test
    fun `local json backup rejects corrupt json, invalid time, invalid date, and wrong app`() {
        val manager = BackupJsonManager(context = null)

        assertFalse(manager.parseAndValidate("{not-valid-json", emptyList()).isValid)
        assertFalse(manager.parseAndValidate("", emptyList()).isValid)

        val wrongApp = """{"app":"OtherApp","backup_version":1,"created_at":"2026-11-22T10:00:00Z","alarms":[]}"""
        assertFalse(manager.parseAndValidate(wrongApp, emptyList()).isValid)

        val badTime = """{"app":"Chroniq","backup_version":2,"created_at":"2026-11-22T10:00:00Z","alarms":[{"id":"1","title":"Bad","hour":25,"minute":0,"dates":["2026-12-25"]}]}"""
        assertFalse(manager.parseAndValidate(badTime, emptyList()).isValid)

        val badDate = """{"app":"Chroniq","backup_version":2,"created_at":"2026-11-22T10:00:00Z","alarms":[{"id":"1","title":"Bad","hour":8,"minute":0,"dates":["2026-99-99"]}]}"""
        assertFalse(manager.parseAndValidate(badDate, emptyList()).isValid)
    }

    // ---------------------------------------------------------
    // 4. MULTI-DEVICE CLOUD SYNC CONFLICT RESOLUTION
    // ---------------------------------------------------------

    @Test
    fun `sync conflict resolver resolves concurrent edits deterministically and preserves tombstones`() {
        // Shared alarm edited on two devices concurrently; remote is newer
        val localEdited = makeAlarm(
            id = "shared-1",
            label = "Meeting Local",
            dateIso = "2026-12-25",
            hour = 9,
            minute = 0,
            updatedAt = 2_000L,
            recordVersion = 2,
            deviceId = "phone-A",
        )
        val remoteEdited = makeAlarm(
            id = "shared-1",
            label = "Meeting Remote",
            dateIso = "2026-12-25",
            hour = 10,
            minute = 0,
            updatedAt = 3_000L,
            recordVersion = 3,
            deviceId = "tablet-B",
        ).toBackupDto()

        // Local-only alarm
        val localOnly = makeAlarm("local-only", "Gym", "2026-12-26", 6, 30, updatedAt = 1_500L, deviceId = "phone-A")
        // Remote-only new alarm
        val remoteOnly = makeAlarm("remote-only", "Webinar", "2026-12-27", 14, 0, updatedAt = 1_800L, deviceId = "tablet-B").toBackupDto()
        // Remote soft-deleted alarm
        val localAlive = makeAlarm("del-1", "Old Task", "2026-12-28", 11, 0, updatedAt = 1_000L, deviceId = "phone-A")
        val remoteDeleted = makeAlarm(
            id = "del-1",
            label = "Old Task",
            dateIso = "2026-12-28",
            hour = 11,
            minute = 0,
            deletedAt = 4_000L,
            updatedAt = 4_000L,
            recordVersion = 2,
            deviceId = "tablet-B",
        ).toBackupDto()

        val result = SyncConflictResolver.resolve(
            localAlarms = listOf(localEdited, localOnly, localAlive),
            remoteDtos = listOf(remoteEdited, remoteOnly, remoteDeleted),
        )

        assertEquals(4, result.mergedAlarms.size)
        assertEquals(2, result.conflicts.size) // shared-1 and del-1 both modified on different devices
        assertEquals(1, result.pulledNewCount)
        assertEquals(1, result.updatedLocalCount)
        assertEquals(1, result.keptLocalCount)
        assertEquals(1, result.softDeletedCount)

        val resolvedShared = result.mergedAlarms.first { it.alarm.id == "shared-1" }
        assertEquals("Meeting Remote", resolvedShared.alarm.label)
        assertEquals(10, resolvedShared.alarm.hour)

        val resolvedDeleted = result.mergedAlarms.first { it.alarm.id == "del-1" }
        assertTrue(resolvedDeleted.alarm.isSoftDeleted)
    }

    // ---------------------------------------------------------
    // 5. CLOUD BACKUP ENDPOINT SECURITY VALIDATION
    // ---------------------------------------------------------

    @Test
    fun `cloud api endpoint validator enforces https and blocks internal ssrf hosts`() {
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://api.chroniq.example.com").isSuccess)
        assertEquals(
            "https://api.chroniq.example.com/",
            CloudBackupApi.validateHttpsEndpoint("https://api.chroniq.example.com").getOrNull(),
        )
        assertTrue(CloudBackupApi.validateHttpsEndpoint("http://api.chroniq.example.com").isFailure)
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://localhost:8443").isFailure)
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://127.0.0.1/api").isFailure)
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://10.0.0.5/api").isFailure)
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://192.168.1.10/api").isFailure)
        assertTrue(CloudBackupApi.validateHttpsEndpoint("https://169.254.169.254/latest").isFailure)
    }

    // ---------------------------------------------------------
    // 6. PERSONALIZATION FORMATTING & LOCALIZATION
    // ---------------------------------------------------------

    @Test
    fun `personalization formatting supports 12h vs 24h, first day of week, and bilingual labels`() {
        assertEquals("00:05", formatAlarmTime(0, 5, TimeFormatMode.HOUR_24))
        assertEquals("12:05 AM", formatAlarmTime(0, 5, TimeFormatMode.HOUR_12))
        assertEquals("12:30 PM", formatAlarmTime(12, 30, TimeFormatMode.HOUR_12))
        assertEquals("11:45 PM", formatAlarmTime(23, 45, TimeFormatMode.HOUR_12))

        assertEquals(DayOfWeek.MONDAY, orderedDaysOfWeek(FirstDayOfWeekPref.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, orderedDaysOfWeek(FirstDayOfWeekPref.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, orderedDaysOfWeek(FirstDayOfWeekPref.SATURDAY).first())

        assertEquals("1 hr before", formatOffsetLabel(60, AppLanguage.ENGLISH))
        assertEquals("1 jam sebelum", formatOffsetLabel(60, AppLanguage.INDONESIAN))
        assertEquals("At event time", formatOffsetLabel(0, AppLanguage.ENGLISH))
        assertEquals("Tepat saat acara", formatOffsetLabel(0, AppLanguage.INDONESIAN))

        val date = LocalDate.of(2026, 12, 25)
        assertTrue(date.formatLongDate(AppLanguage.ENGLISH).contains("December"))
        assertTrue(date.formatLongDate(AppLanguage.INDONESIAN).contains("Desember"))
    }
}
