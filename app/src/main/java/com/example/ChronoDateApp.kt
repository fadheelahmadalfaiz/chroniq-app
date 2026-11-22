package com.example

import android.app.Application
import com.example.alarm.AlarmNotificationHelper
import com.example.alarm.AlarmScheduler
import com.example.backup.BackupJsonManager
import com.example.backup.CloudBackupManager
import com.example.data.local.ChronoDateDatabase
import com.example.data.local.PersonalizationStore
import com.example.data.local.SecureTokenStore
import com.example.data.local.SyncPreferencesStore
import com.example.data.remote.GoogleCalendarApi
import com.example.data.repository.ChronoDateRepository
import com.example.sync.CalendarSyncEngine
import com.example.sync.CalendarSyncWorker
import com.example.sync.DeviceCalendarReader
import com.example.sync.GoogleCalendarAuthManager
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChronoDateApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ChronoDateDatabase by lazy { ChronoDateDatabase.getInstance(this) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(this) }
    val notificationHelper: AlarmNotificationHelper by lazy { AlarmNotificationHelper(this) }
    val syncPreferencesStore: SyncPreferencesStore by lazy { SyncPreferencesStore(this) }
    val personalizationStore: PersonalizationStore by lazy { PersonalizationStore(this) }
    val secureTokenStore: SecureTokenStore by lazy { SecureTokenStore(this) }
    val googleAuthManager: GoogleCalendarAuthManager by lazy {
        GoogleCalendarAuthManager(this, secureTokenStore)
    }
    val deviceCalendarReader: DeviceCalendarReader by lazy { DeviceCalendarReader(this) }
    val calendarApi: GoogleCalendarApi by lazy { GoogleCalendarApi.create() }
    val backupJsonManager: BackupJsonManager by lazy { BackupJsonManager(this) }
    val cloudBackupManager: CloudBackupManager by lazy {
        CloudBackupManager(
            context = this,
            alarmDao = database.alarmDao(),
            personalizationStore = personalizationStore,
            secureTokenStore = secureTokenStore,
            jsonManager = backupJsonManager,
        )
    }

    val syncEngine: CalendarSyncEngine by lazy {
        CalendarSyncEngine(
            alarmDao = database.alarmDao(),
            syncPreferencesStore = syncPreferencesStore,
            calendarApi = calendarApi,
            authManager = googleAuthManager,
            deviceCalendarReader = deviceCalendarReader,
            alarmScheduler = alarmScheduler,
            notificationHelper = notificationHelper,
        )
    }

    val repository: ChronoDateRepository by lazy {
        ChronoDateRepository(
            context = this,
            alarmDao = database.alarmDao(),
            alarmScheduler = alarmScheduler,
            notificationHelper = notificationHelper,
            syncPreferencesStore = syncPreferencesStore,
            personalizationStore = personalizationStore,
            syncEngine = syncEngine,
            authManager = googleAuthManager,
            backupJsonManager = backupJsonManager,
            cloudBackupManager = cloudBackupManager,
        )
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        appScope.launch {
            personalizationStore.getOrCreateDeviceId()
            repository.seedDefaultTemplatesIfEmpty()
            val prefs = syncPreferencesStore.getCurrent()
            CalendarSyncWorker.schedulePeriodicSync(this@ChronoDateApp, prefs.syncIntervalHours)
            WidgetUpdater.refreshAll(this@ChronoDateApp)
        }
    }
}
