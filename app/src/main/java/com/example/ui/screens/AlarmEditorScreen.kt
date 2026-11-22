package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmSourceType
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.PostAlarmAction
import com.example.data.model.RecurrenceType
import com.example.data.model.ReminderTemplateEntity
import com.example.data.model.SmartReminderEntity
import com.example.data.model.SyncStatus
import com.example.data.model.TimeFormatMode
import com.example.data.model.formatCountdownLongIndonesian
import com.example.data.model.formatShortId
import com.example.data.model.orderedDaysOfWeek
import com.example.data.model.shortLabel
import com.example.data.model.triggerMillisForDate
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ChronoMonoBadge
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private enum class EditorDateMode {
    SINGLE,
    MULTI,
    RECURRING,
}

private val ALARM_CATEGORIES = listOf("General", "Work", "Travel", "Health", "Study", "Personal")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditorScreen(
    alarmId: String?,
    initialDateIso: String?,
    prefs: PersonalizationPrefs,
    reminderTemplates: List<ReminderTemplateEntity>,
    loadAlarm: suspend (String) -> AlarmWithDates?,
    onSaveTemplate: (String, List<Int>) -> Unit,
    onSave: (AlarmEntity, List<LocalDate>, List<SmartReminderEntity>) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val isEn = prefs.language == AppLanguage.ENGLISH
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val maxDate = remember(today) { today.plusYears(5) }
    val defaultTime = remember { LocalTime.now().plusHours(1).withSecond(0).withNano(0) }

    val resolvedAlarmId = remember(alarmId) { alarmId ?: UUID.randomUUID().toString() }
    var loadedEntity by remember { mutableStateOf<AlarmEntity?>(null) }
    var isInitialized by remember { mutableStateOf(alarmId == null) }

    var label by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var dateMode by remember {
        mutableStateOf(
            when (prefs.defaultRecurrence) {
                RecurrenceType.MULTI_DATE -> EditorDateMode.MULTI
                RecurrenceType.MONTHLY_DATE,
                RecurrenceType.YEARLY_DATE,
                RecurrenceType.INTERVAL_DAYS,
                -> EditorDateMode.RECURRING
                RecurrenceType.SINGLE_DATE -> EditorDateMode.SINGLE
            },
        )
    }
    var recurringKind by remember {
        mutableStateOf(
            if (prefs.defaultRecurrence in listOf(
                    RecurrenceType.MONTHLY_DATE,
                    RecurrenceType.YEARLY_DATE,
                    RecurrenceType.INTERVAL_DAYS,
                )
            ) {
                prefs.defaultRecurrence
            } else {
                RecurrenceType.MONTHLY_DATE
            },
        )
    }
    var intervalDays by remember { mutableIntStateOf(7) }

    val initialLocalDate = remember(initialDateIso, today) {
        initialDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { !it.isBefore(today) }
            ?: today
    }
    var selectedDates by remember { mutableStateOf(setOf(initialLocalDate)) }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(initialLocalDate)) }

    var soundUri by remember { mutableStateOf(prefs.defaultSoundUri) }
    var soundName by remember { mutableStateOf(prefs.defaultSoundName) }
    var vibrate by remember { mutableStateOf(prefs.defaultVibrate) }
    var volumeLevel by remember { mutableIntStateOf(prefs.defaultVolumeLevel) }
    var fadeInSeconds by remember { mutableIntStateOf(prefs.defaultFadeInSeconds) }
    var snoozeMinutes by remember { mutableIntStateOf(prefs.defaultSnoozeMinutes) }
    var postAlarmAction by remember { mutableStateOf(prefs.defaultPostAlarmAction) }
    var useKeyboardTimeInput by remember { mutableStateOf(false) }

    var isSmartReminderEnabled by remember { mutableStateOf(prefs.defaultSmartRemindersEnabled) }
    var smartReminders by remember { mutableStateOf<List<SmartReminderEntity>>(emptyList()) }

    var initialHour by remember { mutableIntStateOf(defaultTime.hour) }
    var initialMinute by remember { mutableIntStateOf(defaultTime.minute) }

    LaunchedEffect(alarmId) {
        if (alarmId != null) {
            val existing = loadAlarm(alarmId)
            if (existing != null) {
                loadedEntity = existing.alarm
                label = existing.alarm.label
                description = existing.alarm.description
                category = existing.alarm.category
                initialHour = existing.alarm.hour
                initialMinute = existing.alarm.minute
                soundUri = existing.alarm.soundUri
                soundName = existing.alarm.soundName
                vibrate = existing.alarm.vibrate
                volumeLevel = existing.alarm.volumeLevel
                fadeInSeconds = existing.alarm.fadeInSeconds
                snoozeMinutes = existing.alarm.snoozeDurationMinutes
                postAlarmAction = existing.alarm.postAlarmActionEnum
                intervalDays = existing.alarm.intervalDays.coerceAtLeast(2)
                isSmartReminderEnabled = existing.alarm.isSmartReminderEnabled || existing.reminders.isNotEmpty()
                smartReminders = existing.sortedReminders

                when (existing.alarm.recurrenceTypeEnum) {
                    RecurrenceType.MULTI_DATE -> dateMode = EditorDateMode.MULTI
                    RecurrenceType.MONTHLY_DATE,
                    RecurrenceType.YEARLY_DATE,
                    RecurrenceType.INTERVAL_DAYS,
                    -> {
                        dateMode = EditorDateMode.RECURRING
                        recurringKind = existing.alarm.recurrenceTypeEnum
                    }
                    RecurrenceType.SINGLE_DATE -> dateMode = EditorDateMode.SINGLE
                }

                val parsedDates = existing.allLocalDates().filter { !it.isBefore(today) }.toSet()
                selectedDates = parsedDates.ifEmpty { setOf(today) }
                displayedMonth = YearMonth.from(selectedDates.min())
            }
            isInitialized = true
        }
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = prefs.timeFormat == TimeFormatMode.HOUR_24,
    )

    LaunchedEffect(isInitialized, loadedEntity) {
        if (isInitialized && loadedEntity != null) {
            timePickerState.hour = initialHour
            timePickerState.minute = initialMinute
        }
    }

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
                soundUri = uri.toString()
                soundName = runCatching {
                    RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                }.getOrNull() ?: "Custom Alarm"
            }
        }
    }

    val nowMillis = System.currentTimeMillis()
    val validFutureDates = remember(selectedDates, timePickerState.hour, timePickerState.minute) {
        selectedDates.sorted().filter { date ->
            triggerMillisForDate(date, timePickerState.hour, timePickerState.minute) > nowMillis
        }
    }
    val nextValidTriggerMillis = remember(validFutureDates, timePickerState.hour, timePickerState.minute) {
        validFutureDates.firstOrNull()?.let {
            triggerMillisForDate(it, timePickerState.hour, timePickerState.minute)
        }
    }
    val validationError: String? = when {
        selectedDates.isEmpty() -> if (isEn) "Select at least 1 target date." else "Pilih minimal 1 tanggal target."
        validFutureDates.isEmpty() -> if (isEn) "Selected date & time is in the past." else "Waktu & tanggal yang dipilih sudah lewat."
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (alarmId == null) s.editorNewTitle else s.editorEditTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.cancel)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (validationError != null) return@Button
                            val recurrenceType = when (dateMode) {
                                EditorDateMode.SINGLE -> RecurrenceType.SINGLE_DATE
                                EditorDateMode.MULTI -> if (validFutureDates.size > 1) {
                                    RecurrenceType.MULTI_DATE
                                } else {
                                    RecurrenceType.SINGLE_DATE
                                }
                                EditorDateMode.RECURRING -> recurringKind
                            }

                            val existing = loadedEntity
                            val entity = AlarmEntity(
                                id = existing?.id ?: resolvedAlarmId,
                                sourceType = existing?.sourceType ?: AlarmSourceType.MANUAL.name,
                                gcalEventId = existing?.gcalEventId,
                                gcalCalendarId = existing?.gcalCalendarId,
                                gcalCalendarName = existing?.gcalCalendarName,
                                gcalAccountEmail = existing?.gcalAccountEmail,
                                eventLocation = existing?.eventLocation,
                                eventStartTimeMillis = existing?.eventStartTimeMillis,
                                label = label.trim().ifBlank { if (isEn) "Date Alarm" else "Alarm Tanggal" },
                                description = description.trim(),
                                category = category,
                                hour = timePickerState.hour,
                                minute = timePickerState.minute,
                                recurrenceType = recurrenceType.name,
                                intervalDays = if (recurrenceType == RecurrenceType.INTERVAL_DAYS) intervalDays else 0,
                                isEnabled = true,
                                soundUri = soundUri,
                                soundName = soundName,
                                vibrate = vibrate,
                                volumeLevel = volumeLevel,
                                fadeInSeconds = fadeInSeconds,
                                snoozeDurationMinutes = snoozeMinutes,
                                maxSnoozeAttempts = existing?.maxSnoozeAttempts ?: prefs.defaultMaxSnoozeAttempts,
                                alarmDurationMinutes = existing?.alarmDurationMinutes ?: prefs.defaultAlarmDurationMinutes,
                                missedAlarmBehavior = existing?.missedAlarmBehavior ?: prefs.defaultMissedBehavior.name,
                                postAlarmAction = postAlarmAction.name,
                                isArchived = false,
                                isSmartReminderEnabled = isSmartReminderEnabled && smartReminders.isNotEmpty(),
                                offsetMinutesBefore = existing?.offsetMinutesBefore ?: 0,
                                syncStatus = existing?.syncStatus ?: SyncStatus.LOCAL.name,
                                lastSyncedAt = existing?.lastSyncedAt,
                                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                            )
                            val remindersToSave = if (isSmartReminderEnabled) smartReminders else emptyList()
                            onSave(entity, validFutureDates, remindersToSave)
                        },
                        enabled = validationError == null,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("editor_save_button"),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.save)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("alarm_editor_screen"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Live Countdown or Past-Time Validation Banner
            item(key = "countdown_or_error") {
                if (validationError != null) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_validation_error"),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = validationError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                } else if (nextValidTriggerMillis != null) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column {
                                Text(
                                    text = formatCountdownLongIndonesian(nextValidTriggerMillis, language = prefs.language),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = if (isEn) {
                                        "${validFutureDates.size} future date(s) selected"
                                    } else {
                                        "${validFutureDates.size} tanggal masa depan dipilih"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }
            }

            // Time Picker Section
            item(key = "time_picker_section") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isEn) "Alarm Time" else "Waktu Dering",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            IconButton(
                                onClick = { useKeyboardTimeInput = !useKeyboardTimeInput },
                                modifier = Modifier.testTag("toggle_time_input_mode"),
                            ) {
                                Icon(
                                    imageVector = if (useKeyboardTimeInput) Icons.Filled.AccessTime else Icons.Filled.Keyboard,
                                    contentDescription = "Toggle time input mode",
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (useKeyboardTimeInput) {
                            TimeInput(state = timePickerState)
                        } else {
                            TimePicker(state = timePickerState)
                        }
                    }
                }
            }

            // Date Picker Mode + Inline Calendar respecting firstDayOfWeek
            item(key = "date_picker_section") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = s.datesSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = dateMode == EditorDateMode.SINGLE,
                                onClick = {
                                    dateMode = EditorDateMode.SINGLE
                                    selectedDates = setOf(selectedDates.minOrNull() ?: today)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                modifier = Modifier.testTag("date_mode_single"),
                            ) { Text(if (isEn) "Single" else "Tunggal", maxLines = 1) }

                            SegmentedButton(
                                selected = dateMode == EditorDateMode.MULTI,
                                onClick = { dateMode = EditorDateMode.MULTI },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                modifier = Modifier.testTag("date_mode_multi"),
                            ) { Text(if (isEn) "Multi" else "Banyak", maxLines = 1) }

                            SegmentedButton(
                                selected = dateMode == EditorDateMode.RECURRING,
                                onClick = {
                                    dateMode = EditorDateMode.RECURRING
                                    selectedDates = setOf(selectedDates.minOrNull() ?: today)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                modifier = Modifier.testTag("date_mode_recurring"),
                            ) { Text(if (isEn) "Repeat" else "Berkala", maxLines = 1) }
                        }

                        AnimatedVisibility(visible = dateMode == EditorDateMode.RECURRING) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(
                                        selected = recurringKind == RecurrenceType.MONTHLY_DATE,
                                        onClick = { recurringKind = RecurrenceType.MONTHLY_DATE },
                                        shape = RoundedCornerShape(50),
                                        label = { Text(if (isEn) "Monthly" else "Bulanan") },
                                    )
                                    FilterChip(
                                        selected = recurringKind == RecurrenceType.YEARLY_DATE,
                                        onClick = { recurringKind = RecurrenceType.YEARLY_DATE },
                                        shape = RoundedCornerShape(50),
                                        label = { Text(if (isEn) "Yearly" else "Tahunan") },
                                    )
                                    FilterChip(
                                        selected = recurringKind == RecurrenceType.INTERVAL_DAYS,
                                        onClick = { recurringKind = RecurrenceType.INTERVAL_DAYS },
                                        shape = RoundedCornerShape(50),
                                        label = { Text(if (isEn) "Every N Days" else "Tiap N Hari") },
                                    )
                                }
                                if (recurringKind == RecurrenceType.INTERVAL_DAYS) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        listOf(2, 3, 5, 7, 14, 30).forEach { days ->
                                            FilterChip(
                                                selected = intervalDays == days,
                                                onClick = { intervalDays = days },
                                                shape = RoundedCornerShape(50),
                                                label = { Text(if (isEn) "Every ${days}d" else "Tiap $days hari") },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorInlineCalendar(
                            displayedMonth = displayedMonth,
                            today = today,
                            maxDate = maxDate,
                            selectedDates = selectedDates,
                            prefs = prefs,
                            onMonthChange = { displayedMonth = it },
                            onDateTapped = { tapped ->
                                if (tapped.isBefore(today) || tapped.isAfter(maxDate)) return@EditorInlineCalendar
                                selectedDates = when (dateMode) {
                                    EditorDateMode.SINGLE, EditorDateMode.RECURRING -> setOf(tapped)
                                    EditorDateMode.MULTI -> {
                                        if (tapped in selectedDates) selectedDates - tapped else selectedDates + tapped
                                    }
                                }
                            },
                        )

                        if (selectedDates.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                selectedDates.sorted().forEach { date ->
                                    InputChip(
                                        selected = true,
                                        shape = RoundedCornerShape(50),
                                        onClick = {
                                            if (dateMode == EditorDateMode.MULTI) {
                                                selectedDates = selectedDates - date
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = date.formatShortId(prefs.language),
                                                style = ChronoMonoBadge,
                                            )
                                        },
                                        trailingIcon = if (dateMode == EditorDateMode.MULTI) {
                                            {
                                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Smart Alarm Reminder Section
            item(key = "smart_reminder_section") {
                SmartReminderEditorSection(
                    parentAlarmId = loadedEntity?.id ?: resolvedAlarmId,
                    isEnabled = isSmartReminderEnabled,
                    onToggleEnabled = { isSmartReminderEnabled = it },
                    reminders = smartReminders,
                    onRemindersChange = { smartReminders = it },
                    templates = reminderTemplates,
                    onSaveTemplate = onSaveTemplate,
                    selectedDates = selectedDates,
                    hour = timePickerState.hour,
                    minute = timePickerState.minute,
                    prefs = prefs,
                )
            }

            // Label, Description, Category & Sound Settings
            item(key = "attributes_section") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(if (isEn) "Alarm Title" else "Label Alarm") },
                            placeholder = { Text(s.labelHint) },
                            singleLine = true,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_label_input"),
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(if (isEn) "Description / Notes" else "Deskripsi / Catatan") },
                            placeholder = { Text(s.descriptionHint) },
                            maxLines = 2,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_description_input"),
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = s.categoryLabel, style = MaterialTheme.typography.titleSmall)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ALARM_CATEGORIES.forEach { cat ->
                                    FilterChip(
                                        selected = category == cat,
                                        onClick = { category = cat },
                                        label = { Text(cat) },
                                        shape = RoundedCornerShape(50),
                                    )
                                }
                            }
                        }

                        // Ringtone Picker Row
                        Surface(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    soundUri?.let {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                    }
                                }
                                ringtonePickerLauncher.launch(intent)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_ringtone_picker"),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(
                                            text = if (isEn) "Alarm Sound" else "Nada Alarm",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = soundName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(text = if (isEn) "Vibration" else "Getar", style = MaterialTheme.typography.titleSmall)
                            }
                            Switch(
                                checked = vibrate,
                                onCheckedChange = { vibrate = it },
                                modifier = Modifier.testTag("editor_vibrate_switch"),
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(text = if (isEn) "Volume" else "Volume", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(text = "$volumeLevel%", style = ChronoMonoBadge, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = volumeLevel.toFloat(),
                                onValueChange = { volumeLevel = it.toInt() },
                                valueRange = 10f..100f,
                                modifier = Modifier.testTag("editor_volume_slider"),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(0 to "Off", 15 to "15s", 30 to "30s", 60 to "60s").forEach { (sec, txt) ->
                                    FilterChip(
                                        selected = fadeInSeconds == sec,
                                        onClick = { fadeInSeconds = sec },
                                        shape = RoundedCornerShape(50),
                                        label = { Text("Fade $txt") },
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(text = if (isEn) "Snooze Duration" else "Durasi Tunda", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(5, 10, 15, 30).forEach { min ->
                                FilterChip(
                                    selected = snoozeMinutes == min,
                                    onClick = { snoozeMinutes = min },
                                    shape = RoundedCornerShape(50),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    label = { Text("$min min") },
                                    modifier = Modifier.testTag("snooze_chip_$min"),
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(text = s.defaultPostAlarmTitle, style = MaterialTheme.typography.titleSmall)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = postAlarmAction == PostAlarmAction.AUTO_ARCHIVE,
                                onClick = { postAlarmAction = PostAlarmAction.AUTO_ARCHIVE },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            ) { Text(if (isEn) "Archive" else "Arsip", maxLines = 1) }

                            SegmentedButton(
                                selected = postAlarmAction == PostAlarmAction.TURN_OFF,
                                onClick = { postAlarmAction = PostAlarmAction.TURN_OFF },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            ) { Text(if (isEn) "Off" else "Matikan", maxLines = 1) }

                            SegmentedButton(
                                selected = postAlarmAction == PostAlarmAction.AUTO_DELETE,
                                onClick = { postAlarmAction = PostAlarmAction.AUTO_DELETE },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            ) { Text(if (isEn) "Delete" else "Hapus", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorInlineCalendar(
    displayedMonth: YearMonth,
    today: LocalDate,
    maxDate: LocalDate,
    selectedDates: Set<LocalDate>,
    prefs: PersonalizationPrefs,
    onMonthChange: (YearMonth) -> Unit,
    onDateTapped: (LocalDate) -> Unit,
) {
    val minMonth = remember(today) { YearMonth.from(today) }
    val maxMonth = remember(maxDate) { YearMonth.from(maxDate) }
    val locale = remember(prefs.language) {
        if (prefs.language == AppLanguage.ENGLISH) Locale.US else Locale.forLanguageTag("id-ID")
    }
    val monthTitle = remember(displayedMonth, locale) {
        displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val weekDays = remember(prefs.firstDayOfWeek) { orderedDaysOfWeek(prefs.firstDayOfWeek) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (displayedMonth.isAfter(minMonth)) onMonthChange(displayedMonth.minusMonths(1)) },
                enabled = displayedMonth.isAfter(minMonth),
                modifier = Modifier.testTag("editor_cal_prev"),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(text = monthTitle, style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { if (displayedMonth.isBefore(maxMonth)) onMonthChange(displayedMonth.plusMonths(1)) },
                enabled = displayedMonth.isBefore(maxMonth),
                modifier = Modifier.testTag("editor_cal_next"),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { dow ->
                Text(
                    text = dow.shortLabel(prefs.language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val firstDay = displayedMonth.atDay(1)
        val firstDayDowValue = prefs.firstDayOfWeek.dayOfWeek.value
        val offset = (firstDay.dayOfWeek.value - firstDayDowValue + 7) % 7
        val daysInMonth = displayedMonth.lengthOfMonth()
        val rows = ((offset + daysInMonth + 6) / 7)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (c in 0 until 7) {
                        val dayNum = r * 7 + c - offset + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = displayedMonth.atDay(dayNum)
                            val isSelected = date in selectedDates
                            val isPast = date.isBefore(today)
                            val isToday = date == today

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        },
                                    )
                                    .clickable(enabled = !isPast) { onDateTapped(date) }
                                    .testTag("editor_day_$dayNum"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayNum.toString(),
                                    style = ChronoMonoBadge.copy(
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
