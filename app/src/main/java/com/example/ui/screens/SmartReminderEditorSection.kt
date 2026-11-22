package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.computeSmartReminderSchedule
import com.example.data.model.formatAlarmTime
import com.example.data.model.formatOffsetLabel
import com.example.data.model.formatShortId
import com.example.ui.localization.LocalAppStrings
import java.time.LocalDate
import java.util.UUID

private val SMART_REMINDER_PRESETS_MINUTES = listOf(
    5,
    10,
    15,
    30,
    60,
    120,
    180,
    360,
    720,
    1440,
    2880,
    4320,
    10080,
    0, // At event time
)

private enum class CustomOffsetUnit(val multiplierMinutes: Int, val labelId: String, val labelEn: String) {
    MINUTES(1, "Menit", "Minutes"),
    HOURS(60, "Jam", "Hours"),
    DAYS(1440, "Hari", "Days"),
    WEEKS(10080, "Minggu", "Weeks"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartReminderEditorSection(
    parentAlarmId: String,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    reminders: List<SmartReminderEntity>,
    onRemindersChange: (List<SmartReminderEntity>) -> Unit,
    templates: List<ReminderTemplateEntity>,
    onSaveTemplate: (String, List<Int>) -> Unit,
    selectedDates: Set<LocalDate>,
    hour: Int,
    minute: Int,
    prefs: PersonalizationPrefs,
) {
    val s = LocalAppStrings.current
    val isEn = prefs.language == AppLanguage.ENGLISH
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<SmartReminderEntity?>(null) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }

    val activeOffsets = remember(reminders) { reminders.map { it.offsetMinutes }.toSet() }
    val previewSchedule = remember(selectedDates, hour, minute, reminders, isEnabled) {
        if (!isEnabled || reminders.isEmpty() || selectedDates.isEmpty()) {
            emptyList()
        } else {
            computeSmartReminderSchedule(
                dates = selectedDates.sorted().take(2),
                hour = hour,
                minute = minute,
                reminders = reminders.filter { it.isEnabled },
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_reminder_section"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = s.smartReminderSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isEnabled && reminders.isNotEmpty()) {
                                "${reminders.count { it.isEnabled }}/${reminders.size} aktif"
                            } else {
                                s.smartReminderSectionSubtitle
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enabled ->
                        onToggleEnabled(enabled)
                        if (enabled && reminders.isEmpty()) {
                            // Sensible default reminders when toggled on for the first time
                            onRemindersChange(
                                listOf(60, 15).map { offset ->
                                    SmartReminderEntity(
                                        id = UUID.randomUUID().toString(),
                                        parentAlarmId = parentAlarmId,
                                        offsetMinutes = offset,
                                        soundUri = prefs.defaultSoundUri,
                                        soundName = prefs.defaultSoundName,
                                        vibrate = prefs.defaultVibrate,
                                        isEnabled = true,
                                        snoozeMinutes = 5,
                                    )
                                },
                            )
                        }
                    },
                    modifier = Modifier.testTag("smart_reminder_master_switch"),
                )
            }

            AnimatedVisibility(visible = isEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Reusable Templates Row
                    if (templates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = s.reminderTemplatesTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                templates.forEach { tpl ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val newList = tpl.parsedOffsets().map { offset ->
                                                SmartReminderEntity(
                                                    id = UUID.randomUUID().toString(),
                                                    parentAlarmId = parentAlarmId,
                                                    offsetMinutes = offset,
                                                    soundUri = prefs.defaultSoundUri,
                                                    soundName = prefs.defaultSoundName,
                                                    vibrate = prefs.defaultVibrate,
                                                    isEnabled = true,
                                                    snoozeMinutes = 5,
                                                )
                                            }
                                            onRemindersChange(newList)
                                        },
                                        label = { Text(tpl.name, maxLines = 1) },
                                        shape = RoundedCornerShape(50),
                                    )
                                }
                            }
                        }
                    }

                    // Quick Preset Chips (14 presets including At Event Time)
                    Text(
                        text = if (isEn) "Quick Reminder Presets" else "Preset Cepat Pengingat",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SMART_REMINDER_PRESETS_MINUTES.forEach { offsetMin ->
                            val selected = offsetMin in activeOffsets
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        onRemindersChange(reminders.filterNot { it.offsetMinutes == offsetMin })
                                    } else {
                                        val added = SmartReminderEntity(
                                            id = UUID.randomUUID().toString(),
                                            parentAlarmId = parentAlarmId,
                                            offsetMinutes = offsetMin,
                                            soundUri = prefs.defaultSoundUri,
                                            soundName = prefs.defaultSoundName,
                                            vibrate = prefs.defaultVibrate,
                                            isEnabled = true,
                                            snoozeMinutes = 5,
                                        )
                                        onRemindersChange((reminders + added).sortedByDescending { it.offsetMinutes })
                                    }
                                },
                                label = {
                                    Text(
                                        text = formatOffsetLabel(offsetMin, prefs.language),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("smart_preset_$offsetMin"),
                            )
                        }
                    }

                    // Action Buttons: Add Custom + Save Template
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                editingReminder = null
                                showCustomDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_custom_reminder_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.addCustomReminder, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        if (reminders.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showSaveTemplateDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("save_reminder_template_button"),
                            ) {
                                Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(s.saveAsTemplate, maxLines = 1)
                            }
                        }
                    }

                    // Active Child Reminders List (with individual edit, enable/disable, delete)
                    if (reminders.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reminders.sortedByDescending { it.offsetMinutes }.forEach { rem ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    editingReminder = rem
                                                    showCustomDialog = true
                                                },
                                        ) {
                                            Text(
                                                text = rem.customTitle.ifBlank {
                                                    formatOffsetLabel(rem.offsetMinutes, prefs.language)
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val subtitle = buildList {
                                                if (rem.customTitle.isNotBlank()) {
                                                    add(formatOffsetLabel(rem.offsetMinutes, prefs.language))
                                                }
                                                if (rem.customMessage.isNotBlank()) add(rem.customMessage)
                                                add("${rem.soundName} • ${rem.snoozeMinutes}m")
                                            }.joinToString(" • ")
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                editingReminder = rem
                                                showCustomDialog = true
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit reminder", modifier = Modifier.size(18.dp))
                                        }

                                        Switch(
                                            checked = rem.isEnabled,
                                            onCheckedChange = { checked ->
                                                onRemindersChange(
                                                    reminders.map {
                                                        if (it.id == rem.id) it.copy(isEnabled = checked) else it
                                                    },
                                                )
                                            },
                                        )

                                        IconButton(
                                            onClick = {
                                                onRemindersChange(reminders.filterNot { it.id == rem.id })
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = "Remove reminder",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Schedule Preview Card
                    if (previewSchedule.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = s.reminderSchedulePreview,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                previewSchedule.take(8).forEach { occ ->
                                    val timeStr = formatAlarmTime(
                                        occ.triggerDateTime.hour,
                                        occ.triggerDateTime.minute,
                                        prefs.timeFormat,
                                    )
                                    val dateStr = occ.triggerDateTime.toLocalDate().formatShortId(prefs.language)
                                    val labelStr = occ.reminder.customTitle.ifBlank {
                                        formatOffsetLabel(occ.reminder.offsetMinutes, prefs.language)
                                    }
                                    val pastSuffix = if (occ.isInPast) {
                                        if (isEn) " (skipped - past)" else " (dilewati - lewat)"
                                    } else {
                                        ""
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Icon(
                                                Icons.Filled.NotificationsActive,
                                                contentDescription = null,
                                                tint = if (occ.isInPast) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = "$labelStr$pastSuffix",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (occ.isInPast) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "$dateStr • $timeStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (occ.isInPast) {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomReminderDialog(
            parentAlarmId = parentAlarmId,
            existing = editingReminder,
            prefs = prefs,
            onDismiss = { showCustomDialog = false },
            onSave = { saved ->
                val updatedList = if (editingReminder != null) {
                    reminders.map { if (it.id == saved.id) saved else it }
                } else {
                    reminders + saved
                }
                onRemindersChange(updatedList.sortedByDescending { it.offsetMinutes })
                showCustomDialog = false
            },
        )
    }

    if (showSaveTemplateDialog) {
        var tplName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text(s.saveAsTemplate) },
            text = {
                OutlinedTextField(
                    value = tplName,
                    onValueChange = { tplName = it },
                    label = { Text(if (isEn) "Template name" else "Nama template") },
                    placeholder = { Text("e.g. Morning Flight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tplName.isNotBlank()) {
                            onSaveTemplate(tplName, reminders.map { it.offsetMinutes })
                            showSaveTemplateDialog = false
                        }
                    },
                ) { Text(s.save) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) { Text(s.cancel) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomReminderDialog(
    parentAlarmId: String,
    existing: SmartReminderEntity?,
    prefs: PersonalizationPrefs,
    onDismiss: () -> Unit,
    onSave: (SmartReminderEntity) -> Unit,
) {
    val context = LocalContext.current
    val isEn = prefs.language == AppLanguage.ENGLISH

    val (initialVal, initialUnit) = remember(existing) {
        val mins = existing?.offsetMinutes ?: 45
        when {
            mins >= 10080 && mins % 10080 == 0 -> (mins / 10080).toString() to CustomOffsetUnit.WEEKS
            mins >= 1440 && mins % 1440 == 0 -> (mins / 1440).toString() to CustomOffsetUnit.DAYS
            mins >= 60 && mins % 60 == 0 -> (mins / 60).toString() to CustomOffsetUnit.HOURS
            else -> mins.toString() to CustomOffsetUnit.MINUTES
        }
    }

    var amountText by remember { mutableStateOf(initialVal) }
    var unit by remember { mutableStateOf(initialUnit) }
    var customTitle by remember { mutableStateOf(existing?.customTitle.orEmpty()) }
    var customMessage by remember { mutableStateOf(existing?.customMessage.orEmpty()) }
    var soundUri by remember { mutableStateOf(existing?.soundUri ?: prefs.defaultSoundUri) }
    var soundName by remember { mutableStateOf(existing?.soundName ?: prefs.defaultSoundName) }
    var vibrate by remember { mutableStateOf(existing?.vibrate ?: prefs.defaultVibrate) }
    var snoozeMins by remember { mutableIntStateOf(existing?.snoozeMinutes ?: 5) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
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
                soundUri = uri.toString()
                soundName = runCatching {
                    RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                }.getOrNull() ?: "Custom Sound"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing != null) {
                    if (isEn) "Edit Smart Reminder" else "Edit Pengingat Cerdas"
                } else {
                    if (isEn) "Add Custom Reminder" else "Tambah Pengingat Kustom"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() }.take(4) },
                    label = { Text(if (isEn) "Time before alarm (0 = at event)" else "Waktu sebelum alarm (0 = tepat waktu)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CustomOffsetUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(if (isEn) u.labelEn else u.labelId) },
                        )
                    }
                }

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text(if (isEn) "Custom title (optional)" else "Judul kustom (opsional)") },
                    placeholder = { Text(if (isEn) "e.g. Prepare boarding pass" else "mis. Siapkan dokumen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    label = { Text(if (isEn) "Custom message (optional)" else "Pesan kustom (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            soundUri?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        ringtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(soundName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isEn) "Vibration" else "Getar", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15).forEach { m ->
                        FilterChip(
                            selected = snoozeMins == m,
                            onClick = { snoozeMins = m },
                            label = { Text("Snooze ${m}m") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toIntOrNull() ?: 0
                    val totalMinutes = (amount * unit.multiplierMinutes).coerceIn(0, 60 * 24 * 30)
                    onSave(
                        SmartReminderEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            parentAlarmId = parentAlarmId,
                            offsetMinutes = totalMinutes,
                            customTitle = customTitle.trim(),
                            customMessage = customMessage.trim(),
                            soundUri = soundUri,
                            soundName = soundName,
                            vibrate = vibrate,
                            isEnabled = existing?.isEnabled ?: true,
                            snoozeMinutes = snoozeMins,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text(if (isEn) "Save" else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isEn) "Cancel" else "Batal") }
        },
    )
}
