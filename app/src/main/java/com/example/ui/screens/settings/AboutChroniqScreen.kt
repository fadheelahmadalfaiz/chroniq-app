package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.ui.localization.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutChroniqScreen(
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var activeDialogTitle by remember { mutableStateOf<String?>(null) }
    var activeDialogBody by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catAbout) },
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
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.chroniq_logo),
                            contentDescription = "Chroniq Logo",
                            modifier = Modifier
                                .size(112.dp)
                                .clip(RoundedCornerShape(24.dp)),
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Chroniq",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = s.aboutTagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "${s.versionLabel} ${BuildConfig.VERSION_NAME} • ${s.buildNumberLabel} ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutRow(
                            icon = Icons.Filled.PrivacyTip,
                            title = s.privacyPolicy,
                            onClick = {
                                activeDialogTitle = s.privacyPolicy
                                activeDialogBody = "Chroniq menyimpan jadwal alarm dan pengingat secara lokal di perangkat Anda. " +
                                    "Akses Google Calendar bersifat hanya-baca (calendar.readonly) dan token disimpan terenkripsi dengan Android Keystore AES-256-GCM. " +
                                    "Cadangan Cloud hanya dikirim saat Anda secara eksplisit mengonfigurasi endpoint HTTPS dan menekan Unggah/Sinkronisasi."
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AboutRow(
                            icon = Icons.Filled.Description,
                            title = s.termsOfService,
                            onClick = {
                                activeDialogTitle = s.termsOfService
                                activeDialogBody = "Chroniq menyediakan penjadwalan alarm berbasis tanggal dengan AlarmManager Android. " +
                                    "Keandalan dering bergantung pada izin Exact Alarm dan pengecualian optimasi baterai perangkat. " +
                                    "Pengguna bertanggung jawab memeriksa izin keandalan di halaman Diagnostik."
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AboutRow(
                            icon = Icons.Filled.Code,
                            title = s.openSourceLicenses,
                            onClick = {
                                activeDialogTitle = s.openSourceLicenses
                                activeDialogBody = "Chroniq menggunakan komponen Open Source berlisensi Apache 2.0:\n" +
                                    "• Jetpack Compose & Material 3 (AndroidX)\n" +
                                    "• AndroidX Room, WorkManager, DataStore & Navigation\n" +
                                    "• Retrofit, OkHttp & Moshi (Square)\n" +
                                    "• Kotlin Coroutines (JetBrains)"
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        AboutRow(
                            icon = Icons.Filled.Email,
                            title = s.contactSupport,
                            onClick = {
                                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:support@chroniq.app")
                                    putExtra(Intent.EXTRA_SUBJECT, "Chroniq v${BuildConfig.VERSION_NAME} Support")
                                }
                                runCatching { context.startActivity(emailIntent) }
                            },
                        )
                    }
                }
            }
        }
    }

    if (activeDialogTitle != null) {
        AlertDialog(
            onDismissRequest = { activeDialogTitle = null },
            title = { Text(activeDialogTitle.orEmpty()) },
            text = { Text(activeDialogBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { activeDialogTitle = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
