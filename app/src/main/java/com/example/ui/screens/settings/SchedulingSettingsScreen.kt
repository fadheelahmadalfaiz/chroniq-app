package com.example.ui.screens.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.MissedAlarmBehavior
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.RecurrenceType
import com.example.ui.localization.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchedulingSettingsScreen(
    prefs: PersonalizationPrefs,
    onUpdateAlarmDuration: (Int) -> Unit,
    onUpdateDefaultRecurrence: (RecurrenceType) -> Unit,
    onUpdateMissedBehavior: (MissedAlarmBehavior) -> Unit,
    onUpdateDefaultSmartReminders: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val isEn = prefs.language == AppLanguage.ENGLISH

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catScheduling) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                SettingsSectionCard(title = s.defaultAlarmDurationLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1, 5, 10, 15, 20, 30).forEach { mins ->
                            FilterChip(
                                selected = prefs.defaultAlarmDurationMinutes == mins,
                                onClick = { onUpdateAlarmDuration(mins) },
                                label = { Text("$mins min") },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.defaultRecurrenceLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            RecurrenceType.SINGLE_DATE to if (isEn) "Single Date" else "Sekali Tanggal",
                            RecurrenceType.MULTI_DATE to if (isEn) "Multiple Dates" else "Banyak Tanggal",
                            RecurrenceType.MONTHLY_DATE to if (isEn) "Monthly" else "Bulanan",
                            RecurrenceType.YEARLY_DATE to if (isEn) "Yearly" else "Tahunan",
                            RecurrenceType.INTERVAL_DAYS to if (isEn) "Every N Days" else "Setiap N Hari",
                        ).forEach { (type, label) ->
                            FilterChip(
                                selected = prefs.defaultRecurrence == type,
                                onClick = { onUpdateDefaultRecurrence(type) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.smartReminderSectionTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = s.defaultSmartRemindersLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = prefs.defaultSmartRemindersEnabled,
                            onCheckedChange = onUpdateDefaultSmartReminders,
                            modifier = Modifier.testTag("default_smart_reminders_switch"),
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.missedAlarmBehaviorLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MissedAlarmBehavior.NOTIFY_ONLY to if (isEn) "Notify Only" else "Kirim Notifikasi",
                            MissedAlarmBehavior.AUTO_ARCHIVE to if (isEn) "Auto Archive" else "Arsipkan Otomatis",
                            MissedAlarmBehavior.AUTO_DISMISS to if (isEn) "Auto Dismiss" else "Matikan Otomatis",
                        ).forEach { (behavior, label) ->
                            FilterChip(
                                selected = prefs.defaultMissedBehavior == behavior,
                                onClick = { onUpdateMissedBehavior(behavior) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.notificationSettingsLabel) {
                    OutlinedButton(
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(Settings.ACTION_SETTINGS)
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_notification_settings_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(s.openNotificationSettings)
                    }
                }
            }
        }
    }
}
