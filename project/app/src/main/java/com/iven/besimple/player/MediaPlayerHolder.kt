package com.iven.besimple.player

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
import android.support.v4.media.session.PlaybackStateCompat.Builder
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.R
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.extensions.toContentUri
import com.iven.besimple.extensions.toToast
import com.iven.besimple.fragments.EqFragment
import com.iven.besimple.helpers.VersioningHelper
import com.iven.besimple.models.Music
import com.iven.besimple.models.SavedEqualizerSettings
import com.iven.besimple.ui.MainActivity
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Exposes the functionality of the [MediaPlayer]
 */

// The volume we set the media player to when we lose audio focus, but are
// allowed to reduce the volume instead of stopping playback.
private const val VOLUME_DUCK = 0.2f

// The volume we set the media player when we have audio focus.
private const val VOLUME_NORMAL = 1.0f

// We don't have audio focus, and can't duck (play at a low volume)
private const val AUDIO_NO_FOCUS_NO_DUCK = 0

// We don't have focus, but can duck (play at a low volume)
private const val AUDIO_NO_FOCUS_CAN_DUCK = 1

// We have full audio focus
private const val AUDIO_FOCUSED = 2

// The headset connection states (0,1)
private const val HEADSET_DISCONNECTED = 0
private const val HEADSET_CONNECTED = 1

