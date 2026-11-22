package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.ChronoDateDatabase
import com.example.data.local.PersonalizationStore
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.formatAlarmTime
import com.example.data.model.formatCountdownLongIndonesian
import com.example.data.model.formatLongDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class NextAlarmSnapshot(
    val alarmId: String,
    val title: String,
    val hour: Int,
    val minute: Int,
    val targetDate: LocalDate,
    val triggerMillis: Long,
    val isGoogleCalendar: Boolean,
    val calendarName: String?,
)

object NextAlarmSelector {

    /**
     * Selects ONLY the single nearest upcoming active alarm (manual or Google Calendar).
     * Excludes disabled, archived, soft-deleted, dismissed, and expired alarms.
     */
    fun findNextActiveAlarm(
        alarms: List<AlarmWithDates>,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): NextAlarmSnapshot? {
        return alarms
            .asSequence()
            .filter { it.alarm.isEnabled && !it.alarm.isArchived && !it.alarm.isSoftDeleted }
            .mapNotNull { item ->
                val nextDate = item.nextScheduledDate(nowMillis, zoneId) ?: return@mapNotNull null
                val triggerMillis = item.nextTriggerMillis(nowMillis, zoneId) ?: return@mapNotNull null
                if (triggerMillis <= nowMillis) return@mapNotNull null
                NextAlarmSnapshot(
                    alarmId = item.alarm.id,
                    title = item.alarm.label.ifBlank { "Chroniq Alarm" },
                    hour = item.alarm.hour,
                    minute = item.alarm.minute,
                    targetDate = nextDate.localDate,
                    triggerMillis = triggerMillis,
                    isGoogleCalendar = item.alarm.isGoogleCalendar,
                    calendarName = item.alarm.gcalCalendarName,
                )
            }
            .minByOrNull { it.triggerMillis }
    }
}

class ChroniqNextAlarmWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgetsNow(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val shouldRefresh = action == ACTION_REFRESH_WIDGET ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED

        if (shouldRefresh) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ChroniqNextAlarmWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateWidgetsNow(context, appWidgetManager, ids)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.chroniq.ACTION_REFRESH_WIDGET"
        const val EXTRA_OPEN_ALARM_ID = "extra_open_alarm_id"

        suspend fun updateWidgetsNow(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            val db = ChronoDateDatabase.getInstance(context)
            val prefsStore = PersonalizationStore(context)
            val prefs = runCatching { prefsStore.getCurrent() }.getOrDefault(PersonalizationPrefs())
            val activeAlarms = runCatching { db.alarmDao().getAllActiveAlarmsWithDates() }.getOrDefault(emptyList())
            val nowMillis = System.currentTimeMillis()
            val zoneId = ZoneId.systemDefault()
            val nextAlarm = NextAlarmSelector.findNextActiveAlarm(activeAlarms, nowMillis, zoneId)

            appWidgetIds.forEach { widgetId ->
                val views = buildRemoteViews(context, nextAlarm, prefs, nowMillis, zoneId, widgetId)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            nextAlarm: NextAlarmSnapshot?,
            prefs: PersonalizationPrefs,
            nowMillis: Long,
            zoneId: ZoneId,
            widgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            val isEn = prefs.language == AppLanguage.ENGLISH

            views.setTextViewText(
                R.id.widget_header_label,
                if (isEn) "Next Alarm" else "Alarm Berikutnya",
            )
            views.setTextViewText(
                R.id.widget_open_button,
                if (isEn) "Open Chroniq" else "Buka Chroniq",
            )

            // Tapping Open Chroniq opens app root
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 1000,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_open_button, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            if (nextAlarm == null) {
                views.setViewVisibility(R.id.widget_alarm_content, View.GONE)
                views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                views.setViewVisibility(R.id.widget_source_badge, View.GONE)
                views.setTextViewText(
                    R.id.widget_empty_title,
                    if (isEn) "No upcoming alarms" else "Tidak ada alarm aktif",
                )
                views.setTextViewText(
                    R.id.widget_empty_subtitle,
                    if (isEn) "Tap to schedule an alarm in Chroniq" else "Ketuk untuk menambah alarm di Chroniq",
                )
                views.setTextViewText(R.id.widget_countdown, "")
            } else {
                views.setViewVisibility(R.id.widget_alarm_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_empty_state, View.GONE)
                views.setViewVisibility(R.id.widget_source_badge, View.VISIBLE)

                val sourceText = if (nextAlarm.isGoogleCalendar) {
                    "Google Calendar"
                } else {
                    "Manual"
                }
                views.setTextViewText(R.id.widget_source_badge, sourceText)

                val formattedTime = formatAlarmTime(nextAlarm.hour, nextAlarm.minute, prefs.timeFormat)
                val zdtDate = Instant.ofEpochMilli(nextAlarm.triggerMillis).atZone(zoneId).toLocalDate()
                val formattedDate = zdtDate.formatLongDate(prefs.language)
                val countdown = formatCountdownLongIndonesian(nextAlarm.triggerMillis, nowMillis, prefs.language)

                views.setTextViewText(R.id.widget_alarm_time, formattedTime)
                views.setTextViewText(R.id.widget_alarm_title, nextAlarm.title)
                views.setTextViewText(R.id.widget_alarm_date, formattedDate)
                views.setTextViewText(R.id.widget_countdown, countdown)

                // Tapping alarm info opens that specific alarm's details
                val openAlarmIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_ALARM_ID, nextAlarm.alarmId)
                }
                val openAlarmPendingIntent = PendingIntent.getActivity(
                    context,
                    widgetId + 2000,
                    openAlarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_alarm_content, openAlarmPendingIntent)
            }

            return views
        }
    }
}

object WidgetUpdater {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(appContext, ChroniqNextAlarmWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ChroniqNextAlarmWidgetProvider.updateWidgetsNow(appContext, manager, ids)
            }
        }
    }
}
