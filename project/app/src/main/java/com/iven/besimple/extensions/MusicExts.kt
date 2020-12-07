package com.iven.besimple.extensions

import android.content.ContentUris
import android.content.res.Resources
import android.net.Uri
import android.provider.MediaStore
import com.iven.besimple.R
import com.iven.besimple.models.Music
import java.util.*
import java.util.concurrent.TimeUnit

fun Long.toContentUri(): Uri = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        this
)

fun Long.toFormattedDuration(isAlbum: Boolean, isSeekBar: Boolean) = try {

    val defaultFormat = if (isAlbum) {
        "%02dm:%02ds"
    } else {
        "%02d:%02d"
    }

    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this)

    if (minutes < 60) {
        String.format(
                Locale.getDefault(), defaultFormat,
                minutes,
                seconds - TimeUnit.MINUTES.toSeconds(minutes)
        )
    } else {
        // https://stackoverflow.com/a/9027379
        when {
            isSeekBar -> String.format(
                    "%02d:%02d:%02d",
                    hours,
                    minutes - TimeUnit.HOURS.toMinutes(hours),
                    seconds - TimeUnit.MINUTES.toSeconds(minutes)
            )
            else -> String.format(
                    "%02dh:%02dm",
                    hours,
                    minutes - TimeUnit.HOURS.toMinutes(hours)
            )
        }
    }

} catch (e: Exception) {
    e.printStackTrace()
    ""
}

fun Int.toFormattedTrack() = try {
    if (this >= 1000) {
        this % 1000
    } else {
        this
    }
} catch (e: Exception) {
    e.printStackTrace()
    0
}

fun Int.toFormattedYear(resources: Resources) =
        if (this != 0) {
            toString()
        } else {
            resources.getString(R.string.unknown_year)
        }

fun Music.toSavedMusic(playerPosition: Int, savedLaunchedBy: String) =
        Music(
                artist,
                year,
                track,
                title,
                displayName,
                duration,
                album,
                relativePath,
                id,
                savedLaunchedBy,
                playerPosition
        )
