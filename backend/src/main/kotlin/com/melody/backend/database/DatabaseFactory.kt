package com.melody.backend.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object DatabaseFactory {
    fun init() {
        val config = HikariConfig().apply {
            // Use H2 for development, PostgreSQL for production
            jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:h2:mem:melody;DB_CLOSE_DELAY=-1"
            driverClassName = if (System.getenv("DATABASE_URL") != null) {
                "org.postgresql.Driver"
            } else {
                "org.h2.Driver"
            }
            username = System.getenv("DB_USER") ?: "sa"
            password = System.getenv("DB_PASSWORD") ?: ""
            maximumPoolSize = 10
            minimumIdle = 2
        }

        Database.connect(HikariDataSource(config))

        // Create tables
        transaction {
            SchemaUtils.create(Songs)
        }
    }
}

object Songs : Table("songs") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val artist = varchar("artist", 255)
    val album = varchar("album", 255)
    val hash = varchar("hash", 64).uniqueIndex("idx_song_hash")
    val b2Url = varchar("b2_url", 512).nullable()
    val duration = long("duration")
    val fileSize = long("file_size")
    val format = varchar("format", 10)
    val bitrate = integer("bitrate").nullable()
    val year = integer("year").nullable()
    val genre = varchar("genre", 100).nullable()
    val uploadedAt = datetime("uploaded_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)

    // Fixed indexes - proper syntax
    init {
        index(false, title)
        index(false, artist)
        index(false, album)
        index(false, genre)
    }
}