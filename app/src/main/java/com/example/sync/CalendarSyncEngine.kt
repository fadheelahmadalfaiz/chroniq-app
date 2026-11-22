package com.example.sync

import com.example.alarm.AlarmNotificationHelper
import com.example.alarm.AlarmScheduler
import com.example.data.local.AlarmDao
import com.example.data.local.SyncPreferencesStore
import com.example.data.model.AlarmDateEntity
import com.example.data.model.AlarmDateStatus
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmSourceType
import com.example.data.model.CalendarAuthMethod
import com.example.data.model.DeletedEventBehavior
import com.example.data.model.EventFilterMode
import com.example.data.model.GoogleAccountEntity
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncPreferences
import com.example.data.model.SyncStatus
import com.example.data.model.SyncedCalendarEntity
import com.example.data.remote.GoogleCalendarApi
import com.example.data.remote.GoogleCalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class SyncSummary(
    val success: Boolean,
    val createdCount: Int = 0,
    val updatedCount: Int = 0,
    val removedOrDisabledCount: Int = 0,
    val message: String,
    val tokenExpired: Boolean = false,
)

class CalendarSyncEngine(
    private val alarmDao: AlarmDao,
    private val syncPreferencesStore: SyncPreferencesStore,
    private val calendarApi: GoogleCalendarApi,
    private val authManager: GoogleCalendarAuthManager,
    private val deviceCalendarReader: DeviceCalendarReader,
    private val alarmScheduler: AlarmScheduler,
    private val notificationHelper: AlarmNotificationHelper,
) {

    suspend fun connectGoogleAccountWithToken(accessToken: String): SyncSummary {
        val response = runCatching {
            calendarApi.getCalendarList(bearerToken = "Bearer $accessToken")
        }.getOrElse { exc ->
            return SyncSummary(
                success = false,
                message = "Gagal menghubungi Google Calendar API: ${exc.localizedMessage ?: "Periksa koneksi internet."}",
            )
        }

        if (!response.isSuccessful) {
            return SyncSummary(
                success = false,
                message = "Gagal memuat daftar kalender Google (HTTP ${response.code()}).",
                tokenExpired = response.code() == 401,
            )
        }

        val items = response.body()?.items.orEmpty()
        val primaryCalendar = items.firstOrNull { it.primary == true } ?: items.firstOrNull()
        val accountEmail = primaryCalendar?.id ?: "google-account@gmail.com"
        val displayName = primaryCalendar?.summaryOverride ?: primaryCalendar?.summary ?: accountEmail

        authManager.saveToken(accountEmail, accessToken)
        alarmDao.upsertGoogleAccount(
            GoogleAccountEntity(
                email = accountEmail,
                displayName = displayName,
                authMethod = CalendarAuthMethod.GOOGLE_OAUTH.name,
                tokenExpired = false,
                lastSyncedAt = System.currentTimeMillis(),
            ),
        )

        val existingById = alarmDao.getAllSyncedCalendars().associateBy { it.calendarId }
        val calendarsToSave = items.map { item ->
            val existing = existingById[item.id]
            SyncedCalendarEntity(
                calendarId = item.id,
                accountEmail = accountEmail,
                summary = item.summaryOverride ?: item.summary ?: item.id,
                colorHex = item.backgroundColor ?: "#5B6BFF",
                isSelected = existing?.isSelected ?: (item.primary == true || item.selected == true),
                syncToken = existing?.syncToken,
                sourceKind = CalendarAuthMethod.GOOGLE_OAUTH.name,
                lastSyncedAt = existing?.lastSyncedAt,
            )
        }
        if (calendarsToSave.isNotEmpty()) {
            alarmDao.upsertCalendars(calendarsToSave)
        }

        return syncAllConnectedCalendars()
    }

    suspend fun connectDeviceCalendars(): SyncSummary {
        if (!deviceCalendarReader.hasReadCalendarPermission()) {
            return SyncSummary(
                success = false,
                message = "Izin baca kalender perangkat belum diberikan.",
            )
        }

        val discovered = deviceCalendarReader.readDeviceCalendars()
        if (discovered.isEmpty()) {
            return SyncSummary(
                success = false,
                message = "Tidak ada kalender yang ditemukan di perangkat ini.",
            )
        }

        val existingById = alarmDao.getAllSyncedCalendars().associateBy { it.calendarId }
        val groupedByAccount = discovered.groupBy { it.accountEmail }

        groupedByAccount.forEach { (email, _) ->
            alarmDao.upsertGoogleAccount(
                GoogleAccountEntity(
                    email = email,
                    displayName = email,
                    authMethod = CalendarAuthMethod.DEVICE_CALENDAR.name,
                    tokenExpired = false,
                    lastSyncedAt = System.currentTimeMillis(),
                ),
            )
        }

        val merged = discovered.map { cal ->
            val existing = existingById[cal.calendarId]
            cal.copy(
                isSelected = existing?.isSelected ?: cal.isSelected,
                lastSyncedAt = existing?.lastSyncedAt,
            )
        }
        alarmDao.upsertCalendars(merged)

        return syncAllConnectedCalendars()
    }

    suspend fun syncAllConnectedCalendars(): SyncSummary {
        val prefs = syncPreferencesStore.getCurrent()
        val selectedCalendars = alarmDao.getSelectedSyncedCalendars()
        if (selectedCalendars.isEmpty()) {
            return SyncSummary(
                success = true,
                message = "Tidak ada kalender aktif yang dipilih untuk disinkronkan.",
            )
        }

        var created = 0
        var updated = 0
        var removed = 0
        var anyTokenExpired = false
        val nowMillis = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()

        for (calendar in selectedCalendars) {
            when (calendar.sourceKindEnum) {
                CalendarAuthMethod.GOOGLE_OAUTH -> {
                    val outcome = syncSingleOAuthCalendar(calendar, prefs, nowMillis, zoneId)
                    created += outcome.createdCount
                    updated += outcome.updatedCount
                    removed += outcome.removedOrDisabledCount
                    if (outcome.tokenExpired) {
                        anyTokenExpired = true
                        alarmDao.setAccountTokenExpired(calendar.accountEmail, true)
                        notificationHelper.showTokenExpiredNotification(calendar.accountEmail)
                    } else if (outcome.success) {
                        alarmDao.markAccountSynced(calendar.accountEmail, nowMillis)
                    }
                }

                CalendarAuthMethod.DEVICE_CALENDAR -> {
                    val outcome = syncSingleDeviceCalendar(calendar, prefs, nowMillis, zoneId)
                    created += outcome.createdCount
                    updated += outcome.updatedCount
                    removed += outcome.removedOrDisabledCount
                    if (outcome.success) {
                        alarmDao.markAccountSynced(calendar.accountEmail, nowMillis)
                    }
                }
            }
        }

        syncPreferencesStore.updateLastGlobalSyncAt(nowMillis)
        alarmScheduler.rescheduleAllEnabledAlarms(alarmDao.getAllActiveAlarmsWithDates())

        return if (anyTokenExpired) {
            SyncSummary(
                success = false,
                createdCount = created,
                updatedCount = updated,
                removedOrDisabledCount = removed,
                message = "Token Google Calendar kedaluwarsa. Silakan hubungkan ulang akun Anda.",
                tokenExpired = true,
            )
        } else {
            SyncSummary(
                success = true,
                createdCount = created,
                updatedCount = updated,
                removedOrDisabledCount = removed,
                message = "Sinkronisasi selesai: $created baru, $updated diperbarui, $removed dinonaktifkan/dihapus.",
            )
        }
    }

    private suspend fun syncSingleOAuthCalendar(
        calendar: SyncedCalendarEntity,
        prefs: SyncPreferences,
        nowMillis: Long,
        zoneId: ZoneId,
    ): SyncSummary {
        var token = authManager.getToken(calendar.accountEmail)
        if (token.isNullOrBlank()) {
            token = authManager.trySilentRefreshToken()?.also {
                authManager.saveToken(calendar.accountEmail, it)
            }
        }
        if (token.isNullOrBlank()) {
            return SyncSummary(success = false, message = "Token tidak tersedia", tokenExpired = true)
        }

        val response = fetchEventsWithRetry(calendar, token, nowMillis)
            ?: return SyncSummary(success = false, message = "Gagal terhubung ke Google Calendar API")

        if (response.code() == 401) {
            val refreshed = authManager.trySilentRefreshToken()
            if (refreshed.isNullOrBlank()) {
                return SyncSummary(success = false, message = "Token kedaluwarsa", tokenExpired = true)
            }
            authManager.saveToken(calendar.accountEmail, refreshed)
            val retryResp = fetchEventsWithRetry(calendar, refreshed, nowMillis)
            if (retryResp == null || !retryResp.isSuccessful) {
                return SyncSummary(
                    success = false,
                    message = "Gagal memperbarui sesi Google Calendar",
                    tokenExpired = retryResp?.code() == 401,
                )
            }
            return processGoogleEvents(calendar, retryResp.body()?.items.orEmpty(), retryResp.body()?.nextSyncToken, prefs, nowMillis, zoneId)
        }

        if (response.code() == 410) {
            alarmDao.updateCalendarSyncToken(calendar.calendarId, null, nowMillis)
            val fullResp = fetchEventsWithRetry(calendar.copy(syncToken = null), token, nowMillis)
            if (fullResp == null || !fullResp.isSuccessful) {
                return SyncSummary(success = false, message = "Sinkronisasi ulang gagal")
            }
            return processGoogleEvents(calendar, fullResp.body()?.items.orEmpty(), fullResp.body()?.nextSyncToken, prefs, nowMillis, zoneId)
        }

        if (!response.isSuccessful) {
            return SyncSummary(success = false, message = "Google Calendar HTTP ${response.code()}")
        }

        val body = response.body()
        return processGoogleEvents(
            calendar = calendar,
            events = body?.items.orEmpty(),
            nextSyncToken = body?.nextSyncToken,
            prefs = prefs,
            nowMillis = nowMillis,
            zoneId = zoneId,
        )
    }

    private suspend fun fetchEventsWithRetry(
        calendar: SyncedCalendarEntity,
        accessToken: String,
        nowMillis: Long,
    ) = runCatching {
        if (!calendar.syncToken.isNullOrBlank()) {
            calendarApi.getCalendarEvents(
                bearerToken = "Bearer $accessToken",
                calendarId = calendar.calendarId,
                syncToken = calendar.syncToken,
                showDeleted = true,
            )
        } else {
            val timeMin = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(nowMillis))
            val timeMax = DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochMilli(nowMillis).plusSeconds(365L * 24L * 3600L),
            )
            calendarApi.getCalendarEvents(
                bearerToken = "Bearer $accessToken",
                calendarId = calendar.calendarId,
                singleEvents = true,
                timeMin = timeMin,
                timeMax = timeMax,
                showDeleted = true,
            )
        }
    }.getOrNull()

    private suspend fun processGoogleEvents(
        calendar: SyncedCalendarEntity,
        events: List<GoogleCalendarEvent>,
        nextSyncToken: String?,
        prefs: SyncPreferences,
        nowMillis: Long,
        zoneId: ZoneId,
    ): SyncSummary {
        var created = 0
        var updated = 0
        var removed = 0

        for (event in events) {
            if (event.status.equals("cancelled", ignoreCase = true)) {
                val existing = alarmDao.findByGcalEvent(event.id, calendar.calendarId)
                if (existing != null) {
                    applyDeletedBehavior(existing.alarm, existing.reminders, prefs.deletedEventBehavior)
                    removed++
                }
                continue
            }

            if (!matchesFilter(event.summary, event.description, prefs)) continue

            val isAllDay = event.start?.date != null && event.start.dateTime == null
            if (isAllDay && prefs.ignoreAllDayEvents) continue

            val computedTrigger = computeTriggerFromGoogleEvent(event, prefs, zoneId) ?: continue
            if (computedTrigger.triggerMillis <= nowMillis) continue

            val existing = alarmDao.findByGcalEvent(event.id, calendar.calendarId)
            if (existing == null) {
                val alarmId = UUID.randomUUID().toString()
                val defaultReminders = buildDefaultGcalReminders(alarmId, prefs)
                val alarm = AlarmEntity(
                    id = alarmId,
                    sourceType = AlarmSourceType.GOOGLE_CALENDAR.name,
                    gcalEventId = event.id,
                    gcalCalendarId = calendar.calendarId,
                    gcalCalendarName = calendar.summary,
                    gcalAccountEmail = calendar.accountEmail,
                    eventLocation = event.location?.takeIf { it.isNotBlank() },
                    eventStartTimeMillis = computedTrigger.eventStartMillis,
                    label = event.summary?.takeIf { it.isNotBlank() } ?: "Acara Google Calendar",
                    description = event.description?.take(240).orEmpty(),
                    category = "Google Calendar",
                    hour = computedTrigger.triggerDateTime.hour,
                    minute = computedTrigger.triggerDateTime.minute,
                    recurrenceType = RecurrenceType.SINGLE_DATE.name,
                    isEnabled = true,
                    vibrate = true,
                    volumeLevel = 90,
                    fadeInSeconds = 15,
                    snoozeDurationMinutes = 10,
                    postAlarmAction = PostAlarmAction.AUTO_ARCHIVE.name,
                    isSmartReminderEnabled = defaultReminders.isNotEmpty(),
                    offsetMinutesBefore = if (isAllDay) 0 else prefs.defaultOffsetMinutes,
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedAt = nowMillis,
                    updatedAt = nowMillis,
                )
                val dateEntity = AlarmDateEntity(
                    alarmId = alarmId,
                    targetDate = computedTrigger.triggerDateTime.toLocalDate().toString(),
                    status = AlarmDateStatus.SCHEDULED.name,
                )
                alarmDao.saveAlarmComplete(alarm, listOf(dateEntity), defaultReminders)
                created++
            } else {
                // Preserve user's per-event Smart Reminder overrides if any exist
                val remindersToKeep = if (existing.reminders.isNotEmpty()) {
                    existing.reminders
                } else {
                    buildDefaultGcalReminders(existing.alarm.id, prefs)
                }
                val updatedAlarm = existing.alarm.copy(
                    label = event.summary?.takeIf { it.isNotBlank() } ?: existing.alarm.label,
                    eventLocation = event.location?.takeIf { it.isNotBlank() },
                    eventStartTimeMillis = computedTrigger.eventStartMillis,
                    hour = computedTrigger.triggerDateTime.hour,
                    minute = computedTrigger.triggerDateTime.minute,
                    offsetMinutesBefore = if (isAllDay) 0 else prefs.defaultOffsetMinutes,
                    isSmartReminderEnabled = existing.alarm.isSmartReminderEnabled || remindersToKeep.isNotEmpty(),
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedAt = nowMillis,
                    updatedAt = nowMillis,
                    isArchived = false,
                    deletedAt = null,
                )
                val dateEntity = AlarmDateEntity(
                    alarmId = updatedAlarm.id,
                    targetDate = computedTrigger.triggerDateTime.toLocalDate().toString(),
                    status = AlarmDateStatus.SCHEDULED.name,
                )
                alarmDao.saveAlarmComplete(updatedAlarm, listOf(dateEntity), remindersToKeep)
                updated++
            }
        }

        alarmDao.updateCalendarSyncToken(
            calendarId = calendar.calendarId,
            syncToken = nextSyncToken ?: calendar.syncToken,
            syncedAt = nowMillis,
        )

        return SyncSummary(
            success = true,
            createdCount = created,
            updatedCount = updated,
            removedOrDisabledCount = removed,
            message = "OK",
        )
    }

    private suspend fun syncSingleDeviceCalendar(
        calendar: SyncedCalendarEntity,
        prefs: SyncPreferences,
        nowMillis: Long,
        zoneId: ZoneId,
    ): SyncSummary {
        val horizonMillis = nowMillis + 365L * 24L * 3600L * 1000L
        val events = deviceCalendarReader.readUpcomingEvents(calendar.calendarId, nowMillis, horizonMillis)
        val seenEventIds = mutableSetOf<String>()
        var created = 0
        var updated = 0
        var removed = 0

        for (evt in events) {
            if (!matchesFilter(evt.title, evt.description, prefs)) continue
            if (evt.isAllDay && prefs.ignoreAllDayEvents) continue

            val triggerDateTime: LocalDateTime = if (evt.isAllDay && evt.allDayLocalDate != null) {
                LocalDateTime.of(
                    evt.allDayLocalDate,
                    LocalTime.of(prefs.allDayAlarmHour, prefs.allDayAlarmMinute),
                )
            } else {
                Instant.ofEpochMilli(evt.startMillis)
                    .atZone(zoneId)
                    .toLocalDateTime()
                    .minusMinutes(prefs.defaultOffsetMinutes.toLong())
            }

            val triggerMillis = triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
            if (triggerMillis <= nowMillis) continue

            seenEventIds.add(evt.eventId)
            val existing = alarmDao.findByGcalEvent(evt.eventId, calendar.calendarId)
            if (existing == null) {
                val alarmId = UUID.randomUUID().toString()
                val defaultReminders = buildDefaultGcalReminders(alarmId, prefs)
                val alarm = AlarmEntity(
                    id = alarmId,
                    sourceType = AlarmSourceType.GOOGLE_CALENDAR.name,
                    gcalEventId = evt.eventId,
                    gcalCalendarId = calendar.calendarId,
                    gcalCalendarName = calendar.summary,
                    gcalAccountEmail = calendar.accountEmail,
                    eventLocation = evt.location?.takeIf { it.isNotBlank() },
                    eventStartTimeMillis = evt.startMillis,
                    label = evt.title,
                    description = evt.description?.take(240).orEmpty(),
                    category = "Google Calendar",
                    hour = triggerDateTime.hour,
                    minute = triggerDateTime.minute,
                    recurrenceType = RecurrenceType.SINGLE_DATE.name,
                    isEnabled = true,
                    isSmartReminderEnabled = defaultReminders.isNotEmpty(),
                    offsetMinutesBefore = if (evt.isAllDay) 0 else prefs.defaultOffsetMinutes,
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedAt = nowMillis,
                    updatedAt = nowMillis,
                )
                val date = AlarmDateEntity(
                    alarmId = alarmId,
                    targetDate = triggerDateTime.toLocalDate().toString(),
                    status = AlarmDateStatus.SCHEDULED.name,
                )
                alarmDao.saveAlarmComplete(alarm, listOf(date), defaultReminders)
                created++
            } else {
                val remindersToKeep = if (existing.reminders.isNotEmpty()) {
                    existing.reminders
                } else {
                    buildDefaultGcalReminders(existing.alarm.id, prefs)
                }
                val updatedAlarm = existing.alarm.copy(
                    label = evt.title,
                    eventLocation = evt.location?.takeIf { it.isNotBlank() },
                    eventStartTimeMillis = evt.startMillis,
                    hour = triggerDateTime.hour,
                    minute = triggerDateTime.minute,
                    offsetMinutesBefore = if (evt.isAllDay) 0 else prefs.defaultOffsetMinutes,
                    isSmartReminderEnabled = existing.alarm.isSmartReminderEnabled || remindersToKeep.isNotEmpty(),
                    syncStatus = SyncStatus.SYNCED.name,
                    lastSyncedAt = nowMillis,
                    updatedAt = nowMillis,
                )
                val date = AlarmDateEntity(
                    alarmId = updatedAlarm.id,
                    targetDate = triggerDateTime.toLocalDate().toString(),
                    status = AlarmDateStatus.SCHEDULED.name,
                )
                alarmDao.saveAlarmComplete(updatedAlarm, listOf(date), remindersToKeep)
                updated++
            }
        }

        // Detect deleted events for this device calendar
        val existingAlarms = alarmDao.getAlarmsByCalendarId(calendar.calendarId)
        for (existing in existingAlarms) {
            val eventId = existing.alarm.gcalEventId ?: continue
            val nextTrigger = existing.nextTriggerMillis(nowMillis, zoneId)
            if (nextTrigger != null && eventId !in seenEventIds) {
                applyDeletedBehavior(existing.alarm, existing.reminders, prefs.deletedEventBehavior)
                removed++
            }
        }

        alarmDao.updateCalendarSyncToken(calendar.calendarId, null, nowMillis)
        return SyncSummary(
            success = true,
            createdCount = created,
            updatedCount = updated,
            removedOrDisabledCount = removed,
            message = "OK",
        )
    }

    private fun buildDefaultGcalReminders(
        parentAlarmId: String,
        prefs: SyncPreferences,
    ): List<SmartReminderEntity> {
        if (!prefs.enableGcalSmartReminders) return emptyList()
        return prefs.gcalSmartReminderOffsets
            .distinct()
            .sortedDescending()
            .map { offsetMin ->
                SmartReminderEntity(
                    parentAlarmId = parentAlarmId,
                    offsetMinutes = offsetMin,
                    isEnabled = true,
                    vibrate = true,
                    snoozeMinutes = 5,
                )
            }
    }

    private suspend fun applyDeletedBehavior(
        alarm: AlarmEntity,
        reminders: List<SmartReminderEntity>,
        behavior: DeletedEventBehavior,
    ) {
        alarmScheduler.cancelAlarmAndReminders(alarm.id, reminders)
        when (behavior) {
            DeletedEventBehavior.DELETE -> alarmDao.softDeleteAlarmById(alarm.id)
            DeletedEventBehavior.DISABLE -> {
                alarmDao.updateAlarm(
                    alarm.copy(
                        isEnabled = false,
                        syncStatus = SyncStatus.DELETED_REMOTE.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private fun matchesFilter(title: String?, description: String?, prefs: SyncPreferences): Boolean {
        if (prefs.filterMode == EventFilterMode.ALL_EVENTS) return true
        if (prefs.keywords.isEmpty()) return true
        val haystack = "${title.orEmpty()} ${description.orEmpty()}".lowercase()
        return prefs.keywords.any { keyword -> haystack.contains(keyword.lowercase()) }
    }

    private data class ComputedEventTrigger(
        val eventStartMillis: Long,
        val triggerDateTime: LocalDateTime,
        val triggerMillis: Long,
    )

    private fun computeTriggerFromGoogleEvent(
        event: GoogleCalendarEvent,
        prefs: SyncPreferences,
        zoneId: ZoneId,
    ): ComputedEventTrigger? {
        val start = event.start ?: return null
        return when {
            !start.dateTime.isNullOrBlank() -> {
                val instant = runCatching { OffsetDateTime.parse(start.dateTime).toInstant() }
                    .getOrNull() ?: return null
                val eventZoned = instant.atZone(zoneId)
                val triggerDt = eventZoned.toLocalDateTime()
                    .minusMinutes(prefs.defaultOffsetMinutes.toLong())
                ComputedEventTrigger(
                    eventStartMillis = instant.toEpochMilli(),
                    triggerDateTime = triggerDt,
                    triggerMillis = triggerDt.atZone(zoneId).toInstant().toEpochMilli(),
                )
            }

            !start.date.isNullOrBlank() -> {
                val localDate = runCatching { LocalDate.parse(start.date) }.getOrNull() ?: return null
                val triggerDt = LocalDateTime.of(
                    localDate,
                    LocalTime.of(prefs.allDayAlarmHour, prefs.allDayAlarmMinute),
                )
                val millis = triggerDt.atZone(zoneId).toInstant().toEpochMilli()
                ComputedEventTrigger(
                    eventStartMillis = millis,
                    triggerDateTime = triggerDt,
                    triggerMillis = millis,
                )
            }

            else -> null
        }
    }
}
