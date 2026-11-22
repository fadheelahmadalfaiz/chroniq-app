package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DeletedEventBehavior
import com.example.data.model.EventFilterMode
import com.example.data.model.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.syncPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chronodate_sync_prefs",
)

class SyncPreferencesStore(private val context: Context) {

    private object Keys {
        val DEFAULT_OFFSET_MINUTES = intPreferencesKey("default_offset_minutes")
        val FILTER_MODE = stringPreferencesKey("filter_mode")
        val KEYWORDS_CSV = stringPreferencesKey("keywords_csv")
        val IGNORE_ALL_DAY = booleanPreferencesKey("ignore_all_day")
        val ALL_DAY_HOUR = intPreferencesKey("all_day_hour")
        val ALL_DAY_MINUTE = intPreferencesKey("all_day_minute")
        val DELETED_BEHAVIOR = stringPreferencesKey("deleted_behavior")
        val SYNC_INTERVAL_HOURS = intPreferencesKey("sync_interval_hours")
        val LAST_GLOBAL_SYNC_AT = longPreferencesKey("last_global_sync_at")
        val ENABLE_GCAL_SMART_REMINDERS = booleanPreferencesKey("enable_gcal_smart_reminders")
        val GCAL_SMART_REMINDER_OFFSETS = stringPreferencesKey("gcal_smart_reminder_offsets")
    }

    val preferencesFlow: Flow<SyncPreferences> = context.syncPreferencesDataStore.data.map { prefs ->
        val rawKeywords = prefs[Keys.KEYWORDS_CSV] ?: "#alarm,[ALARM],Shift,Ujian,Flight"
        val keywordList = rawKeywords
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val rawOffsets = prefs[Keys.GCAL_SMART_REMINDER_OFFSETS] ?: "60,30,10"
        val gcalOffsets = rawOffsets
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .sortedDescending()
            .ifEmpty { listOf(60, 30, 10) }

        SyncPreferences(
            defaultOffsetMinutes = prefs[Keys.DEFAULT_OFFSET_MINUTES] ?: 30,
            filterMode = runCatching {
                EventFilterMode.valueOf(prefs[Keys.FILTER_MODE] ?: EventFilterMode.ALL_EVENTS.name)
            }.getOrDefault(EventFilterMode.ALL_EVENTS),
            keywords = keywordList,
            ignoreAllDayEvents = prefs[Keys.IGNORE_ALL_DAY] ?: true,
            allDayAlarmHour = prefs[Keys.ALL_DAY_HOUR] ?: 7,
            allDayAlarmMinute = prefs[Keys.ALL_DAY_MINUTE] ?: 0,
            deletedEventBehavior = runCatching {
                DeletedEventBehavior.valueOf(
                    prefs[Keys.DELETED_BEHAVIOR] ?: DeletedEventBehavior.DISABLE.name,
                )
            }.getOrDefault(DeletedEventBehavior.DISABLE),
            syncIntervalHours = prefs[Keys.SYNC_INTERVAL_HOURS] ?: 6,
            lastGlobalSyncAt = prefs[Keys.LAST_GLOBAL_SYNC_AT] ?: 0L,
            enableGcalSmartReminders = prefs[Keys.ENABLE_GCAL_SMART_REMINDERS] ?: false,
            gcalSmartReminderOffsets = gcalOffsets,
        )
    }

    suspend fun getCurrent(): SyncPreferences = preferencesFlow.first()

    suspend fun updateOffsetMinutes(minutes: Int) {
        context.syncPreferencesDataStore.edit { it[Keys.DEFAULT_OFFSET_MINUTES] = minutes }
    }

    suspend fun updateFilterMode(mode: EventFilterMode) {
        context.syncPreferencesDataStore.edit { it[Keys.FILTER_MODE] = mode.name }
    }

    suspend fun updateKeywords(keywords: List<String>) {
        val cleaned = keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        context.syncPreferencesDataStore.edit { it[Keys.KEYWORDS_CSV] = cleaned.joinToString(",") }
    }

    suspend fun updateIgnoreAllDay(ignore: Boolean) {
        context.syncPreferencesDataStore.edit { it[Keys.IGNORE_ALL_DAY] = ignore }
    }

    suspend fun updateAllDayTime(hour: Int, minute: Int) {
        context.syncPreferencesDataStore.edit {
            it[Keys.ALL_DAY_HOUR] = hour.coerceIn(0, 23)
            it[Keys.ALL_DAY_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun updateDeletedEventBehavior(behavior: DeletedEventBehavior) {
        context.syncPreferencesDataStore.edit { it[Keys.DELETED_BEHAVIOR] = behavior.name }
    }

    suspend fun updateSyncIntervalHours(hours: Int) {
        context.syncPreferencesDataStore.edit { it[Keys.SYNC_INTERVAL_HOURS] = hours.coerceIn(1, 24) }
    }

    suspend fun updateLastGlobalSyncAt(timestampMillis: Long) {
        context.syncPreferencesDataStore.edit { it[Keys.LAST_GLOBAL_SYNC_AT] = timestampMillis }
    }

    suspend fun updateEnableGcalSmartReminders(enabled: Boolean) {
        context.syncPreferencesDataStore.edit { it[Keys.ENABLE_GCAL_SMART_REMINDERS] = enabled }
    }

    suspend fun updateGcalSmartReminderOffsets(offsets: List<Int>) {
        val cleaned = offsets.distinct().sortedDescending()
        context.syncPreferencesDataStore.edit {
            it[Keys.GCAL_SMART_REMINDER_OFFSETS] = cleaned.joinToString(",")
        }
    }

    suspend fun resetAll() {
        context.syncPreferencesDataStore.edit { it.clear() }
    }
}
