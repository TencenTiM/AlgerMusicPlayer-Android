package com.algermusic.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.algermusic.player.ui.MainScreen
import com.algermusic.player.ui.theme.AlgerMusicTheme
import com.algermusic.player.ui.viewmodel.MainViewModel

data class DisplayModeCandidate(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

fun selectHighestRefreshDisplayMode(
    modes: List<DisplayModeCandidate>,
    currentWidth: Int,
    currentHeight: Int
): DisplayModeCandidate? {
    if (modes.isEmpty()) return null
    val sameResolution = modes.filter {
        it.physicalWidth == currentWidth && it.physicalHeight == currentHeight
    }
    val pool = sameResolution.ifEmpty { modes }
    return pool.maxByOrNull { it.refreshRate }
}

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }

    private val viewModel: MainViewModel by viewModels()
    private var openPlayerSignal by mutableIntStateOf(0)

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }

        setContent {
            val settings by viewModel.settingsFlow.collectAsState()
            AlgerMusicTheme(themeMode = settings.themeMode) {
                MainScreen(
                    viewModel = viewModel,
                    openPlayerSignal = openPlayerSignal
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
    }

    private fun requestHighRefreshRate() {
        runCatching {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            activeDisplay ?: return
            val currentMode = activeDisplay.mode
            val candidates = activeDisplay.supportedModes.map { mode ->
                DisplayModeCandidate(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRate = mode.refreshRate
                )
            }
            val best = selectHighestRefreshDisplayMode(
                modes = candidates,
                currentWidth = currentMode.physicalWidth,
                currentHeight = currentMode.physicalHeight
            ) ?: return

            val attrs = window.attributes
            @Suppress("DEPRECATION")
            val currentPrefRate = attrs.preferredRefreshRate
            if (attrs.preferredDisplayModeId != best.modeId || currentPrefRate != best.refreshRate) {
                attrs.preferredDisplayModeId = best.modeId
                @Suppress("DEPRECATION")
                attrs.preferredRefreshRate = best.refreshRate
                window.attributes = attrs
            }
        }
    }
}
