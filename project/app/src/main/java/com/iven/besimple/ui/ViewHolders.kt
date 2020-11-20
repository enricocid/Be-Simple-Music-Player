package com.iven.besimple.ui

import android.view.View
import android.widget.TextView
import com.afollestad.recyclical.ViewHolder
import com.iven.besimple.R

class GenericViewHolder(itemView: View) : ViewHolder(itemView) {
    val title: TextView = itemView.findViewById(R.id.title)
    val subtitle: TextView = itemView.findViewById(R.id.subtitle)
}

class AlbumsViewHolder(itemView: View) : ViewHolder(itemView) {
    val album: TextView = itemView.findViewById(R.id.album)
    val year: TextView = itemView.findViewById(R.id.year)
    val totalDuration: TextView = itemView.findViewById(R.id.total_duration)
}

class PresetsViewHolder(itemView: View) : ViewHolder(itemView) {
    val presetTitle: TextView = itemView.findViewById(R.id.preset)
}
