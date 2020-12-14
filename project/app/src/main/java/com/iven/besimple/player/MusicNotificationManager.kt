package com.iven.besimple.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.R
import com.iven.besimple.extensions.toSpanned
import com.iven.besimple.helpers.ThemeHelper
import com.iven.besimple.ui.MainActivity

class MusicNotificationManager(private val playerService: PlayerService) {

    //notification manager/builder
    private val mNotificationManager = NotificationManagerCompat.from(playerService)
    private lateinit var mNotificationBuilder: NotificationCompat.Builder

    private val mNotificationActions
        @SuppressLint("RestrictedApi")
        get() = mNotificationBuilder.mActions

    private var mAlbumArt =
        BitmapFactory.decodeResource(playerService.resources, R.drawable.album_art)

    private fun playerAction(action: String): PendingIntent {

        val pauseIntent = Intent()
        pauseIntent.action = action

        return PendingIntent.getBroadcast(
            playerService,
            BeSimpleConstants.NOTIFICATION_INTENT_REQUEST_CODE,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun createNotification(): Notification {

        mNotificationBuilder =
            NotificationCompat.Builder(playerService, BeSimpleConstants.NOTIFICATION_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val openPlayerIntent = Intent(playerService, MainActivity::class.java)
        openPlayerIntent.flags =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentIntent = PendingIntent.getActivity(
            playerService, BeSimpleConstants.NOTIFICATION_INTENT_REQUEST_CODE,
            openPlayerIntent, 0
        )

        mNotificationBuilder
            .setShowWhen(false)
            .setStyle(
                MediaStyle()
                    .setShowActionsInCompactView(1, 2, 3)
                    .setMediaSession(playerService.getMediaSession().sessionToken)
            )
            .setContentIntent(contentIntent)
            .addAction(notificationAction(BeSimpleConstants.REPEAT_ACTION))
            .addAction(notificationAction(BeSimpleConstants.PREV_ACTION))
            .addAction(notificationAction(BeSimpleConstants.PLAY_PAUSE_ACTION))
            .addAction(notificationAction(BeSimpleConstants.NEXT_ACTION))
            .addAction(notificationAction(BeSimpleConstants.CLOSE_ACTION))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        updateNotificationContent()
        return mNotificationBuilder.build()
    }

    fun updateNotification() {
        mNotificationManager
            .notify(
                BeSimpleConstants.NOTIFICATION_ID,
                mNotificationBuilder.build()
            )
    }

    fun onUpdateDefaultAlbumArt(bitmapRes: Bitmap, updateNotification: Boolean) {
        mAlbumArt = bitmapRes
        if (updateNotification) {
            onHandleNotificationUpdate()
        }
    }

    fun onHandleNotificationUpdate() {
        if (::mNotificationBuilder.isInitialized) {
            updateNotificationContent()
            updateNotification()
        }
    }

    fun updateNotificationContent() {
        val mediaPlayerHolder = playerService.mediaPlayerHolder
        mediaPlayerHolder.currentSong?.let { song ->

            mNotificationBuilder.setContentText(
                playerService.getString(
                    R.string.artist_and_album,
                    song.artist,
                    song.album
                )
            )
                .setContentTitle(
                    playerService.getString(
                        R.string.song_title_notification,
                        song.title
                    ).toSpanned()
                )
                .setLargeIcon(mAlbumArt)
                .setColorized(true)
                .setSmallIcon(getNotificationSmallIcon(mediaPlayerHolder))
        }
    }

    private fun getNotificationSmallIcon(mediaPlayerHolder: MediaPlayerHolder) =
        when (mediaPlayerHolder.launchedBy) {
            BeSimpleConstants.FOLDER_VIEW -> R.drawable.ic_folder_music
            else -> R.drawable.ic_music_note
        }

    fun updatePlayPauseAction() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationActions[2] =
                notificationAction(BeSimpleConstants.PLAY_PAUSE_ACTION)
        }
    }

    fun updateRepeatIcon() {
        if (::mNotificationBuilder.isInitialized) {
            mNotificationActions[0] =
                notificationAction(BeSimpleConstants.REPEAT_ACTION)
            updateNotification()
        }
    }

    private fun notificationAction(action: String): NotificationCompat.Action {
        var icon =
            if (playerService.mediaPlayerHolder.state != BeSimpleConstants.PAUSED) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        when (action) {
            BeSimpleConstants.REPEAT_ACTION -> icon =
                ThemeHelper.getRepeatIcon(playerService.mediaPlayerHolder)
            BeSimpleConstants.PREV_ACTION -> icon = R.drawable.ic_skip_previous
            BeSimpleConstants.NEXT_ACTION -> icon = R.drawable.ic_skip_next
            BeSimpleConstants.CLOSE_ACTION -> icon = R.drawable.ic_close
        }
        return NotificationCompat.Action.Builder(icon, action, playerAction(action)).build()
    }

    @RequiresApi(26)
    private fun createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(BeSimpleConstants.NOTIFICATION_CHANNEL_ID) == null) {
            NotificationChannel(
                BeSimpleConstants.NOTIFICATION_CHANNEL_ID,
                playerService.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = playerService.getString(R.string.app_name)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                mNotificationManager.createNotificationChannel(this)
            }
        }
    }
}
