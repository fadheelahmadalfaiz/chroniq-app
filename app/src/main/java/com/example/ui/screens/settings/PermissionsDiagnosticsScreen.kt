package com.example.ui.screens.settings

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.ui.localization.LocalAppStrings

private enum class DiagnosticStatus {
    ENABLED,
    DISABLED,
    ACTION_REQUIRED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsDiagnosticsScreen(
    onRescheduleAllNow: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val diagnostics = remember(refreshTick) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val notifOk = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val fullScreenOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.canUseFullScreenIntent()
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val bgRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            am.isBackgroundRestricted
        } else {
            false
        }

        DiagnosticsSnapshot(
            exactAlarm = if (exactOk) DiagnosticStatus.ENABLED else DiagnosticStatus.ACTION_REQUIRED,
            notifications = if (notifOk) DiagnosticStatus.ENABLED else DiagnosticStatus.ACTION_REQUIRED,
            fullScreenIntent = if (fullScreenOk) DiagnosticStatus.ENABLED else DiagnosticStatus.ACTION_REQUIRED,
            batteryOptimization = if (batteryExempt) DiagnosticStatus.ENABLED else DiagnosticStatus.ACTION_REQUIRED,
            backgroundActivity = if (!bgRestricted) DiagnosticStatus.ENABLED else DiagnosticStatus.DISABLED,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catPermissions) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                DiagnosticCard(
                    title = s.permExactAlarmTitle,
                    description = s.permExactAlarmDesc,
                    status = diagnostics.exactAlarm,
                    actionLabel = s.fixNow,
                    onAction = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            item {
                DiagnosticCard(
                    title = s.permNotificationTitle,
                    description = s.permNotificationDesc,
                    status = diagnostics.notifications,
                    actionLabel = s.fixNow,
                    onAction = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            item {
                DiagnosticCard(
                    title = s.permFullScreenTitle,
                    description = s.permFullScreenDesc,
                    status = diagnostics.fullScreenIntent,
                    actionLabel = s.fixNow,
                    onAction = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            item {
                DiagnosticCard(
                    title = s.permBatteryTitle,
                    description = s.permBatteryDesc,
                    status = diagnostics.batteryOptimization,
                    actionLabel = s.fixNow,
                    onAction = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                runCatching { context.startActivity(fallback) }
                            }
                    },
                )
            }

            item {
                DiagnosticCard(
                    title = s.permBackgroundTitle,
                    description = s.permBackgroundDesc,
                    status = diagnostics.backgroundActivity,
                    actionLabel = s.fixNow,
                    onAction = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = s.permRebootTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            StatusPill(DiagnosticStatus.ENABLED)
                        }
                        Text(
                            text = s.permRebootDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRescheduleAllNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reschedule_all_alarms_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(s.testRebootRecovery)
                        }
                    }
                }
            }
        }
    }
}

private data class DiagnosticsSnapshot(
    val exactAlarm: DiagnosticStatus,
    val notifications: DiagnosticStatus,
    val fullScreenIntent: DiagnosticStatus,
    val batteryOptimization: DiagnosticStatus,
    val backgroundActivity: DiagnosticStatus,
)

@Composable
private fun DiagnosticCard(
    title: String,
    description: String,
    status: DiagnosticStatus,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.size(8.dp))
                StatusPill(status)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status != DiagnosticStatus.ENABLED) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: DiagnosticStatus) {
    val s = LocalAppStrings.current
    val (bg, fg, icon, label) = when (status) {
        DiagnosticStatus.ENABLED -> Quad(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Filled.CheckCircle,
            s.statusEnabled,
        )
        DiagnosticStatus.DISABLED -> Quad(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.ErrorOutline,
            s.statusDisabled,
        )
        DiagnosticStatus.ACTION_REQUIRED -> Quad(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Filled.Warning,
            s.statusActionRequired,
        )
    }

    Surface(shape = RoundedCornerShape(50), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.SemiBold)
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
