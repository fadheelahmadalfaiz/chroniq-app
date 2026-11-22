package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ChronoDateApp
import com.example.backup.BackupPreviewResult
import com.example.backup.ChroniqBackupPayload
import com.example.backup.RestoreMode
import com.example.data.model.AccentColorPreset
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.AlarmSourceType
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.CloudBackupRecordEntity
import com.example.data.model.DeletedEventBehavior
import com.example.data.model.EventFilterMode
import com.example.data.model.FirstDayOfWeekPref
import com.example.data.model.GoogleAccountEntity
import com.example.data.model.MissedAlarmBehavior
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncPreferences
import com.example.data.model.SyncedCalendarEntity
import com.example.data.model.ThemeMode
import com.example.data.model.TimeFormatMode
import com.example.data.model.formatCountdownLongIndonesian
import com.example.sync.OAuthLaunchOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class AlarmFilterTab {
    ALL,
    MANUAL,
    GOOGLE_CALENDAR,
    ARCHIVED,
}

data class ChronoDateUiState(
    val allAlarms: List<AlarmWithDates> = emptyList(),
    val filteredAlarms: List<AlarmWithDates> = emptyList(),
    val nextUpcomingAlarm: AlarmWithDates? = null,
    val nextUpcomingTriggerMillis: Long? = null,
    val selectedFilter: AlarmFilterTab = AlarmFilterTab.ALL,
    val syncedCalendars: List<SyncedCalendarEntity> = emptyList(),
    val googleAccounts: List<GoogleAccountEntity> = emptyList(),
    val syncPreferences: SyncPreferences = SyncPreferences(),
    val personalization: PersonalizationPrefs = PersonalizationPrefs(),
    val reminderTemplates: List<ReminderTemplateEntity> = emptyList(),
    val alarmHistory: List<AlarmHistoryEntity> = emptyList(),
    val cloudBackups: List<CloudBackupRecordEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val isCloudBusy: Boolean = false,
    val hasCloudToken: Boolean = false,
    val canScheduleExactAlarms: Boolean = true,
    val nowMillis: Long = System.currentTimeMillis(),
    val snackbarMessage: String? = null,
    val pendingImportPreview: BackupPreviewResult? = null,
    val pendingOpenAlarmId: String? = null,
)

class ChronoDateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ChronoDateApp
    private val repository = app.repository
    val authManager = app.googleAuthManager

    private val selectedFilter = MutableStateFlow(AlarmFilterTab.ALL)
    private val isSyncing = MutableStateFlow(false)
    private val isCloudBusy = MutableStateFlow(false)
    private val hasCloudToken = MutableStateFlow(repository.hasCloudBearerToken())
    private val canScheduleExact = MutableStateFlow(repository.canScheduleExactAlarms())
    private val nowTicker = MutableStateFlow(System.currentTimeMillis())
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val pendingImportPreview = MutableStateFlow<BackupPreviewResult?>(null)
    private val pendingOpenAlarmId = MutableStateFlow<String?>(null)

    private data class CoreInputs(
        val alarms: List<AlarmWithDates>,
        val calendars: List<SyncedCalendarEntity>,
        val accounts: List<GoogleAccountEntity>,
        val syncPrefs: SyncPreferences,
        val personalization: PersonalizationPrefs,
    )

    private data class ExtendedData(
        val templates: List<ReminderTemplateEntity>,
        val history: List<AlarmHistoryEntity>,
        val cloudBackups: List<CloudBackupRecordEntity>,
        val cloudBusy: Boolean,
        val hasToken: Boolean,
    )

    private data class UiMeta(
        val filter: AlarmFilterTab,
        val syncing: Boolean,
        val canExact: Boolean,
        val now: Long,
        val snackbar: String?,
        val importPreview: BackupPreviewResult?,
        val openAlarmId: String?,
    )

    private val coreFlow = combine(
        repository.allAlarms,
        repository.syncedCalendars,
        repository.googleAccounts,
        repository.syncPreferences,
        repository.personalizationPrefs,
    ) { alarms, calendars, accounts, syncPrefs, personalization ->
        CoreInputs(alarms, calendars, accounts, syncPrefs, personalization)
    }

    private val extendedFlow = combine(
        repository.reminderTemplates,
        repository.alarmHistory,
        repository.cloudBackups,
        isCloudBusy,
        hasCloudToken,
    ) { templates, history, backups, cloudBusy, hasToken ->
        ExtendedData(templates, history, backups, cloudBusy, hasToken)
    }

    private val metaFlowA = combine(
        selectedFilter,
        isSyncing,
        canScheduleExact,
        nowTicker,
        snackbarMessage,
    ) { filter, syncing, canExact, now, snack ->
        listOf(filter, syncing, canExact, now, snack)
    }

    private val metaFlow = combine(
        metaFlowA,
        pendingImportPreview,
        pendingOpenAlarmId,
    ) { list, preview, openId ->
        UiMeta(
            filter = list[0] as AlarmFilterTab,
            syncing = list[1] as Boolean,
            canExact = list[2] as Boolean,
            now = list[3] as Long,
            snackbar = list[4] as String?,
            importPreview = preview,
            openAlarmId = openId,
        )
    }

    val uiState: StateFlow<ChronoDateUiState> = combine(coreFlow, extendedFlow, metaFlow) { core, ext, meta ->
        val now = meta.now

        val sortedActive = core.alarms
            .filter { !it.alarm.isArchived && !it.alarm.isSoftDeleted }
            .sortedWith(
                compareBy<AlarmWithDates>(
                    { it.nextTriggerMillis(now) ?: Long.MAX_VALUE },
                    { !it.alarm.isEnabled },
                    { it.alarm.hour * 60 + it.alarm.minute },
                ),
            )

        val archived = core.alarms
            .filter { it.alarm.isArchived && !it.alarm.isSoftDeleted }
            .sortedByDescending { it.alarm.createdAt }

        val filtered = when (meta.filter) {
            AlarmFilterTab.ALL -> sortedActive
            AlarmFilterTab.MANUAL -> sortedActive.filter { it.alarm.sourceTypeEnum == AlarmSourceType.MANUAL }
            AlarmFilterTab.GOOGLE_CALENDAR -> sortedActive.filter { it.alarm.isGoogleCalendar }
            AlarmFilterTab.ARCHIVED -> archived
        }

        val nextEntry = sortedActive
            .mapNotNull { item -> item.nextTriggerMillis(now)?.let { item to it } }
            .minByOrNull { it.second }

        ChronoDateUiState(
            allAlarms = core.alarms,
            filteredAlarms = filtered,
            nextUpcomingAlarm = nextEntry?.first,
            nextUpcomingTriggerMillis = nextEntry?.second,
            selectedFilter = meta.filter,
            syncedCalendars = core.calendars,
            googleAccounts = core.accounts,
            syncPreferences = core.syncPrefs,
            personalization = core.personalization,
            reminderTemplates = ext.templates,
            alarmHistory = ext.history,
            cloudBackups = ext.cloudBackups,
            isSyncing = meta.syncing,
            isCloudBusy = ext.cloudBusy,
            hasCloudToken = ext.hasToken,
            canScheduleExactAlarms = meta.canExact,
            nowMillis = now,
            snackbarMessage = meta.snackbar,
            pendingImportPreview = meta.importPreview,
            pendingOpenAlarmId = meta.openAlarmId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ChronoDateUiState(),
    )

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                nowTicker.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshPermissions() {
        canScheduleExact.value = repository.canScheduleExactAlarms()
        hasCloudToken.value = repository.hasCloudBearerToken()
        nowTicker.value = System.currentTimeMillis()
    }

    fun requestOpenAlarmDetails(alarmId: String) {
        pendingOpenAlarmId.value = alarmId
    }

    fun consumeOpenAlarmDetails() {
        pendingOpenAlarmId.value = null
    }

    fun selectFilter(tab: AlarmFilterTab) {
        selectedFilter.value = tab
    }

    fun consumeSnackbar() {
        snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        snackbarMessage.value = message
    }

    suspend fun loadAlarmById(alarmId: String): AlarmWithDates? = repository.getAlarmById(alarmId)

    fun saveAlarm(
        alarm: AlarmEntity,
        dates: List<LocalDate>,
        reminders: List<SmartReminderEntity>? = null,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            val triggerMillis = repository.saveAlarm(alarm, dates, reminders)
            nowTicker.value = System.currentTimeMillis()
            val lang = uiState.value.personalization.language
            snackbarMessage.value = if (triggerMillis != null) {
                val savedLabel = if (lang == AppLanguage.ENGLISH) "Alarm saved" else "Alarm tersimpan"
                "$savedLabel • ${formatCountdownLongIndonesian(triggerMillis, language = lang)}"
            } else {
                if (lang == AppLanguage.ENGLISH) "Alarm saved" else "Alarm tersimpan"
            }
            onSaved()
        }
    }

    fun toggleSmartReminder(alarmId: String, reminderId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleSmartReminder(alarmId, reminderId, enabled)
        }
    }

    fun saveReminderTemplate(name: String, offsets: List<Int>) {
        viewModelScope.launch {
            repository.saveReminderTemplate(name, offsets)
            snackbarMessage.value = "Template pengingat '$name' disimpan"
        }
    }

    fun deleteReminderTemplate(templateId: String) {
        viewModelScope.launch {
            repository.deleteReminderTemplate(templateId)
        }
    }

    fun toggleAlarmEnabled(alarmId: String, enabled: Boolean) {
        viewModelScope.launch {
            val triggerMillis = repository.setAlarmEnabled(alarmId, enabled)
            nowTicker.value = System.currentTimeMillis()
            val lang = uiState.value.personalization.language
            if (enabled) {
                snackbarMessage.value = if (triggerMillis != null) {
                    val activeLabel = if (lang == AppLanguage.ENGLISH) "Alarm active" else "Alarm aktif"
                    "$activeLabel • ${formatCountdownLongIndonesian(triggerMillis, language = lang)}"
                } else {
                    if (lang == AppLanguage.ENGLISH) {
                        "No future dates left. Edit alarm to add dates."
                    } else {
                        "Tidak ada tanggal masa depan tersisa. Edit alarm untuk menambah tanggal."
                    }
                }
            }
        }
    }

    fun archiveAlarm(alarmId: String, archive: Boolean) {
        viewModelScope.launch {
            repository.setAlarmArchived(alarmId, archive)
            snackbarMessage.value = if (archive) "Alarm dipindahkan ke Arsip" else "Alarm dipulihkan dari Arsip"
        }
    }

    fun deleteAlarm(alarmId: String) {
        viewModelScope.launch {
            repository.deleteAlarm(alarmId)
            snackbarMessage.value = "Alarm dihapus"
        }
    }

    fun testRingAlarm(alarmId: String) {
        repository.launchPreviewRinging(alarmId)
    }

    fun rescheduleAllAlarmsNow() {
        viewModelScope.launch {
            repository.rescheduleAllActiveAlarms()
            snackbarMessage.value = "Semua alarm aktif dan Smart Reminder dijadwalkan ulang."
        }
    }

    fun quickUpdateAlarmAttributes(
        alarmId: String,
        label: String,
        soundUri: String?,
        soundName: String,
        vibrate: Boolean,
    ) {
        viewModelScope.launch {
            val existing = repository.getAlarmById(alarmId) ?: return@launch
            val updated = existing.alarm.copy(
                label = label.trim().ifBlank { "Alarm Tanggal" },
                soundUri = soundUri,
                soundName = soundName,
                vibrate = vibrate,
            )
            val dates = existing.allLocalDates().toList()
            repository.saveAlarm(updated, dates, existing.reminders)
            nowTicker.value = System.currentTimeMillis()
            snackbarMessage.value = "Perubahan alarm tersimpan"
        }
    }

    // ==================== LOCAL JSON BACKUP & RESTORE ====================

    fun exportLocalBackupToUri(uri: Uri) {
        viewModelScope.launch {
            repository.exportLocalBackupToUri(uri)
                .onSuccess { count ->
                    snackbarMessage.value = "Berhasil mengekspor $count alarm ke file JSON."
                }
                .onFailure { exc ->
                    snackbarMessage.value = exc.localizedMessage ?: "Gagal mengekspor cadangan."
                }
        }
    }

    fun shareLocalBackup(onReadyToShare: (Uri) -> Unit) {
        viewModelScope.launch {
            repository.createShareableLocalBackupUri()
                .onSuccess { uri -> onReadyToShare(uri) }
                .onFailure { exc ->
                    snackbarMessage.value = exc.localizedMessage ?: "Gagal menyiapkan file untuk dibagikan."
                }
        }
    }

    fun inspectImportBackupUri(uri: Uri) {
        viewModelScope.launch {
            val preview = repository.previewLocalBackupFromUri(uri)
            if (preview.isValid) {
                pendingImportPreview.value = preview
            } else {
                snackbarMessage.value = preview.errorMessage ?: "File cadangan tidak valid."
            }
        }
    }

    fun dismissImportPreview() {
        pendingImportPreview.value = null
    }

    fun confirmImportRestore(payload: ChroniqBackupPayload, mode: RestoreMode) {
        viewModelScope.launch {
            pendingImportPreview.value = null
            val report = repository.restoreFromBackupPayload(payload, mode)
            nowTicker.value = System.currentTimeMillis()
            snackbarMessage.value = report.message
        }
    }

    // ==================== CLOUD BACKUP (POSTGRESQL REST API) ====================

    fun saveCloudConfiguration(baseUrl: String, email: String, bearerToken: String) {
        viewModelScope.launch {
            repository.saveCloudConfiguration(baseUrl, email, bearerToken)
                .onSuccess {
                    hasCloudToken.value = repository.hasCloudBearerToken()
                    snackbarMessage.value = "Konfigurasi Cloud PostgreSQL API tersimpan."
                }
                .onFailure { exc ->
                    snackbarMessage.value = exc.localizedMessage ?: "Konfigurasi Cloud tidak valid."
                }
        }
    }

    fun uploadCloudBackupNow() {
        viewModelScope.launch {
            if (isCloudBusy.value) return@launch
            isCloudBusy.value = true
            val res = repository.uploadCloudBackupNow()
            snackbarMessage.value = res.message
            isCloudBusy.value = false
        }
    }

    fun refreshCloudHistoryNow() {
        viewModelScope.launch {
            if (isCloudBusy.value) return@launch
            isCloudBusy.value = true
            val res = repository.refreshCloudHistoryNow()
            snackbarMessage.value = res.message
            isCloudBusy.value = false
        }
    }

    fun syncCloudBidirectionalNow() {
        viewModelScope.launch {
            if (isCloudBusy.value) return@launch
            isCloudBusy.value = true
            val res = repository.syncCloudBidirectionalNow()
            snackbarMessage.value = res.message
            nowTicker.value = System.currentTimeMillis()
            isCloudBusy.value = false
        }
    }

    fun restoreCloudBackupVersion(backupId: String, mode: RestoreMode) {
        viewModelScope.launch {
            if (isCloudBusy.value) return@launch
            isCloudBusy.value = true
            val report = repository.restoreCloudBackupVersion(backupId, mode)
            snackbarMessage.value = report.message
            nowTicker.value = System.currentTimeMillis()
            isCloudBusy.value = false
        }
    }

    fun clearAlarmHistory() {
        viewModelScope.launch {
            repository.clearAlarmHistory()
            snackbarMessage.value = "Riwayat alarm dibersihkan."
        }
    }

    fun resetAllAppData() {
        viewModelScope.launch {
            repository.resetAllAppData()
            snackbarMessage.value = "Semua data aplikasi telah direset."
        }
    }

    // ==================== PERSONALIZATION & SETTINGS ====================

    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.updateThemeMode(mode) }
    fun updateAccentColor(preset: AccentColorPreset) = viewModelScope.launch { repository.updateAccentColor(preset) }
    fun updateCustomAccent(argb: Long) = viewModelScope.launch { repository.updateCustomAccent(argb) }
    fun updateTimeFormat(format: TimeFormatMode) = viewModelScope.launch { repository.updateTimeFormat(format) }
    fun updateFirstDayOfWeek(pref: FirstDayOfWeekPref) = viewModelScope.launch { repository.updateFirstDayOfWeek(pref) }
    fun updateLanguage(language: AppLanguage) = viewModelScope.launch { repository.updateLanguage(language) }
    fun updateDefaultSound(uri: String?, name: String) = viewModelScope.launch { repository.updateDefaultSound(uri, name) }
    fun updateDefaultVolume(volume: Int) = viewModelScope.launch { repository.updateDefaultVolume(volume) }
    fun updateDefaultVibrate(vibrate: Boolean) = viewModelScope.launch { repository.updateDefaultVibrate(vibrate) }
    fun updateDefaultFadeIn(seconds: Int) = viewModelScope.launch { repository.updateDefaultFadeIn(seconds) }
    fun updateDefaultSnooze(minutes: Int) = viewModelScope.launch { repository.updateDefaultSnooze(minutes) }
    fun updateDefaultMaxSnooze(attempts: Int) = viewModelScope.launch { repository.updateDefaultMaxSnooze(attempts) }
    fun updateDefaultAlarmDuration(minutes: Int) = viewModelScope.launch { repository.updateDefaultAlarmDuration(minutes) }
    fun updateDefaultRecurrence(recurrence: RecurrenceType) = viewModelScope.launch { repository.updateDefaultRecurrence(recurrence) }
    fun updateDefaultPostAlarmAction(action: PostAlarmAction) = viewModelScope.launch { repository.updateDefaultPostAlarmAction(action) }
    fun updateDefaultMissedBehavior(behavior: MissedAlarmBehavior) = viewModelScope.launch { repository.updateDefaultMissedBehavior(behavior) }
    fun updateDefaultSmartRemindersEnabled(enabled: Boolean) = viewModelScope.launch { repository.updateDefaultSmartRemindersEnabled(enabled) }
    fun updateAutoCloudSync(enabled: Boolean) = viewModelScope.launch { repository.updateAutoCloudSync(enabled) }

    fun resetPersonalizationToDefaults() {
        viewModelScope.launch {
            repository.resetPersonalizationToDefaults()
            snackbarMessage.value = "Personalisasi dikembalikan ke default Chroniq."
        }
    }

    // ==================== GOOGLE CALENDAR OAUTH & SYNC ====================

    fun startGoogleOAuth(
        onNeedsResolution: (android.app.PendingIntent) -> Unit,
        onFallbackToDeviceCalendar: () -> Unit,
    ) {
        viewModelScope.launch {
            isSyncing.value = true
            when (val outcome = authManager.requestCalendarAuthorization()) {
                is OAuthLaunchOutcome.Authorized -> {
                    val summary = repository.connectGoogleAccount(outcome.accessToken)
                    snackbarMessage.value = summary.message
                    isSyncing.value = false
                }
                is OAuthLaunchOutcome.NeedsResolution -> {
                    isSyncing.value = false
                    onNeedsResolution(outcome.pendingIntent)
                }
                is OAuthLaunchOutcome.FallbackToDeviceCalendar -> {
                    isSyncing.value = false
                    snackbarMessage.value = outcome.reason
                    onFallbackToDeviceCalendar()
                }
                is OAuthLaunchOutcome.Error -> {
                    snackbarMessage.value = outcome.message
                    isSyncing.value = false
                }
            }
        }
    }

    fun handleGoogleOAuthResult(
        resultCode: Int,
        data: android.content.Intent?,
        onFallbackToDeviceCalendar: () -> Unit,
    ) {
        viewModelScope.launch {
            isSyncing.value = true
            when (val outcome = authManager.extractTokenFromIntent(resultCode, data)) {
                is OAuthLaunchOutcome.Authorized -> {
                    val summary = repository.connectGoogleAccount(outcome.accessToken)
                    snackbarMessage.value = summary.message
                    isSyncing.value = false
                }
                is OAuthLaunchOutcome.FallbackToDeviceCalendar -> {
                    isSyncing.value = false
                    snackbarMessage.value = outcome.reason
                    onFallbackToDeviceCalendar()
                }
                is OAuthLaunchOutcome.Error -> {
                    snackbarMessage.value = outcome.message
                    isSyncing.value = false
                }
                is OAuthLaunchOutcome.NeedsResolution -> {
                    snackbarMessage.value = "Otorisasi membutuhkan konfirmasi tambahan."
                    isSyncing.value = false
                }
            }
        }
    }

    fun connectDeviceCalendars() {
        viewModelScope.launch {
            isSyncing.value = true
            val summary = repository.connectDeviceCalendars()
            snackbarMessage.value = summary.message
            isSyncing.value = false
        }
    }

    fun syncCalendarsNow() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            isSyncing.value = true
            val summary = repository.syncCalendarsNow()
            snackbarMessage.value = summary.message
            nowTicker.value = System.currentTimeMillis()
            isSyncing.value = false
        }
    }

    fun toggleCalendarSelection(calendarId: String, selected: Boolean) {
        viewModelScope.launch { repository.setCalendarSelected(calendarId, selected) }
    }

    fun disconnectAccount(email: String) {
        viewModelScope.launch {
            repository.disconnectAccount(email)
            snackbarMessage.value = "Akun $email diputuskan"
        }
    }

    fun updateOffsetMinutes(minutes: Int) = viewModelScope.launch { repository.updateOffsetMinutes(minutes) }
    fun updateFilterMode(mode: EventFilterMode) = viewModelScope.launch { repository.updateFilterMode(mode) }

    fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = uiState.value.syncPreferences.keywords
            repository.updateKeywords(current + trimmed)
        }
    }

    fun removeKeyword(keyword: String) {
        viewModelScope.launch {
            val current = uiState.value.syncPreferences.keywords
            repository.updateKeywords(current.filterNot { it.equals(keyword, ignoreCase = true) })
        }
    }

    fun updateIgnoreAllDay(ignore: Boolean) = viewModelScope.launch { repository.updateIgnoreAllDay(ignore) }
    fun updateAllDayTime(hour: Int, minute: Int) = viewModelScope.launch { repository.updateAllDayTime(hour, minute) }
    fun updateDeletedEventBehavior(behavior: DeletedEventBehavior) = viewModelScope.launch {
        repository.updateDeletedEventBehavior(behavior)
    }
    fun updateEnableGcalSmartReminders(enabled: Boolean) = viewModelScope.launch {
        repository.updateEnableGcalSmartReminders(enabled)
    }
    fun toggleGcalSmartReminderOffset(offsetMinutes: Int) = viewModelScope.launch {
        val current = uiState.value.syncPreferences.gcalSmartReminderOffsets
        val updated = if (offsetMinutes in current) {
            current.filterNot { it == offsetMinutes }
        } else {
            (current + offsetMinutes).distinct().sortedDescending()
        }
        repository.updateGcalSmartReminderOffsets(updated)
    }

    fun updateSyncIntervalHours(hours: Int) {
        viewModelScope.launch {
            repository.updateSyncIntervalHours(hours)
            snackbarMessage.value = "Sinkronisasi latar belakang diatur setiap $hours jam"
        }
    }
}
