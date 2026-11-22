package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.localization.LocalAppStrings
import com.example.ui.screens.AlarmEditorScreen
import com.example.ui.screens.CalendarViewScreen
import com.example.ui.screens.GoogleSyncScreen
import com.example.ui.screens.UpcomingAlarmsScreen
import com.example.ui.screens.settings.AboutChroniqScreen
import com.example.ui.screens.settings.AlarmSoundSettingsScreen
import com.example.ui.screens.settings.BackupRestoreScreen
import com.example.ui.screens.settings.HistoryStatsScreen
import com.example.ui.screens.settings.PermissionsDiagnosticsScreen
import com.example.ui.screens.settings.PersonalizationScreen
import com.example.ui.screens.settings.SchedulingSettingsScreen
import com.example.ui.screens.settings.SettingsHubScreen
import com.example.ui.screens.settings.SettingsRoutes
import java.time.LocalDate

object ChronoRoutes {
    const val ROUTE_UPCOMING = "upcoming"
    const val ROUTE_CALENDAR = "calendar"
    const val ROUTE_SYNC = "sync"
    const val ROUTE_SETTINGS = SettingsRoutes.HUB
    const val ROUTE_EDITOR_PATTERN = "editor?alarmId={alarmId}&initialDate={initialDate}"

    fun editorRoute(alarmId: String? = null, initialDate: LocalDate? = null): String {
        val params = buildList {
            if (!alarmId.isNullOrBlank()) add("alarmId=$alarmId")
            if (initialDate != null) add("initialDate=$initialDate")
        }
        return if (params.isEmpty()) "editor" else "editor?${params.joinToString("&")}"
    }
}

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

