package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "surah_states")
data class SurahState(
    @PrimaryKey val id: Int,
    val isFavorite: Boolean = false,
    val downloadStatus: Int = 0, // 0: Not Downloaded, 1: Downloading, 2: Downloaded, 3: Failed
    val downloadProgress: Int = 0, // 0 to 100
    val localFilePath: String? = null,
    val lastPlayedPositionMs: Long = 0
) {
    companion object {
        const val STATUS_NOT_DOWNLOADED = 0
        const val STATUS_DOWNLOADING = 1
        const val STATUS_DOWNLOADED = 2
        const val STATUS_FAILED = 3
    }
}
