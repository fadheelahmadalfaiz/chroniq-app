package com.example.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import com.example.ChronoDateApp
import com.example.data.model.AlarmEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.formatOffsetLabel
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var fadeInJob: Job? = null
    private var autoSilenceJob: Job? = null
    private var vibrator: Vibrator? = null

    private var activeAlarmId: String? = null
    private var activeAlarmDateId: String? = null
    private var activeReminderId: String? = null
    private var activeSnoozeCount: Int = 0
    private var activeIsPreview: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_RINGING
        val alarmId = intent?.getStringExtra(EXTRA_ALARM_ID) ?: activeAlarmId
        val alarmDateId = intent?.getStringExtra(EXTRA_ALARM_DATE_ID) ?: activeAlarmDateId
        val reminderId = intent?.getStringExtra(EXTRA_REMINDER_ID) ?: activeReminderId
        val snoozeCount = intent?.getIntExtra(EXTRA_SNOOZE_COUNT, activeSnoozeCount) ?: activeSnoozeCount
        val isPreview = intent?.getBooleanExtra(EXTRA_IS_PREVIEW, activeIsPreview) ?: activeIsPreview

        when (action) {
            ACTION_START_RINGING -> {
                if (alarmId != null) {
                    activeAlarmId = alarmId
                    activeAlarmDateId = alarmDateId
                    activeReminderId = reminderId
                    activeSnoozeCount = snoozeCount
                    activeIsPreview = isPreview
                    startRingingForAlarm(alarmId, alarmDateId, reminderId, snoozeCount, isPreview)
                } else {
                    stopSelf()
                }
            }

            ACTION_DISMISS -> {
                stopPlaybackAndVibration()
                if (alarmId != null && !isPreview) {
                    val app = applicationContext as? ChronoDateApp
                    serviceScope.launch(Dispatchers.IO) {
                        app?.repository?.handleAlarmDismissed(alarmId, alarmDateId, reminderId)
                        WidgetUpdater.refreshAll(this@AlarmRingingService)
                    }
                }
                sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))
                stopForegroundAndExit()
            }

            ACTION_SNOOZE -> {
                stopPlaybackAndVibration()
                if (alarmId != null && !isPreview) {
                    val app = applicationContext as? ChronoDateApp
                    serviceScope.launch(Dispatchers.IO) {
                        app?.repository?.handleAlarmSnoozed(
                            alarmId = alarmId,
                            alarmDateId = alarmDateId,
                            reminderId = reminderId,
                            currentSnoozeCount = snoozeCount,
                        )
                        WidgetUpdater.refreshAll(this@AlarmRingingService)
                    }
                }
                sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))
                stopForegroundAndExit()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRingingForAlarm(
        alarmId: String,
        alarmDateId: String?,
        reminderId: String?,
        snoozeCount: Int,
        isPreview: Boolean,
    ) {
        val app = applicationContext as? ChronoDateApp ?: run {
            stopSelf()
            return
        }

        // Start foreground immediately to satisfy Android 12+ FGS deadline
        val initialNotification = app.notificationHelper.buildRingingNotification(
            alarmId = alarmId,
            alarmDateId = alarmDateId,
            label = "Chroniq Alarm",
            calendarName = null,
            location = null,
            snoozeMinutes = 10,
            isPreview = isPreview,
            reminderId = reminderId,
        )
        startForegroundSafe(initialNotification)

        serviceScope.launch(Dispatchers.IO) {
            val prefs = app.personalizationStore.getCurrent()
            val alarmWithDates = app.repository.getAlarmById(alarmId)
            val alarm = alarmWithDates?.alarm
            if (alarm == null) {
                stopForegroundAndExit()
                return@launch
            }
            val reminder: SmartReminderEntity? = reminderId?.let { id ->
                alarmWithDates.reminders.firstOrNull { it.id == id }
            }

            val displayTitle = if (reminder != null) {
                reminder.customTitle.ifBlank { alarm.label }
            } else {
                alarm.label
            }
            val reminderMessage = reminder?.let { r ->
                r.customMessage.ifBlank { formatOffsetLabel(r.offsetMinutes, prefs.language) }
            }
            val effectiveSoundUri = reminder?.soundUri?.takeIf { it.isNotBlank() } ?: alarm.soundUri
            val effectiveVibrate = reminder?.vibrate ?: alarm.vibrate
            val effectiveSnoozeMin = reminder?.snoozeMinutes ?: alarm.snoozeDurationMinutes

            val updatedNotification = app.notificationHelper.buildRingingNotification(
                alarmId = alarm.id,
                alarmDateId = alarmDateId,
                label = displayTitle,
                calendarName = alarm.gcalCalendarName,
                location = alarm.eventLocation,
                snoozeMinutes = effectiveSnoozeMin,
                isPreview = isPreview,
                reminderId = reminderId,
                reminderMessage = reminderMessage,
                language = prefs.language,
            )

            launch(Dispatchers.Main) {
                startForegroundSafe(updatedNotification)
                startAudioWithFadeIn(alarm, effectiveSoundUri)
                if (effectiveVibrate) {
                    startVibration()
                }
                scheduleAutoSilenceTimeout(alarm, alarmDateId, reminderId, isPreview)
                launchFullScreenActivity(alarm.id, alarmDateId, reminderId, snoozeCount, isPreview)
            }
        }
    }

    private fun scheduleAutoSilenceTimeout(
        alarm: AlarmEntity,
        alarmDateId: String?,
        reminderId: String?,
        isPreview: Boolean,
    ) {
        autoSilenceJob?.cancel()
        if (isPreview) return
        val timeoutMs = alarm.alarmDurationMinutes.coerceIn(1, 30) * 60_000L
        autoSilenceJob = serviceScope.launch {
            delay(timeoutMs)
            val app = applicationContext as? ChronoDateApp
            launch(Dispatchers.IO) {
                app?.repository?.handleAlarmMissed(alarm.id, alarmDateId, reminderId)
                WidgetUpdater.refreshAll(this@AlarmRingingService)
            }
            sendBroadcast(Intent(ACTION_RINGING_FINISHED).setPackage(packageName))
            stopForegroundAndExit()
        }
    }

    private fun startForegroundSafe(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                AlarmNotificationHelper.RINGING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmNotificationHelper.RINGING_NOTIFICATION_ID, notification)
        }
    }

    private fun launchFullScreenActivity(
        alarmId: String,
        alarmDateId: String?,
        reminderId: String?,
        snoozeCount: Int,
        isPreview: Boolean,
    ) {
        val activityIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(EXTRA_IS_PREVIEW, isPreview)
        }
        runCatching { startActivity(activityIntent) }
    }

    private fun startAudioWithFadeIn(alarm: AlarmEntity, soundUriOverride: String?) {
        stopPlaybackAndVibration()
        val ringtoneUri = soundUriOverride?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        val targetVolume = (alarm.volumeLevel.coerceIn(10, 100) / 100f)
        val fadeSeconds = alarm.fadeInSeconds.coerceAtLeast(0)

        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmRingingService, ringtoneUri)
                isLooping = true
                val initialVol = if (fadeSeconds > 0) 0.1f else targetVolume
                setVolume(initialVol, initialVol)
                prepare()
                start()
            }
            mediaPlayer = player

            if (fadeSeconds > 0) {
                fadeInJob = serviceScope.launch {
                    val steps = (fadeSeconds * 2).coerceAtLeast(2)
                    val stepDelayMs = (fadeSeconds * 1000L) / steps
                    for (step in 1..steps) {
                        if (!isActive) break
                        val fraction = step.toFloat() / steps.toFloat()
                        val vol = (0.1f + (targetVolume - 0.1f) * fraction).coerceIn(0.1f, 1.0f)
                        runCatching { mediaPlayer?.setVolume(vol, vol) }
                        delay(stepDelayMs)
                    }
                }
            }
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 600, 400, 600, 800)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        }
    }

    private fun stopPlaybackAndVibration() {
        fadeInJob?.cancel()
        fadeInJob = null
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun stopForegroundAndExit() {
        stopPlaybackAndVibration()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPlaybackAndVibration()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RINGING = "com.example.chronodate.ACTION_START_RINGING"
        const val ACTION_DISMISS = "com.example.chronodate.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.example.chronodate.ACTION_SNOOZE"
        const val ACTION_RINGING_FINISHED = "com.example.chronodate.ACTION_RINGING_FINISHED"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_DATE_ID = "extra_alarm_date_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
        const val EXTRA_IS_PREVIEW = "extra_is_preview"
    }
}
