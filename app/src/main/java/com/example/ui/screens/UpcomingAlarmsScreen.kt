package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AlarmDateStatus
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.RecurrenceType
import com.example.data.model.TimeFormatMode
import com.example.data.model.formatClockTime
import com.example.data.model.formatCountdownIndonesian
import com.example.data.model.formatCountdownLongIndonesian
import com.example.data.model.formatFullId
import com.example.data.model.formatOffsetLabel
import com.example.data.model.formatRelativeSyncTime
import com.example.data.model.formatShortId
import com.example.ui.AlarmFilterTab
import com.example.ui.ChronoDateUiState
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ChronoClockCard
import com.example.ui.theme.ChronoClockHero
import com.example.ui.theme.ChronoMonoBadge
import com.example.ui.theme.WarningGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingAlarmsScreen(
    uiState: ChronoDateUiState,
    onSelectFilter: (AlarmFilterTab) -> Unit,
    onCreateAlarm: () -> Unit,
    onEditAlarm: (String) -> Unit,
    onQuickUpdateAlarm: (alarmId: String, label: String, soundUri: String?, soundName: String, vibrate: Boolean) -> Unit,
    onToggleAlarm: (String, Boolean) -> Unit,
    onToggleSmartReminder: (String, String, Boolean) -> Unit,
    onArchiveAlarm: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onTestRingAlarm: (String) -> Unit,
    onSyncNow: () -> Unit,
    onOpenSyncTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val timeFormat = uiState.personalization.timeFormat
    val language = uiState.personalization.language
    val isEn = language == AppLanguage.ENGLISH
    var selectedAlarmForSheet by remember { mutableStateOf<AlarmWithDates?>(null) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotificationPermission = granted
    }

    PullToRefreshBox(
        isRefreshing = uiState.isSyncing,
        onRefresh = onSyncNow,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("upcoming_alarm_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Google Clock-style clean top header
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = s.appTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val hasConnectedAccounts = uiState.googleAccounts.isNotEmpty()
                    val anyExpired = uiState.googleAccounts.any { it.tokenExpired }
                    Surface(
                        onClick = onOpenSyncTab,
                        shape = RoundedCornerShape(50),
                        color = when {
                            anyExpired -> MaterialTheme.colorScheme.errorContainer
                            hasConnectedAccounts -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.testTag("sync_status_pill"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    anyExpired -> Icons.Filled.WarningAmber
                                    hasConnectedAccounts -> Icons.Filled.CloudDone
                                    else -> Icons.Filled.Sync
                                },
                                contentDescription = s.syncTitle,
                                tint = when {
                                    anyExpired -> MaterialTheme.colorScheme.error
                                    hasConnectedAccounts -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = when {
                                    anyExpired -> if (isEn) "Reconnect" else "Hubungkan Ulang"
                                    uiState.isSyncing -> if (isEn) "Syncing..." else "Menyinkronkan..."
                                    hasConnectedAccounts -> {
                                        val count = uiState.syncedCalendars.count { it.isSelected }
                                        if (isEn) "$count Calendars" else "$count Kalender"
                                    }
                                    else -> "Google Sync"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = when {
                                    anyExpired -> MaterialTheme.colorScheme.onErrorContainer
                                    hasConnectedAccounts -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // Hero Next-Alarm Card (Material You Tonal Container — No wrapping digits!)
            item(key = "hero_next_alarm") {
                NextAlarmHeroCard(
                    nextAlarm = uiState.nextUpcomingAlarm,
                    nextTriggerMillis = uiState.nextUpcomingTriggerMillis,
                    nowMillis = uiState.nowMillis,
                    lastGlobalSyncAt = uiState.syncPreferences.lastGlobalSyncAt,
                    hasGoogleAccount = uiState.googleAccounts.isNotEmpty(),
                    timeFormat = timeFormat,
                    language = language,
                    onCreateAlarm = onCreateAlarm,
                    onCardClick = { item -> selectedAlarmForSheet = item },
                    onTestRing = { id -> onTestRingAlarm(id) },
                )
            }

            // Exact Alarm Permission Warning (Android 12+)
            if (!uiState.canScheduleExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item(key = "exact_alarm_banner") {
                    PermissionWarningBanner(
                        title = s.exactAlarmWarningTitle,
                        body = s.exactAlarmWarningBody,
                        ctaText = s.exactAlarmOpenSettings,
                        testTag = "grant_exact_alarm_button",
                        onAction = {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                    )
                }
            }

            // Notification Permission Warning (Android 13+)
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item(key = "notif_permission_banner") {
                    PermissionWarningBanner(
                        title = s.permNotificationTitle,
                        body = s.permNotificationDesc,
                        ctaText = if (isEn) "Allow" else "Izinkan",
                        testTag = "grant_notification_button",
                        onAction = {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
            }

            // Material You Pill Filter Chips
            item(key = "filter_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val tabs = listOf(
                        AlarmFilterTab.ALL to s.filterAll,
                        AlarmFilterTab.MANUAL to s.filterManual,
                        AlarmFilterTab.GOOGLE_CALENDAR to s.filterGcal,
                        AlarmFilterTab.ARCHIVED to s.filterArchived,
                    )
                    tabs.forEach { (tab, label) ->
                        val selected = uiState.selectedFilter == tab
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectFilter(tab) },
                            label = { Text(label, maxLines = 1) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            modifier = Modifier.testTag("filter_chip_${tab.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }

            // Alarm Cards or Empty State
            if (uiState.filteredAlarms.isEmpty()) {
                item(key = "empty_state") {
                    EmptyAlarmsCard(
                        selectedFilter = uiState.selectedFilter,
                        language = language,
                        onCreateAlarm = onCreateAlarm,
                        onOpenSyncTab = onOpenSyncTab,
                    )
                }
            } else {
                items(
                    items = uiState.filteredAlarms,
                    key = { it.alarm.id },
                ) { alarmWithDates ->
                    AlarmItemCard(
                        alarmWithDates = alarmWithDates,
                        nowMillis = uiState.nowMillis,
                        timeFormat = timeFormat,
                        language = language,
                        onClick = { selectedAlarmForSheet = alarmWithDates },
                        onToggleEnabled = { enabled -> onToggleAlarm(alarmWithDates.alarm.id, enabled) },
                    )
                }
            }
        }
    }

    // Google Clock-style Modal Bottom Sheet for quick alarm management
    val sheetAlarm = selectedAlarmForSheet?.let { selected ->
        uiState.allAlarms.firstOrNull { it.alarm.id == selected.alarm.id }
    }
    if (sheetAlarm != null) {
        AlarmQuickSheet(
            alarmWithDates = sheetAlarm,
            nowMillis = uiState.nowMillis,
            timeFormat = timeFormat,
            language = language,
            onDismissSheet = { selectedAlarmForSheet = null },
            onOpenFullEditor = {
                selectedAlarmForSheet = null
                onEditAlarm(sheetAlarm.alarm.id)
            },
            onSaveQuickChanges = { label, soundUri, soundName, vibrate ->
                onQuickUpdateAlarm(sheetAlarm.alarm.id, label, soundUri, soundName, vibrate)
                selectedAlarmForSheet = null
            },
            onToggleSmartReminder = { reminderId, enabled ->
                onToggleSmartReminder(sheetAlarm.alarm.id, reminderId, enabled)
            },
            onArchiveToggle = {
                onArchiveAlarm(sheetAlarm.alarm.id, !sheetAlarm.alarm.isArchived)
                selectedAlarmForSheet = null
            },
            onDelete = {
                onDeleteAlarm(sheetAlarm.alarm.id)
                selectedAlarmForSheet = null
            },
            onTestRing = {
                selectedAlarmForSheet = null
                onTestRingAlarm(sheetAlarm.alarm.id)
            },
        )
    }
}

@Composable
private fun NextAlarmHeroCard(
    nextAlarm: AlarmWithDates?,
    nextTriggerMillis: Long?,
    nowMillis: Long,
    lastGlobalSyncAt: Long,
    hasGoogleAccount: Boolean,
    timeFormat: TimeFormatMode,
    language: AppLanguage,
    onCreateAlarm: () -> Unit,
    onCardClick: (AlarmWithDates) -> Unit,
    onTestRing: (String) -> Unit,
) {
    val s = LocalAppStrings.current
    val isEn = language == AppLanguage.ENGLISH
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        onClick = { if (nextAlarm != null) onCardClick(nextAlarm) },
        enabled = nextAlarm != null,
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("next_alarm_hero_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (nextAlarm != null && nextTriggerMillis != null) {
                val nextDate = nextAlarm.nextScheduledDate(nowMillis)?.localDate
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (nextDate != null) {
                            if (isEn) {
                                "Scheduled for ${nextDate.formatFullId(language)}"
                            } else {
                                "Dijadwalkan untuk ${nextDate.formatFullId(language)}"
                            }
                        } else {
                            s.nextAlarmLabel
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = onContainer.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (nextAlarm.alarm.isGoogleCalendar) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = s.gcalSource,
                            tint = onContainer.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Full-width single-line clock so digits NEVER wrap or clip
                Text(
                    text = nextAlarm.alarm.formattedTime(timeFormat),
                    style = ChronoClockHero,
                    color = onContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = nextAlarm.alarm.label.ifBlank { if (isEn) "Date Alarm" else "Alarm Tanggal" },
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Countdown pill + Test Ring action neatly on its own row below
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = onContainer.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text = formatCountdownLongIndonesian(nextTriggerMillis, nowMillis, language),
                            style = ChronoMonoBadge,
                            color = onContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        onClick = { onTestRing(nextAlarm.alarm.id) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("hero_test_ring_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = s.testRing,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }

                if (hasGoogleAccount && lastGlobalSyncAt > 0L) {
                    Text(
                        text = formatRelativeSyncTime(lastGlobalSyncAt, nowMillis, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = s.noUpcomingAlarmsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = onContainer,
                )
                Text(
                    text = s.noUpcomingAlarmsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCreateAlarm,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.testTag("hero_create_alarm_button"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.addAlarm)
                }
            }
        }
    }
}

@Composable
fun AlarmItemCard(
    alarmWithDates: AlarmWithDates,
    nowMillis: Long,
    timeFormat: TimeFormatMode = TimeFormatMode.HOUR_24,
    language: AppLanguage = AppLanguage.INDONESIAN,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val isEn = language == AppLanguage.ENGLISH
    val alarm = alarmWithDates.alarm
    val isActive = alarm.isEnabled && !alarm.isArchived
    val nextTrigger = alarmWithDates.nextTriggerMillis(nowMillis)
    val upcomingDates = remember(alarmWithDates.dates) {
        alarmWithDates.sortedDates.filter {
            it.statusEnum == AlarmDateStatus.SCHEDULED || it.statusEnum == AlarmDateStatus.SNOOZED
        }
    }
    val displayDates = upcomingDates.ifEmpty { alarmWithDates.sortedDates }
    val activeReminderCount = remember(alarmWithDates.reminders, alarm.isSmartReminderEnabled) {
        if (alarm.isSmartReminderEnabled) alarmWithDates.reminders.count { it.isEnabled } else 0
    }

    val cardColor = when {
        !isActive -> MaterialTheme.colorScheme.surfaceContainerLow
        alarm.isGoogleCalendar -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    val scheduleCaption = remember(displayDates, alarm.recurrenceTypeEnum, alarm.intervalDays, language) {
        val firstDate = displayDates.firstOrNull()?.localDate
        when {
            displayDates.size > 1 -> {
                val preview = displayDates.take(2).joinToString(", ") { it.localDate.formatShortId(language) }
                if (isEn) "${displayDates.size} dates • $preview" else "${displayDates.size} tanggal • $preview"
            }
            firstDate != null -> {
                if (isEn) "Scheduled for ${firstDate.formatFullId(language)}" else "Dijadwalkan untuk ${firstDate.formatFullId(language)}"
            }
            else -> if (isEn) "No date set" else "Belum ada tanggal"
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        modifier = modifier
            .fillMaxWidth()
            .testTag("alarm_card_${alarm.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top caption: Schedule Date + Source / Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scheduleCaption,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (alarm.isArchived) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = s.filterArchived,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else if (nextTrigger != null && isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatCountdownIndonesian(nextTrigger, nowMillis, language),
                        style = ChronoMonoBadge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }

            // Middle row: Large crisp single-line time + Material 3 Switch (exact Google Clock style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = alarm.formattedTime(timeFormat),
                    style = ChronoClockCard,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    },
                    maxLines = 1,
                    softWrap = false,
                )

                if (!alarm.isArchived) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("alarm_toggle_${alarm.id}"),
                    )
                }
            }

            // Label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = alarm.label.ifBlank { if (isEn) "Date Alarm" else "Alarm Tanggal" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // Metadata badges row: Google Calendar / Recurrence + Smart Reminders badge
            val hasSecondaryBadges = alarm.isGoogleCalendar ||
                alarm.recurrenceTypeEnum != RecurrenceType.SINGLE_DATE ||
                activeReminderCount > 0

            if (hasSecondaryBadges) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (alarm.isGoogleCalendar) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = s.gcalSource,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = buildString {
                                        append(alarm.gcalCalendarName ?: s.gcalSource)
                                        if (alarm.offsetMinutesBefore > 0) {
                                            append(" • -${alarm.offsetMinutesBefore}m")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else if (alarm.recurrenceTypeEnum != RecurrenceType.SINGLE_DATE) {
                        val recLabel = when (alarm.recurrenceTypeEnum) {
                            RecurrenceType.MONTHLY_DATE -> if (isEn) "Monthly" else "Berulang tiap bulan"
                            RecurrenceType.YEARLY_DATE -> if (isEn) "Yearly" else "Berulang tiap tahun"
                            RecurrenceType.INTERVAL_DAYS -> if (isEn) "Every ${alarm.intervalDays}d" else "Tiap ${alarm.intervalDays} hari"
                            RecurrenceType.MULTI_DATE -> if (isEn) "${displayDates.size} dates" else "${displayDates.size} tanggal dipilih"
                            RecurrenceType.SINGLE_DATE -> ""
                        }
                        if (recLabel.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = recLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (activeReminderCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.testTag("alarm_smart_reminders_badge_${alarm.id}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = s.smartRemindersBadge,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "$activeReminderCount ${s.smartRemindersBadge}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Google Clock-inspired ModalBottomSheet for quick inline editing + Smart Reminder controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmQuickSheet(
    alarmWithDates: AlarmWithDates,
    nowMillis: Long,
    timeFormat: TimeFormatMode = TimeFormatMode.HOUR_24,
    language: AppLanguage = AppLanguage.INDONESIAN,
    onDismissSheet: () -> Unit,
    onOpenFullEditor: () -> Unit,
    onSaveQuickChanges: (label: String, soundUri: String?, soundName: String, vibrate: Boolean) -> Unit,
    onToggleSmartReminder: (reminderId: String, enabled: Boolean) -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: () -> Unit,
    onTestRing: () -> Unit,
) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val isEn = language == AppLanguage.ENGLISH
    val alarm = alarmWithDates.alarm
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftLabel by remember(alarm.id) { mutableStateOf(alarm.label) }
    var draftSoundUri by remember(alarm.id) { mutableStateOf(alarm.soundUri) }
    var draftSoundName by remember(alarm.id) { mutableStateOf(alarm.soundName) }
    var draftVibrate by remember(alarm.id) { mutableStateOf(alarm.vibrate) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                draftSoundUri = uri.toString()
                draftSoundName = runCatching {
                    RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                }.getOrNull() ?: if (isEn) "Custom Tone" else "Nada Khusus"
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top Row: Big Time + "Edit" pill button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = alarm.formattedTime(timeFormat),
                    style = ChronoClockHero,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                FilledTonalButton(
                    onClick = onOpenFullEditor,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("sheet_edit_full_button"),
                ) {
                    Text(s.edit)
                }
            }

            // Scheduled Dates Row + Archive toggle
            val displayDates = alarmWithDates.sortedDates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (displayDates.size == 1) {
                            if (isEn) {
                                "Scheduled for ${displayDates.first().localDate.formatFullId(language)}"
                            } else {
                                "Dijadwalkan untuk ${displayDates.first().localDate.formatFullId(language)}"
                            }
                        } else {
                            if (isEn) {
                                "Scheduled for ${displayDates.size} dates"
                            } else {
                                "Dijadwalkan untuk ${displayDates.size} tanggal"
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isEn) "Edit dates, reminders & schedule" else "Ubah tanggal, pengingat & jadwal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable(onClick = onOpenFullEditor),
                    )
                }

                TextButton(
                    onClick = onArchiveToggle,
                    modifier = Modifier.testTag("sheet_archive_button"),
                ) {
                    Icon(
                        imageVector = if (alarm.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (alarm.isArchived) s.restore else s.archive)
                }
            }

            // Google Calendar Metadata pill if synced
            if (alarm.isGoogleCalendar) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "${s.gcalSource} • ${alarm.gcalCalendarName ?: if (isEn) "Primary" else "Utama"}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val subInfo = buildList {
                            alarm.eventStartTimeMillis?.let {
                                val prefix = if (isEn) "Starts" else "Mulai"
                                add("$prefix ${it.formatClockTime(timeFormat)}")
                            }
                            if (alarm.offsetMinutesBefore > 0) {
                                add(formatOffsetLabel(alarm.offsetMinutesBefore, language))
                            }
                            add(formatRelativeSyncTime(alarm.lastSyncedAt, nowMillis, language))
                        }.joinToString(" • ")
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        )
                        if (!alarm.eventLocation.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = alarm.eventLocation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Associated Smart Reminders with quick individual toggles
            val sortedReminders = alarmWithDates.sortedReminders
            if (sortedReminders.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "${s.smartReminderSectionTitle} (${sortedReminders.count { it.isEnabled }}/${sortedReminders.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = s.edit,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(onClick = onOpenFullEditor),
                            )
                        }

                        sortedReminders.forEachIndexed { idx, rem ->
                            if (idx > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rem.customTitle.ifBlank { formatOffsetLabel(rem.offsetMinutes, language) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (rem.isEnabled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (rem.customTitle.isNotBlank()) {
                                        Text(
                                            text = formatOffsetLabel(rem.offsetMinutes, language),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Switch(
                                    checked = rem.isEnabled,
                                    onCheckedChange = { checked -> onToggleSmartReminder(rem.id, checked) },
                                    modifier = Modifier.testTag("sheet_smart_reminder_toggle_${rem.id}"),
                                )
                            }
                        }
                    }
                }
            }

            // Grouped Google Clock Settings Stack
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SheetSettingRow(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = if (isEn) "Alarm name" else "Nama alarm",
                    value = draftLabel.ifBlank { "Alarm" },
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.testTag("sheet_label_row"),
                )
                SheetSettingRow(
                    icon = Icons.Filled.NotificationsNone,
                    title = if (isEn) "Sound" else "Suara",
                    value = draftSoundName,
                    shape = RoundedCornerShape(6.dp),
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            draftSoundUri?.let {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                            }
                        }
                        ringtonePickerLauncher.launch(intent)
                    },
                    modifier = Modifier.testTag("sheet_sound_row"),
                )
                Surface(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = if (isEn) "Vibrate" else "Getar",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Switch(
                            checked = draftVibrate,
                            onCheckedChange = { draftVibrate = it },
                            modifier = Modifier.testTag("sheet_vibrate_switch"),
                        )
                    }
                }
            }

            // Standalone pill row: Full-screen test ring
            Surface(
                onClick = onTestRing,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_test_ring_row"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = if (isEn) "Test full-screen alarm" else "Uji dering layar penuh",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Bottom Action Bar: Left Delete + Right Save pill buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("sheet_delete_button"),
                ) {
                    Text(s.delete, style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onSaveQuickChanges(draftLabel, draftSoundUri, draftSoundName, draftVibrate)
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                    modifier = Modifier.testTag("sheet_save_button"),
                ) {
                    Text(s.save, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showRenameDialog) {
        var tempText by remember { mutableStateOf(draftLabel) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(if (isEn) "Alarm name" else "Nama alarm") },
            text = {
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it },
                    singleLine = true,
                    placeholder = { Text(if (isEn) "e.g. Morning Shift, Flight" else "Contoh: Shift Pagi, Rapat") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftLabel = tempText.trim()
                        showRenameDialog = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(s.cancel)
                }
            },
        )
    }
}

@Composable
private fun SheetSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    title: String,
    body: String,
    ctaText: String,
    testTag: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = WarningGold,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onAction,
                modifier = Modifier.testTag(testTag),
            ) {
                Text(ctaText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyAlarmsCard(
    selectedFilter: AlarmFilterTab,
    language: AppLanguage,
    onCreateAlarm: () -> Unit,
    onOpenSyncTab: () -> Unit,
) {
    val s = LocalAppStrings.current
    val isEn = language == AppLanguage.ENGLISH
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selectedFilter == AlarmFilterTab.GOOGLE_CALENDAR) {
                        Icons.Filled.CalendarMonth
                    } else {
                        Icons.Filled.Alarm
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = when (selectedFilter) {
                    AlarmFilterTab.GOOGLE_CALENDAR -> if (isEn) "No Google Calendar alarms yet" else "Belum ada alarm Google Calendar"
                    AlarmFilterTab.ARCHIVED -> if (isEn) "Archive is empty" else "Arsip alarm masih kosong"
                    AlarmFilterTab.MANUAL -> if (isEn) "No manual alarms scheduled" else "Belum ada alarm manual terjadwal"
                    AlarmFilterTab.ALL -> if (isEn) "Schedule your first date alarm" else "Mulai jadwalkan alarm tanggal"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when (selectedFilter) {
                    AlarmFilterTab.GOOGLE_CALENDAR -> if (isEn) {
                        "Sync Google Calendar so meetings, shifts, or flights automatically become full-screen alarms."
                    } else {
                        "Sinkronkan Google Calendar agar jadwal rapat, shift, atau penerbangan otomatis menjadi alarm keras."
                    }
                    AlarmFilterTab.ARCHIVED -> if (isEn) {
                        "Completed date alarms will be archived automatically here."
                    } else {
                        "Alarm tanggal yang telah selesai akan diarsipkan secara otomatis di sini."
                    }
                    else -> if (isEn) {
                        "Pick specific dates up to 5 years ahead or select multiple dates at once for shift schedules."
                    } else {
                        "Pilih tanggal spesifik hingga 5 tahun ke depan atau pilih banyak tanggal sekaligus untuk jadwal shift."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedFilter == AlarmFilterTab.GOOGLE_CALENDAR) {
                Button(
                    onClick = onOpenSyncTab,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("empty_connect_gcal_button"),
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEn) "Open Google Sync" else "Buka Google Sync")
                }
            } else if (selectedFilter != AlarmFilterTab.ARCHIVED) {
                Button(
                    onClick = onCreateAlarm,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("empty_create_alarm_button"),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(s.addAlarm)
                }
            }
        }
    }
}
