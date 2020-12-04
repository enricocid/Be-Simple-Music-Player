package com.iven.besimple.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.IBinder
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.slider.Slider
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.MusicViewModel
import com.iven.besimple.R
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.databinding.MainActivityBinding
import com.iven.besimple.databinding.PlayerControlsPanelBinding
import com.iven.besimple.extensions.*
import com.iven.besimple.fragments.*
import com.iven.besimple.helpers.*
import com.iven.besimple.models.Music
import com.iven.besimple.player.EqualizerUtils
import com.iven.besimple.player.MediaPlayerHolder
import com.iven.besimple.player.MediaPlayerInterface
import com.iven.besimple.player.PlayerService
import de.halfbit.edgetoedge.Edge
import de.halfbit.edgetoedge.edgeToEdge


@Suppress("UNUSED_PARAMETER")
class MainActivity : AppCompatActivity(), UIControlInterface {

    // View binding classes
    private lateinit var mMainActivityBinding: MainActivityBinding
    private lateinit var mPlayerControlsPanelBinding: PlayerControlsPanelBinding

    // View model
    private val mMusicViewModel: MusicViewModel by viewModels()

    // fragments buttons
    private val mFragmentButtons: Array<ImageButton?> = arrayOfNulls(3)

    // Fragments
    private var mArtistsFragment: MusicContainersListFragment? = null
    private var mFoldersFragment: MusicContainersListFragment? = null
    private var mSettingsFragment: SettingsFragment? = null
    private lateinit var mDetailsFragment: DetailsFragment
    private lateinit var mEqualizerFragment: EqFragment

    // Booleans
    private val sDetailsFragmentExpanded get() = supportFragmentManager.isFragment(BeSimpleConstants.DETAILS_FRAGMENT_TAG)
    private val sErrorFragmentExpanded get() = supportFragmentManager.isFragment(BeSimpleConstants.ERROR_FRAGMENT_TAG)
    private val sEqFragmentExpanded get() = supportFragmentManager.isFragment(BeSimpleConstants.EQ_FRAGMENT_TAG)

    private var sCloseDetailsFragment = true

    private var sRevealAnimationRunning = false

    private var sAppearanceChanged = false

    // The player
    private lateinit var mMediaPlayerHolder: MediaPlayerHolder
    private val isMediaPlayerHolder get() = ::mMediaPlayerHolder.isInitialized

    // Our PlayerService shit
    private lateinit var mPlayerService: PlayerService
    private var sBound = false
    private lateinit var mBindingIntent: Intent

    private fun checkIsPlayer(): Boolean {
        if (!isMediaPlayerHolder && !mMediaPlayerHolder.isMediaPlayer && !mMediaPlayerHolder.isSongRestoredFromPrefs) {
            getString(
                R.string.error_bad_id
            ).toToast(
                this@MainActivity
            )
            return false
        }
        return true
    }

    // Defines callbacks for service binding, passed to bindService()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
            val binder = service as PlayerService.LocalBinder
            mPlayerService = binder.getService()
            sBound = true
            mMediaPlayerHolder = mPlayerService.mediaPlayerHolder
            mMediaPlayerHolder.mediaPlayerInterface = mMediaPlayerInterface

