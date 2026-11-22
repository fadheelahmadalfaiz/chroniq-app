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
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.FirstDayOfWeekPref
import com.example.data.model.MissedAlarmBehavior
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ThemeMode
import com.example.data.model.TimeFormatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.personalizationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chroniq_personalization_prefs",
)

class PersonalizationStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val CUSTOM_ACCENT_ARGB = longPreferencesKey("custom_accent_argb")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val LANGUAGE = stringPreferencesKey("language")

        val DEFAULT_SOUND_URI = stringPreferencesKey("default_sound_uri")
        val DEFAULT_SOUND_NAME = stringPreferencesKey("default_sound_name")
        val DEFAULT_VOLUME = intPreferencesKey("default_volume")
        val DEFAULT_VIBRATE = booleanPreferencesKey("default_vibrate")
        val DEFAULT_FADE_IN = intPreferencesKey("default_fade_in")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze")
        val DEFAULT_MAX_SNOOZE = intPreferencesKey("default_max_snooze")

        val DEFAULT_ALARM_DURATION = intPreferencesKey("default_alarm_duration")
        val DEFAULT_RECURRENCE = stringPreferencesKey("default_recurrence")
        val DEFAULT_POST_ACTION = stringPreferencesKey("default_post_action")
        val DEFAULT_MISSED_BEHAVIOR = stringPreferencesKey("default_missed_behavior")
        val DEFAULT_SMART_REMINDERS = booleanPreferencesKey("default_smart_reminders")

        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_LOCAL_BACKUP_AT = longPreferencesKey("last_local_backup_at")
        val CLOUD_API_BASE_URL = stringPreferencesKey("cloud_api_base_url")
        val CLOUD_USER_EMAIL = stringPreferencesKey("cloud_user_email")
        val LAST_CLOUD_SYNC_AT = longPreferencesKey("last_cloud_sync_at")
        val AUTO_CLOUD_SYNC = booleanPreferencesKey("auto_cloud_sync")
    }

    val prefsFlow: Flow<PersonalizationPrefs> = context.personalizationDataStore.data.map { p ->
        PersonalizationPrefs(
            themeMode = runCatching {
                ThemeMode.valueOf(p[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            accentColor = runCatching {
                AccentColorPreset.valueOf(p[Keys.ACCENT_COLOR] ?: AccentColorPreset.BLUE.name)
            }.getOrDefault(AccentColorPreset.BLUE),
            customAccentArgb = p[Keys.CUSTOM_ACCENT_ARGB],
            timeFormat = runCatching {
                TimeFormatMode.valueOf(p[Keys.TIME_FORMAT] ?: TimeFormatMode.HOUR_24.name)
            }.getOrDefault(TimeFormatMode.HOUR_24),
            firstDayOfWeek = runCatching {
                FirstDayOfWeekPref.valueOf(p[Keys.FIRST_DAY_OF_WEEK] ?: FirstDayOfWeekPref.MONDAY.name)
            }.getOrDefault(FirstDayOfWeekPref.MONDAY),
            language = runCatching {
                AppLanguage.valueOf(p[Keys.LANGUAGE] ?: AppLanguage.INDONESIAN.name)
            }.getOrDefault(AppLanguage.INDONESIAN),
            defaultSoundUri = p[Keys.DEFAULT_SOUND_URI],
            defaultSoundName = p[Keys.DEFAULT_SOUND_NAME] ?: "Default Alarm",
            defaultVolumeLevel = p[Keys.DEFAULT_VOLUME] ?: 90,
            defaultVibrate = p[Keys.DEFAULT_VIBRATE] ?: true,
            defaultFadeInSeconds = p[Keys.DEFAULT_FADE_IN] ?: 15,
            defaultSnoozeMinutes = p[Keys.DEFAULT_SNOOZE] ?: 10,
            defaultMaxSnoozeAttempts = p[Keys.DEFAULT_MAX_SNOOZE] ?: 3,
            defaultAlarmDurationMinutes = p[Keys.DEFAULT_ALARM_DURATION] ?: 10,
            defaultRecurrence = runCatching {
                RecurrenceType.valueOf(p[Keys.DEFAULT_RECURRENCE] ?: RecurrenceType.SINGLE_DATE.name)
            }.getOrDefault(RecurrenceType.SINGLE_DATE),
            defaultPostAlarmAction = runCatching {
                PostAlarmAction.valueOf(p[Keys.DEFAULT_POST_ACTION] ?: PostAlarmAction.AUTO_ARCHIVE.name)
            }.getOrDefault(PostAlarmAction.AUTO_ARCHIVE),
            defaultMissedBehavior = runCatching {
                MissedAlarmBehavior.valueOf(p[Keys.DEFAULT_MISSED_BEHAVIOR] ?: MissedAlarmBehavior.NOTIFY_ONLY.name)
            }.getOrDefault(MissedAlarmBehavior.NOTIFY_ONLY),
            defaultSmartRemindersEnabled = p[Keys.DEFAULT_SMART_REMINDERS] ?: false,
            deviceId = p[Keys.DEVICE_ID] ?: "",
            lastLocalBackupAt = p[Keys.LAST_LOCAL_BACKUP_AT] ?: 0L,
            cloudApiBaseUrl = p[Keys.CLOUD_API_BASE_URL] ?: "",
            cloudUserEmail = p[Keys.CLOUD_USER_EMAIL] ?: "",
            lastCloudSyncAt = p[Keys.LAST_CLOUD_SYNC_AT] ?: 0L,
            autoCloudSyncEnabled = p[Keys.AUTO_CLOUD_SYNC] ?: false,
        )
    }

    suspend fun getCurrent(): PersonalizationPrefs = prefsFlow.first()

    suspend fun getOrCreateDeviceId(): String {
        val current = getCurrent().deviceId
        if (current.isNotBlank()) return current
        val created = "chroniq-${UUID.randomUUID().toString().take(12)}"
        context.personalizationDataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.personalizationDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateAccentColor(preset: AccentColorPreset) {
        context.personalizationDataStore.edit {
            it[Keys.ACCENT_COLOR] = preset.name
            it.remove(Keys.CUSTOM_ACCENT_ARGB)
        }
    }

    suspend fun updateCustomAccent(argb: Long) {
        context.personalizationDataStore.edit { it[Keys.CUSTOM_ACCENT_ARGB] = argb }
    }

    suspend fun updateTimeFormat(format: TimeFormatMode) {
        context.personalizationDataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    suspend fun updateFirstDayOfWeek(pref: FirstDayOfWeekPref) {
        context.personalizationDataStore.edit { it[Keys.FIRST_DAY_OF_WEEK] = pref.name }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.personalizationDataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun updateDefaultSound(uri: String?, name: String) {
        context.personalizationDataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.DEFAULT_SOUND_URI) else it[Keys.DEFAULT_SOUND_URI] = uri
            it[Keys.DEFAULT_SOUND_NAME] = name
        }
    }

    suspend fun updateDefaultVolume(volume: Int) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_VOLUME] = volume.coerceIn(10, 100) }
    }

    suspend fun updateDefaultVibrate(vibrate: Boolean) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_VIBRATE] = vibrate }
    }

    suspend fun updateDefaultFadeIn(seconds: Int) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_FADE_IN] = seconds.coerceAtLeast(0) }
    }

    suspend fun updateDefaultSnooze(minutes: Int) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_SNOOZE] = minutes.coerceIn(1, 60) }
    }

    suspend fun updateDefaultMaxSnooze(attempts: Int) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_MAX_SNOOZE] = attempts.coerceIn(0, 10) }
    }

    suspend fun updateDefaultAlarmDuration(minutes: Int) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_ALARM_DURATION] = minutes.coerceIn(1, 30) }
    }

    suspend fun updateDefaultRecurrence(recurrence: RecurrenceType) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_RECURRENCE] = recurrence.name }
    }

    suspend fun updateDefaultPostAlarmAction(action: PostAlarmAction) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_POST_ACTION] = action.name }
    }

    suspend fun updateDefaultMissedBehavior(behavior: MissedAlarmBehavior) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_MISSED_BEHAVIOR] = behavior.name }
    }

    suspend fun updateDefaultSmartRemindersEnabled(enabled: Boolean) {
        context.personalizationDataStore.edit { it[Keys.DEFAULT_SMART_REMINDERS] = enabled }
    }

    suspend fun updateLastLocalBackupAt(timestamp: Long) {
        context.personalizationDataStore.edit { it[Keys.LAST_LOCAL_BACKUP_AT] = timestamp }
    }

    suspend fun updateCloudConfig(baseUrl: String, userEmail: String) {
        context.personalizationDataStore.edit {
            it[Keys.CLOUD_API_BASE_URL] = baseUrl.trim()
            it[Keys.CLOUD_USER_EMAIL] = userEmail.trim()
        }
    }

    suspend fun updateLastCloudSyncAt(timestamp: Long) {
        context.personalizationDataStore.edit { it[Keys.LAST_CLOUD_SYNC_AT] = timestamp }
    }

    suspend fun updateAutoCloudSync(enabled: Boolean) {
        context.personalizationDataStore.edit { it[Keys.AUTO_CLOUD_SYNC] = enabled }
    }

    suspend fun resetPersonalizationToDefaults() {
        context.personalizationDataStore.edit {
            it[Keys.THEME_MODE] = ThemeMode.DARK.name
            it[Keys.ACCENT_COLOR] = AccentColorPreset.BLUE.name
            it.remove(Keys.CUSTOM_ACCENT_ARGB)
            it[Keys.TIME_FORMAT] = TimeFormatMode.HOUR_24.name
            it[Keys.FIRST_DAY_OF_WEEK] = FirstDayOfWeekPref.MONDAY.name
            it[Keys.LANGUAGE] = AppLanguage.INDONESIAN.name
            it[Keys.DEFAULT_POST_ACTION] = PostAlarmAction.AUTO_ARCHIVE.name
        }
    }

    suspend fun resetAllPreferences() {
        val existingDeviceId = getOrCreateDeviceId()
        context.personalizationDataStore.edit {
            it.clear()
            it[Keys.DEVICE_ID] = existingDeviceId
        }
    }
}
