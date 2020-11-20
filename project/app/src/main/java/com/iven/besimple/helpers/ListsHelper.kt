package com.iven.besimple.helpers

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import com.iven.besimple.BeSimpleConstants
import com.iven.besimple.R
import com.iven.besimple.models.Music
import java.util.*

@SuppressLint("DefaultLocale")
object ListsHelper {

    @JvmStatic
    fun processQueryForStringsLists(
            query: String?,
            list: List<String>?
    ): List<String>? {
        // In real app you'd have it instantiated just once
        val filteredStrings = mutableListOf<String>()

        return try {
            // Case insensitive search
            list?.iterator()?.forEach { filteredString ->
                if (filteredString.toLowerCase().contains(query?.toLowerCase()!!)) {
                    filteredStrings.add(filteredString)
                }
            }
            return filteredStrings
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun processQueryForMusic(query: String?, musicList: List<Music>?): List<Music>? {
        // In real app you'd have it instantiated just once
        val filteredSongs = mutableListOf<Music>()

        return try {
            // Case insensitive search
            musicList?.iterator()?.forEach { filteredSong ->
                if (filteredSong.title?.toLowerCase()!!.contains(query?.toLowerCase()!!)) {
                    filteredSongs.add(filteredSong)
                }
            }
            return filteredSongs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getSortedList(
            id: Int,
            list: MutableList<String>?
    ) = when (id) {
        BeSimpleConstants.DESCENDING_SORTING -> {
            list?.apply {
                Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
            }
            list
        }

        BeSimpleConstants.ASCENDING_SORTING -> {
            list?.apply {
                Collections.sort(this, String.CASE_INSENSITIVE_ORDER)
            }
            list?.asReversed()
        }
        else -> list
    }

    @JvmStatic
    fun getSortedListWithNull(
            id: Int,
            list: MutableList<String?>?
    ): MutableList<String>? {
        val withoutNulls = list?.map {
            transformNullToEmpty(it)
        }?.toMutableList()

        return getSortedList(id, withoutNulls)
    }

    private fun transformNullToEmpty(toTrans: String?): String {
        if (toTrans == null) {
            return ""
        }
        return toTrans
    }

    fun getSelectedSorting(sorting: Int, menu: Menu): MenuItem = when (sorting) {
        BeSimpleConstants.DEFAULT_SORTING -> menu.findItem(R.id.default_sorting)
        BeSimpleConstants.ASCENDING_SORTING -> menu.findItem(R.id.ascending_sorting)
        else -> menu.findItem(R.id.descending_sorting)
    }

    @JvmStatic
    fun getSortedMusicList(
            id: Int,
            list: MutableList<Music>?
    ) = when (id) {

        BeSimpleConstants.DESCENDING_SORTING -> {
            list?.sortBy { it.title }
            list
        }

        BeSimpleConstants.ASCENDING_SORTING -> {
            list?.sortBy { it.title }
            list?.asReversed()
        }

        BeSimpleConstants.TRACK_SORTING -> {
            list?.sortBy { it.track }
            list
        }

        BeSimpleConstants.TRACK_SORTING_INVERTED -> {
            list?.sortBy { it.track }
            list?.asReversed()
        }
        else -> list
    }

    @JvmStatic
    fun getSongsSorting(currentSorting: Int): Int {
        return when (currentSorting) {
            BeSimpleConstants.TRACK_SORTING -> BeSimpleConstants.TRACK_SORTING_INVERTED
            BeSimpleConstants.TRACK_SORTING_INVERTED -> BeSimpleConstants.ASCENDING_SORTING
            BeSimpleConstants.ASCENDING_SORTING -> BeSimpleConstants.DESCENDING_SORTING
            else -> BeSimpleConstants.TRACK_SORTING
        }
    }
}
