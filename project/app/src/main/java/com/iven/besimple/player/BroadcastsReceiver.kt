package com.iven.besimple.player

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.beSimplePreferences

// The headset connection states (0,1)
private const val HEADSET_DISCONNECTED = 0
private const val HEADSET_CONNECTED = 1

class NotificationReceiver(
    private val playerService: PlayerService,
    private val mediaPlayerHolder: MediaPlayerHolder
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            val action = intent?.action
            if (action != null) {
                mediaPlayerHolder.run {
                    when (action) {
                        BeSimpleConstants.PREV_ACTION -> instantReset()
                        BeSimpleConstants.PLAY_PAUSE_ACTION -> resumeOrPause()
                        BeSimpleConstants.NEXT_ACTION -> skip(true)
                        BeSimpleConstants.REPEAT_ACTION -> {
                            mediaPlayerHolder.repeat(true)
                            mediaPlayerInterface.onUpdateRepeatStatus()
                        }
                        BeSimpleConstants.CLOSE_ACTION -> if (playerService.isRunning && isMediaPlayer) {
                            stopPlaybackService(
                                stopPlayback = true
                            )
                        }

                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                            pauseMediaPlayer()
                        }
                        BluetoothDevice.ACTION_ACL_CONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                            resumeMediaPlayer()
                        }

                        AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->
                            when (intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)) {
                                AudioManager.SCO_AUDIO_STATE_CONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                                    resumeMediaPlayer()
                                }
                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                                    pauseMediaPlayer()
                                }
                            }

                        Intent.ACTION_HEADSET_PLUG -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                            when (intent.getIntExtra("state", -1)) {
                                // 0 means disconnected
                                HEADSET_DISCONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                                    pauseMediaPlayer()
                                }
                                // 1 means connected
                                HEADSET_CONNECTED -> if (isCurrentSong && beSimplePreferences.isHeadsetPlugEnabled) {
                                    resumeMediaPlayer()
                                }
                            }
                        }
                        AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (isPlaying && beSimplePreferences.isHeadsetPlugEnabled) {
                            pauseMediaPlayer()
                        }
                    }
                }
            }
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
