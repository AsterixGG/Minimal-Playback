package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.MainActivity
import com.example.R
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.util.formatCountdownMs

object PlaybackNotificationHelper {

    const val CHANNEL_ID = "minimal_playback_channel"
    const val NOTIFICATION_ID = 4041

    const val ACTION_PREVIOUS = "com.minimalplayback.ACTION_PREVIOUS"
    const val ACTION_PLAY_PAUSE = "com.minimalplayback.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.minimalplayback.ACTION_NEXT"
    const val ACTION_START_RADIO = "com.minimalplayback.ACTION_START_RADIO"
    const val ACTION_SLEEP_TIMER = "com.minimalplayback.ACTION_SLEEP_TIMER"
    const val ACTION_CANCEL_SLEEP_TIMER = "com.minimalplayback.ACTION_CANCEL_SLEEP_TIMER"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun buildNotification(
        context: Context,
        snapshot: PlaybackSnapshot,
        mediaSessionCompat: MediaSessionCompat,
        sleepTimer: SleepTimerState = PlaybackStateBus.sleepTimerState.value,
        cachedRoundedArt: Bitmap? = null
    ): Notification {
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPending = actionPendingIntent(context, ACTION_PREVIOUS, 101)
        val playPausePending = actionPendingIntent(context, ACTION_PLAY_PAUSE, 102)
        val nextPending = actionPendingIntent(context, ACTION_NEXT, 103)

        val title = snapshot.track.title.ifBlank { context.getString(R.string.app_name) }
        val artist = snapshot.track.artist.ifBlank {
            if (snapshot.source == PlaybackSource.LOCAL) {
                context.getString(R.string.source_local)
            } else {
                context.getString(R.string.source_external)
            }
        }

        val baseBadge = when {
            snapshot.source == PlaybackSource.LOCAL -> "Local Audio"
            snapshot.track.sourceAppLabel.isNotBlank() -> snapshot.track.sourceAppLabel
            else -> "MinimalPlayback"
        }

        val sleepBadgeText = when {
            !sleepTimer.isActive -> null
            sleepTimer.isFadingOut -> "Fading out…"
            sleepTimer.mode == SleepTimerMode.END_OF_TRACK -> "Sleep • End of track"
            else -> "Sleep • ${formatCountdownMs(sleepTimer.remainingMs)}"
        }
        val headerSubText = if (sleepBadgeText != null) "$baseBadge • $sleepBadgeText" else baseBadge

        val playPauseRes = if (snapshot.isPlaying) R.drawable.ic_vec_pause else R.drawable.ic_vec_play
        val playPauseTitle = if (snapshot.isPlaying) "Pause" else "Play"
        val largeIcon = cachedRoundedArt ?: snapshot.track.artworkBitmap?.let { roundCorners(it, 28f) }

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSessionCompat.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vec_music_note)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(headerSubText)
            .setLargeIcon(largeIcon)
            .setContentIntent(contentIntent)
            .setOngoing(snapshot.isPlaying || sleepTimer.isActive)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .addAction(R.drawable.ic_vec_previous, "Previous", prevPending)
            .addAction(playPauseRes, playPauseTitle, playPausePending)
            .addAction(R.drawable.ic_vec_next, "Next", nextPending)
            .setStyle(mediaStyle)
            .build()
    }

    fun roundCorners(source: Bitmap, radiusPx: Float): Bitmap? {
        return runCatching {
            val size = 192
            val soft = if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            } ?: return null

            val scaled = Bitmap.createScaledBitmap(soft, size, size, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, size, size)
            val rectF = RectF(rect)
            canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(scaled, rect, rect, paint)
            if (scaled != soft) scaled.recycle()
            if (soft != source) soft.recycle()
            output
        }.getOrNull()
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PlaybackActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
