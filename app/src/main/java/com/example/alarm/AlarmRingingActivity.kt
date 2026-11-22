package com.example.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ChronoDateApp
import com.example.data.model.AlarmEntity
import com.example.data.model.AppLanguage
import com.example.data.model.PersonalizationPrefs
import com.example.data.model.SmartReminderEntity
import com.example.data.model.formatAlarmTime
import com.example.data.model.formatClockTime
import com.example.data.model.formatFullId
import com.example.data.model.formatOffsetLabel
import com.example.ui.theme.ChronoClockHuge
import com.example.ui.theme.ChronoDateTheme
import com.example.ui.theme.ChronoMonoBadge
import com.example.ui.theme.CobaltSkyLight
import com.example.ui.theme.ObsidianBackground
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

class AlarmRingingActivity : ComponentActivity() {

    private var finishReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockScreenWindow()

        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID)
        val alarmDateId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID)
        val reminderId = intent.getStringExtra(AlarmRingingService.EXTRA_REMINDER_ID)
        val snoozeCount = intent.getIntExtra(AlarmRingingService.EXTRA_SNOOZE_COUNT, 0)
        val isPreview = intent.getBooleanExtra(AlarmRingingService.EXTRA_IS_PREVIEW, false)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                finish()
            }
        }
        finishReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AlarmRingingService.ACTION_RINGING_FINISHED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            var prefs by remember { mutableStateOf(PersonalizationPrefs()) }
            var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
            var reminder by remember { mutableStateOf<SmartReminderEntity?>(null) }

            LaunchedEffect(alarmId, reminderId) {
                val app = applicationContext as? ChronoDateApp
                if (app != null) {
                    prefs = app.personalizationStore.getCurrent()
                    if (alarmId != null) {
                        val loaded = app.repository.getAlarmById(alarmId)
                        alarm = loaded?.alarm
                        reminder = reminderId?.let { rid -> loaded?.reminders?.firstOrNull { it.id == rid } }
                    }
                }
            }

            ChronoDateTheme(
                darkTheme = true,
                accentColor = prefs.accentColor,
                customAccentArgb = prefs.customAccentArgb,
            ) {
                AlarmRingingScreen(
                    alarm = alarm,
                    reminder = reminder,
                    prefs = prefs,
                    snoozeCount = snoozeCount,
                    isPreview = isPreview,
                    onSnooze = {
                        sendServiceAction(
                            AlarmRingingService.ACTION_SNOOZE,
                            alarmId,
                            alarmDateId,
                            reminderId,
                            snoozeCount,
                            isPreview,
                        )
                        finish()
                    },
                    onDismiss = {
                        sendServiceAction(
                            AlarmRingingService.ACTION_DISMISS,
                            alarmId,
                            alarmDateId,
                            reminderId,
                            snoozeCount,
                            isPreview,
                        )
                        finish()
                    },
                )
            }
        }
    }

    private fun configureLockScreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sendServiceAction(
        action: String,
        alarmId: String?,
        alarmDateId: String?,
        reminderId: String?,
        snoozeCount: Int,
        isPreview: Boolean,
    ) {
        val serviceIntent = Intent(this, AlarmRingingService::class.java).apply {
            this.action = action
            putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmRingingService.EXTRA_ALARM_DATE_ID, alarmDateId)
            putExtra(AlarmRingingService.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmRingingService.EXTRA_SNOOZE_COUNT, snoozeCount)
            putExtra(AlarmRingingService.EXTRA_IS_PREVIEW, isPreview)
        }
        startService(serviceIntent)
    }

    override fun onDestroy() {
        finishReceiver?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }
}

