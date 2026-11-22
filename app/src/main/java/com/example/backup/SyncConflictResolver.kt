package com.example.backup

import com.example.data.model.AlarmWithDates

data class SyncConflictItem(
    val alarmId: String,
    val title: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val chosenWinner: String, // "LOCAL" or "REMOTE"
)

data class SyncResolutionResult(
    val mergedAlarms: List<AlarmWithDates>,
    val conflicts: List<SyncConflictItem>,
    val pulledNewCount: Int,
    val updatedLocalCount: Int,
    val keptLocalCount: Int,
    val softDeletedCount: Int,
)

/**
 * Deterministic multi-device synchronization & conflict resolver.
 * Uses stable UUIDs, recordVersion, updatedAt timestamps, and soft-delete deletedAt tombstones
 * so two devices modifying alarms never create duplicates or silently lose newer changes.
 */
object SyncConflictResolver {

    fun resolve(
        localAlarms: List<AlarmWithDates>,
        remoteDtos: List<AlarmBackupDto>,
    ): SyncResolutionResult {
        val localById = localAlarms.associateBy { it.alarm.id }
        val remoteById = remoteDtos.associateBy { it.id }
        val allIds = (localById.keys + remoteById.keys).distinct()

        val merged = mutableListOf<AlarmWithDates>()
        val conflicts = mutableListOf<SyncConflictItem>()
        var pulledNew = 0
        var updatedLocal = 0
        var keptLocal = 0
        var softDeleted = 0

        for (id in allIds) {
            val local = localById[id]
            val remoteDto = remoteById[id]

            when {
                local != null && remoteDto == null -> {
                    merged.add(local)
                    keptLocal++
                }

                local == null && remoteDto != null -> {
                    val converted = remoteDto.toAlarmWithDates()
                    merged.add(converted)
                    if (converted.alarm.isSoftDeleted) softDeleted++ else pulledNew++
                }

                local != null && remoteDto != null -> {
                    val localUpdated = local.alarm.updatedAt
                    val remoteUpdated = remoteDto.updatedAt
                    val localDeleted = local.alarm.deletedAt
                    val remoteDeleted = remoteDto.deletedAt

                    // Detect concurrent modification from two different devices
                    val differentDevices = local.alarm.deviceId.isNotBlank() &&
                        remoteDto.deviceId.isNotBlank() &&
                        local.alarm.deviceId != remoteDto.deviceId
                    val contentDiffers = local.alarm.label != remoteDto.title ||
                        local.alarm.hour != remoteDto.hour ||
                        local.alarm.minute != remoteDto.minute ||
                        local.alarm.isEnabled != remoteDto.enabled ||
                        local.sortedDates.map { it.targetDate } != remoteDto.dates.sorted() ||
                        (localDeleted == null) != (remoteDeleted == null)

                    // Last-writer-wins with version tie-breaker and tombstone preservation
                    val remoteWins = when {
                        remoteUpdated > localUpdated -> true
                        localUpdated > remoteUpdated -> false
                        remoteDto.recordVersion > local.alarm.recordVersion -> true
                        local.alarm.recordVersion > remoteDto.recordVersion -> false
                        remoteDeleted != null && localDeleted == null -> true
                        else -> false
                    }

                    if (differentDevices && contentDiffers) {
                        conflicts.add(
                            SyncConflictItem(
                                alarmId = id,
                                title = if (remoteWins) remoteDto.title else local.alarm.label,
                                localUpdatedAt = localUpdated,
                                remoteUpdatedAt = remoteUpdated,
                                chosenWinner = if (remoteWins) "REMOTE" else "LOCAL",
                            ),
                        )
                    }

                    if (remoteWins) {
                        val winner = remoteDto.toAlarmWithDates()
                        merged.add(winner)
                        if (winner.alarm.isSoftDeleted) softDeleted++ else updatedLocal++
                    } else {
                        merged.add(local)
                        keptLocal++
                    }
                }
            }
        }

        return SyncResolutionResult(
            mergedAlarms = merged,
            conflicts = conflicts,
            pulledNewCount = pulledNew,
            updatedLocalCount = updatedLocal,
            keptLocalCount = keptLocal,
            softDeletedCount = softDeleted,
        )
    }
}
