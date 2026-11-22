package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.model.AlarmWithDates
import com.example.data.model.SmartReminderEntity
import com.example.data.model.computeSmartReminderSchedule
import java.time.ZoneId

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Schedules the next upcoming occurrence for both the primary alarm AND
     * all enabled Smart Alarm Reminders attached to it, using AlarmManager.setAlarmClock().
     */
    fun scheduleNextOccurrence(alarmWithDates: AlarmWithDates): Long? {
        val alarm = alarmWithDates.alarm
        cancelAlarmAndReminders(alarm.id, alarmWithDates.reminders)

        if (!alarm.isEnabled || alarm.isArchived || alarm.isSoftDeleted) return null
        val nowMillis = System.currentTimeMillis()
        val zoneId = ZoneId.systemDefault()
        val nextDate = alarmWithDates.nextScheduledDate(nowMillis, zoneId) ?: return null
        val triggerMillis = alarmWithDates.nextTriggerMillis(nowMillis, zoneId) ?: return null

        // Schedule primary parent alarm
        scheduleAtMillis(
            requestCode = requestCodeForAlarm(alarm.id),
            alarmId = alarm.id,
            alarmDateId = nextDate.id,
            reminderId = null,
            triggerAtMillis = triggerMillis,
            isSnooze = false,
            snoozeCount = 0,
        )

        // Schedule Smart Alarm Reminders (only future, enabled reminders)
        if (alarm.isSmartReminderEnabled && alarmWithDates.reminders.isNotEmpty()) {
            val enabledReminders = alarmWithDates.reminders.filter { it.isEnabled }
            val upcomingDates = alarmWithDates.sortedDates
                .filter { it.statusEnum == com.example.data.model.AlarmDateStatus.SCHEDULED }
                .map { it.localDate }

            val schedule = computeSmartReminderSchedule(
                dates = upcomingDates,
                hour = alarm.hour,
                minute = alarm.minute,
                reminders = enabledReminders,
                nowMillis = nowMillis,
                zoneId = zoneId,
            )

            // For each enabled reminder, schedule its nearest future occurrence
            val nearestByReminderId = schedule
                .filter { !it.isInPast }
                .groupBy { it.reminder.id }
                .mapValues { (_, list) -> list.minByOrNull { it.triggerMillis } }

            nearestByReminderId.values.filterNotNull().forEach { occurrence ->
                // Avoid double-firing exact-time reminder at the exact same millisecond as parent if offset == 0
                val adjustedMillis = if (occurrence.reminder.offsetMinutes == 0 && occurrence.triggerMillis == triggerMillis) {
                    triggerMillis + 1_000L
                } else {
                    occurrence.triggerMillis
                }
                scheduleAtMillis(
                    requestCode = requestCodeForReminder(occurrence.reminder.id),
                    alarmId = alarm.id,
                    alarmDateId = nextDate.id,
                    reminderId = occurrence.reminder.id,
                    triggerAtMillis = adjustedMillis,
                    isSnooze = false,
                    snoozeCount = 0,
                )
            }
        }

        return triggerMillis
    }

    fun scheduleSnooze(
        alarmId: String,
        alarmDateId: String?,
        snoozeMinutes: Int,
        reminderId: String? = null,
        nextSnoozeCount: Int = 1,
    ): Long {
        val triggerAtMillis = System.currentTimeMillis() + snoozeMinutes.coerceAtLeast(1) * 60_000L
        val reqCode = if (reminderId != null) requestCodeForReminder(reminderId) else requestCodeForAlarm(alarmId)
        scheduleAtMillis(
            requestCode = reqCode,
            alarmId = alarmId,
            alarmDateId = alarmDateId,
            reminderId = reminderId,
            triggerAtMillis = triggerAtMillis,
            isSnooze = true,
            snoozeCount = nextSnoozeCount,
        )
        return triggerAtMillis
    }

    fun cancelAlarm(alarmId: String) {
        cancelRequestCode(requestCodeForAlarm(alarmId))
    }

    fun cancelReminder(reminderId: String) {
        cancelRequestCode(requestCodeForReminder(reminderId))
    }

    fun cancelAlarmAndReminders(alarmId: String, reminders: List<SmartReminderEntity>) {
        cancelAlarm(alarmId)
        reminders.forEach { cancelReminder(it.id) }
    }

    fun rescheduleAllEnabledAlarms(activeAlarms: List<AlarmWithDates>) {
        activeAlarms.forEach { scheduleNextOccurrence(it) }
    }

    private fun cancelRequestCode(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleAtMillis(
        requestCode: Int,
        alarmId: String,
        alarmDateId: String?,
        reminderId: String?,
        triggerAtMillis: Long,
        isSnooze: Boolean,
        snoozeCount: Int,
    ) {
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmRingingService.EXTRA_IS_SNOOZE, isSnooze)
            putExtra(AlarmRingingService.EXTRA_SNOOZE_COUNT, snoozeCount)
        }
        val triggerPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode + 10_000,
            showAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (canScheduleExactAlarms()) {
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(info, triggerPendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    triggerPendingIntent,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                triggerPendingIntent,
            )
        }
    }

    private fun requestCodeForAlarm(alarmId: String): Int =
        (alarmId.hashCode() and 0x7FFFFFFF) % 450_000

    private fun requestCodeForReminder(reminderId: String): Int =
        450_000 + ((reminderId.hashCode() and 0x7FFFFFFF) % 450_000)
}