            mMusicViewModel.deviceMusic.observe(this@MainActivity, { returnedMusic ->
                finishSetup(returnedMusic)
            })
            mMusicViewModel.getDeviceMusic()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            sBound = false
        }
    }

    private fun doBindService() {
        mMainActivityBinding.loadingProgressBar.handleViewVisibility(true)
        mBindingIntent = Intent(this, PlayerService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onBackPressed() {
        when {
            sDetailsFragmentExpanded and !sEqFragmentExpanded -> closeDetailsFragment()
            !sDetailsFragmentExpanded and sEqFragmentExpanded -> closeEqualizerFragment()
            sEqFragmentExpanded and sDetailsFragmentExpanded -> if (sCloseDetailsFragment) {
                closeDetailsFragment()
            } else {
                closeEqualizerFragment()
            }
            sErrorFragmentExpanded -> finishAndRemoveTask()
            else -> if (mMainActivityBinding.viewPager2.currentItem != 0) {
                mMainActivityBinding.viewPager2.currentItem =
                    0
            } else {
                if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) {
                    DialogHelper.stopPlaybackDialog(
                        this,
                        mMediaPlayerHolder
                    )
                } else {
                    onCloseActivity()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mMusicViewModel.cancel()
        if (sBound) {
            unbindService(connection)
        }
        if (isMediaPlayerHolder && !mMediaPlayerHolder.isPlaying && ::mPlayerService.isInitialized && mPlayerService.isRunning) {
            mPlayerService.stopForeground(true)
            stopService(mBindingIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
            mMediaPlayerHolder.onRestartSeekBarCallback()
        }
    }

    // Pause SeekBar callback
    override fun onPause() {
        super.onPause()
        if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
            saveSongToPref()
            mMediaPlayerHolder.run {
                onPauseSeekBarCallback()
                if (!isPlaying) {
                    mMediaPlayerHolder.giveUpAudioFocus()
                }
            }
        }
    }

    // Manage request permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            BeSimpleConstants.PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission was granted, yay! Do bind service
                    doBindService()
                } else {
                    // Permission denied, boo! Error!
                    notifyError(BeSimpleConstants.TAG_NO_PERMISSION)
                }
            }
        }
    }

    override fun onDenyPermission() {
        notifyError(BeSimpleConstants.TAG_NO_PERMISSION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mMainActivityBinding = MainActivityBinding.inflate(layoutInflater)
        mPlayerControlsPanelBinding = PlayerControlsPanelBinding.bind(mMainActivityBinding.root)

        setContentView(mMainActivityBinding.root)

        if (VersioningHelper.isOreoMR1()) {
            window.run {
                val sbColor = R.color.windowBackground.decodeColor(this@MainActivity)
                statusBarColor = sbColor
                navigationBarColor = sbColor
                ThemeHelper.handleLightSystemBars(resources.configuration, this)
            }
            edgeToEdge {
                mMainActivityBinding.root.fit { Edge.Top + Edge.Bottom }
            }
        }

        initMediaButtons()

        if (PermissionsHelper.hasToAskForReadStoragePermission(this)) {
            DialogHelper.manageAskForReadStoragePermission(
                activity = this, uiControlInterface = this
            )
        } else {
            doBindService()
        }
    }

    private fun notifyError(errorType: String) {
        mPlayerControlsPanelBinding.playerView.handleViewVisibility(false)
        mMainActivityBinding.loadingProgressBar.handleViewVisibility(false)
        mMainActivityBinding.viewPager2.handleViewVisibility(false)
        supportFragmentManager.addFragment(
            ErrorFragment.newInstance(errorType),
            BeSimpleConstants.ERROR_FRAGMENT_TAG
        )
    }

    private fun finishSetup(music: MutableList<Music>?) {

        if (!music.isNullOrEmpty()) {

            mMainActivityBinding.loadingProgressBar.handleViewVisibility(false)

            initViewPager()

            synchronized(restorePlayerStatus()) {
                mPlayerControlsPanelBinding.playerView.animate().apply {
                    duration = 500
                    alpha(1.0F)
                }
            }

        } else {
            notifyError(BeSimpleConstants.TAG_NO_MUSIC)
        }
    }

    private fun initViewPager() {

        mFragmentButtons[0] = mPlayerControlsPanelBinding.artists
        mFragmentButtons[1] = mPlayerControlsPanelBinding.folders
        mFragmentButtons[2] = mPlayerControlsPanelBinding.settings

        val pagerAdapter = ScreenSlidePagerAdapter(this)

        mMainActivityBinding.viewPager2.run {
            offscreenPageLimit = 2
            adapter = pagerAdapter

            handleFragmentsButton(currentItem)

            mFragmentButtons.iterator().withIndex().forEach { fb ->
                fb.value?.setOnClickListener {
                    synchronized(closeFragments()) {
                        currentItem = fb.index
                    }
                }
            }

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    handleFragmentsButton(position)
                }
            })
        }

        initFragments()
    }

    private fun initFragments() {
        if (mArtistsFragment == null) {
            mArtistsFragment =
                MusicContainersListFragment.newInstance(BeSimpleConstants.ARTIST_VIEW)
        }
        if (mFoldersFragment == null) {
            mFoldersFragment =
                MusicContainersListFragment.newInstance(BeSimpleConstants.FOLDER_VIEW)
        }
        if (mSettingsFragment == null) {
            mSettingsFragment =
                SettingsFragment.newInstance()
        }
    }

    private fun getFragmentForIndex(index: Int) = when (index) {
        0 -> mArtistsFragment
        1 -> mFoldersFragment
        else -> mSettingsFragment
    }

    private fun handleFragmentsButton(index: Int) {

        val shapeAppearanceModel = ShapeAppearanceModel()
            .toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, resources.getDimension(R.dimen.md_corner_radius))
            .build()
        val buttonsBackground = MaterialShapeDrawable(shapeAppearanceModel).apply {
            fillColor = ColorStateList.valueOf(R.color.blue.decodeColor(this@MainActivity))
        }

        mFragmentButtons.iterator().withIndex().forEach {
            val isButtonSelected = index == it.index
            val color = if (isButtonSelected) {
                Pair(R.color.windowBackground.decodeColor(this), buttonsBackground)
            } else {
                Pair(R.color.widgetsColor.decodeColor(this), null)
            }
            mFragmentButtons[it.index]?.let { fragmentButton ->
                ThemeHelper.updateIconTint(fragmentButton, color.first)
                fragmentButton.background = color.second
            }
        }

    }

    private fun closeFragments() {
        supportFragmentManager.run {
            goBackFromFragment(sEqFragmentExpanded)
            goBackFromFragment(sDetailsFragmentExpanded)
        }
    }

    private fun openDetailsFragment(
        selectedArtistOrFolder: String?,
        launchedBy: String
    ) {
        if (!sDetailsFragmentExpanded) {
            if (sEqFragmentExpanded) {
                mEqualizerFragment.setDisableCircleReveal()
            }
            mDetailsFragment =
                DetailsFragment.newInstance(
                    selectedArtistOrFolder,
                    launchedBy,
                    MusicOrgHelper.getPlayingAlbumPosition(
                        selectedArtistOrFolder,
                        mMediaPlayerHolder,
                        mMusicViewModel.deviceAlbumsByArtist
                    )
                )
            sCloseDetailsFragment = true
            supportFragmentManager.addFragment(
                mDetailsFragment,
                BeSimpleConstants.DETAILS_FRAGMENT_TAG
            )
        }
    }

    private fun closeDetailsFragment() {
        if (!sRevealAnimationRunning) {
            mDetailsFragment.onHandleBackPressed().apply {
                sRevealAnimationRunning = true
                doOnEnd {
                    synchronized(super.onBackPressed()) {
                        sRevealAnimationRunning = false
                    }
                }
            }
        }
    }

    private fun initMediaButtons() {
        mPlayerControlsPanelBinding.run {
            songProgress.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {

                var isUserSeeking = false

                override fun onStartTrackingTouch(slider: Slider) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    if (isUserSeeking) {
                        mMediaPlayerHolder.onPauseSeekBarCallback()
                        isUserSeeking = false
                        mMediaPlayerHolder.seekTo(
                            slider.value.toInt(),
                            updatePlaybackStatus = mMediaPlayerHolder.isPlaying,
                            restoreProgressCallBack = !isUserSeeking
                        )
                    }
                }
            })

            songProgress.setLabelFormatter {
                return@setLabelFormatter it.toLong()
                    .toFormattedDuration(isAlbum = false, isSeekBar = true)
            }

            playingSongContainer.setOnClickListener { openPlayingArtistAlbum() }

            skipPrev.setOnClickListener { skip(false) }
            playPauseButton.setOnClickListener { resumeOrPause() }
            skipNext.setOnClickListener { skip(true) }
            repeat.setOnClickListener { setRepeat() }
            equalizer.setOnClickListener { openEqualizer() }
        }
    }

    private fun saveSongToPref() {
        if (::mMediaPlayerHolder.isInitialized && !mMediaPlayerHolder.isPlaying || mMediaPlayerHolder.state == BeSimpleConstants.PAUSED) {
            mMediaPlayerHolder.run {
                MusicOrgHelper.saveLatestSong(currentSong, this, launchedBy)
            }
        }
    }

    override fun onThemeChanged() {
        sAppearanceChanged = true
        synchronized(saveSongToPref()) {
            AppCompatDelegate.setDefaultNightMode(
                ThemeHelper.getDefaultNightMode(
                    this
                )
            )
        }
    }

    override fun onAppearanceChanged(isAccentChanged: Boolean, restoreSettings: Boolean) {
        sAppearanceChanged = true
        synchronized(saveSongToPref()) {
            ThemeHelper.applyChanges(this)
        }
    }

    private fun updatePlayingStatus() {
        val isPlaying = mMediaPlayerHolder.state != BeSimpleConstants.PAUSED
        val drawable =
            if (isPlaying) {
                R.drawable.ic_pause
            } else {
                R.drawable.ic_play
            }
        mPlayerControlsPanelBinding.playPauseButton.setImageResource(
            drawable
        )
    }

    private fun restorePlayerStatus() {
        if (isMediaPlayerHolder) {
            // If we are playing and the activity was restarted
            // update the controls panel
            mMediaPlayerHolder.run {

                if (isMediaPlayer && mMediaPlayerHolder.isPlaying) {

                    onRestartSeekBarCallback()
                    updatePlayingInfo(true)

                } else {

                    isSongRestoredFromPrefs = beSimplePreferences.latestPlayedSong != null

                    val song =
                        if (isSongRestoredFromPrefs) {
                            beSimplePreferences.latestPlayedSong
                        } else {
                            mMusicViewModel.randomMusic
                        }

                    val songs = MusicOrgHelper.getAlbumSongs(
                        song?.artist,
                        song?.album,
                        mMusicViewModel.deviceAlbumsByArtist
                    )

                    if (!songs.isNullOrEmpty()) {

                        isPlay = false

                        startPlayback(
                            song,
                            songs,
                            getLatestSongLaunchedBy()
                        )

                        updatePlayingInfo(false)

                        mPlayerControlsPanelBinding.run {
                            val startFrom = if (isSongRestoredFromPrefs) {
                                beSimplePreferences.latestPlayedSong?.startFrom!!
                            } else {
                                0
                            }
                            songProgress.value = startFrom.toFloat()
                        }

                    } else {
                        notifyError(BeSimpleConstants.TAG_SD_NOT_READY)
                    }
                }
                onUpdateDefaultAlbumArt(
                    BitmapFactory.decodeResource(
                        resources,
                        R.drawable.album_art
                    )
                )
            }
        }
    }

    private fun getLatestSongLaunchedBy() = beSimplePreferences.latestPlayedSong?.launchedBy
        ?: BeSimpleConstants.ARTIST_VIEW

    // method to update info on controls panel
    private fun updatePlayingInfo(restore: Boolean) {

        val selectedSong = mMediaPlayerHolder.currentSong

        mPlayerControlsPanelBinding.songProgress.value = 0F
        mPlayerControlsPanelBinding.songProgress.valueTo = selectedSong?.duration!!.toFloat()

        mPlayerControlsPanelBinding.songDuration.text =
            selectedSong.duration.toFormattedDuration(false, isSeekBar = true)

        mPlayerControlsPanelBinding.playingSong.text = selectedSong.title

        mPlayerControlsPanelBinding.playingArtist.text =
            getString(
                R.string.artist_and_album,
                selectedSong.artist,
                selectedSong.album
            )

        updateRepeatStatus(false)

        if (restore) {

            updatePlayingStatus()

            if (::mPlayerService.isInitialized) {
                //stop foreground if coming from pause state
                mPlayerService.run {
                    if (isRestoredFromPause) {
                        stopForeground(false)
                        musicNotificationManager.updateNotification()
                        isRestoredFromPause = false
                    }
                }
            }
        }
    }

    private fun updateRepeatStatus(onPlaybackCompletion: Boolean) {

        val resolveIconsColor = R.color.widgetsColor.decodeColor(this)
        mPlayerControlsPanelBinding.run {

            repeat.setImageResource(
                ThemeHelper.getRepeatIcon(
                    mMediaPlayerHolder
                )
            )

            when {
                onPlaybackCompletion -> ThemeHelper.updateIconTint(
                    repeat,
                    resolveIconsColor
                )
                mMediaPlayerHolder.isRepeat1X or mMediaPlayerHolder.isLooping -> {
                    ThemeHelper.updateIconTint(
                        repeat,
                        R.color.blue.decodeColor(this@MainActivity)
                    )
                }
                else -> ThemeHelper.updateIconTint(
                    repeat,
                    resolveIconsColor
                )
            }
        }
    }

    private fun getSongSource(selectedSong: Music?, launchedBy: String): String? {
        return when (launchedBy) {
            BeSimpleConstants.ARTIST_VIEW -> selectedSong?.artist
            else -> selectedSong?.relativePath
        }
    }

    private fun openPlayingArtistAlbum() {
        if (checkIsPlayer() && isMediaPlayerHolder && mMediaPlayerHolder.isCurrentSong) {
            if (!sDetailsFragmentExpanded || sDetailsFragmentExpanded and !sEqFragmentExpanded) {
                val isPlayingFromFolder = mMediaPlayerHolder.launchedBy
                val selectedSong = mMediaPlayerHolder.currentSong
                val selectedArtistOrFolder = getSongSource(selectedSong, isPlayingFromFolder)
                if (sDetailsFragmentExpanded) {
                    if (mDetailsFragment.hasToUpdate(selectedArtistOrFolder)) {
                        synchronized(super.onBackPressed()) {
                            openDetailsFragment(
                                selectedArtistOrFolder,
                                mMediaPlayerHolder.launchedBy
                            )
                        }
                    } else {
                        mDetailsFragment.tryToSnapToAlbumPosition(
                            MusicOrgHelper.getPlayingAlbumPosition(
                                selectedArtistOrFolder,
                                mMediaPlayerHolder,
                                mMusicViewModel.deviceAlbumsByArtist
                            )
                        )
                    }
                } else {
                    openDetailsFragment(selectedArtistOrFolder, mMediaPlayerHolder.launchedBy)
                }
            }
        }
    }

    override fun onCloseActivity() {
        if (isMediaPlayerHolder && mMediaPlayerHolder.isPlaying) {
            DialogHelper.stopPlaybackDialog(
                this,
                mMediaPlayerHolder
            )
        } else {
            finishAndRemoveTask()
        }
    }

    override fun onHandleFocusPref() {
        if (isMediaPlayerHolder) {
            if (isMediaPlayerHolder && mMediaPlayerHolder.isMediaPlayer) {
                if (beSimplePreferences.isFocusEnabled) {
                    mMediaPlayerHolder.tryToGetAudioFocus()
                } else {
                    mMediaPlayerHolder.giveUpAudioFocus()
                }
            }
        }
    }

    override fun onHandleNotificationUpdate() {
        if (isMediaPlayerHolder) {
            mMediaPlayerHolder.onHandleNotificationUpdate()
        }
    }

    override fun onArtistOrFolderSelected(artistOrFolder: String, launchedBy: String) {
        openDetailsFragment(
            artistOrFolder,
            launchedBy
        )
    }

    private fun startPlayback(song: Music?, songs: List<Music>?, launchedBy: String) {
        if (isMediaPlayerHolder) {
            if (::mPlayerService.isInitialized && !mPlayerService.isRunning) {
                startService(
                    mBindingIntent
                )
            }
            mMediaPlayerHolder.run {
                setCurrentSong(song, songs, launchedBy)
                initMediaPlayer(song)
            }
        }
    }

    override fun onSongSelected(song: Music?, songs: List<Music>?, launchedBy: String) {
        if (isMediaPlayerHolder) {
            mMediaPlayerHolder.run {
                isSongRestoredFromPrefs = false
                isPlay = true
                startPlayback(song, songs, launchedBy)
            }
        }
    }

    private fun resumeOrPause() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.resumeOrPause()
        }
    }

    private fun skip(isNext: Boolean) {
        if (checkIsPlayer()) {
            if (!mMediaPlayerHolder.isPlay) {
                mMediaPlayerHolder.isPlay = true
            }
            if (isNext) {
                mMediaPlayerHolder.skip(true)
            } else {
                mMediaPlayerHolder.instantReset()
            }
            if (mMediaPlayerHolder.isSongRestoredFromPrefs) {
                mMediaPlayerHolder.isSongRestoredFromPrefs =
                    false
            }
        }
    }

    private fun setRepeat() {
        if (checkIsPlayer()) {
            mMediaPlayerHolder.repeat(mMediaPlayerHolder.isPlaying)
            updateRepeatStatus(false)
        }
    }

    override fun onGetEqualizer(): Pair<Equalizer, BassBoost> = mMediaPlayerHolder.getEqualizer()

    override fun onEnableEqualizer(isEnabled: Boolean) {
        if (::mMediaPlayerHolder.isInitialized) {
            mMediaPlayerHolder.setEqualizerEnabled(isEnabled)
        }
    }

    override fun onSaveEqualizerSettings(
        selectedPreset: Int,
        bassBoost: Short
    ) {
        mMediaPlayerHolder.onSaveEqualizerSettings(selectedPreset, bassBoost)
    }

    private fun openEqualizer() {
        if (checkIsPlayer()) {
            if (!EqualizerUtils.hasEqualizer(this)) {
                if (!sEqFragmentExpanded) {
                    mEqualizerFragment = mMediaPlayerHolder.openEqualizerCustom()
                    if (sDetailsFragmentExpanded) {
                        mDetailsFragment.setDisableCircleReveal()
                    }
                    sCloseDetailsFragment = !sDetailsFragmentExpanded
                    supportFragmentManager.addFragment(
                        mEqualizerFragment,
                        BeSimpleConstants.EQ_FRAGMENT_TAG
                    )
                }
            } else {
                mMediaPlayerHolder.openEqualizer(this)
            }
        }
    }

    private fun closeEqualizerFragment() {
        if (!sRevealAnimationRunning) {
            mEqualizerFragment.onHandleBackPressed().apply {
                sRevealAnimationRunning = true
                doOnEnd {
                    synchronized(super.onBackPressed()) {
                        sRevealAnimationRunning = false
                    }
                }
            }
        }
    }

    override fun onShuffleSongs(songs: MutableList<Music>?, launchedBy: String) {
        val randomNumber = (0 until songs?.size!!).getRandom()
        songs.shuffle()
        val song = songs[randomNumber]
        onSongSelected(song, songs, launchedBy)
    }

    // interface to let MediaPlayerHolder update the UI media player controls.
    private val mMediaPlayerInterface = object : MediaPlayerInterface {

        override fun onPlaybackCompleted() {
            updateRepeatStatus(true)
        }

        override fun onUpdateRepeatStatus() {
            updateRepeatStatus(false)
        }

        override fun onClose() {
            //finish activity if visible
            finishAndRemoveTask()
        }

        override fun onPositionChanged(position: Int) {
            mPlayerControlsPanelBinding.songProgress.value = position.toFloat()
        }

        override fun onStateChanged() {
            updatePlayingStatus()
            if (mMediaPlayerHolder.state != BeSimpleConstants.RESUMED && mMediaPlayerHolder.state != BeSimpleConstants.PAUSED) {
                updatePlayingInfo(false)
            }
        }

        override fun onFocusLoss() {
            saveSongToPref()
        }

        override fun onSaveSong() {
            saveSongToPref()
        }
    }

    // ViewPager2 adapter class
    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int): Fragment {
            return getFragmentForIndex(position)!!
        }
    }
}
