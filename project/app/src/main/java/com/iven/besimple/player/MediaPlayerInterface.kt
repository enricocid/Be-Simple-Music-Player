package com.iven.besimple.player

interface MediaPlayerInterface {
    fun onPositionChanged(position: Int)
    fun onStateChanged()
    fun onPlaybackCompleted()
    fun onClose()
    fun onUpdateRepeatStatus()
    fun onSaveSong()
    fun onFocusLoss()
}
