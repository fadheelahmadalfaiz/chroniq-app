package com.example.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.AlarmWithDates
import com.example.data.model.ReminderTemplateEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BackupJsonManager(private val context: Context? = null) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(ChroniqBackupPayload::class.java).indent("  ")

    fun buildPayload(
        alarms: List<AlarmWithDates>,
        templates: List<ReminderTemplateEntity>,
        deviceId: String,
    ): ChroniqBackupPayload {
        return ChroniqBackupPayload(
            app = "Chroniq",
            backupVersion = CURRENT_BACKUP_VERSION,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            deviceId = deviceId,
            alarms = alarms.filter { !it.alarm.isSoftDeleted }.map { it.toBackupDto() },
            reminderTemplates = templates.map { it.toBackupDto() },
        )
    }

    fun serializeToJson(payload: ChroniqBackupPayload): String = payloadAdapter.toJson(payload)

    fun parseAndValidate(
        rawJson: String,
        existingAlarms: List<AlarmWithDates>,
    ): BackupPreviewResult {
        if (rawJson.isBlank()) {
            return BackupPreviewResult(isValid = false, errorMessage = "File cadangan kosong.")
        }

        val payload = runCatching { payloadAdapter.fromJson(rawJson) }.getOrNull()
            ?: return BackupPreviewResult(
                isValid = false,
                errorMessage = "Format JSON tidak valid atau file rusak.",
            )

        if (!payload.app.equals("Chroniq", ignoreCase = true) && !payload.app.equals("ChronoDate", ignoreCase = true)) {
            return BackupPreviewResult(
                isValid = false,
                errorMessage = "File ini bukan cadangan Chroniq yang valid (app=${payload.app}).",
            )
        }

        if (payload.backupVersion < 1 || payload.backupVersion > CURRENT_BACKUP_VERSION + 1) {
            return BackupPreviewResult(
                isValid = false,
                errorMessage = "Versi format cadangan tidak didukung (v${payload.backupVersion}).",
            )
        }

        // Validate every alarm's time & dates
        for ((index, alarm) in payload.alarms.withIndex()) {
            if (alarm.id.isBlank()) {
                return BackupPreviewResult(
                    isValid = false,
                    errorMessage = "Alarm ke-${index + 1} tidak memiliki ID unik.",
                )
            }
            if (alarm.hour !in 0..23 || alarm.minute !in 0..59) {
                return BackupPreviewResult(
                    isValid = false,
                    errorMessage = "Waktu alarm '${alarm.title}' tidak valid (${alarm.hour}:${alarm.minute}).",
                )
            }
            for (dateStr in alarm.dates) {
                val parsed = runCatching { LocalDate.parse(dateStr.trim()) }.getOrNull()
                if (parsed == null) {
                    return BackupPreviewResult(
                        isValid = false,
                        errorMessage = "Tanggal tidak valid ('$dateStr') pada alarm '${alarm.title}'.",
                    )
                }
            }
        }

        val existingIds = existingAlarms.map { it.alarm.id }.toSet()
        val existingSignatures = existingAlarms.map { naturalSignature(it.toBackupDto()) }.toSet()

        var duplicateCount = 0
        var newCount = 0
        var totalReminders = 0

        for (dto in payload.alarms) {
            totalReminders += dto.smartReminders.size
            val isDup = dto.id in existingIds || naturalSignature(dto) in existingSignatures
            if (isDup) duplicateCount++ else newCount++
        }

        return BackupPreviewResult(
            isValid = true,
            payload = payload,
            totalAlarms = payload.alarms.size,
            newAlarmsCount = newCount,
            duplicateAlarmsCount = duplicateCount,
            totalSmartReminders = totalReminders,
            totalTemplates = payload.reminderTemplates.size,
            createdAtDisplay = payload.createdAt,
            backupVersion = payload.backupVersion,
        )
    }

    fun writeJsonToUri(uri: Uri, json: String): Result<Unit> = runCatching {
        val ctx = requireNotNull(context) { "Context required for SAF export" }
        ctx.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("Tidak dapat menulis ke lokasi yang dipilih.")
    }

    fun readJsonFromUri(uri: Uri): Result<String> = runCatching {
        val ctx = requireNotNull(context) { "Context required for SAF import" }
        ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Tidak dapat membaca file yang dipilih.")
    }

    fun createShareableBackupFile(json: String): Result<Uri> = runCatching {
        val ctx = requireNotNull(context) { "Context required for sharing backup" }
        val dir = File(ctx.cacheDir, "backups").apply { mkdirs() }
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(java.time.LocalDateTime.now())
        val outFile = File(dir, "chroniq_backup_$stamp.json")
        outFile.writeText(json, Charsets.UTF_8)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
    }

    fun computeMergeItems(
        incoming: ChroniqBackupPayload,
        existing: List<AlarmWithDates>,
    ): Pair<List<AlarmWithDates>, Int> {
        val existingById = existing.associateBy { it.alarm.id }
        val existingSignatures = existing.map { naturalSignature(it.toBackupDto()) }.toSet()
        val toSave = mutableListOf<AlarmWithDates>()
        var skipped = 0

        for (dto in incoming.alarms) {
            val existingSameId = existingById[dto.id]
            when {
                existingSameId != null -> {
                    // Keep whichever is newer by updatedAt; if same or older, skip as duplicate
                    if (dto.updatedAt > existingSameId.alarm.updatedAt) {
                        toSave.add(dto.toAlarmWithDates())
                    } else {
                        skipped++
                    }
                }
                naturalSignature(dto) in existingSignatures -> {
                    skipped++
                }
                else -> {
                    toSave.add(dto.toAlarmWithDates())
                }
            }
        }
        return toSave to skipped
    }

    private fun naturalSignature(dto: AlarmBackupDto): String {
        val sortedDates = dto.dates.sorted().joinToString(",")
        return "${dto.title.trim().lowercase()}|${dto.hour}:${dto.minute}|$sortedDates|${dto.sourceType}"
    }
}
