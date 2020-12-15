package com.iven.besimple.helpers

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.res.Resources
import android.provider.MediaStore
import android.provider.MediaStore.Audio.AudioColumns
import com.iven.besimple.beSimplePreferences
import com.iven.besimple.extensions.toFormattedYear
import com.iven.besimple.extensions.toSavedMusic
import com.iven.besimple.models.Album
import com.iven.besimple.models.Music
import com.iven.besimple.player.MediaPlayerHolder


object MusicOrgHelper {

    // Returns the position in list of the current played album
    // pass selected artist from artists adapter and not from current song
    // so when played artist is selected the album position will be returned
    // if selected artist differs from played artist -1 will be returned
    @JvmStatic
    fun getPlayingAlbumPosition(
            selectedArtist: String?,
            mediaPlayerHolder: MediaPlayerHolder,
            deviceAlbumsByArtist: MutableMap<String, List<Album>>?
    ) = try {
        val currentSong = mediaPlayerHolder.currentSong
        val album = getAlbumFromList(
                selectedArtist,
                currentSong?.album,
                deviceAlbumsByArtist
        )
        album.second
    } catch (e: Exception) {
        e.printStackTrace()
        -1
    }

    @JvmStatic
    fun getAlbumSongs(
            artist: String?,
            album: String?,
            deviceAlbumsByArtist: MutableMap<String, List<Album>>?
    ) = try {
        getAlbumFromList(
                artist,
                album,
                deviceAlbumsByArtist
        ).first.music
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    @JvmStatic
    // Returns a pair of album and its position given a list of albums
    fun getAlbumFromList(
            artist: String?,
            album: String?,
            deviceAlbumsByArtist: MutableMap<String, List<Album>>?
    ): Pair<Album, Int> {
        val albums = deviceAlbumsByArtist?.get(artist)
        return try {
            val position = albums?.indexOfFirst { it.title == album }!!
            Pair(albums[position], position)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(albums?.get(0)!!, 0)
        }
    }

    @JvmStatic
    fun saveLatestSong(
            latestSong: Music?,
            mediaPlayerHolder: MediaPlayerHolder,
            launchedBy: String
    ) {
        val playerPosition = mediaPlayerHolder.playerPosition
        latestSong?.let { musicToSave ->
            val toSave = musicToSave.toSavedMusic(playerPosition, launchedBy)
            if (beSimplePreferences.latestPlayedSong != toSave) {
                beSimplePreferences.latestPlayedSong =
                        toSave
            }
        }
    }

    @JvmStatic
    fun buildSortedArtistAlbums(
            resources: Resources,
            artistSongs: List<Music>?
    ): List<Album> {

        val sortedAlbums = mutableListOf<Album>()

        artistSongs?.let {

            try {

                val groupedSongs = it.groupBy { song -> song.album }

                val iterator = groupedSongs.keys.iterator()

                while (iterator.hasNext()) {

                    val album = iterator.next()
                    val albumSongs = groupedSongs.getValue(album).toMutableList()
                    albumSongs.sortBy { song -> song.track }

                    sortedAlbums.add(
                            Album(
                                    album,
                                    albumSongs[0].year.toFormattedYear(resources),
                                    albumSongs,
                                    albumSongs.map { song -> song.duration }.sum()
                            )
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            sortedAlbums.sortBy { album -> album.year }
        }

        return sortedAlbums
    }

    @JvmStatic
    @SuppressLint("InlinedApi")
    fun getMusicCursor(contentResolver: ContentResolver) = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                    AudioColumns.ARTIST, // 0
                    AudioColumns.YEAR, // 1
                    AudioColumns.TRACK, // 2
                    AudioColumns.TITLE, // 3
                    AudioColumns.DISPLAY_NAME, // 4,
                    AudioColumns.DURATION, //5,
                    AudioColumns.ALBUM, // 6
                    getPathColumn(), // 7
                    AudioColumns._ID //8
            ), AudioColumns.IS_MUSIC + "=1", null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER
    )

    @JvmStatic
    @Suppress("DEPRECATION")
    fun getPathColumn() =
            if (VersioningHelper.isQ()) {
                AudioColumns.BUCKET_DISPLAY_NAME
            } else {
                AudioColumns.DATA
            }
}
