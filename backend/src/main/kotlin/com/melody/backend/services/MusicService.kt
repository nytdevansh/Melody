package com.melody.backend.services

import com.melody.backend.database.Songs
import com.melody.backend.models.Song
import com.melody.backend.models.UploadRequest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

class MusicService {

    fun insertSong(uploadRequest: UploadRequest, b2Url: String): Song {
        return transaction {
            val songId = UUID.randomUUID().toString()

            Songs.insert {
                it[id] = songId
                it[title] = uploadRequest.title
                it[artist] = uploadRequest.artist
                it[album] = uploadRequest.album
                it[hash] = uploadRequest.hash
                it[Songs.b2Url] = b2Url
                it[duration] = uploadRequest.duration
                it[fileSize] = uploadRequest.fileSize
                it[format] = uploadRequest.format
                it[bitrate] = uploadRequest.bitrate
                it[year] = uploadRequest.year
                it[genre] = uploadRequest.genre
                it[uploadedAt] = LocalDateTime.now()
            }

            Song(
                id = songId,
                title = uploadRequest.title,
                artist = uploadRequest.artist,
                album = uploadRequest.album,
                hash = uploadRequest.hash,
                b2Url = b2Url,
                duration = uploadRequest.duration,
                fileSize = uploadRequest.fileSize,
                format = uploadRequest.format,
                bitrate = uploadRequest.bitrate,
                year = uploadRequest.year,
                genre = uploadRequest.genre,
                uploadedAt = LocalDateTime.now().toString()
            )
        }
    }

    fun getSongByHash(hash: String): Song? {
        return transaction {
            Songs.select { Songs.hash eq hash }
                .map { rowToSong(it) }
                .singleOrNull()
        }
    }

    fun getSongById(id: String): Song? {
        return transaction {
            Songs.select { Songs.id eq id }
                .map { rowToSong(it) }
                .singleOrNull()
        }
    }

    fun getAllSongs(limit: Int = 100, offset: Long = 0): List<Song> {
        return transaction {
            Songs.selectAll()
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit, offset)
                .map { rowToSong(it) }
        }
    }

    fun searchSongs(query: String, limit: Int = 50): List<Song> {
        return transaction {
            val searchPattern = "%${query.lowercase()}%"

            Songs.select {
                (Songs.title.lowerCase() like searchPattern) or
                        (Songs.artist.lowerCase() like searchPattern) or
                        (Songs.album.lowerCase() like searchPattern) or
                        (Songs.genre.isNotNull() and (Songs.genre.lowerCase() like searchPattern))
            }
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToSong(it) }
        }
    }

    fun getSongsByArtist(artist: String, limit: Int = 50): List<Song> {
        return transaction {
            Songs.select { Songs.artist.lowerCase() like "%${artist.lowercase()}%" }
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToSong(it) }
        }
    }

    fun getSongsByAlbum(album: String, limit: Int = 50): List<Song> {
        return transaction {
            Songs.select { Songs.album.lowerCase() like "%${album.lowercase()}%" }
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToSong(it) }
        }
    }

    fun getSongsByGenre(genre: String, limit: Int = 50): List<Song> {
        return transaction {
            Songs.select {
                Songs.genre.isNotNull() and (Songs.genre.lowerCase() like "%${genre.lowercase()}%")
            }
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToSong(it) }
        }
    }

    fun updateSongB2Url(songId: String, b2Url: String): Boolean {
        return transaction {
            Songs.update({ Songs.id eq songId }) {
                it[Songs.b2Url] = b2Url
            } > 0
        }
    }

    fun deleteSong(songId: String): Boolean {
        return transaction {
            Songs.deleteWhere { Songs.id eq songId } > 0
        }
    }

    fun getTotalSongCount(): Long {
        return transaction {
            Songs.selectAll().count()
        }
    }

    fun getRecentSongs(limit: Int = 20): List<Song> {
        return transaction {
            Songs.selectAll()
                .orderBy(Songs.uploadedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToSong(it) }
        }
    }

    fun getPopularArtists(limit: Int = 20): List<Pair<String, Int>> {
        return transaction {
            Songs.slice(Songs.artist, Songs.artist.count())
                .selectAll()
                .groupBy(Songs.artist)
                .orderBy(Songs.artist.count(), SortOrder.DESC)
                .limit(limit)
                .map { it[Songs.artist] to it[Songs.artist.count()].toInt() }
        }
    }

    fun getPopularGenres(limit: Int = 20): List<Pair<String, Int>> {
        return transaction {
            Songs.slice(Songs.genre, Songs.genre.count())
                .select { Songs.genre.isNotNull() }
                .groupBy(Songs.genre)
                .orderBy(Songs.genre.count(), SortOrder.DESC)
                .limit(limit)
                .mapNotNull { row ->
                    row[Songs.genre]?.let { genre ->
                        genre to row[Songs.genre.count()].toInt()
                    }
                }
        }
    }

    private fun rowToSong(row: ResultRow): Song {
        return Song(
            id = row[Songs.id],
            title = row[Songs.title],
            artist = row[Songs.artist],
            album = row[Songs.album],
            hash = row[Songs.hash],
            b2Url = row[Songs.b2Url],
            duration = row[Songs.duration],
            fileSize = row[Songs.fileSize],
            format = row[Songs.format],
            bitrate = row[Songs.bitrate],
            year = row[Songs.year],
            genre = row[Songs.genre],
            uploadedAt = row[Songs.uploadedAt].toString()
        )
    }
}