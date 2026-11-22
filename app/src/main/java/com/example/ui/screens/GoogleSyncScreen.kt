package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.AppLanguage
import com.example.data.model.CalendarAuthMethod
import com.example.data.model.DeletedEventBehavior
import com.example.data.model.EventFilterMode
import com.example.data.model.formatAlarmTime
import com.example.data.model.formatOffsetLabel
import com.example.data.model.formatRelativeSyncTime
import com.example.ui.ChronoDateUiState
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ChronoMonoBadge
import com.example.ui.theme.CobaltSky
import com.example.ui.theme.EmeraldSync

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleSyncScreen(
    uiState: ChronoDateUiState,
    onStartGoogleOAuth: (
        onNeedsResolution: (android.app.PendingIntent) -> Unit,
        onFallbackToDeviceCalendar: () -> Unit,
    ) -> Unit,
    onGoogleOAuthResult: (
        resultCode: Int,
        data: android.content.Intent?,
        onFallbackToDeviceCalendar: () -> Unit,
    ) -> Unit,
    onConnectDeviceCalendars: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleCalendar: (String, Boolean) -> Unit,
    onDisconnectAccount: (String) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onFilterModeChange: (EventFilterMode) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onIgnoreAllDayChange: (Boolean) -> Unit,
    onAllDayTimeChange: (Int, Int) -> Unit,
    onDeletedBehaviorChange: (DeletedEventBehavior) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onToggleGcalSmartReminders: (Boolean) -> Unit,
    onToggleGcalReminderOffset: (Int) -> Unit,
    onPermissionDeniedMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val prefs = uiState.syncPreferences
    val personalPrefs = uiState.personalization
    val language = personalPrefs.language
    val timeFormat = personalPrefs.timeFormat
    val isEn = language == AppLanguage.ENGLISH
    var newKeyword by remember { mutableStateOf("") }

    val readCalendarPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onConnectDeviceCalendars()
        } else {
            onPermissionDeniedMessage(
                if (isEn) {
                    "Device calendar read permission was denied."
                } else {
                    "Izin baca Google Calendar pada perangkat ditolak."
                },
            )
        }
    }

    val connectDeviceGoogleCalendar = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onConnectDeviceCalendars()
        } else {
            readCalendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    val oauthResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onGoogleOAuthResult(
            result.resultCode,
            result.data,
            connectDeviceGoogleCalendar,
        )
    }

    val triggerGoogleOAuth = {
        onStartGoogleOAuth(
            { pendingIntent ->
                val senderReq = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                oauthResolutionLauncher.launch(senderReq)
            },
            connectDeviceGoogleCalendar,
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_sync_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        item(key = "sync_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s.gcalSource,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatRelativeSyncTime(prefs.lastGlobalSyncAt, uiState.nowMillis, language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onSyncNow,
                    enabled = !uiState.isSyncing && uiState.syncedCalendars.any { it.isSelected },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("sync_now_button"),
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync")
                }
            }
        }

        // Token Expired Alert Banner
        if (uiState.googleAccounts.any { it.tokenExpired }) {
            item(key = "token_expired_alert") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = if (isEn) {
                                "Your Google Calendar session has expired. Reconnect to resume syncing."
                            } else {
                                "Sesi Google Calendar telah kedaluwarsa. Hubungkan ulang untuk melanjutkan sinkronisasi."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = triggerGoogleOAuth,
                            modifier = Modifier.testTag("reconnect_oauth_button"),
                        ) {
                            Text(if (isEn) "Reconnect" else "Hubungkan", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Connected Accounts Card
        item(key = "accounts_card") {
            SyncSectionCard(title = if (isEn) "Google Calendar Accounts" else "Akun Google Calendar") {
                if (uiState.googleAccounts.isEmpty()) {
                    Text(
                        text = if (isEn) {
                            "Connect your Google Calendar to automatically convert meetings, exams, shifts, or flights into full-screen alarms."
                        } else {
                            "Hubungkan Google Calendar Anda untuk mengubah jadwal rapat, ujian, shift, atau penerbangan secara otomatis menjadi alarm layar penuh."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.googleAccounts.forEach { account ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = account.email,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = buildString {
                                                append(
                                                    if (account.authMethodEnum == CalendarAuthMethod.GOOGLE_OAUTH) {
                                                        "Google OAuth 2.0"
                                                    } else {
                                                        if (isEn) "Device Google Account" else "Akun Google Perangkat"
                                                    },
                                                )
                                                append(" • ")
                                                append(formatRelativeSyncTime(account.lastSyncedAt, uiState.nowMillis, language))
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDisconnectAccount(account.email) },
                                        modifier = Modifier.testTag("disconnect_account_${account.email}"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.LinkOff,
                                            contentDescription = if (isEn) "Disconnect" else "Putuskan akun",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = connectDeviceGoogleCalendar,
                        enabled = !uiState.isSyncing,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("connect_device_calendar_button"),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEn) "Sync Google Calendar Account" else "Sinkronkan Akun Google Calendar")
                    }

                    FilledTonalButton(
                        onClick = triggerGoogleOAuth,
                        enabled = !uiState.isSyncing,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("connect_google_oauth_button"),
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEn) "Authorize Cloud Google OAuth 2.0" else "Otorisasi Cloud Google OAuth 2.0")
                    }
                }
            }
        }

        // Multi-Calendar Selection Card
        if (uiState.syncedCalendars.isNotEmpty()) {
            item(key = "multi_calendar_card") {
                SyncSectionCard(title = if (isEn) "Synced Calendars" else "Kalender Tersinkronisasi") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.syncedCalendars.forEachIndexed { idx, calendar ->
                            val parsedColor = remember(calendar.colorHex) {
                                runCatching {
                                    Color(android.graphics.Color.parseColor(calendar.colorHex))
                                }.getOrDefault(CobaltSky)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = calendar.summary,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = calendar.accountEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Checkbox(
                                    checked = calendar.isSelected,
                                    onCheckedChange = { checked ->
                                        onToggleCalendar(calendar.calendarId, checked)
                                    },
                                    modifier = Modifier.testTag("calendar_checkbox_${calendar.calendarId}"),
                                )
                            }
                            if (idx < uiState.syncedCalendars.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        // Sync Rules Card
        item(key = "sync_rules_card") {
            SyncSectionCard(title = if (isEn) "Sync Rules & Smart Reminders" else "Aturan Sinkronisasi & Smart Reminder") {
                Text(
                    text = if (isEn) "Main Alarm Trigger Offset" else "Waktu Pemicu Alarm Utama",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (isEn) {
                        "Set when the primary alarm rings before a Google Calendar event starts."
                    } else {
                        "Setel kapan alarm utama berbunyi sebelum waktu mulai acara Google Calendar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val offsetOptions = listOf(0, 10, 15, 30, 60, 120)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    offsetOptions.forEach { minutes ->
                        val chipLabel = if (minutes == 0) {
                            if (isEn) "On time" else "Tepat waktu"
                        } else {
                            formatOffsetLabel(minutes, language)
                        }
                        FilterChip(
                            selected = prefs.defaultOffsetMinutes == minutes,
                            onClick = { onOffsetChange(minutes) },
                            label = { Text(chipLabel) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.testTag("offset_chip_$minutes"),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Google Calendar Smart Alarm Reminders
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column {
                            Text(
                                text = s.gcalSmartRemindersTitle,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = s.gcalSmartRemindersDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = prefs.enableGcalSmartReminders,
                        onCheckedChange = onToggleGcalSmartReminders,
                        modifier = Modifier.testTag("gcal_smart_reminders_switch"),
                    )
                }

                AnimatedVisibility(visible = prefs.enableGcalSmartReminders) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isEn) {
                                "Select default reminder offsets attached to synced events (customizable per event):"
                            } else {
                                "Pilih offset pengingat default untuk acara tersinkronisasi (dapat diubah per acara):"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(5, 10, 15, 30, 60, 120, 1440).forEach { mins ->
                                val selected = mins in prefs.gcalSmartReminderOffsets
                                FilterChip(
                                    selected = selected,
                                    onClick = { onToggleGcalReminderOffset(mins) },
                                    label = { Text(formatOffsetLabel(mins, language)) },
                                    shape = RoundedCornerShape(50),
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    modifier = Modifier.testTag("gcal_smart_offset_$mins"),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = if (isEn) "Event Filter" else "Filter Acara",
                    style = MaterialTheme.typography.titleSmall,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = prefs.filterMode == EventFilterMode.ALL_EVENTS,
                        onClick = { onFilterModeChange(EventFilterMode.ALL_EVENTS) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("filter_mode_all"),
                    ) {
                        Text(if (isEn) "All Events" else "Semua Acara", maxLines = 1)
                    }
                    SegmentedButton(
                        selected = prefs.filterMode == EventFilterMode.KEYWORD_FILTER,
                        onClick = { onFilterModeChange(EventFilterMode.KEYWORD_FILTER) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("filter_mode_keywords"),
                    ) {
                        Text(if (isEn) "Keywords" else "Kata Kunci", maxLines = 1)
                    }
                }

                AnimatedVisibility(visible = prefs.filterMode == EventFilterMode.KEYWORD_FILTER) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isEn) {
                                "Only create alarms if event title or description contains these keywords:"
                            } else {
                                "Hanya buat alarm jika judul atau deskripsi acara memuat kata kunci berikut:"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            prefs.keywords.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = { onRemoveKeyword(tag) },
                                    label = { Text(tag) },
                                    shape = RoundedCornerShape(50),
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = if (isEn) "Remove keyword $tag" else "Hapus kata kunci $tag",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = newKeyword,
                                onValueChange = { newKeyword = it },
                                placeholder = { Text(if (isEn) "Add tag, e.g. #alarm" else "Tambah tag, misal: #alarm") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("keyword_input"),
                            )
                            Button(
                                onClick = {
                                    if (newKeyword.isNotBlank()) {
                                        onAddKeyword(newKeyword)
                                        newKeyword = ""
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("add_keyword_button"),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = if (isEn) "Add keyword" else "Tambah kata kunci")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Ignore All-Day Events" else "Abaikan Acara Seharian (All-Day)",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val formattedAllDay = formatAlarmTime(prefs.allDayAlarmHour, prefs.allDayAlarmMinute, timeFormat)
                        Text(
                            text = if (prefs.ignoreAllDayEvents) {
                                if (isEn) {
                                    "Events without a specific start time will not generate alarms."
                                } else {
                                    "Acara tanpa jam spesifik tidak akan dibuatkan alarm."
                                }
                            } else {
                                if (isEn) {
                                    "All-day events trigger at $formattedAllDay."
                                } else {
                                    "Acara All-Day dibunyikan pukul $formattedAllDay."
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = prefs.ignoreAllDayEvents,
                        onCheckedChange = onIgnoreAllDayChange,
                        modifier = Modifier.testTag("ignore_all_day_switch"),
                    )
                }

                AnimatedVisibility(visible = !prefs.ignoreAllDayEvents) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(6, 7, 8, 9).forEach { hr ->
                            FilterChip(
                                selected = prefs.allDayAlarmHour == hr,
                                onClick = { onAllDayTimeChange(hr, 0) },
                                shape = RoundedCornerShape(50),
                                border = null,
                                label = {
                                    Text(
                                        text = formatAlarmTime(hr, 0, timeFormat),
                                        style = ChronoMonoBadge,
                                    )
                                },
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = if (isEn) "When Calendar Event is Deleted" else "Jika Acara Dihapus di Kalender",
                    style = MaterialTheme.typography.titleSmall,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = prefs.deletedEventBehavior == DeletedEventBehavior.DISABLE,
                        onClick = { onDeletedBehaviorChange(DeletedEventBehavior.DISABLE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("deleted_behavior_disable"),
                    ) {
                        Text(if (isEn) "Disable" else "Nonaktifkan", maxLines = 1)
                    }
                    SegmentedButton(
                        selected = prefs.deletedEventBehavior == DeletedEventBehavior.DELETE,
                        onClick = { onDeletedBehaviorChange(DeletedEventBehavior.DELETE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("deleted_behavior_delete"),
                    ) {
                        Text(if (isEn) "Delete Alarm" else "Hapus Alarm", maxLines = 1)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (isEn) "Background Sync Interval" else "Interval Sinkronisasi Otomatis",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1, 3, 6, 12, 24).forEach { hrs ->
                        FilterChip(
                            selected = prefs.syncIntervalHours == hrs,
                            onClick = { onSyncIntervalChange(hrs) },
                            shape = RoundedCornerShape(50),
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            label = { Text(if (isEn) "Every ${hrs}h" else "Tiap $hrs jam") },
                            modifier = Modifier.testTag("sync_interval_$hrs"),
                        )
                    }
                }
            }
        }

        // Offline Resilience & Keystore Info Card
        item(key = "offline_security_card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OfflineBolt,
                            contentDescription = null,
                            tint = EmeraldSync,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (isEn) "Offline-First & Local Security" else "Offline-First & Keamanan Lokal",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = if (isEn) {
                            "Synced alarms are stored locally and continue ringing on time even without an internet connection."
                        } else {
                            "Semua alarm hasil sinkronisasi disimpan lokal di perangkat dan tetap berbunyi tepat waktu meski tanpa internet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = EmeraldSync,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (isEn) {
                                "OAuth credentials are encrypted with AES-256-GCM in Android Keystore."
                            } else {
                                "Kredensial OAuth dienkripsi AES-256-GCM di Android Keystore."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}
