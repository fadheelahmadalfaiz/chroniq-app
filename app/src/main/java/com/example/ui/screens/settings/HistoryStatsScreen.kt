package com.example.ui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AlarmHistoryEntity
import com.example.data.model.AlarmHistoryOutcome
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.formatClockTime
import com.example.data.model.formatShortId
import com.example.data.model.orderedDaysOfWeek
import com.example.data.model.shortLabel
import com.example.ui.localization.LocalAppStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryStatsScreen(
    history: List<AlarmHistoryEntity>,
    prefs: PersonalizationPrefs,
    onClearHistory: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    var showClearConfirm by remember { mutableStateOf(false) }

    val completedCount = remember(history) { history.count { it.outcomeEnum == AlarmHistoryOutcome.COMPLETED } }
    val dismissedCount = remember(history) { history.count { it.outcomeEnum == AlarmHistoryOutcome.DISMISSED } }
    val missedCount = remember(history) { history.count { it.outcomeEnum == AlarmHistoryOutcome.MISSED } }
    val snoozedCount = remember(history) { history.count { it.outcomeEnum == AlarmHistoryOutcome.SNOOZED } }

    val zoneId = remember { ZoneId.systemDefault() }
    val weekDays = remember(prefs.firstDayOfWeek) { orderedDaysOfWeek(prefs.firstDayOfWeek) }
    val today = remember { LocalDate.now(zoneId) }

    val weeklyCounts = remember(history, weekDays) {
        val sevenDaysAgo = today.minusDays(6)
        val countsByDow = history
            .map { Instant.ofEpochMilli(it.triggeredAt).atZone(zoneId).toLocalDate() }
            .filter { !it.isBefore(sevenDaysAgo) }
            .groupingBy { it.dayOfWeek }
            .eachCount()
        weekDays.map { dow -> dow to (countsByDow[dow] ?: 0) }
    }

    val monthlyTotal = remember(history) {
        val thirtyDaysAgo = today.minusDays(29)
        history.count {
            val d = Instant.ofEpochMilli(it.triggeredAt).atZone(zoneId).toLocalDate()
            !d.isBefore(thirtyDaysAgo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catHistoryStats) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag("clear_history_icon_button"),
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = s.clearHistory)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 4 Summary Metric Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = s.statsCompleted,
                        count = completedCount,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = s.statsDismissed,
                        count = dismissedCount,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        label = s.statsSnoozed,
                        count = snoozedCount,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = s.statsMissed,
                        count = missedCount,
                        highlightError = missedCount > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Weekly Bar Chart respecting firstDayOfWeek
            item {
                SettingsSectionCard(
                    title = s.weeklyActivityTitle,
                    subtitle = "${s.monthlyBreakdownTitle}: $monthlyTotal",
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    val maxVal = (weeklyCounts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    ) {
                        val barCount = weeklyCounts.size.coerceAtLeast(1)
                        val slotWidth = size.width / barCount
                        val barWidth = (slotWidth * 0.45f).coerceAtLeast(12.dp.toPx())
                        val maxHeight = size.height - 12.dp.toPx()

                        weeklyCounts.forEachIndexed { idx, (_, count) ->
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            drawRoundRect(
                                color = trackColor,
                                topLeft = Offset(left, 6.dp.toPx()),
                                size = Size(barWidth, maxHeight),
                                cornerRadius = CornerRadius(10.dp.toPx()),
                            )
                            val filledH = (count.toFloat() / maxVal.toFloat()) * maxHeight
                            if (filledH > 0f) {
                                drawRoundRect(
                                    color = primaryColor,
                                    topLeft = Offset(left, 6.dp.toPx() + (maxHeight - filledH)),
                                    size = Size(barWidth, filledH.coerceAtLeast(8.dp.toPx())),
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        weeklyCounts.forEach { (dow, count) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dow.shortLabel(prefs.language),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Recent History Header
            item {
                Text(
                    text = s.recentHistoryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (history.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = s.noHistoryYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            } else {
                items(history, key = { it.id }) { entry ->
                    val date = Instant.ofEpochMilli(entry.triggeredAt).atZone(zoneId).toLocalDate()
                    val timeText = entry.triggeredAt.formatClockTime(prefs.timeFormat, zoneId)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.alarmLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!entry.reminderTitle.isNullOrBlank()) {
                                    Text(
                                        text = "Smart Reminder: ${entry.reminderTitle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${date.formatShortId(prefs.language)} • $timeText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val outcomeLabel = when (entry.outcomeEnum) {
                                AlarmHistoryOutcome.COMPLETED -> s.statsCompleted
                                AlarmHistoryOutcome.DISMISSED -> s.statsDismissed
                                AlarmHistoryOutcome.MISSED -> s.statsMissed
                                AlarmHistoryOutcome.SNOOZED -> s.statsSnoozed
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (entry.outcomeEnum == AlarmHistoryOutcome.MISSED) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ) {
                                Text(
                                    text = outcomeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (entry.outcomeEnum == AlarmHistoryOutcome.MISSED) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(s.clearHistory) },
            text = { Text("Semua catatan riwayat alarm akan dihapus permanen.") },
            confirmButton = {
                Button(onClick = {
                    showClearConfirm = false
                    onClearHistory()
                }) { Text(s.confirmAction) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    count: Int,
    highlightError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (highlightError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlightError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