class MediaPlayerHolder(private val playerService: PlayerService) :
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener {

    private val mStateBuilder =
            Builder().apply {
                setActions(ACTION_SEEK_TO)
            }

    lateinit var mediaPlayerInterface: MediaPlayerInterface

    // Equalizer
    private lateinit var mEqualizer: Equalizer
    private lateinit var mBassBoost: BassBoost

    // Audio focus
    private var mAudioManager = playerService.getSystemService(AUDIO_SERVICE) as AudioManager
    private lateinit var mAudioFocusRequestOreo: AudioFocusRequest
    private val mHandler = Handler(Looper.getMainLooper())

    private val sFocusEnabled get() = beSimplePreferences.isFocusEnabled
    private var mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var sPlayOnFocusGain = false

    private val mOnAudioFocusChangeListener =
            AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> mCurrentAudioFocusState = AUDIO_FOCUSED
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
                        mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        // Lost audio focus, but will gain it back (shortly), so note whether
                        // playback should resume
                        mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                        sPlayOnFocusGain =
                                isMediaPlayer && state == BeSimpleConstants.PLAYING || state == BeSimpleConstants.RESUMED
                    }
                    AudioManager.AUDIOFOCUS_LOSS ->
                        // Lost audio focus, probably "permanently"
                        mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                }
                // Update the player state based on the change
                if (isMediaPlayer) {
                    configurePlayerState()
                }
            }

    // Media player
    private lateinit var mediaPlayer: MediaPlayer
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekBarPositionUpdateTask: Runnable? = null

    // First: current song, second: isFromQueue
    var currentSong: Music? = null
    var launchedBy = BeSimpleConstants.ARTIST_VIEW
    private var mPlayingAlbumSongs: List<Music>? = null

    val playerPosition
        get() = if (!isMediaPlayer) {
            beSimplePreferences.latestPlayedSong?.startFrom!!
        } else {
            mediaPlayer.currentPosition
        }

    // Media player state/booleans
    val isPlaying get() = isMediaPlayer && mediaPlayer.isPlaying
    val isMediaPlayer get() = ::mediaPlayer.isInitialized

    private var sNotificationForeground = false

    val isCurrentSong get() = currentSong != null
    var isRepeat1X = false
    var isLooping = false

    private val mCurrentAlbumSize get() = mPlayingAlbumSongs?.size!! - 1
    private val mCurrentSongIndex get() = mPlayingAlbumSongs?.indexOf(currentSong)!!
    private val mNextSongIndex get() = mCurrentSongIndex + 1
    private val mPrevSongIndex get() = mCurrentSongIndex - 1
    private val mNextSong: Music?
        get() = when {
            mNextSongIndex <= mCurrentAlbumSize -> mPlayingAlbumSongs?.get(mNextSongIndex)
            else -> mPlayingAlbumSongs?.get(0)
        }
    private val mPrevSong: Music?
        get() = when {
            mPrevSongIndex <= mCurrentAlbumSize && mPrevSongIndex != -1 -> mPlayingAlbumSongs?.get(
                    mPrevSongIndex
            )
            else -> mPlayingAlbumSongs?.get(mPlayingAlbumSongs?.lastIndex!!)
        }

    var isSongRestoredFromPrefs = false

    var state = BeSimpleConstants.PAUSED
    var isPlay = false

    // Notifications
    private lateinit var mNotificationActionsReceiver: NotificationReceiver
    private val mMusicNotificationManager: MusicNotificationManager by lazy {
        playerService.musicNotificationManager
    }

    private fun startForeground() {
        if (!sNotificationForeground) {
            playerService.startForeground(
                    BeSimpleConstants.NOTIFICATION_ID,
                    mMusicNotificationManager.createNotification()
            )
            sNotificationForeground = true
        } else {
            mMusicNotificationManager.apply {
                updateNotificationContent()
                updatePlayPauseAction()
                updateRepeatIcon()
                updateNotification()
            }
        }
    }

    fun registerActionsReceiver() {
        mNotificationActionsReceiver = NotificationReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(BeSimpleConstants.REPEAT_ACTION)
            addAction(BeSimpleConstants.PREV_ACTION)
            addAction(BeSimpleConstants.PLAY_PAUSE_ACTION)
            addAction(BeSimpleConstants.NEXT_ACTION)
            addAction(BeSimpleConstants.CLOSE_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        playerService.registerReceiver(mNotificationActionsReceiver, intentFilter)
    }

    private fun unregisterActionsReceiver() {
        try {
            playerService.unregisterReceiver(mNotificationActionsReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun createCustomEqualizer() {
        if (mediaPlayer.audioSessionId != AudioEffect.ERROR_BAD_VALUE && !::mEqualizer.isInitialized && !::mBassBoost.isInitialized) {
            mEqualizer = Equalizer(0, mediaPlayer.audioSessionId)
            mBassBoost = BassBoost(0, mediaPlayer.audioSessionId)
            setEqualizerEnabled(false)
            restoreCustomEqSettings()
        }
    }

    fun getEqualizer() = Pair(mEqualizer, mBassBoost)

    fun setEqualizerEnabled(isEnabled: Boolean) {
        mEqualizer.enabled = isEnabled
        mBassBoost.enabled = isEnabled
    }

    fun onSaveEqualizerSettings(selectedPreset: Int, bassBoost: Short) {
        beSimplePreferences.savedEqualizerSettings = SavedEqualizerSettings(mEqualizer.enabled, selectedPreset, mEqualizer.properties.bandLevels.toList(), bassBoost)
    }

    fun setCurrentSong(
            song: Music?,
            songs: List<Music>?,
            isFolderAlbum: String
    ) {
        currentSong = song
        mPlayingAlbumSongs = songs
        launchedBy = isFolderAlbum
    }

    private fun updateMediaSessionMetaData() {
        val mediaMediaPlayerCompat = MediaMetadataCompat.Builder().apply {
            putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    currentSong?.duration!!
            )
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentSong?.artist)
            putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, currentSong?.artist)
            putString(MediaMetadataCompat.METADATA_KEY_COMPOSER, currentSong?.artist)
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSong?.title)
            putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentSong?.title)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, currentSong?.album)
            putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, currentSong?.album)
            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentSong?.album)
            BitmapFactory.decodeResource(playerService.resources, R.drawable.ic_music_note)
                    ?.let { bmp ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bmp)
                    }
        }
        playerService.getMediaSession().setMetadata(mediaMediaPlayerCompat.build())
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {

        playerService.acquireWakeLock()

        mediaPlayerInterface.onStateChanged()
        mediaPlayerInterface.onPlaybackCompleted()

        when {
            isRepeat1X or isLooping -> if (isMediaPlayer) {
                repeatSong()
            }
            else -> {
                skip(true)
            }
        }
    }

    fun onRestartSeekBarCallback() {
        if (mExecutor == null) {
            startUpdatingCallbackWithPosition()
        }
    }

    fun onPauseSeekBarCallback() {
        stopUpdatingCallbackWithPosition()
    }

    fun onUpdateDefaultAlbumArt(bitmapRes: Bitmap) {
        mMusicNotificationManager.onUpdateDefaultAlbumArt(bitmapRes, isPlaying)
    }

    fun onHandleNotificationUpdate() {
        mMusicNotificationManager.onHandleNotificationUpdate()
    }

    fun tryToGetAudioFocus() {
        mCurrentAudioFocusState = when (getAudioFocusResult()) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> AUDIO_FOCUSED
            else -> AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    @Suppress("DEPRECATION")
    private fun getAudioFocusResult() = when {
        VersioningHelper.isOreoMR1() -> {
            mAudioFocusRequestOreo =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                        })
                        setAcceptsDelayedFocusGain(true)
                        setOnAudioFocusChangeListener(mOnAudioFocusChangeListener, mHandler)
                        build()
                    }
            mAudioManager.requestAudioFocus(mAudioFocusRequestOreo)
        }
        else -> mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
        )
    }

    @Suppress("DEPRECATION")
    fun giveUpAudioFocus() {
        when {
            VersioningHelper.isOreo() -> if (::mAudioFocusRequestOreo.isInitialized) {
                mAudioManager.abandonAudioFocusRequest(
                        mAudioFocusRequestOreo
                )
            }
            else -> mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener)
        }
        mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    }

    private fun updatePlaybackStatus(updateUI: Boolean) {
        playerService.getMediaSession().setPlaybackState(
                mStateBuilder.setState(
                        if (state == BeSimpleConstants.RESUMED) {
                            BeSimpleConstants.PLAYING
                        } else state,
                        mediaPlayer.currentPosition.toLong(),
                        1F
                ).build()
        )
        if (updateUI) {
            mediaPlayerInterface.onStateChanged()
        }
    }

    fun resumeMediaPlayer() {
        if (!isPlaying) {
            if (isMediaPlayer) {
                if (sFocusEnabled) {
                    tryToGetAudioFocus()
                }
                mediaPlayer.start()
            }
            state = if (isSongRestoredFromPrefs) {
                isSongRestoredFromPrefs = false
                BeSimpleConstants.PLAYING
            } else {
                BeSimpleConstants.RESUMED
            }

            updatePlaybackStatus(true)

            startForeground()

            if (!isPlay) {
                isPlay = true
            }
        }
    }

    fun pauseMediaPlayer() {
        mediaPlayer.pause()
        playerService.stopForeground(false)
        sNotificationForeground = false
        state = BeSimpleConstants.PAUSED
        updatePlaybackStatus(true)
        mMusicNotificationManager.apply {
            updatePlayPauseAction()
            updateNotification()
        }
        mediaPlayerInterface.onFocusLoss()
    }

    fun repeatSong() {
        isRepeat1X = false
        mediaPlayer.setOnSeekCompleteListener { mp ->
            mp.setOnSeekCompleteListener(null)
            play()
        }
        mediaPlayer.seekTo(0)
    }

    private fun getSkipSong(isNext: Boolean): Music? {
        if (isNext) {
            if (mNextSong != null) {
                return mNextSong
            }
        } else {
            if (mPrevSong != null) {
                return mPrevSong
            }
        }
        return null
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private fun startUpdatingCallbackWithPosition() {

        if (mSeekBarPositionUpdateTask == null) {
            mSeekBarPositionUpdateTask =
                    Runnable { updateProgressCallbackTask() }
        }

        mExecutor = Executors.newSingleThreadScheduledExecutor()
        mExecutor?.scheduleAtFixedRate(
                mSeekBarPositionUpdateTask!!,
                0,
                1000,
                TimeUnit.MILLISECONDS
        )
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private fun stopUpdatingCallbackWithPosition() {
        mExecutor?.shutdownNow()
        mExecutor = null
        mSeekBarPositionUpdateTask = null
    }

    private fun updateProgressCallbackTask() {
        if (isPlaying) {
            val currentPosition = mediaPlayer.currentPosition
            mediaPlayerInterface.onPositionChanged(currentPosition)
        }
    }

    fun instantReset() {
        if (isMediaPlayer && !isSongRestoredFromPrefs) {
            when {
                mediaPlayer.currentPosition < 5000 -> skip(false)
                else -> repeatSong()
            }
        } else {
            skip(false)
        }
    }

    /**
     * Once the [MediaPlayer] is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the [MainActivity] the [MediaPlayer] is
     * released. Then in the onStart() of the [MainActivity] a new [MediaPlayer]
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     */
    fun initMediaPlayer(song: Music?) {

        try {
            if (isMediaPlayer) {
                mediaPlayer.reset()
            } else {
                mediaPlayer = MediaPlayer().apply {

                    EqualizerUtils.openAudioEffectSession(
                            playerService.applicationContext,
                            audioSessionId
                    )

                    setOnPreparedListener(this@MediaPlayerHolder)
                    setOnCompletionListener(this@MediaPlayerHolder)
                    setOnErrorListener(this@MediaPlayerHolder)
                    setWakeMode(playerService, PowerManager.PARTIAL_WAKE_LOCK)
                    setAudioAttributes(
                            AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                    )
                }

                if (sFocusEnabled && isPlay) {
                    tryToGetAudioFocus()
                }
            }

            song?.id?.toContentUri()?.let { uri ->
                mediaPlayer.setDataSource(playerService, uri)
            }
            mediaPlayer.prepare()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            mediaPlayer.release()
            initMediaPlayer(currentSong)
        }
        return false
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {

        if (isRepeat1X) {
            isRepeat1X = false
        }

        if (isSongRestoredFromPrefs) {
            mediaPlayer.seekTo(beSimplePreferences.latestPlayedSong?.startFrom!!)
        }

        updateMediaSessionMetaData()

        if (mExecutor == null) {
            startUpdatingCallbackWithPosition()
        }

        if (isPlay) {
            play()
        }

        if (!EqualizerUtils.hasEqualizer(playerService)) {
            // set equalizer the first time is instantiated
            createCustomEqualizer()
        }
    }

    private fun play() {
        mediaPlayer.start()
        state = BeSimpleConstants.PLAYING
        updatePlaybackStatus(true)
        startForeground()
    }

    private fun restoreCustomEqSettings() {
        mediaPlayer.apply {
            val savedEqualizerSettings = beSimplePreferences.savedEqualizerSettings

            savedEqualizerSettings?.let { eqSettings ->

                setEqualizerEnabled(eqSettings.enabled)

                mEqualizer.usePreset(eqSettings.preset.toShort())

                val bandSettings = eqSettings.bandsSettings

                bandSettings?.iterator()?.withIndex()?.forEach {
                    mEqualizer.setBandLevel(it.index.toShort(), it.value.toInt().toShort())
                }

                mBassBoost.setStrength(eqSettings.bassBoost)
            }
        }
    }

    fun openEqualizer(activity: Activity) {
        EqualizerUtils.openEqualizer(activity, mediaPlayer)
    }

    fun openEqualizerCustom() = EqFragment.newInstance()

    fun release() {
        if (isMediaPlayer) {
            EqualizerUtils.closeAudioEffectSession(
                    playerService,
                    mediaPlayer.audioSessionId
            )
            releaseCustomEqualizer()
            mediaPlayer.release()
            if (sFocusEnabled) {
                giveUpAudioFocus()
            }
            stopUpdatingCallbackWithPosition()
        }
        unregisterActionsReceiver()
    }

    private fun releaseCustomEqualizer() {
        if (::mEqualizer.isInitialized) {
            mEqualizer.release()
            mBassBoost.release()
        }
    }

    fun resumeOrPause() {
        if (isPlaying) {
            pauseMediaPlayer()
        } else {
            resumeMediaPlayer()
        }
    }

    private fun getRepeatMode() {
        var toastMessage = R.string.repeat_enabled
        when {
            isRepeat1X -> {
                isRepeat1X = false
                isLooping = true
                toastMessage = R.string.repeat_loop_enabled
            }
            isLooping -> {
                isLooping = false
                toastMessage = R.string.repeat_disabled
            }
            else -> isRepeat1X = true
        }
        playerService.getString(toastMessage)
                .toToast(playerService)
    }

    fun repeat(updatePlaybackStatus: Boolean) {
        getRepeatMode()
        if (updatePlaybackStatus) {
            updatePlaybackStatus(true)
        }
        if (isPlaying) {
            mMusicNotificationManager.updateRepeatIcon()
        }
    }

    fun skip(isNext: Boolean) {
        currentSong = getSkipSong(isNext)
        initMediaPlayer(currentSong)
    }

    fun seekTo(position: Int, updatePlaybackStatus: Boolean, restoreProgressCallBack: Boolean) {
        if (isMediaPlayer) {
            mediaPlayer.setOnSeekCompleteListener { mp ->
                mp.setOnSeekCompleteListener(null)
                if (restoreProgressCallBack) {
                    startUpdatingCallbackWithPosition()
                }
                if (updatePlaybackStatus) {
                    updatePlaybackStatus(!restoreProgressCallBack)
                }
            }
            mediaPlayer.seekTo(position)
        }
    }

    /**
     * Reconfigures the player according to audio focus settings and starts/restarts it. This method
     * starts/restarts the MediaPlayer instance respecting the current audio focus state. So if we
     * have focus, it will play normally; if we don't have focus, it will either leave the player
     * paused or set it to a low volume, depending on what is permitted by the current focus
     * settings.
     */
    private fun configurePlayerState() {

        if (isMediaPlayer) {
            when (mCurrentAudioFocusState) {
                AUDIO_NO_FOCUS_NO_DUCK -> pauseMediaPlayer()
                else -> {
                    when (mCurrentAudioFocusState) {
                        AUDIO_NO_FOCUS_CAN_DUCK -> mediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK)
                        else -> mediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)
                    }
                    // If we were playing when we lost focus, we need to resume playing.
                    if (sPlayOnFocusGain) {
                        resumeMediaPlayer()
                        sPlayOnFocusGain = false
                    }
                }
            }
        }
    }

    fun stopPlaybackService(stopPlayback: Boolean) {
        if (playerService.isRunning && isMediaPlayer && stopPlayback) {
            playerService.stopForeground(true)
            playerService.stopSelf()
        }
        mediaPlayerInterface.onClose()
    }

    private inner class NotificationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val action = intent.action

            if (action != null) {
                when (action) {
                    BeSimpleConstants.PREV_ACTION -> instantReset()
                    BeSimpleConstants.PLAY_PAUSE_ACTION -> resumeOrPause()
                    BeSimpleConstants.NEXT_ACTION -> skip(true)
                    BeSimpleConstants.REPEAT_ACTION -> {
                        repeat(true)
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
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
        }
    }
}
