package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_ALARM) return
        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID) ?: return
        val alarmDateId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID)
        val reminderId = intent.getStringExtra(AlarmRingingService.EXTRA_REMINDER_ID)
        val isSnooze = intent.getBooleanExtra(AlarmRingingService.EXTRA_IS_SNOOZE, false)
        val snoozeCount = intent.getIntExtra(AlarmRingingService.EXTRA_SNOOZE_COUNT, 0)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Chroniq:AlarmTriggerWakeLock",
        )
        wakeLock.acquire(10_000L)

        try {
            val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                action = AlarmRingingService.ACTION_START_RINGING
                putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
                putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
                putExtra(AlarmRingingService.EXTRA_IS_SNOOZE, isSnooze)
                putExtra(AlarmRingingService.EXTRA_SNOOZE_COUNT, snoozeCount)
                putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, false)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.chronodate.ACTION_TRIGGER_ALARM"
    }
}