@Composable
private fun AlarmRingingScreen(
    alarm: AlarmEntity?,
    reminder: SmartReminderEntity?,
    prefs: PersonalizationPrefs,
    snoozeCount: Int,
    isPreview: Boolean,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isEn = prefs.language == AppLanguage.ENGLISH
    val isGoogleCalendar = alarm?.isGoogleCalendar == true
    val accentColor = if (isGoogleCalendar) CobaltSkyLight else MaterialTheme.colorScheme.primary
    val accentStrong = if (isGoogleCalendar) Color(0xFF2563EB) else MaterialTheme.colorScheme.primaryContainer

    var nowTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTime = LocalTime.now()
            delay(1_000L)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "ringPulse")
    val ring1Scale by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1Scale",
    )
    val ring2Scale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring2Scale",
    )

    val formattedNow = remember(nowTime, prefs.timeFormat) {
        formatAlarmTime(nowTime.hour, nowTime.minute, prefs.timeFormat)
    }
    val todayLabel = remember(prefs.language) { LocalDate.now().formatFullId(prefs.language) }
    val maxSnooze = alarm?.maxSnoozeAttempts ?: 3
    val canSnooze = isPreview || maxSnooze == 0 || snoozeCount < maxSnooze
    val snoozeMinutes = reminder?.snoozeMinutes ?: alarm?.snoozeDurationMinutes ?: 10

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF151C36),
                        ObsidianBackground,
                        Color(0xFF070912),
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.32f)
            val baseRadius = size.minDimension * 0.28f

            val alpha1 = ((1.25f - ring1Scale) / 0.6f).coerceIn(0f, 0.35f)
            val alpha2 = ((1.45f - ring2Scale) / 0.6f).coerceIn(0f, 0.22f)

            drawCircle(
                color = accentColor.copy(alpha = alpha1),
                radius = baseRadius * ring1Scale,
                center = center,
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = accentColor.copy(alpha = alpha2),
                radius = baseRadius * ring2Scale,
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (isGoogleCalendar) Icons.Filled.CalendarMonth else Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = when {
                                isPreview -> if (isEn) "TEST RING MODE" else "MODE UJI DERING"
                                reminder != null -> "Smart Reminder • ${formatOffsetLabel(reminder.offsetMinutes, prefs.language)}"
                                isGoogleCalendar -> alarm?.gcalCalendarName ?: "Google Calendar"
                                else -> if (isEn) "Date Alarm" else "Alarm Tanggal"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = todayLabel,
                    style = ChronoMonoBadge,
                    color = Color(0xFF94A3B8),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = formattedNow,
                    style = ChronoClockHuge,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                )

                Spacer(modifier = Modifier.height(16.dp))

                val mainTitle = reminder?.customTitle?.takeIf { it.isNotBlank() }
                    ?: alarm?.label?.ifBlank { "Chroniq Alarm" }
                    ?: "Chroniq Alarm"

                Text(
                    text = mainTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (reminder != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val note = reminder.customMessage.ifBlank {
                        val parentTime = alarm?.formattedTime(prefs.timeFormat).orEmpty()
                        if (isEn) "Upcoming event: ${alarm?.label} at $parentTime" else "Jadwal utama: ${alarm?.label} pukul $parentTime"
                    }
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFCBD5E1),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isGoogleCalendar && alarm != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF15223C).copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            alarm.eventStartTimeMillis?.let { startMillis ->
                                val prefix = if (isEn) "Event starts at " else "Acara dimulai pukul "
                                Text(
                                    text = "$prefix${startMillis.formatClockTime(prefs.timeFormat)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CobaltSkyLight,
                                )
                            }
                            if (!alarm.eventLocation.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = alarm.eventLocation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (canSnooze) {
                    Button(
                        onClick = onSnooze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .testTag("ringing_snooze_button"),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val snoozeText = if (isEn) "Snooze $snoozeMinutes min" else "Tunda $snoozeMinutes menit"
                        Text(
                            text = snoozeText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                SlideToDismissTrack(
                    accentColor = accentStrong,
                    label = if (isEn) "Slide to dismiss" else "Geser untuk matikan",
                    onDismissed = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun SlideToDismissTrack(
    accentColor: Color,
    label: String,
    onDismissed: () -> Unit,
) {
    val thumbSize = 62.dp
    val trackHeight = 72.dp
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xFF162033))
            .padding(5.dp)
            .testTag("ringing_slide_to_dismiss"),
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxDragPx = with(density) { (maxWidth - thumbSize).toPx().coerceAtLeast(1f) }
        val progress = (dragOffsetPx / maxDragPx).coerceIn(0f, 1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(36.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = (1f - progress * 1.1f).coerceIn(0.15f, 0.9f)),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = (1f - progress).coerceIn(0.2f, 0.9f)),
                modifier = Modifier.size(22.dp),
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(accentColor)
                .pointerInput(maxDragPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetPx >= maxDragPx * 0.78f) {
                                onDismissed()
                            } else {
                                dragOffsetPx = 0f
                            }
                        },
                        onDragCancel = { dragOffsetPx = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxDragPx)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.AlarmOff,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
