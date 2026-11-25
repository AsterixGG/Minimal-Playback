package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AppThemeStyle
import com.example.model.PlaybackSource
import com.example.model.PlaybackStatsState
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.ui.components.DashboardStatsPanel
import com.example.ui.components.DimLockOverlay
import com.example.ui.components.DynamicAmbientBackground
import com.example.ui.components.EqualizerAndSettingsSheet
import com.example.ui.components.LeftLyricsAndArtCard
import com.example.ui.components.LocalLibrarySheet
import com.example.ui.components.RightControlsAndQueueCard
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SleepTimerSheet
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.contrastOnColor
import com.example.ui.theme.parseColorHex
import com.example.ui.theme.resolveThemePalette
import com.example.util.formatCountdownMs

@Composable
fun DashboardScreen(viewModel: PlaybackViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val snapshot by viewModel.playbackSnapshot.collectAsStateWithLifecycle()
    val trackArtwork by viewModel.trackArtwork.collectAsStateWithLifecycle()
    val livePos by viewModel.livePositionMs.collectAsStateWithLifecycle()
    val trackColors by viewModel.trackColors.collectAsStateWithLifecycle()
    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val recommendationsState by viewModel.recommendationsState.collectAsStateWithLifecycle()
    val notificationAccessGranted by viewModel.notificationAccessGranted.collectAsStateWithLifecycle()
    val localLibrary by viewModel.localLibrary.collectAsStateWithLifecycle()
    val isScanningLibrary by viewModel.isScanningLibrary.collectAsStateWithLifecycle()
    val isDimLockActive by viewModel.isDimLockActive.collectAsStateWithLifecycle()
    val gestureFeedback by viewModel.gestureFeedback.collectAsStateWithLifecycle()
    val statusToast by viewModel.statusToast.collectAsStateWithLifecycle()
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val mediaVolume by viewModel.mediaVolume.collectAsStateWithLifecycle()
    val fontUploadStatus by viewModel.fontUploadStatus.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val playbackStats by viewModel.playbackStats.collectAsStateWithLifecycle()

    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showLocalLibrarySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var openUpNextSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.openUpNextTrigger.collect {
            openUpNextSignal++
        }
    }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.scanLocalLibrary()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onRecordAudioPermissionChanged(granted)
    }

    val customFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomFont(uri)
        }
    }

    val exportLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.writeLyricsToUri(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onRecordAudioPermissionChanged(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (hasAudioPermission) {
            viewModel.scanLocalLibrary()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setAppForegroundActive(true)
                    viewModel.refreshNotificationAccess()
                    viewModel.refreshMediaVolume()
                    hasAudioPermission = ContextCompat.checkSelfPermission(
                        context,
                        audioPermission
                    ) == PackageManager.PERMISSION_GRANTED
                    viewModel.onRecordAudioPermissionChanged(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                    viewModel.setVisualizerLifecycleResumed(true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setAppForegroundActive(false)
                    viewModel.setVisualizerLifecycleResumed(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val customAccentColor = remember(settings.customAccentHex) {
        parseColorHex(settings.customAccentHex)
    }
    val palette = resolveThemePalette(
        themeStyle = settings.themeStyle,
        useAmoledBlack = settings.useAmoledBlack,
        trackPrimary = trackColors.primary,
        trackSecondary = trackColors.secondary,
        customAccentColor = customAccentColor
    )
    val accentColor = palette.primaryAccent
    val secondaryAccent = palette.secondaryAccent
    val fontColors = LocalAppFontColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicAmbientBackground(
            primaryAccent = accentColor,
            secondaryAccent = secondaryAccent,
            isPlaying = snapshot.isPlaying,
            themeStyle = settings.themeStyle,
            voidBackground = palette.voidBackground,
            glassIntensity = settings.glassBlurIntensity
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Studio Console Header
            TopConsoleHeader(
                selectedSource = selectedSource,
                accentColor = accentColor,
                cardBackground = palette.cardBackground,
                isDimLockActive = isDimLockActive,
                sleepTimerState = sleepTimerState,
                onSwitchSource = { src ->
                    viewModel.switchSource(src)
                    if (src == PlaybackSource.LOCAL && !snapshot.hasActiveSession) {
                        if (hasAudioPermission) {
                            viewModel.scanLocalLibrary()
                        }
                        showLocalLibrarySheet = true
                    }
                },
                onOpenSleepTimer = { showSleepTimerSheet = true },
                onOpenLibrary = {
                    if (hasAudioPermission && localLibrary.songs.isEmpty()) {
                        viewModel.scanLocalLibrary()
                    }
                    showLocalLibrarySheet = true
                },
                onOpenEqualizer = { showEqualizerSheet = true },
                onOpenSettings = { showSettingsSheet = true },
                onActivateDimLock = { viewModel.setDimLockActive(true) }
            )

            // Main Dashboard Content
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val isLandscapeSplit = maxWidth > maxHeight && maxWidth >= 520.dp
                val availableHeight = maxHeight

                if (selectedSource == PlaybackSource.EXTERNAL && !notificationAccessGranted) {
                    IdleDashboardWithStats(
                        isLandscapeSplit = isLandscapeSplit,
                        title = stringResource(R.string.external_permission_title),
                        description = stringResource(R.string.external_permission_desc),
                        primaryActionLabel = stringResource(R.string.grant_notification_access),
                        secondaryActionLabel = stringResource(R.string.switch_to_local),
                        accentColor = accentColor,
                        secondaryAccent = secondaryAccent,
                        themeStyle = settings.themeStyle,
                        cardBackground = palette.cardBackground,
                        playbackStats = playbackStats,
                        onPrimaryAction = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        },
                        onSecondaryAction = {
                            viewModel.switchSource(PlaybackSource.LOCAL)
                            showLocalLibrarySheet = true
                        },
                        onResetStats = viewModel::resetPlaybackStats,
                        primaryTag = "enable_notification_access_button"
                    )
                } else if (!snapshot.hasActiveSession) {
                    if (selectedSource == PlaybackSource.EXTERNAL) {
                        IdleDashboardWithStats(
                            isLandscapeSplit = isLandscapeSplit,
                            title = stringResource(R.string.waiting_for_playback_title),
                            description = stringResource(R.string.waiting_for_playback_desc),
                            primaryActionLabel = stringResource(R.string.switch_to_local),
                            secondaryActionLabel = stringResource(R.string.local_library_title),
                            accentColor = accentColor,
                            secondaryAccent = secondaryAccent,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            playbackStats = playbackStats,
                            onPrimaryAction = {
                                viewModel.switchSource(PlaybackSource.LOCAL)
                                showLocalLibrarySheet = true
                            },
                            onSecondaryAction = {
                                viewModel.switchSource(PlaybackSource.LOCAL)
                                showLocalLibrarySheet = true
                            },
                            onResetStats = viewModel::resetPlaybackStats,
                            primaryTag = "switch_to_local_button"
                        )
                    } else {
                        IdleDashboardWithStats(
                            isLandscapeSplit = isLandscapeSplit,
                            title = stringResource(R.string.local_library_title),
                            description = stringResource(R.string.local_permission_desc),
                            primaryActionLabel = if (hasAudioPermission) {
                                stringResource(R.string.local_library_title)
                            } else {
                                stringResource(R.string.grant_audio_permission)
                            },
                            secondaryActionLabel = stringResource(R.string.source_external),
                            accentColor = accentColor,
                            secondaryAccent = secondaryAccent,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            playbackStats = playbackStats,
                            onPrimaryAction = {
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(audioPermission)
                                } else {
                                    viewModel.scanLocalLibrary()
                                    showLocalLibrarySheet = true
                                }
                            },
                            onSecondaryAction = {
                                viewModel.switchSource(PlaybackSource.EXTERNAL)
                            },
                            onResetStats = viewModel::resetPlaybackStats,
                            primaryTag = "open_local_library_cta"
                        )
                    }
                } else if (isLandscapeSplit) {
                    // Android Auto-Style Landscape Split Dashboard
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LeftLyricsAndArtCard(
                            snapshot = snapshot,
                            artwork = trackArtwork,
                            lyricsState = lyricsState,
                            accentColor = accentColor,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            voidBackground = palette.voidBackground,
                            glassBlurIntensity = settings.glassBlurIntensity,
                            bassEnergy = visualizerFrame.bassEnergy,
                            isCompactHeight = availableHeight < 420.dp,
                            lyricsTextScale = settings.lyricsTextScale,
                            onSeekTo = viewModel::seekTo,
                            onNudgeOffset = viewModel::nudgeVocalOffset,
                            onRetryLyrics = viewModel::retryLyrics,
                            onExportLyrics = { filename -> exportLyricsLauncher.launch(filename) },
                            modifier = Modifier
                                .weight(1.08f)
                                .fillMaxHeight()
                        )

                        RightControlsAndQueueCard(
                            snapshot = snapshot,
                            livePositionMs = livePos,
                            recommendationsState = recommendationsState,
                            visualizerFrame = visualizerFrame,
                            mediaVolume = mediaVolume,
                            sleepTimerState = sleepTimerState,
                            playbackStats = playbackStats,
                            openUpNextSignal = openUpNextSignal,
                            localFallbackTracks = localLibrary.songs,
                            accentColor = accentColor,
                            secondaryAccent = secondaryAccent,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            voidBackground = palette.voidBackground,
                            glassBlurIntensity = settings.glassBlurIntensity,
                            onRequestVisualizerPermission = {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onPlayPause = viewModel::togglePlayPause,
                            onPrevious = viewModel::skipPrevious,
                            onNext = viewModel::skipNext,
                            onSeekTo = viewModel::seekTo,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onCycleRepeat = viewModel::cycleRepeat,
                            onTriggerSongRadio = viewModel::triggerSongRadio,
                            onSetVolume = viewModel::setMediaVolume,
                            onStepVolumeUp = viewModel::stepVolumeUp,
                            onStepVolumeDown = viewModel::stepVolumeDown,
                            onOpenSleepTimer = { showSleepTimerSheet = true },
                            onSliderHapticTick = viewModel::performSliderHapticTick,
                            onUpNextTabChanged = viewModel::setUpNextTabVisible,
                            onSkipToQueueItem = viewModel::skipToQueueItem,
                            onRetryRecommendations = viewModel::retryRecommendations,
                            onRecommendationClick = { rec ->
                                viewModel.launchSearchIntent(context, rec)
                            },
                            onPlayLocalFallbackTrack = { tracks, idx ->
                                viewModel.playLocalTracks(tracks, idx)
                            },
                            onResetStats = viewModel::resetPlaybackStats,
                            modifier = Modifier
                                .weight(0.92f)
                                .fillMaxHeight()
                        )
                    }
                } else {
                    // Portrait Responsive Dashboard
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LeftLyricsAndArtCard(
                            snapshot = snapshot,
                            artwork = trackArtwork,
                            lyricsState = lyricsState,
                            accentColor = accentColor,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            voidBackground = palette.voidBackground,
                            glassBlurIntensity = settings.glassBlurIntensity,
                            bassEnergy = visualizerFrame.bassEnergy,
                            isCompactHeight = true,
                            lyricsTextScale = settings.lyricsTextScale,
                            onSeekTo = viewModel::seekTo,
                            onNudgeOffset = viewModel::nudgeVocalOffset,
                            onRetryLyrics = viewModel::retryLyrics,
                            onExportLyrics = { filename -> exportLyricsLauncher.launch(filename) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.02f)
                        )

                        RightControlsAndQueueCard(
                            snapshot = snapshot,
                            livePositionMs = livePos,
                            recommendationsState = recommendationsState,
                            visualizerFrame = visualizerFrame,
                            mediaVolume = mediaVolume,
                            sleepTimerState = sleepTimerState,
                            playbackStats = playbackStats,
                            openUpNextSignal = openUpNextSignal,
                            localFallbackTracks = localLibrary.songs,
                            accentColor = accentColor,
                            secondaryAccent = secondaryAccent,
                            themeStyle = settings.themeStyle,
                            cardBackground = palette.cardBackground,
                            voidBackground = palette.voidBackground,
                            glassBlurIntensity = settings.glassBlurIntensity,
                            onRequestVisualizerPermission = {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onPlayPause = viewModel::togglePlayPause,
                            onPrevious = viewModel::skipPrevious,
                            onNext = viewModel::skipNext,
                            onSeekTo = viewModel::seekTo,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onCycleRepeat = viewModel::cycleRepeat,
                            onTriggerSongRadio = viewModel::triggerSongRadio,
                            onSetVolume = viewModel::setMediaVolume,
                            onStepVolumeUp = viewModel::stepVolumeUp,
                            onStepVolumeDown = viewModel::stepVolumeDown,
                            onOpenSleepTimer = { showSleepTimerSheet = true },
                            onSliderHapticTick = viewModel::performSliderHapticTick,
                            onUpNextTabChanged = viewModel::setUpNextTabVisible,
                            onSkipToQueueItem = viewModel::skipToQueueItem,
                            onRetryRecommendations = viewModel::retryRecommendations,
                            onRecommendationClick = { rec ->
                                viewModel.launchSearchIntent(context, rec)
                            },
                            onPlayLocalFallbackTrack = { tracks, idx ->
                                viewModel.playLocalTracks(tracks, idx)
                            },
                            onResetStats = viewModel::resetPlaybackStats,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.98f)
                        )
                    }
                }
            }
        }

        // Pocket / Dim Lock Mode Overlay
        DimLockOverlay(
            visible = isDimLockActive,
            snapshot = snapshot,
            lyricsState = lyricsState,
            livePositionMs = livePos,
            gestureFeedback = gestureFeedback,
            settings = settings,
            accentColor = accentColor,
            onUnlock = { viewModel.setDimLockActive(false) }
        )

        if (showEqualizerSheet) {
            EqualizerAndSettingsSheet(
                eqState = eqState,
                settings = settings,
                accentColor = accentColor,
                onDismiss = { showEqualizerSheet = false },
                onToggleEq = viewModel::setEqEnabled,
                onSelectPreset = viewModel::selectEqPreset,
                onBandLevelChange = viewModel::setEqBandLevel,
                onBassBoostChange = viewModel::setBassBoost
            )
        }

        if (showSettingsSheet) {
            SettingsSheet(
                settings = settings,
                fontUploadStatus = fontUploadStatus,
                accentColor = accentColor,
                onDismiss = { showSettingsSheet = false },
                onUpdateVolUpShort = viewModel::updateVolUpShortAction,
                onUpdateVolDownShort = viewModel::updateVolDownShortAction,
                onUpdateVolUpLong = viewModel::updateVolUpLongAction,
                onUpdateVolDownLong = viewModel::updateVolDownLongAction,
                onUpdateWristFlickAction = viewModel::updateWristFlickAction,
                onToggleWristFlick = viewModel::setWristFlickEnabled,
                onWristFlickSensitivity = viewModel::setWristFlickSensitivity,
                onUpdateFaceDownAction = viewModel::updateFaceDownAction,
                onToggleFaceDown = viewModel::setFaceDownEnabled,
                onFaceDownSensitivity = viewModel::setFaceDownSensitivity,
                onSelectThemeStyle = viewModel::setThemeStyle,
                onGlassBlurChange = viewModel::setGlassBlurIntensity,
                onSelectCustomAccentHex = { hex -> viewModel.setCustomAccentHex(hex) },
                onSelectCustomFontHex = viewModel::setCustomFontHex,
                onResetColorsToDefault = viewModel::resetColorsToDefault,
                onToggleUse24HourClock = viewModel::setUse24HourClock,
                onSelectDimLockTimeout = viewModel::setDimLockTimeoutSeconds,
                onUploadCustomFont = {
                    customFontPickerLauncher.launch(
                        arrayOf(
                            "font/ttf",
                            "font/otf",
                            "application/x-font-ttf",
                            "application/x-font-opentype",
                            "*/*"
                        )
                    )
                },
                onResetCustomFont = viewModel::resetCustomFont,
                onLyricsTextScaleChange = viewModel::setLyricsTextScale,
                onToggleHaptics = viewModel::setHapticsEnabled,
                onSelectHapticIntensity = viewModel::setHapticIntensity,
                onToggleHapticTransport = viewModel::setHapticOnTransport,
                onToggleHapticSliders = viewModel::setHapticOnSliders,
                onToggleHapticGestures = viewModel::setHapticOnGestures
            )
        }

        if (showSleepTimerSheet) {
            SleepTimerSheet(
                sleepTimerState = sleepTimerState,
                accentColor = accentColor,
                onDismiss = { showSleepTimerSheet = false },
                onStartTimer = viewModel::startSleepTimer,
                onStartEndOfTrack = viewModel::startEndOfTrackSleepTimer,
                onAddMinutes = viewModel::addSleepTimerMinutes,
                onCancelTimer = viewModel::cancelSleepTimer
            )
        }

        if (showLocalLibrarySheet) {
            LocalLibrarySheet(
                hasAudioPermission = hasAudioPermission,
                isScanning = isScanningLibrary,
                library = localLibrary,
                accentColor = accentColor,
                onRequestPermission = { audioPermissionLauncher.launch(audioPermission) },
                onRescan = viewModel::scanLocalLibrary,
                onPlayTracks = { tracks, startIdx ->
                    viewModel.playLocalTracks(tracks, startIdx)
                },
                onDismiss = { showLocalLibrarySheet = false }
            )
        }

        // Transient feedback pill banner for Song Radio / Sleep Timer actions
        AnimatedVisibility(
            visible = statusToast != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 18.dp, start = 20.dp, end = 20.dp)
        ) {
            statusToast?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.cardBackground.copy(alpha = 0.94f))
                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .testTag("status_toast_banner")
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelLarge,
                        color = fontColors.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun TopConsoleHeader(
    selectedSource: PlaybackSource,
    accentColor: Color,
    cardBackground: Color,
    isDimLockActive: Boolean,
    sleepTimerState: SleepTimerState,
    onSwitchSource: (PlaybackSource) -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSettings: () -> Unit,
    onActivateDimLock: () -> Unit
) {
    val fontColors = LocalAppFontColors.current
    val lockScale by animateFloatAsState(
        targetValue = if (isDimLockActive) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "lockScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Source Switcher Pill
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(cardBackground)
                .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
                .padding(4.dp)
        ) {
            val sources = listOf(
                PlaybackSource.EXTERNAL to stringResource(R.string.source_external),
                PlaybackSource.LOCAL to stringResource(R.string.source_local)
            )
            sources.forEach { (source, label) ->
                val active = selectedSource == source
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) accentColor else Color.Transparent)
                        .clickable { onSwitchSource(source) }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                        .testTag("source_pill_${source.name.lowercase()}")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) contrastOnColor(accentColor) else fontColors.secondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Right Action Buttons: Sleep Timer + Local Library + Equalizer + Settings Gear + Dim Lock
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(
                        if (sleepTimerState.isActive) accentColor.copy(alpha = 0.22f)
                        else cardBackground
                    )
                    .border(
                        1.dp,
                        if (sleepTimerState.isActive) accentColor.copy(alpha = 0.65f) else HairlineBorder,
                        CircleShape
                    )
                    .clickable(onClick = onOpenSleepTimer)
                    .padding(horizontal = if (sleepTimerState.isActive) 12.dp else 14.dp)
                    .testTag("top_sleep_timer_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_timer),
                    contentDescription = stringResource(R.string.cd_sleep_timer),
                    colorFilter = ColorFilter.tint(
                        if (sleepTimerState.isActive) accentColor else fontColors.primary
                    ),
                    modifier = Modifier.size(18.dp)
                )
                if (sleepTimerState.isActive) {
                    Text(
                        text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) {
                            "End"
                        } else {
                            formatCountdownMs(sleepTimerState.remainingMs)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cardBackground)
                    .border(1.dp, HairlineBorder, CircleShape)
                    .clickable(onClick = onOpenLibrary)
                    .testTag("open_library_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_folder),
                    contentDescription = stringResource(R.string.cd_local_library),
                    colorFilter = ColorFilter.tint(fontColors.primary),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cardBackground)
                    .border(1.dp, HairlineBorder, CircleShape)
                    .clickable(onClick = onOpenEqualizer)
                    .testTag("open_equalizer_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_equalizer),
                    contentDescription = stringResource(R.string.cd_equalizer_settings),
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cardBackground)
                    .border(1.dp, HairlineBorder, CircleShape)
                    .clickable(onClick = onOpenSettings)
                    .testTag("open_settings_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_settings),
                    contentDescription = stringResource(R.string.cd_app_settings),
                    colorFilter = ColorFilter.tint(fontColors.primary),
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .scale(lockScale)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.5.dp, accentColor.copy(alpha = 0.65f), CircleShape)
                    .clickable(onClick = onActivateDimLock)
                    .testTag("dim_lock_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_lock),
                    contentDescription = stringResource(R.string.cd_dim_lock),
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun IdleDashboardWithStats(
    isLandscapeSplit: Boolean,
    title: String,
    description: String,
    primaryActionLabel: String,
    secondaryActionLabel: String,
    accentColor: Color,
    secondaryAccent: Color,
    themeStyle: AppThemeStyle,
    cardBackground: Color,
    playbackStats: PlaybackStatsState,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onResetStats: () -> Unit,
    primaryTag: String
) {
    val effectiveCardBg = if (themeStyle == AppThemeStyle.AMOLED_PURE_BLACK) {
        Color.Black
    } else {
        cardBackground
    }

    if (isLandscapeSplit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            OnboardingStatusCard(
                title = title,
                description = description,
                primaryActionLabel = primaryActionLabel,
                secondaryActionLabel = secondaryActionLabel,
                accentColor = accentColor,
                cardBackground = effectiveCardBg,
                onPrimaryAction = onPrimaryAction,
                onSecondaryAction = onSecondaryAction,
                primaryTag = primaryTag,
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
            )

            Box(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(effectiveCardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                DashboardStatsPanel(
                    stats = playbackStats,
                    accentColor = accentColor,
                    secondaryAccent = secondaryAccent,
                    themeStyle = themeStyle,
                    onResetStats = onResetStats
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            OnboardingStatusCard(
                title = title,
                description = description,
                primaryActionLabel = primaryActionLabel,
                secondaryActionLabel = secondaryActionLabel,
                accentColor = accentColor,
                cardBackground = effectiveCardBg,
                onPrimaryAction = onPrimaryAction,
                onSecondaryAction = onSecondaryAction,
                primaryTag = primaryTag,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(effectiveCardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
                    .padding(16.dp)
            ) {
                DashboardStatsPanel(
                    stats = playbackStats,
                    accentColor = accentColor,
                    secondaryAccent = secondaryAccent,
                    themeStyle = themeStyle,
                    onResetStats = onResetStats
                )
            }
        }
    }
}

@Composable
private fun OnboardingStatusCard(
    title: String,
    description: String,
    primaryActionLabel: String,
    secondaryActionLabel: String,
    accentColor: Color,
    cardBackground: Color,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    primaryTag: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardBackground)
                .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
                .padding(if (compact) 18.dp else 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compact) 48.dp else 60.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_music_note),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                )
            }

            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = fontColors.primary,
                textAlign = TextAlign.Center
            )

            Text(
                text = description,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = fontColors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPrimaryAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = contrastOnColor(accentColor)
                    ),
                    modifier = Modifier.testTag(primaryTag)
                ) {
                    Text(primaryActionLabel, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel, color = fontColors.primary)
                }
            }
        }
    }
}
