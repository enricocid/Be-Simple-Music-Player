package com.iven.besimple

import android.content.Context
import androidx.preference.PreferenceManager
import com.iven.besimple.models.Music
import com.iven.besimple.models.SavedEqualizerSettings
import com.squareup.moshi.Moshi

class BeSimplePreferences(context: Context) {

    private val prefsSavedEqualizerSettings = context.getString(R.string.saved_eq_settings)
    private val prefsLatestPlayedSong = context.getString(R.string.latest_played_song_pref)

    private val prefsTheme = context.getString(R.string.theme_pref)
    private val prefsThemeDef = context.getString(R.string.theme_pref_auto)

    private val prefsSongsVisual = context.getString(R.string.song_visual_pref)

    private val prefsArtistsSorting = context.getString(R.string.artists_sorting_pref)
    private val prefsFoldersSorting = context.getString(R.string.folders_sorting_pref)

    private val prefsFocus = context.getString(R.string.focus_pref)
    private val prefsHeadsetPlug = context.getString(R.string.headset_pref)

    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val mMoshi = Moshi.Builder().build()

    var latestPlayedSong: Music?
        get() = getObjectForClass(
            prefsLatestPlayedSong,
            Music::class.java
        )
        set(value) = putObjectForClass(prefsLatestPlayedSong, value, Music::class.java)

    var savedEqualizerSettings: SavedEqualizerSettings?
        get() = getObjectForClass(
            prefsSavedEqualizerSettings,
            SavedEqualizerSettings::class.java
        )
        set(value) = putObjectForClass(prefsSavedEqualizerSettings, value, SavedEqualizerSettings::class.java)

    var theme
        get() = mPrefs.getString(prefsTheme, prefsThemeDef)
        set(value) = mPrefs.edit().putString(prefsTheme, value).apply()

    var songsVisualization
        get() = mPrefs.getString(prefsSongsVisual, BeSimpleConstants.TITLE)
        set(value) = mPrefs.edit().putString(prefsSongsVisual, value.toString()).apply()

    var artistsSorting
        get() = mPrefs.getInt(prefsArtistsSorting, BeSimpleConstants.DESCENDING_SORTING)
        set(value) = mPrefs.edit().putInt(prefsArtistsSorting, value).apply()

    var foldersSorting
        get() = mPrefs.getInt(prefsFoldersSorting, BeSimpleConstants.DEFAULT_SORTING)
        set(value) = mPrefs.edit().putInt(prefsFoldersSorting, value).apply()

    var isFocusEnabled
        get() = mPrefs.getBoolean(prefsFocus, true)
        set(value) = mPrefs.edit().putBoolean(prefsFocus, value).apply()

    var isHeadsetPlugEnabled
        get() = mPrefs.getBoolean(prefsHeadsetPlug, true)
        set(value) = mPrefs.edit().putBoolean(prefsHeadsetPlug, value).apply()

    // Saves object into the Preferences using Moshi
    private fun <T : Any> getObjectForClass(key: String, clazz: Class<T>): T? {
        mPrefs.getString(key, null)?.let { json ->
            return mMoshi.adapter(clazz).fromJson(json)
        }
        return null
    }

    private fun <T : Any> putObjectForClass(key: String, value: T?, clazz: Class<T>) {
        val json = mMoshi.adapter(clazz).toJson(value)
        mPrefs.edit().putString(key, json).apply()
    }
}

