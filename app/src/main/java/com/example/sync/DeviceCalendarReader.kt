package com.example.sync

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.data.model.CalendarAuthMethod
import com.example.data.model.SyncedCalendarEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DeviceCalendarEvent(
    val eventId: String,
    val calendarId: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val isAllDay: Boolean,
    val allDayLocalDate: LocalDate?,
)

class DeviceCalendarReader(private val context: Context) {

    fun hasReadCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readDeviceCalendars(): List<SyncedCalendarEntity> {
        if (!hasReadCalendarPermission()) return emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
        )

        val result = mutableListOf<Pair<Boolean, SyncedCalendarEntity>>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            val accountIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val colorIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
            val visibleIdx = cursor.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(idIdx)
                val account = cursor.getString(accountIdx).orEmpty().ifBlank { "Google Calendar" }
                val accountType = if (typeIdx >= 0) cursor.getString(typeIdx).orEmpty() else ""
                val isGoogleAccount = accountType.equals("com.google", ignoreCase = true) || account.contains("@")
                val summary = cursor.getString(nameIdx).orEmpty().ifBlank { account }
                val colorInt = if (colorIdx >= 0) cursor.getInt(colorIdx) else 0xFF4285F4.toInt()
                val visible = if (visibleIdx >= 0) cursor.getInt(visibleIdx) == 1 else true
                val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) == 1 else false
                val hex = String.format("#%06X", 0xFFFFFF and colorInt)

                result.add(
                    isGoogleAccount to SyncedCalendarEntity(
                        calendarId = "device:$rawId",
                        accountEmail = account,
                        summary = summary,
                        colorHex = hex,
                        isSelected = visible || isPrimary,
                        syncToken = null,
                        sourceKind = CalendarAuthMethod.DEVICE_CALENDAR.name,
                    ),
                )
            }
        }
        // Prioritize Google accounts first
        return result.sortedByDescending { it.first }.map { it.second }
    }

    fun readUpcomingEvents(
        deviceCalendarId: String,
        fromMillis: Long,
        toMillis: Long,
    ): List<DeviceCalendarEvent> {
        if (!hasReadCalendarPermission()) return emptyList()
        val rawCalendarId = deviceCalendarId.removePrefix("device:").toLongOrNull() ?: return emptyList()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, fromMillis)
        ContentUris.appendId(builder, toMillis)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )

        val selection = "${CalendarContract.Instances.CALENDAR_ID} = ?"
        val selectionArgs = arrayOf(rawCalendarId.toString())

        val events = mutableListOf<DeviceCalendarEvent>()
        context.contentResolver.query(
            builder.build(),
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val descIdx = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val statusIdx = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
            val attendeeIdx = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)

            while (cursor.moveToNext()) {
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else CalendarContract.Events.STATUS_CONFIRMED
                if (status == CalendarContract.Events.STATUS_CANCELED) continue

                val selfStatus = if (attendeeIdx >= 0) cursor.getInt(attendeeIdx) else CalendarContract.Attendees.ATTENDEE_STATUS_NONE
                if (selfStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue

                val eventId = cursor.getLong(idIdx)
                val title = cursor.getString(titleIdx).orEmpty().ifBlank { "Acara Google Calendar" }
                val description = if (descIdx >= 0) cursor.getString(descIdx) else null
                val location = if (locIdx >= 0) cursor.getString(locIdx) else null
                val beginMillis = cursor.getLong(beginIdx)
                val isAllDay = if (allDayIdx >= 0) cursor.getInt(allDayIdx) == 1 else false

                val allDayDate = if (isAllDay) {
                    Instant.ofEpochMilli(beginMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                } else {
                    null
                }

                events.add(
                    DeviceCalendarEvent(
                        eventId = "dev_evt_${eventId}_$beginMillis",
                        calendarId = deviceCalendarId,
                        title = title,
                        description = description,
                        location = location,
                        startMillis = beginMillis,
                        isAllDay = isAllDay,
                        allDayLocalDate = allDayDate,
                    ),
                )
            }
        }
        return events
    }
}
