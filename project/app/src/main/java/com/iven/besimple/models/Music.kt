package com.iven.besimple.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Music(
        val artist: String?,
        val year: Int,
        val track: Int,
        val title: String?,
        val displayName: String?,
        val duration: Long,
        val album: String?,
        val relativePath: String?,
        val id: Long?,
        val launchedBy: String,
        val startFrom: Int
)
