package com.example

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ai.configureAppCheck
import com.example.ui.DashboardScreen
import com.example.ui.PlaybackViewModel
import com.example.ui.theme.MinimalPlaybackTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PlaybackViewModel by viewModels()

    private val keyHandler = Handler(Looper.getMainLooper())
    private var volumeUpLongPressTriggered = false
    private var volumeDownLongPressTriggered = false

    private val volumeUpLongPressRunnable = Runnable {
        if (viewModel.isDimLockActive.value) {
            volumeUpLongPressTriggered = true
            viewModel.handleDimVolumeUpLongPress()
        }
    }

    private val volumeDownLongPressRunnable = Runnable {
        if (viewModel.isDimLockActive.value) {
            volumeDownLongPressTriggered = true
            viewModel.handleDimVolumeDownLongPress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.isDimLockActive,
                    viewModel.settings
                ) { isDimLocked, settings ->
                    isDimLocked to (settings.dimLockTimeoutSeconds > 0)
                }.collect { (isDimLocked, hasInactivityOverride) ->
                    applyDimLockWindowState(
                        isDimLocked = isDimLocked,
                        overrideSystemTimeout = isDimLocked || hasInactivityOverride
                    )
                }
            }
        }

        setContent {
            val trackColors by viewModel.trackColors.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val customFontFamily by viewModel.customFontFamily.collectAsStateWithLifecycle()

            MinimalPlaybackTheme(
                themeStyle = settings.themeStyle,
                dynamicAccent = trackColors.primary,
                dynamicSecondary = trackColors.secondary,
                useAmoledBlack = settings.useAmoledBlack,
                customAccentHex = settings.customAccentHex,
                customFontHex = settings.customFontHex,
                customFontFamily = customFontFamily
            ) {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            viewModel.notifyUserInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        viewModel.notifyUserInteraction()
    }

    private fun applyDimLockWindowState(
        isDimLocked: Boolean,
        overrideSystemTimeout: Boolean
    ) {
        val params = window.attributes
        if (overrideSystemTimeout) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        params.screenBrightness = if (isDimLocked) {
            0.01f
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = params
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        viewModel.notifyUserInteraction()
        if (!viewModel.isDimLockActive.value) {
            return super.onKeyDown(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event?.repeatCount == 0) {
                    volumeUpLongPressTriggered = false
                    keyHandler.removeCallbacks(volumeUpLongPressRunnable)
                    keyHandler.postDelayed(volumeUpLongPressRunnable, 500L)
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event?.repeatCount == 0) {
                    volumeDownLongPressTriggered = false
                    keyHandler.removeCallbacks(volumeDownLongPressRunnable)
                    keyHandler.postDelayed(volumeDownLongPressRunnable, 500L)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (!viewModel.isDimLockActive.value) {
            return super.onKeyUp(keyCode, event)
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                keyHandler.removeCallbacks(volumeUpLongPressRunnable)
                if (!volumeUpLongPressTriggered) {
                    viewModel.handleDimVolumeUpShortPress()
                }
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                keyHandler.removeCallbacks(volumeDownLongPressRunnable)
                if (!volumeDownLongPressTriggered) {
                    viewModel.handleDimVolumeDownShortPress()
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        keyHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
