package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ChronoDateRoot
import com.example.ui.ChronoDateViewModel
import com.example.ui.localization.AppStrings
import com.example.ui.localization.LocalAppStrings
import com.example.ui.theme.ChronoDateTheme
import com.example.widget.ChroniqNextAlarmWidgetProvider

class MainActivity : ComponentActivity() {

    private val viewModel: ChronoDateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val prefs = uiState.personalization
            val strings = AppStrings.forLanguage(prefs.language)

            CompositionLocalProvider(LocalAppStrings provides strings) {
                ChronoDateTheme(
                    themeMode = prefs.themeMode,
                    accentColor = prefs.accentColor,
                    customAccentArgb = prefs.customAccentArgb,
                ) {
                    ChronoDateRoot(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetDeepLink(intent)
    }

    private fun handleWidgetDeepLink(intent: Intent?) {
        val openAlarmId = intent?.getStringExtra(ChroniqNextAlarmWidgetProvider.EXTRA_OPEN_ALARM_ID)
        if (!openAlarmId.isNullOrBlank()) {
            viewModel.requestOpenAlarmDetails(openAlarmId)
            intent.removeExtra(ChroniqNextAlarmWidgetProvider.EXTRA_OPEN_ALARM_ID)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}
