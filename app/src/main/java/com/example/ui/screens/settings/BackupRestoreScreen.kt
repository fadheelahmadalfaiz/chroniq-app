package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.backup.BackupPreviewResult
import com.example.backup.ChroniqBackupPayload
import com.example.backup.RestoreMode
import com.example.data.model.CloudBackupRecordEntity
import com.example.data.model.formatRelativeSyncTime
import com.example.ui.ChronoDateUiState
import com.example.ui.localization.LocalAppStrings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    uiState: ChronoDateUiState,
    onExportJsonToUri: (Uri) -> Unit,
    onShareJsonBackup: ((Uri) -> Unit) -> Unit,
    onInspectImportUri: (Uri) -> Unit,
    onDismissImportPreview: () -> Unit,
    onConfirmImportRestore: (ChroniqBackupPayload, RestoreMode) -> Unit,
    onSaveCloudConfig: (String, String, String) -> Unit,
    onUploadCloudBackup: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onRefreshCloudHistory: () -> Unit,
    onRestoreCloudVersion: (String, RestoreMode) -> Unit,
    onUpdateAutoCloudSync: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onResetAppData: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val prefs = uiState.personalization

    var apiUrlInput by remember(prefs.cloudApiBaseUrl) { mutableStateOf(prefs.cloudApiBaseUrl) }
    var emailInput by remember(prefs.cloudUserEmail) {
        mutableStateOf(
            prefs.cloudUserEmail.ifBlank { uiState.googleAccounts.firstOrNull()?.email.orEmpty() },
        )
    }
    var bearerTokenInput by remember { mutableStateOf("") }

    var pendingCloudRestoreId by remember { mutableStateOf<String?>(null) }
    var pendingReplacePayload by remember { mutableStateOf<ChroniqBackupPayload?>(null) }
    var showResetDataConfirm by remember { mutableStateOf(false) }

    val exportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExportJsonToUri(uri)
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onInspectImportUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.catBackupData) },
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
            // Section A: Local JSON Backup & Restore
            item {
                val lastLocalText = if (prefs.lastLocalBackupAt > 0L) {
                    formatRelativeSyncTime(prefs.lastLocalBackupAt, uiState.nowMillis, prefs.language)
                } else {
                    s.neverBackedUp
                }

                SettingsSectionCard(
                    title = s.localBackupSection,
                    subtitle = "${s.lastLocalBackup}: $lastLocalText",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
                                    .format(java.time.LocalDateTime.now())
                                exportDocumentLauncher.launch("chroniq_backup_$stamp.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_json_backup_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(s.exportJsonBackup, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                onShareJsonBackup { shareUri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Chroniq Alarm Backup")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(shareIntent, s.shareJsonBackup))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_json_backup_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(s.shareJsonBackup, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            importDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("import_json_backup_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(s.importJsonBackup)
                    }
                }
            }

            // Section B: Cloud Backup (PostgreSQL Backend HTTPS API)
            item {
                val lastCloudText = if (prefs.lastCloudSyncAt > 0L) {
                    formatRelativeSyncTime(prefs.lastCloudSyncAt, uiState.nowMillis, prefs.language)
                } else {
                    s.neverBackedUp
                }

                SettingsSectionCard(
                    title = s.cloudBackupSection,
                    subtitle = "Device ID: ${prefs.deviceId.ifBlank { "..." }} • $lastCloudText",
                ) {
                    OutlinedTextField(
                        value = apiUrlInput,
                        onValueChange = { apiUrlInput = it },
                        label = { Text(s.cloudApiUrlLabel) },
                        placeholder = { Text("https://api.example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_api_url_input"),
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text(s.cloudAccountEmailLabel) },
                        placeholder = { Text("user@example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_account_email_input"),
                    )

                    OutlinedTextField(
                        value = bearerTokenInput,
                        onValueChange = { bearerTokenInput = it },
                        label = {
                            Text(
                                if (uiState.hasCloudToken) {
                                    "${s.cloudBearerTokenLabel} (Tersimpan)"
                                } else {
                                    s.cloudBearerTokenLabel
                                },
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cloud_bearer_token_input"),
                    )

                    OutlinedButton(
                        onClick = {
                            onSaveCloudConfig(apiUrlInput, emailInput, bearerTokenInput)
                            bearerTokenInput = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_cloud_config_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(s.saveCloudConfig)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(s.autoCloudSyncLabel, style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = prefs.autoCloudSyncEnabled,
                            onCheckedChange = onUpdateAutoCloudSync,
                        )
                    }

                    if (uiState.isCloudBusy) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.size(10.dp))
                            Text("Menghubungkan ke server...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onUploadCloudBackup,
                            enabled = !uiState.isCloudBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_upload_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(s.uploadCloudBackup, maxLines = 1)
                        }

                        Button(
                            onClick = onSyncCloudNow,
                            enabled = !uiState.isCloudBusy,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_sync_now_button"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(s.syncNowCloud, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = onRefreshCloudHistory,
                        enabled = !uiState.isCloudBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(s.refreshCloudHistory)
                    }
                }
            }

            // Cloud Backup Version History
            item {
                Text(
                    text = s.cloudHistoryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (uiState.cloudBackups.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = s.noCloudBackups,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(uiState.cloudBackups, key = { it.backupId }) { record ->
                    CloudBackupHistoryItem(
                        record = record,
                        restoreLabel = s.restoreVersion,
                        onRestoreClick = { pendingCloudRestoreId = record.backupId },
                    )
                }
            }

            // Section C: Data Management (Clear History & Reset App Data)
            item {
                SettingsSectionCard(
                    title = s.dataManagementSection,
                    subtitle = s.resetAppDataDesc,
                ) {
                    OutlinedButton(
                        onClick = onClearHistory,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(s.clearHistory)
                    }

                    Button(
                        onClick = { showResetDataConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_app_data_button"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(s.resetAppData)
                    }
                }
            }
        }
    }

    // Local JSON Import Preview Dialog (Merge vs Replace)
    uiState.pendingImportPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onDismiss = onDismissImportPreview,
            onMerge = {
                preview.payload?.let { onConfirmImportRestore(it, RestoreMode.MERGE) }
            },
            onRequestReplace = {
                onDismissImportPreview()
                pendingReplacePayload = preview.payload
            },
        )
    }

    // Explicit Confirmation before Replace Restore
    pendingReplacePayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingReplacePayload = null },
            title = { Text("Ganti Semua Data Alarm?") },
            text = {
                Text(
                    "Mode Ganti Semua akan menghapus seluruh alarm lokal yang ada saat ini dan menggantinya dengan ${payload.alarms.size} alarm dari file cadangan.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingReplacePayload = null
                        onConfirmImportRestore(payload, RestoreMode.REPLACE)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Ganti Semua") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplacePayload = null }) { Text(s.cancel) }
            },
        )
    }

    // Cloud Version Restore Dialog
    pendingCloudRestoreId?.let { backupId ->
        AlertDialog(
            onDismissRequest = { pendingCloudRestoreId = null },
            title = { Text("Pulihkan Cadangan Cloud") },
            text = {
                Text("Pilih cara pemulihan dari versi cadangan Cloud ini. Gabung akan mempertahankan alarm lokal tanpa duplikasi.")
            },
            confirmButton = {
                Button(onClick = {
                    pendingCloudRestoreId = null
                    onRestoreCloudVersion(backupId, RestoreMode.MERGE)
                }) { Text("Gabung (Aman)") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingCloudRestoreId = null }) { Text(s.cancel) }
                    TextButton(onClick = {
                        pendingCloudRestoreId = null
                        onRestoreCloudVersion(backupId, RestoreMode.REPLACE)
                    }) { Text("Ganti Semua") }
                }
            },
        )
    }

    // Reset App Data Confirmation
    if (showResetDataConfirm) {
        AlertDialog(
            onDismissRequest = { showResetDataConfirm = false },
            title = { Text(s.resetAppData) },
            text = { Text(s.resetAppDataDesc) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDataConfirm = false
                        onResetAppData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(s.confirmAction) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDataConfirm = false }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun CloudBackupHistoryItem(
    record: CloudBackupRecordEntity,
    restoreLabel: String,
    onRestoreClick: () -> Unit,
) {
    val formattedDate = remember(record.createdAt) {
        DateTimeFormatter.ofPattern("dd MMM yyyy • HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(record.createdAt))
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${record.alarmCount} alarm • v${record.backupVersion} • ${record.deviceId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onRestoreClick,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(6.dp))
                Text(restoreLabel)
            }
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: BackupPreviewResult,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onRequestReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pratinjau Impor Cadangan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Format Versi: v${preview.backupVersion}", style = MaterialTheme.typography.bodyMedium)
                Text("Total Alarm: ${preview.totalAlarms}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("• Alarm Baru: ${preview.newAlarmsCount}", style = MaterialTheme.typography.bodySmall)
                Text("• Duplikat Terdeteksi: ${preview.duplicateAlarmsCount}", style = MaterialTheme.typography.bodySmall)
                Text("• Smart Reminders: ${preview.totalSmartReminders}", style = MaterialTheme.typography.bodySmall)
                Text("• Template Pengingat: ${preview.totalTemplates}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onMerge,
                modifier = Modifier.testTag("import_merge_confirm_button"),
            ) { Text("Gabung (Merge)") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Batal") }
                TextButton(
                    onClick = onRequestReplace,
                    modifier = Modifier.testTag("import_replace_button"),
                ) { Text("Ganti Semua") }
            }
        },
    )
}
