package com.example.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.AlarmDao
import com.example.data.local.PersonalizationStore
import com.example.data.local.SecureTokenStore
import com.example.data.model.CloudBackupRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CloudOperationResult(
    val success: Boolean,
    val message: String,
    val conflictCount: Int = 0,
)

class CloudBackupManager(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val personalizationStore: PersonalizationStore,
    private val secureTokenStore: SecureTokenStore,
    private val jsonManager: BackupJsonManager,
) {

    fun saveBearerToken(token: String) {
        if (token.isBlank()) {
            secureTokenStore.removeAccessToken(CLOUD_TOKEN_KEY)
        } else {
            secureTokenStore.saveAccessToken(CLOUD_TOKEN_KEY, token.trim())
        }
    }

    fun hasBearerToken(): Boolean = !secureTokenStore.getAccessToken(CLOUD_TOKEN_KEY).isNullOrBlank()

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun buildAuthenticatedClient(): Result<Triple<CloudBackupApi, String, Pair<String, String>>> {
        if (!isOnline()) {
            return Result.failure(IllegalStateException("Perangkat sedang offline. Periksa koneksi internet Anda."))
        }
        val prefs = personalizationStore.getCurrent()
        val validatedUrl = CloudBackupApi.validateHttpsEndpoint(prefs.cloudApiBaseUrl)
            .getOrElse { return Result.failure(it) }

        val email = prefs.cloudUserEmail.trim()
        if (email.isBlank() || !email.contains("@")) {
            return Result.failure(IllegalArgumentException("Masukkan email akun cadangan yang valid terlebih dahulu."))
        }
        val token = secureTokenStore.getAccessToken(CLOUD_TOKEN_KEY)
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Token autentikasi API belum diatur di konfigurasi Cloud."))
        }
        val deviceId = personalizationStore.getOrCreateDeviceId()
        val api = CloudBackupApi.create(validatedUrl)
        return Result.success(Triple(api, "Bearer $token", email to deviceId))
    }

    suspend fun uploadBackupNow(): CloudOperationResult = withContext(Dispatchers.IO) {
        val (api, authHeader, accountPair) = buildAuthenticatedClient().getOrElse { err ->
            return@withContext CloudOperationResult(false, err.message ?: "Konfigurasi Cloud belum lengkap.")
        }
        val (userEmail, deviceId) = accountPair
        val alarms = alarmDao.getAllNonDeletedAlarmsWithDates()
        val templates = alarmDao.getAllReminderTemplates()
        val payload = jsonManager.buildPayload(alarms, templates, deviceId)

        val response = runCatching {
            api.uploadBackup(
                bearerToken = authHeader,
                deviceId = deviceId,
                request = CloudBackupUploadRequest(
                    userEmail = userEmail,
                    deviceId = deviceId,
                    backupVersion = CURRENT_BACKUP_VERSION,
                    payload = payload,
                ),
            )
        }.getOrElse { exc ->
            return@withContext CloudOperationResult(
                false,
                "Gagal menghubungi server PostgreSQL API: ${exc.localizedMessage ?: "Timeout"}",
            )
        }

        if (!response.isSuccessful) {
            return@withContext CloudOperationResult(
                false,
                "Server menolak unggahan cadangan (HTTP ${response.code()}).",
            )
        }

        val summary = response.body()
        val now = System.currentTimeMillis()
        personalizationStore.updateLastCloudSyncAt(now)
        if (summary != null) {
            alarmDao.upsertCloudBackups(
                listOf(
                    CloudBackupRecordEntity(
                        backupId = summary.backupId,
                        userEmail = summary.userEmail,
                        deviceId = summary.deviceId,
                        backupVersion = summary.backupVersion,
                        alarmCount = summary.alarmCount,
                        createdAt = summary.createdAt,
                        payloadJson = jsonManager.serializeToJson(payload),
                    ),
                ),
            )
        }

        CloudOperationResult(
            success = true,
            message = "Cadangan berhasil diunggah ke Cloud (${alarms.size} alarm).",
        )
    }

    suspend fun refreshBackupHistory(): CloudOperationResult = withContext(Dispatchers.IO) {
        val (api, authHeader, accountPair) = buildAuthenticatedClient().getOrElse { err ->
            return@withContext CloudOperationResult(false, err.message ?: "Konfigurasi Cloud belum lengkap.")
        }
        val (userEmail, deviceId) = accountPair

        val response = runCatching {
            api.listBackups(bearerToken = authHeader, deviceId = deviceId, userEmail = userEmail)
        }.getOrElse { exc ->
            return@withContext CloudOperationResult(
                false,
                "Gagal memuat riwayat cadangan Cloud: ${exc.localizedMessage ?: "Jaringan gagal"}",
            )
        }

        if (!response.isSuccessful) {
            return@withContext CloudOperationResult(
                false,
                "Gagal memuat riwayat cadangan (HTTP ${response.code()}).",
            )
        }

        val list = response.body()?.backups.orEmpty()
        alarmDao.upsertCloudBackups(
            list.map { dto ->
                CloudBackupRecordEntity(
                    backupId = dto.backupId,
                    userEmail = dto.userEmail,
                    deviceId = dto.deviceId,
                    backupVersion = dto.backupVersion,
                    alarmCount = dto.alarmCount,
                    createdAt = dto.createdAt,
                )
            },
        )
        CloudOperationResult(true, "Riwayat Cloud diperbarui (${list.size} versi ditemukan).")
    }

    suspend fun downloadBackupPayload(backupId: String): Result<ChroniqBackupPayload> = withContext(Dispatchers.IO) {
        val (api, authHeader, accountPair) = buildAuthenticatedClient().getOrElse { err ->
            return@withContext Result.failure(err)
        }
        val (_, deviceId) = accountPair
        val response = runCatching {
            api.downloadBackup(bearerToken = authHeader, deviceId = deviceId, backupId = backupId)
        }.getOrElse { return@withContext Result.failure(it) }

        if (!response.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("Gagal mengunduh cadangan (HTTP ${response.code()})."))
        }
        val body = response.body()
            ?: return@withContext Result.failure(IllegalStateException("Respons cadangan dari server kosong."))
        Result.success(body.payload)
    }

    suspend fun syncBidirectionalNow(): CloudOperationResult = withContext(Dispatchers.IO) {
        val (api, authHeader, accountPair) = buildAuthenticatedClient().getOrElse { err ->
            return@withContext CloudOperationResult(false, err.message ?: "Konfigurasi Cloud belum lengkap.")
        }
        val (userEmail, deviceId) = accountPair
        val prefs = personalizationStore.getCurrent()
        val localAll = alarmDao.getAllAlarmsIncludingDeleted()
        val localTemplates = alarmDao.getAllReminderTemplates()

        val response = runCatching {
            api.syncAlarms(
                bearerToken = authHeader,
                deviceId = deviceId,
                request = CloudSyncRequest(
                    userEmail = userEmail,
                    deviceId = deviceId,
                    lastSyncedAt = prefs.lastCloudSyncAt,
                    alarms = localAll.map { it.toBackupDto() },
                    reminderTemplates = localTemplates.map { it.toBackupDto() },
                ),
            )
        }.getOrElse { exc ->
            return@withContext CloudOperationResult(
                false,
                "Gagal menyinkronkan dengan server Cloud: ${exc.localizedMessage ?: "Timeout"}",
            )
        }

        if (!response.isSuccessful) {
            return@withContext CloudOperationResult(
                false,
                "Sinkronisasi Cloud ditolak server (HTTP ${response.code()}).",
            )
        }

        val syncResp = response.body()
            ?: return@withContext CloudOperationResult(false, "Respons sinkronisasi kosong.")

        val resolution = SyncConflictResolver.resolve(localAll, syncResp.alarms)
        resolution.mergedAlarms.forEach { item ->
            alarmDao.saveAlarmComplete(item.alarm, item.dates, item.reminders)
        }
        syncResp.reminderTemplates.forEach { tpl ->
            alarmDao.upsertReminderTemplate(tpl.toEntity())
        }

        personalizationStore.updateLastCloudSyncAt(syncResp.serverTime)
        val conflictNote = if (resolution.conflicts.isNotEmpty()) {
            " • ${resolution.conflicts.size} konflik diselesaikan otomatis"
        } else {
            ""
        }
        CloudOperationResult(
            success = true,
            message = "Sinkronisasi Cloud selesai: +${resolution.pulledNewCount} baru, ${resolution.updatedLocalCount} diperbarui$conflictNote.",
            conflictCount = resolution.conflicts.size,
        )
    }

    companion object {
        private const val CLOUD_TOKEN_KEY = "chroniq_cloud_pg_bearer_token"
    }
}
