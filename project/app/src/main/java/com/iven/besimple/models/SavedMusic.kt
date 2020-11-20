package com.iven.besimple.models

data class SavedMusic(
        val artist: String?,
        val title: String?,
        val displayName: String?,
        val year: Int,
        val startFrom: Int,
        val duration: Long,
        val album: String?,
        val launchedBy: String
)
