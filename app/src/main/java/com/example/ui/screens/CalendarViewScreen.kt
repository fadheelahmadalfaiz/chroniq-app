package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AlarmWithDates
import com.example.data.model.AppLanguage
import com.example.data.model.formatFullId
import com.example.data.model.orderedDaysOfWeek
import com.example.data.model.shortLabel
import com.example.ui.ChronoDateUiState
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ChronoMonoBadge
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarViewScreen(
    uiState: ChronoDateUiState,
    onCreateAlarmOnDate: (LocalDate) -> Unit,
    onEditAlarm: (String) -> Unit,
    onQuickUpdateAlarm: (alarmId: String, label: String, soundUri: String?, soundName: String, vibrate: Boolean) -> Unit,
    onToggleAlarm: (String, Boolean) -> Unit,
    onToggleSmartReminder: (String, String, Boolean) -> Unit,
    onArchiveAlarm: (String, Boolean) -> Unit,
    onDeleteAlarm: (String) -> Unit,
    onTestRingAlarm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val prefs = uiState.personalization
    val language = prefs.language
    val timeFormat = prefs.timeFormat
    val isEn = language == AppLanguage.ENGLISH
    val today = remember { LocalDate.now() }
    val maxYearMonth = remember(today) { YearMonth.from(today.plusYears(5)) }
    val minYearMonth = remember(today) { YearMonth.from(today.minusMonths(2)) }

    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by remember { mutableStateOf(today) }
    var selectedAlarmForSheet by remember { mutableStateOf<AlarmWithDates?>(null) }

    val markersByDate = remember(uiState.allAlarms) {
        val map = mutableMapOf<LocalDate, Pair<Boolean, Boolean>>()
        uiState.allAlarms
            .filter { !it.alarm.isArchived }
            .forEach { item ->
                val isGcal = item.alarm.isGoogleCalendar
                item.allLocalDates().forEach { date ->
                    val prev = map[date] ?: (false to false)
                    map[date] = if (isGcal) (prev.first to true) else (true to prev.second)
                }
            }
        map
    }

    val alarmsForSelectedDay = remember(uiState.allAlarms, selectedDate) {
        uiState.allAlarms
            .filter { !it.alarm.isArchived && selectedDate in it.allLocalDates() }
            .sortedBy { it.alarm.hour * 60 + it.alarm.minute }
    }

    val monthTitle = remember(displayedMonth, language) {
        val locale = if (isEn) Locale.US else Locale.forLanguageTag("id-ID")
        displayedMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", locale),
        ).replaceFirstChar { it.titlecase(locale) }
    }

    val weekDays = remember(prefs.firstDayOfWeek) {
        orderedDaysOfWeek(prefs.firstDayOfWeek)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("calendar_view_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "calendar_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = s.navCalendar,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                FilledTonalButton(
                    onClick = {
                        displayedMonth = YearMonth.from(today)
                        selectedDate = today
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("calendar_today_button"),
                ) {
                    Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isEn) "Today" else "Hari Ini")
                }
            }
        }

        // Material You Tonal Monthly Calendar Surface
        item(key = "calendar_grid_card") {
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (displayedMonth.isAfter(minYearMonth)) {
                                    displayedMonth = displayedMonth.minusMonths(1)
                                }
                            },
                            enabled = displayedMonth.isAfter(minYearMonth),
                            modifier = Modifier.testTag("calendar_prev_month"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = if (isEn) "Previous month" else "Bulan sebelumnya",
                            )
                        }
                        Text(
                            text = monthTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = {
                                if (displayedMonth.isBefore(maxYearMonth)) {
                                    displayedMonth = displayedMonth.plusMonths(1)
                                }
                            },
                            enabled = displayedMonth.isBefore(maxYearMonth),
                            modifier = Modifier.testTag("calendar_next_month"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (isEn) "Next month" else "Bulan berikutnya",
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekDays.forEach { dow ->
                            Text(
                                text = dow.shortLabel(language),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    val firstDay = displayedMonth.atDay(1)
                    val offset = (firstDay.dayOfWeek.value - prefs.firstDayOfWeek.dayOfWeek.value + 7) % 7
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
                                        val isSelected = date == selectedDate
                                        val isToday = date == today
                                        val (hasManual, hasGcal) = markersByDate[date] ?: (false to false)

                                        CalendarDayCell(
                                            dayNumber = dayNum,
                                            isSelected = isSelected,
                                            isToday = isToday,
                                            hasManualAlarm = hasManual,
                                            hasGcalAlarm = hasGcal,
                                            onClick = { selectedDate = date },
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(
                            color = MaterialTheme.colorScheme.primary,
                            label = s.manualSource,
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        LegendDot(
                            color = MaterialTheme.colorScheme.secondary,
                            label = s.gcalSource,
                        )
                    }
                }
            }
        }

        // Selected Date Header
        item(key = "selected_day_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedDate.formatFullId(language),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (isEn) {
                            "${alarmsForSelectedDay.size} alarm${if (alarmsForSelectedDay.size == 1) "" else "s"} scheduled"
                        } else {
                            "${alarmsForSelectedDay.size} alarm terjadwal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!selectedDate.isBefore(today)) {
                    Button(
                        onClick = { onCreateAlarmOnDate(selectedDate) },
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("calendar_add_on_selected_date"),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isEn) "Add" else "Tambah")
                    }
                }
            }
        }

        if (alarmsForSelectedDay.isEmpty()) {
            item(key = "selected_day_empty") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = s.noAlarmsOnDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        } else {
            items(
                items = alarmsForSelectedDay,
                key = { "cal_${it.alarm.id}" },
            ) { item: AlarmWithDates ->
                AlarmItemCard(
                    alarmWithDates = item,
                    nowMillis = uiState.nowMillis,
                    timeFormat = timeFormat,
                    language = language,
                    onClick = { selectedAlarmForSheet = item },
                    onToggleEnabled = { onToggleAlarm(item.alarm.id, it) },
                )
            }
        }
    }

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
private fun CalendarDayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasManualAlarm: Boolean,
    hasGcalAlarm: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .testTag("calendar_day_$dayNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = dayNumber.toString(),
                style = ChronoMonoBadge.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                ),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(5.dp),
            ) {
                if (hasManualAlarm) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.primary,
                            ),
                    )
                }
                if (hasGcalAlarm) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White
                                else MaterialTheme.colorScheme.secondary,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
