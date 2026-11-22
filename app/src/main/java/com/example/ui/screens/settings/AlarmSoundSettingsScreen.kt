package com.example.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PersonalizationPrefs
import com.example.ui.localization.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmSoundSettingsScreen(
    prefs: PersonalizationPrefs,
    onUpdateDefaultSound: (String?, String) -> Unit,
    onUpdateDefaultVolume: (Int) -> Unit,
    onUpdateDefaultVibrate: (Boolean) -> Unit,
    onUpdateDefaultFadeIn: (Int) -> Unit,
    onUpdateDefaultSnooze: (Int) -> Unit,
    onUpdateDefaultMaxSnooze: (Int) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        runCatching {
            previewPlayer?.stop()
            previewPlayer?.release()
        }
        previewPlayer = null
        isPreviewPlaying = false
    }

    fun togglePreview() {
        if (isPreviewPlaying) {
            stopPreview()
            return
        }
        val uri = prefs.defaultSoundUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                val vol = (prefs.defaultVolumeLevel.coerceIn(10, 100) / 100f)
                setVolume(vol, vol)
                isLooping = false
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
            previewPlayer = player
            isPreviewPlaying = true
        }.onFailure {
            stopPreview()
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
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
            val title = uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) }
                ?: "Default Alarm"
            onUpdateDefaultSound(uri?.toString(), title)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catAlarmSound) },
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
                SettingsSectionCard(
                    title = s.defaultSoundLabel,
                    subtitle = prefs.defaultSoundName,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    prefs.defaultSoundUri?.let {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                    }
                                }
                                ringtonePickerLauncher.launch(intent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_default_sound_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(s.pickCustomSound, maxLines = 1)
                        }

                        Button(
                            onClick = { togglePreview() },
                            modifier = Modifier.testTag("preview_sound_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (isPreviewPlaying) s.stopSoundPreview else s.testSoundPreview, maxLines = 1)
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "${s.volumeLabel}: ${prefs.defaultVolumeLevel}%") {
                    Slider(
                        value = prefs.defaultVolumeLevel.toFloat(),
                        onValueChange = { onUpdateDefaultVolume(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.testTag("default_volume_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(s.vibrationLabel, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = prefs.defaultVibrate,
                            onCheckedChange = onUpdateDefaultVibrate,
                            modifier = Modifier.testTag("default_vibrate_switch"),
                        )
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.gradualFadeInLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 10, 15, 30, 60).forEach { secs ->
                            FilterChip(
                                selected = prefs.defaultFadeInSeconds == secs,
                                onClick = { onUpdateDefaultFadeIn(secs) },
                                label = { Text(if (secs == 0) "Off" else "${secs}s") },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.defaultSnoozeLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(5, 10, 15, 20, 30).forEach { mins ->
                            FilterChip(
                                selected = prefs.defaultSnoozeMinutes == mins,
                                onClick = { onUpdateDefaultSnooze(mins) },
                                label = { Text("$mins min") },
                            )
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = s.maxSnoozeAttemptsLabel) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 1, 2, 3, 5).forEach { attempts ->
                            FilterChip(
                                selected = prefs.defaultMaxSnoozeAttempts == attempts,
                                onClick = { onUpdateDefaultMaxSnooze(attempts) },
                                label = { Text(if (attempts == 0) "Unlimited" else "${attempts}x") },
                            )
                        }
                    }
                }
            }
        }
    }
}
