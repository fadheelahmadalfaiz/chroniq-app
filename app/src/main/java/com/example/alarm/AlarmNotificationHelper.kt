package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppLanguage

class AlarmNotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM_RINGING,
                context.getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarm_desc)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Audio is managed directly by AlarmRingingService with fade-in
                enableVibration(false)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC_STATUS,
                context.getString(R.string.channel_sync_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_sync_desc)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }

    fun buildRingingNotification(
        alarmId: String,
        alarmDateId: String?,
        label: String,
        calendarName: String?,
        location: String?,
        snoozeMinutes: Int,
        isPreview: Boolean,
        reminderId: String? = null,
        reminderMessage: String? = null,
        language: AppLanguage = AppLanguage.INDONESIAN,
    ): Notification {
        ensureChannels()
        val isEn = language == AppLanguage.ENGLISH

        val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, isPreview)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (alarmId + (reminderId ?: "")).hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_DISMISS
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, isPreview)
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            alarmId.hashCode() + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_SNOOZE
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, isPreview)
        }
        val snoozePendingIntent = PendingIntent.getService(
            context,
            alarmId.hashCode() + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val subtext = buildList {
            if (!reminderMessage.isNullOrBlank()) add(reminderMessage)
            if (!calendarName.isNullOrBlank()) add("Google Calendar: $calendarName")
            if (!location.isNullOrBlank()) add(location)
        }.joinToString(" • ").ifBlank {
            if (isEn) "Chroniq Date Alarm" else "Alarm Tanggal Chroniq"
        }

        val snoozeText = if (isEn) "Snooze ${snoozeMinutes}m" else "Tunda ${snoozeMinutes}m"
        val dismissText = if (isEn) "Dismiss" else "Matikan"

        return NotificationCompat.Builder(context, CHANNEL_ALARM_RINGING)
            .setSmallIcon(R.drawable.ic_stat_chroniq)
            .setContentTitle(label.ifBlank { "Chroniq Alarm" })
            .setContentText(subtext)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, snoozeText, snoozePendingIntent)
            .addAction(0, dismissText, dismissPendingIntent)
            .build()
    }

    fun showMissedAlarmNotification(
        alarmLabel: String,
        scheduledTimeText: String,
        language: AppLanguage = AppLanguage.INDONESIAN,
    ) {
        ensureChannels()
        val isEn = language == AppLanguage.ENGLISH
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            MISSED_ALARM_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (isEn) "Missed alarm: $alarmLabel" else "Alarm terlewat: $alarmLabel"
        val body = if (isEn) {
            "Scheduled at $scheduledTimeText was silenced automatically."
        } else {
            "Alarm pukul $scheduledTimeText berhenti otomatis karena tidak dimatikan."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_stat_chroniq)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            notificationManager.notify(MISSED_ALARM_NOTIFICATION_ID + alarmLabel.hashCode(), notification)
        }
    }

    fun showTokenExpiredNotification(accountEmail: String) {
        ensureChannels()
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TOKEN_EXPIRED_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_STATUS)
            .setSmallIcon(R.drawable.ic_stat_chroniq)
            .setContentTitle("Hubungkan Ulang Google Calendar")
            .setContentText("Silakan hubungkan ulang akun $accountEmail agar sinkronisasi alarm tetap aktif.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            notificationManager.notify(TOKEN_EXPIRED_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val CHANNEL_ALARM_RINGING = "chronodate_alarm_ringing_v1"
        const val CHANNEL_SYNC_STATUS = "chronodate_sync_status_v1"
        const val RINGING_NOTIFICATION_ID = 7001
        const val TOKEN_EXPIRED_NOTIFICATION_ID = 7002
        const val MISSED_ALARM_NOTIFICATION_ID = 7100
    }
}