@Composable
fun ChronoDateRoot(viewModel: ChronoDateViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = LocalAppStrings.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val topDestinations = remember(s) {
        listOf(
            TopDestination(
                route = ChronoRoutes.ROUTE_UPCOMING,
                label = s.navUpcoming,
                selectedIcon = Icons.Filled.Alarm,
                unselectedIcon = Icons.Outlined.Alarm,
                testTag = "nav_tab_upcoming",
            ),
            TopDestination(
                route = ChronoRoutes.ROUTE_CALENDAR,
                label = s.navCalendar,
                selectedIcon = Icons.Filled.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_tab_calendar",
            ),
            TopDestination(
                route = ChronoRoutes.ROUTE_SYNC,
                label = s.navSync,
                selectedIcon = Icons.Filled.CloudSync,
                unselectedIcon = Icons.Outlined.CloudSync,
                testTag = "nav_tab_sync",
            ),
            TopDestination(
                route = ChronoRoutes.ROUTE_SETTINGS,
                label = s.navSettings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_tab_settings",
            ),
        )
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    // Deep-link from Home Screen Widget tapping the alarm opens its details/editor directly
    LaunchedEffect(uiState.pendingOpenAlarmId) {
        val openId = uiState.pendingOpenAlarmId
        if (!openId.isNullOrBlank()) {
            viewModel.consumeOpenAlarmDetails()
            navController.navigate(ChronoRoutes.editorRoute(alarmId = openId)) {
                launchSingleTop = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ChronoRoutes.ROUTE_UPCOMING
    val isTopLevel = topDestinations.any { it.route == currentRoute }

    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == ChronoRoutes.ROUTE_UPCOMING) {
                    FloatingActionButton(
                        onClick = { navController.navigate(ChronoRoutes.editorRoute()) },
                        shape = RoundedCornerShape(22.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("fab_create_alarm"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = s.addAlarm,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isTopLevel && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ChronoRoutes.ROUTE_UPCOMING,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                    ) {
                        composable(ChronoRoutes.ROUTE_UPCOMING) {
                            UpcomingAlarmsScreen(
                                uiState = uiState,
                                onSelectFilter = viewModel::selectFilter,
                                onCreateAlarm = { navController.navigate(ChronoRoutes.editorRoute()) },
                                onEditAlarm = { id -> navController.navigate(ChronoRoutes.editorRoute(alarmId = id)) },
                                onQuickUpdateAlarm = viewModel::quickUpdateAlarmAttributes,
                                onToggleAlarm = viewModel::toggleAlarmEnabled,
                                onToggleSmartReminder = viewModel::toggleSmartReminder,
                                onArchiveAlarm = viewModel::archiveAlarm,
                                onDeleteAlarm = viewModel::deleteAlarm,
                                onTestRingAlarm = viewModel::testRingAlarm,
                                onSyncNow = viewModel::syncCalendarsNow,
                                onOpenSyncTab = { navigateToTopLevel(ChronoRoutes.ROUTE_SYNC) },
                            )
                        }

                        composable(ChronoRoutes.ROUTE_CALENDAR) {
                            CalendarViewScreen(
                                uiState = uiState,
                                onCreateAlarmOnDate = { date ->
                                    navController.navigate(ChronoRoutes.editorRoute(initialDate = date))
                                },
                                onEditAlarm = { id -> navController.navigate(ChronoRoutes.editorRoute(alarmId = id)) },
                                onQuickUpdateAlarm = viewModel::quickUpdateAlarmAttributes,
                                onToggleAlarm = viewModel::toggleAlarmEnabled,
                                onToggleSmartReminder = viewModel::toggleSmartReminder,
                                onArchiveAlarm = viewModel::archiveAlarm,
                                onDeleteAlarm = viewModel::deleteAlarm,
                                onTestRingAlarm = viewModel::testRingAlarm,
                            )
                        }

                        composable(ChronoRoutes.ROUTE_SYNC) {
                            GoogleSyncScreen(
                                uiState = uiState,
                                onStartGoogleOAuth = viewModel::startGoogleOAuth,
                                onGoogleOAuthResult = viewModel::handleGoogleOAuthResult,
                                onConnectDeviceCalendars = viewModel::connectDeviceCalendars,
                                onSyncNow = viewModel::syncCalendarsNow,
                                onToggleCalendar = viewModel::toggleCalendarSelection,
                                onDisconnectAccount = viewModel::disconnectAccount,
                                onOffsetChange = viewModel::updateOffsetMinutes,
                                onFilterModeChange = viewModel::updateFilterMode,
                                onAddKeyword = viewModel::addKeyword,
                                onRemoveKeyword = viewModel::removeKeyword,
                                onIgnoreAllDayChange = viewModel::updateIgnoreAllDay,
                                onAllDayTimeChange = viewModel::updateAllDayTime,
                                onDeletedBehaviorChange = viewModel::updateDeletedEventBehavior,
                                onSyncIntervalChange = viewModel::updateSyncIntervalHours,
                                onToggleGcalSmartReminders = viewModel::updateEnableGcalSmartReminders,
                                onToggleGcalReminderOffset = viewModel::toggleGcalSmartReminderOffset,
                                onPermissionDeniedMessage = viewModel::showMessage,
                            )
                        }

                        composable(SettingsRoutes.HUB) {
                            SettingsHubScreen(
                                uiState = uiState,
                                onNavigateToCategory = { route -> navController.navigate(route) },
                            )
                        }

                        composable(SettingsRoutes.PERSONALIZATION) {
                            PersonalizationScreen(
                                prefs = uiState.personalization,
                                onUpdateThemeMode = viewModel::updateThemeMode,
                                onUpdateAccentColor = viewModel::updateAccentColor,
                                onUpdateCustomAccent = viewModel::updateCustomAccent,
                                onUpdateTimeFormat = viewModel::updateTimeFormat,
                                onUpdateFirstDayOfWeek = viewModel::updateFirstDayOfWeek,
                                onUpdateLanguage = viewModel::updateLanguage,
                                onUpdateDefaultPostAlarmAction = viewModel::updateDefaultPostAlarmAction,
                                onResetToDefaults = viewModel::resetPersonalizationToDefaults,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.ALARM_SOUND) {
                            AlarmSoundSettingsScreen(
                                prefs = uiState.personalization,
                                onUpdateDefaultSound = viewModel::updateDefaultSound,
                                onUpdateDefaultVolume = viewModel::updateDefaultVolume,
                                onUpdateDefaultVibrate = viewModel::updateDefaultVibrate,
                                onUpdateDefaultFadeIn = viewModel::updateDefaultFadeIn,
                                onUpdateDefaultSnooze = viewModel::updateDefaultSnooze,
                                onUpdateDefaultMaxSnooze = viewModel::updateDefaultMaxSnooze,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.SCHEDULING) {
                            SchedulingSettingsScreen(
                                prefs = uiState.personalization,
                                onUpdateAlarmDuration = viewModel::updateDefaultAlarmDuration,
                                onUpdateDefaultRecurrence = viewModel::updateDefaultRecurrence,
                                onUpdateMissedBehavior = viewModel::updateDefaultMissedBehavior,
                                onUpdateDefaultSmartReminders = viewModel::updateDefaultSmartRemindersEnabled,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.PERMISSIONS) {
                            PermissionsDiagnosticsScreen(
                                onRescheduleAllNow = viewModel::rescheduleAllAlarmsNow,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.HISTORY_STATS) {
                            HistoryStatsScreen(
                                history = uiState.alarmHistory,
                                prefs = uiState.personalization,
                                onClearHistory = viewModel::clearAlarmHistory,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.BACKUP_RESTORE) {
                            BackupRestoreScreen(
                                uiState = uiState,
                                onExportJsonToUri = viewModel::exportLocalBackupToUri,
                                onShareJsonBackup = viewModel::shareLocalBackup,
                                onInspectImportUri = viewModel::inspectImportBackupUri,
                                onDismissImportPreview = viewModel::dismissImportPreview,
                                onConfirmImportRestore = viewModel::confirmImportRestore,
                                onSaveCloudConfig = viewModel::saveCloudConfiguration,
                                onUploadCloudBackup = viewModel::uploadCloudBackupNow,
                                onSyncCloudNow = viewModel::syncCloudBidirectionalNow,
                                onRefreshCloudHistory = viewModel::refreshCloudHistoryNow,
                                onRestoreCloudVersion = viewModel::restoreCloudBackupVersion,
                                onUpdateAutoCloudSync = viewModel::updateAutoCloudSync,
                                onClearHistory = viewModel::clearAlarmHistory,
                                onResetAppData = viewModel::resetAllAppData,
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }

                        composable(SettingsRoutes.ABOUT) {
                            AboutChroniqScreen(onNavigateBack = { navController.popBackStack() })
                        }

                        composable(
                            route = ChronoRoutes.ROUTE_EDITOR_PATTERN,
                            arguments = listOf(
                                navArgument("alarmId") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                navArgument("initialDate") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { entry ->
                            val alarmId = entry.arguments?.getString("alarmId")
                            val initialDate = entry.arguments?.getString("initialDate")
                            AlarmEditorScreen(
                                alarmId = alarmId,
                                initialDateIso = initialDate,
                                prefs = uiState.personalization,
                                reminderTemplates = uiState.reminderTemplates,
                                loadAlarm = viewModel::loadAlarmById,
                                onSaveTemplate = viewModel::saveReminderTemplate,
                                onSave = { alarm, dates, reminders ->
                                    viewModel.saveAlarm(alarm, dates, reminders) {
                                        navController.popBackStack()
                                    }
                                },
                                onNavigateBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
