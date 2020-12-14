package com.iven.besimple.ui

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.iven.besimple.models.Music

interface UIControlInterface {
    fun onAppearanceChanged(isAccentChanged: Boolean, restoreSettings: Boolean)
    fun onThemeChanged()
    fun onArtistOrFolderSelected(artistOrFolder: String, launchedBy: String)
    fun onSongSelected(song: Music?, songs: List<Music>?, launchedBy: String)
    fun onCloseActivity()
    fun onDenyPermission()
    fun onHandleFocusPref()
    fun onHandleNotificationUpdate()
    fun onGetEqualizer(): Pair<Equalizer?, BassBoost?>
    fun onEnableEqualizer(isEnabled: Boolean)
    fun onSaveEqualizerSettings(selectedPreset: Int, bassBoost: Short)
}
