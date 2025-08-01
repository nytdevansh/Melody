package com.example.melody.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.melody.R
import com.example.melody.data.Song

class SongAdapter(private val onSongClick: (Song) -> Unit) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
    private var songs: List<Song> = emptyList()

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song)
        holder.itemView.setOnClickListener { onSongClick(song) }
    }

    override fun getItemCount() = songs.size

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.songTitle)
        private val artistText: TextView = itemView.findViewById(R.id.songArtist)

        fun bind(song: Song) {
            titleText.text = song.title ?: "Unknown"
            artistText.text = song.artist ?: "Unknown Artist"
        }
    }
}
