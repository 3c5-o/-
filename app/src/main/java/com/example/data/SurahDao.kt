package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SurahDao {
    @Query("SELECT * FROM surah_states")
    fun getAllStatesFlow(): Flow<List<SurahState>>

    @Query("SELECT * FROM surah_states")
    suspend fun getAllStates(): List<SurahState>

    @Query("SELECT * FROM surah_states WHERE id = :id")
    fun getStateFlow(id: Int): Flow<SurahState?>

    @Query("SELECT * FROM surah_states WHERE id = :id")
    suspend fun getStateById(id: Int): SurahState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: SurahState)

    @Transaction
    suspend fun toggleFavorite(id: Int) {
        val current = getStateById(id)
        if (current != null) {
            insertOrUpdate(current.copy(isFavorite = !current.isFavorite))
        } else {
            insertOrUpdate(SurahState(id = id, isFavorite = true))
        }
    }

    @Transaction
    suspend fun updateDownloadProgress(id: Int, status: Int, progress: Int, filePath: String? = null) {
        val current = getStateById(id) ?: SurahState(id = id)
        insertOrUpdate(current.copy(
            downloadStatus = status,
            downloadProgress = progress,
            localFilePath = if (status == SurahState.STATUS_DOWNLOADED) filePath else current.localFilePath
        ))
    }

    @Transaction
    suspend fun savePosition(id: Int, positionMs: Long) {
        val current = getStateById(id) ?: SurahState(id = id)
        insertOrUpdate(current.copy(lastPlayedPositionMs = positionMs))
    }
}
