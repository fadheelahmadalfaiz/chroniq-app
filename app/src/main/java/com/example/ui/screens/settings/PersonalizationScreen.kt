package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentColorPreset
import com.example.data.model.AppLanguage
import com.example.data.model.FirstDayOfWeekPref
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.PostAlarmAction
import com.example.data.model.ThemeMode
import com.example.data.model.TimeFormatMode
import com.example.ui.localization.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonalizationScreen(
    prefs: PersonalizationPrefs,
    onUpdateThemeMode: (ThemeMode) -> Unit,
    onUpdateAccentColor: (AccentColorPreset) -> Unit,
    onUpdateCustomAccent: (Long) -> Unit,
    onUpdateTimeFormat: (TimeFormatMode) -> Unit,
    onUpdateFirstDayOfWeek: (FirstDayOfWeekPref) -> Unit,
    onUpdateLanguage: (AppLanguage) -> Unit,
    onUpdateDefaultPostAlarmAction: (PostAlarmAction) -> Unit,
    onResetToDefaults: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    var showCustomColorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catPersonalization) },
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
            // Theme Mode
            item {
                SettingsSectionCard(title = s.themeModeTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            ThemeMode.DARK to s.themeDark,
                            ThemeMode.LIGHT to s.themeLight,
                            ThemeMode.SYSTEM to s.themeSystem,
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = prefs.themeMode == mode,
                                onClick = { onUpdateThemeMode(mode) },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // Accent Color Palette + Custom Picker
            item {
                SettingsSectionCard(title = s.accentColorTitle) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AccentColorPreset.entries.forEach { preset ->
                            val isSelected = prefs.customAccentArgb == null && prefs.accentColor == preset
                            val swatchColor = Color(preset.seedHex)
                            val label = if (prefs.language == AppLanguage.ENGLISH) {
                                preset.displayNameEn
                            } else {
                                preset.displayNameId
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onUpdateAccentColor(preset) }
                                    .testTag("accent_preset_${preset.name.lowercase()}"),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = label,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        // Custom color button
                        val isCustom = prefs.customAccentArgb != null
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showCustomColorDialog = true }
                                .testTag("accent_custom_picker_button"),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        prefs.customAccentArgb?.let { Color(it.toULong() and 0xFFFFFFFFUL or 0xFF000000UL) }
                                            ?: MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                    .then(
                                        if (isCustom) {
                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Colorize,
                                    contentDescription = s.customAccentColor,
                                    tint = if (isCustom) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCustom) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // Time Format (12h vs 24h)
            item {
                SettingsSectionCard(title = s.timeFormatTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = prefs.timeFormat == TimeFormatMode.HOUR_24,
                            onClick = { onUpdateTimeFormat(TimeFormatMode.HOUR_24) },
                            label = { Text(s.timeFormat24) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("time_format_24"),
                        )
                        FilterChip(
                            selected = prefs.timeFormat == TimeFormatMode.HOUR_12,
                            onClick = { onUpdateTimeFormat(TimeFormatMode.HOUR_12) },
                            label = { Text(s.timeFormat12) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("time_format_12"),
                        )
                    }
                }
            }

            // First Day of Week
            item {
                SettingsSectionCard(title = s.firstDayOfWeekTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            FirstDayOfWeekPref.MONDAY to s.dayMonday,
                            FirstDayOfWeekPref.SUNDAY to s.daySunday,
                            FirstDayOfWeekPref.SATURDAY to s.daySaturday,
                        ).forEach { (pref, label) ->
                            FilterChip(
                                selected = prefs.firstDayOfWeek == pref,
                                onClick = { onUpdateFirstDayOfWeek(pref) },
                                label = { Text(label) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("first_day_${pref.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // App Language
            item {
                SettingsSectionCard(title = s.languageTitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = prefs.language == AppLanguage.INDONESIAN,
                            onClick = { onUpdateLanguage(AppLanguage.INDONESIAN) },
                            label = { Text(s.langIndonesian) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_id_chip"),
                        )
                        FilterChip(
                            selected = prefs.language == AppLanguage.ENGLISH,
                            onClick = { onUpdateLanguage(AppLanguage.ENGLISH) },
                            label = { Text(s.langEnglish) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lang_en_chip"),
                        )
                    }
                }
            }

            // Default Post-Alarm Behavior
            item {
                SettingsSectionCard(title = s.defaultPostAlarmTitle) {
                    val isEn = prefs.language == AppLanguage.ENGLISH
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            PostAlarmAction.AUTO_ARCHIVE to if (isEn) "Auto Archive" else "Arsipkan Otomatis",
                            PostAlarmAction.TURN_OFF to if (isEn) "Turn Off" else "Matikan Saja",
                            PostAlarmAction.AUTO_DELETE to if (isEn) "Auto Delete" else "Hapus Otomatis",
                        ).forEach { (action, label) ->
                            FilterChip(
                                selected = prefs.defaultPostAlarmAction == action,
                                onClick = { onUpdateDefaultPostAlarmAction(action) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            // Reset Personalization to Defaults
            item {
                OutlinedButton(
                    onClick = onResetToDefaults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("reset_personalization_button"),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(s.resetPersonalization)
                }
            }
        }
    }

    if (showCustomColorDialog) {
        CustomAccentColorDialog(
            initialArgb = prefs.customAccentArgb ?: prefs.accentColor.seedHex,
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { argb ->
                onUpdateCustomAccent(argb)
                showCustomColorDialog = false
            },
        )
    }
}

@Composable
private fun CustomAccentColorDialog(
    initialArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initialColor = Color(initialArgb.toULong() and 0xFFFFFFFFUL or 0xFF000000UL)
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    val previewColor = Color(red, green, blue, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Accent Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(previewColor),
                )
                Text("Red (${(red * 255).toInt()})", style = MaterialTheme.typography.labelMedium)
                Slider(value = red, onValueChange = { red = it })
                Text("Green (${(green * 255).toInt()})", style = MaterialTheme.typography.labelMedium)
                Slider(value = green, onValueChange = { green = it })
                Text("Blue (${(blue * 255).toInt()})", style = MaterialTheme.typography.labelMedium)
                Slider(value = blue, onValueChange = { blue = it })
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(previewColor.toArgb().toLong() and 0xFFFFFFFFL) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SettingsSectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}
