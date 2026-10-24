package com.kodastream.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kodastream.app.data.AppEvents
import com.kodastream.app.data.AppPrefs
import com.kodastream.app.data.DownloadStore
import com.kodastream.app.downloads.DownloadMigration
import com.kodastream.app.downloads.DownloadService
import com.kodastream.app.engine.AppBootstrap
import com.kodastream.app.ui.AppRoot
import com.kodastream.app.ui.setup.SetupWizard
import com.kodastream.app.ui.theme.KodaStreamTheme
import com.kodastream.app.ui.theme.ThemeMode
import com.kodastream.app.ui.theme.ThemeViewModel
import com.lagradost.cloudstream3.CommonActivity

/**
 * AppCompatActivity rather than ComponentActivity on purpose: extensions
 * written for CloudStream sometimes cast the current activity to AppCompatActivity.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CommonActivity.setActivityInstance(this)
        AppBootstrap.start(this)
        handleIntent(intent)

        // One-time: older downloads move into their title folders.
        lifecycleScope.launch(Dispatchers.IO) { DownloadMigration.run(this@MainActivity) }

        // Interrupted and queued downloads resume where they left off.
        if (DownloadStore.current(this).any {
                it.state == DownloadStore.STATE_QUEUED || it.state == DownloadStore.STATE_DOWNLOADING
            }
        ) {
            DownloadService.ensure(this)
        }

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            var setupDone by remember { mutableStateOf(AppPrefs.setupDone(this@MainActivity)) }
            val dark = when (themeViewModel.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KodaStreamTheme(
                darkTheme = dark,
                colorPalette = themeViewModel.palette,
                amoledDark = themeViewModel.amoled,
                paletteStyle = themeViewModel.style,
            ) {
                if (setupDone) {
                    AppRoot(themeViewModel)
                } else {
                    SetupWizard(themeViewModel) { language ->
                        AppPrefs.setSetupDone(this@MainActivity, true)
                        AppPrefs.setAppLanguage(this@MainActivity, language)
                        AppPrefs.applyAppLanguage(this@MainActivity)
                        setupDone = true
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        CommonActivity.setActivityInstance(this)
    }

    private fun handleIntent(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (link.startsWith("cloudstreamrepo://")) {
            AppEvents.pendingRepoUrl.value = link
        }
    }
}
