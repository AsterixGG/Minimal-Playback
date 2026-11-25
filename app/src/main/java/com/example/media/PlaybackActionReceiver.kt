package com.example.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PlaybackStateBus.init(context)
        when (intent?.action) {
            PlaybackNotificationHelper.ACTION_PREVIOUS -> PlaybackStateBus.previous()
            PlaybackNotificationHelper.ACTION_PLAY_PAUSE -> PlaybackStateBus.playPause()
            PlaybackNotificationHelper.ACTION_NEXT -> PlaybackStateBus.next()
            PlaybackNotificationHelper.ACTION_START_RADIO -> PlaybackStateBus.requestSongRadio(context)
            PlaybackNotificationHelper.ACTION_SLEEP_TIMER -> PlaybackStateBus.cycleSleepTimerPreset()
            PlaybackNotificationHelper.ACTION_CANCEL_SLEEP_TIMER -> PlaybackStateBus.cancelSleepTimer()
        }
    }
}
