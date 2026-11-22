package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.ChronoDateUiState
import com.example.ui.localization.LocalAppStrings

object SettingsRoutes {
    const val HUB = "settings_hub"
    const val PERSONALIZATION = "settings_personalization"
    const val ALARM_SOUND = "settings_alarm_sound"
    const val SCHEDULING = "settings_scheduling"
    const val PERMISSIONS = "settings_permissions"
    const val HISTORY_STATS = "settings_history_stats"
    const val BACKUP_RESTORE = "settings_backup_restore"
    const val ABOUT = "settings_about"
}

@Composable
fun SettingsHubScreen(
    uiState: ChronoDateUiState,
    onNavigateToCategory: (String) -> Unit,
) {
    val s = LocalAppStrings.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_hub_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text(
                    text = s.settingsTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = s.settingsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingsCategoryCard(
                title = s.catPersonalization,
                subtitle = s.catPersonalizationDesc,
                icon = Icons.Filled.Palette,
                badge = "${uiState.personalization.themeMode.name.lowercase().replaceFirstChar { it.uppercase() }} • ${uiState.personalization.language.tag.uppercase()}",
                testTag = "settings_cat_personalization",
                onClick = { onNavigateToCategory(SettingsRoutes.PERSONALIZATION) },
            )
        }

        item {
            SettingsCategoryCard(
                title = s.catAlarmSound,
                subtitle = s.catAlarmSoundDesc,
                icon = Icons.Filled.NotificationsActive,
                badge = "${uiState.personalization.defaultVolumeLevel}% • ${uiState.personalization.defaultSnoozeMinutes}m",
                testTag = "settings_cat_alarm_sound",
                onClick = { onNavigateToCategory(SettingsRoutes.ALARM_SOUND) },
            )
        }

        item {
            SettingsCategoryCard(
                title = s.catScheduling,
                subtitle = s.catSchedulingDesc,
                icon = Icons.Filled.Schedule,
                badge = "${uiState.personalization.defaultAlarmDurationMinutes}m",
                testTag = "settings_cat_scheduling",
                onClick = { onNavigateToCategory(SettingsRoutes.SCHEDULING) },
            )
        }

        item {
            val permBadge = if (uiState.canScheduleExactAlarms) s.statusEnabled else s.statusActionRequired
            SettingsCategoryCard(
                title = s.catPermissions,
                subtitle = s.catPermissionsDesc,
                icon = Icons.Filled.VerifiedUser,
                badge = permBadge,
                highlightWarning = !uiState.canScheduleExactAlarms,
                testTag = "settings_cat_permissions",
                onClick = { onNavigateToCategory(SettingsRoutes.PERMISSIONS) },
            )
        }

        item {
            SettingsCategoryCard(
                title = s.catHistoryStats,
                subtitle = s.catHistoryStatsDesc,
                icon = Icons.Filled.BarChart,
                badge = "${uiState.alarmHistory.size}",
                testTag = "settings_cat_history",
                onClick = { onNavigateToCategory(SettingsRoutes.HISTORY_STATS) },
            )
        }

        item {
            SettingsCategoryCard(
                title = s.catBackupData,
                subtitle = s.catBackupDataDesc,
                icon = Icons.Filled.CloudUpload,
                badge = "JSON • Cloud",
                testTag = "settings_cat_backup",
                onClick = { onNavigateToCategory(SettingsRoutes.BACKUP_RESTORE) },
            )
        }

        item {
            SettingsCategoryCard(
                title = s.catAbout,
                subtitle = s.catAboutDesc,
                icon = Icons.Filled.Info,
                badge = "Chroniq",
                testTag = "settings_cat_about",
                onClick = { onNavigateToCategory(SettingsRoutes.ABOUT) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    highlightWarning: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (highlightWarning) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (highlightWarning) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (highlightWarning) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (highlightWarning) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
