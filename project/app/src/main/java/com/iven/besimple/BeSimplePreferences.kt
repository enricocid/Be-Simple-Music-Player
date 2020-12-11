package com.iven.besimple

import android.content.Context
import androidx.preference.PreferenceManager
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.iven.besimple.models.Music
import com.iven.besimple.models.SavedEqualizerSettings
import java.lang.reflect.Type

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
    private val mGson = GsonBuilder().create()

    // saved equalizer settings is a SavedEqualizerSettings
    private val typeSavedEqualizerSettings = object : TypeToken<SavedEqualizerSettings>() {}.type

    // last played song is a SavedMusic
    private val typeLastPlayedSong = object : TypeToken<Music>() {}.type

    var latestPlayedSong: Music?
        get() = getObject(
            prefsLatestPlayedSong,
            typeLastPlayedSong
        )
        set(value) = putObject(prefsLatestPlayedSong, value)

    var savedEqualizerSettings: SavedEqualizerSettings?
        get() = getObject(
            prefsSavedEqualizerSettings,
            typeSavedEqualizerSettings
        )
        set(value) = putObject(prefsSavedEqualizerSettings, value)

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

    /**
     * Saves object into the Preferences.
     * Only the fields are stored. Methods, Inner classes, Nested classes and inner interfaces are not stored.
     **/
    private fun <T> putObject(key: String, y: T) {
        //Convert object to JSON String.
        val inString = mGson.toJson(y)
        //Save that String in SharedPreferences
        mPrefs.edit().putString(key, inString).apply()
    }

    /**
     * Get object from the Preferences.
     **/
    private fun <T> getObject(key: String, t: Type): T? {
        //We read JSON String which was saved.
        val value = mPrefs.getString(key, null)

        //JSON String was found which means object can be read.
        //We convert this JSON String to model object. Parameter "c" (of type Class<T>" is used to cast.
        return mGson.fromJson(value, t)
    }
}

