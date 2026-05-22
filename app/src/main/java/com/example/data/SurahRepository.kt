package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SurahWithState(
    val surah: Surah,
    val state: SurahState?
) {
    val isFavorite: Boolean 
        get() = state?.isFavorite == true
    val downloadStatus: Int 
        get() = state?.downloadStatus ?: SurahState.STATUS_NOT_DOWNLOADED
    val downloadProgress: Int 
        get() = state?.downloadProgress ?: 0
    val localFilePath: String? 
        get() = state?.localFilePath
    val lastPlayedPositionMs: Long 
        get() = state?.lastPlayedPositionMs ?: 0L
}

class SurahRepository(private val surahDao: SurahDao) {
    
    val surahsWithStateFlow: Flow<List<SurahWithState>> = surahDao.getAllStatesFlow().map { states ->
        val stateMap = states.associateBy { it.id }
        SurahData.list.map { surah ->
            SurahWithState(surah, stateMap[surah.id])
        }
    }
    
    suspend fun toggleFavorite(id: Int) {
        surahDao.toggleFavorite(id)
    }
    
    suspend fun updateDownloadStatus(id: Int, status: Int, progress: Int, filePath: String? = null) {
        surahDao.updateDownloadProgress(id, status, progress, filePath)
    }
    
    suspend fun saveLastPosition(id: Int, positionMs: Long) {
        surahDao.savePosition(id, positionMs)
    }
    
    suspend fun getStateById(id: Int): SurahState? {
        return surahDao.getStateById(id)
    }
}
