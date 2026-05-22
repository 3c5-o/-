package com.example.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream

sealed class PlayerState {
    object Idle : PlayerState()
    object Preparing : PlayerState()
    object Playing : PlayerState()
    object Paused : PlayerState()
    data class Error(val message: String) : PlayerState()
}

enum class QuranRepeatMode {
    OFF, ONE, ALL, SHUFFLE
}

enum class SurahFilter {
    ALL, MECCAN, MEDINAN, FAVORITES, DOWNLOADED
}

class SurahViewModel(private val repository: SurahRepository) : ViewModel() {

    private var mediaPlayer = MediaPlayer()

    private val _currentPlaylist = MutableStateFlow<List<SurahWithState>>(emptyList())
    val currentPlaylist = _currentPlaylist.asStateFlow()

    private val _currentPlayingIndex = MutableStateFlow(-1)
    val currentPlayingIndex = _currentPlayingIndex.asStateFlow()

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState = _playerState.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs = _currentPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _repeatMode = MutableStateFlow(QuranRepeatMode.ALL)
    val repeatMode = _repeatMode.asStateFlow()

    // Download Job Management
    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()
    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Sleep Timer
    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft = _sleepTimerMinutesLeft.asStateFlow()
    private var sleepTimerJob: Job? = null

    // Filter and Search UI State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _activeFilter = MutableStateFlow(SurahFilter.ALL)
    val activeFilter = _activeFilter.asStateFlow()

    private var positionJob: Job? = null
    private var isUserSeeking = false

    // Emits the complete list of Surahs reactive to user search queries and filters
    val filteredSurahs: StateFlow<List<SurahWithState>> = combine(
        repository.surahsWithStateFlow,
        _searchQuery,
        _activeFilter
    ) { surahs, query, filter ->
        surahs.filter { item ->
            val matchesQuery = item.surah.nameAr.contains(query, ignoreCase = true) ||
                    item.surah.nameEn.contains(query, ignoreCase = true)
            
            val matchesFilter = when (filter) {
                SurahFilter.ALL -> true
                SurahFilter.MECCAN -> item.surah.type == "مكية"
                SurahFilter.MEDINAN -> item.surah.type == "مدنية"
                SurahFilter.FAVORITES -> item.isFavorite
                SurahFilter.DOWNLOADED -> item.downloadStatus == SurahState.STATUS_DOWNLOADED
            }
            matchesQuery && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks current playing item directly from list index
    val currentPlayingItem: StateFlow<SurahWithState?> = combine(
        _currentPlaylist,
        _currentPlayingIndex,
        repository.surahsWithStateFlow
    ) { playlist, index, allSurahs ->
        if (index in playlist.indices) {
            val surahId = playlist[index].surah.id
            allSurahs.find { it.surah.id == surahId } ?: playlist[index]
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        setupMediaPlayer()
        startTrackingPosition()
    }

    private fun setupMediaPlayer() {
        mediaPlayer.setOnPreparedListener { mp ->
            _durationMs.value = mp.duration.toLong()
            applyPlaybackSpeed()
            mp.start()
            _playerState.value = PlayerState.Playing
        }

        mediaPlayer.setOnCompletionListener {
            viewModelScope.launch {
                handlePlaybackCompletion()
            }
        }

        mediaPlayer.setOnErrorListener { _, what, extra ->
            _playerState.value = PlayerState.Error("خطأ في تشغيل الصوت ($what, $extra)")
            false
        }
    }

    private fun startTrackingPosition() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                if (_playerState.value is PlayerState.Playing && !isUserSeeking) {
                    _currentPositionMs.value = mediaPlayer.currentPosition.toLong()
                }
                delay(400)
            }
        }
    }

    private fun applyPlaybackSpeed() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val params = mediaPlayer.playbackParams
                params.speed = _playbackSpeed.value
                mediaPlayer.playbackParams = params
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Audio Controls
    fun playSurah(playlist: List<SurahWithState>, index: Int, context: Context) {
        if (playlist.isEmpty()) return
        _currentPlaylist.value = playlist
        _currentPlayingIndex.value = index
        
        loadAndPlaySurah(playlist[index], context)
    }

    private fun loadAndPlaySurah(item: SurahWithState, context: Context) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                _playerState.value = PlayerState.Preparing
                _currentPositionMs.value = 0L
                mediaPlayer.reset()

                val isLocalAvailable = item.downloadStatus == SurahState.STATUS_DOWNLOADED && item.localFilePath != null
                if (isLocalAvailable) {
                    val file = File(context.filesDir, item.localFilePath!!)
                    if (file.exists()) {
                        mediaPlayer.setDataSource(file.absolutePath)
                    } else {
                        mediaPlayer.setDataSource(item.surah.audioUrl)
                    }
                } else {
                    mediaPlayer.setDataSource(item.surah.audioUrl)
                }

                mediaPlayer.prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                _playerState.value = PlayerState.Error(e.message ?: "فشل تحميل الصوت")
            }
        }
    }

    fun togglePlayPause() {
        val state = _playerState.value
        if (state is PlayerState.Playing) {
            mediaPlayer.pause()
            _playerState.value = PlayerState.Paused
        } else if (state is PlayerState.Paused) {
            mediaPlayer.start()
            _playerState.value = PlayerState.Playing
        }
    }

    fun seekStarted() {
        isUserSeeking = true
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        mediaPlayer.seekTo(positionMs.toInt())
        isUserSeeking = false
    }

    fun skipForward() {
        val target = (mediaPlayer.currentPosition + 10000).coerceAtMost(mediaPlayer.duration)
        mediaPlayer.seekTo(target)
        _currentPositionMs.value = target.toLong()
    }

    fun skipBackward() {
        val target = (mediaPlayer.currentPosition - 10000).coerceAtLeast(0)
        mediaPlayer.seekTo(target)
        _currentPositionMs.value = target.toLong()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (_playerState.value is PlayerState.Playing || _playerState.value is PlayerState.Paused) {
            applyPlaybackSpeed()
        }
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            QuranRepeatMode.OFF -> QuranRepeatMode.ONE
            QuranRepeatMode.ONE -> QuranRepeatMode.ALL
            QuranRepeatMode.ALL -> QuranRepeatMode.SHUFFLE
            QuranRepeatMode.SHUFFLE -> QuranRepeatMode.OFF
        }
    }

    fun playNext(context: Context) {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return
        
        val nextIndex = when (_repeatMode.value) {
            QuranRepeatMode.SHUFFLE -> playlist.indices.random()
            else -> {
                val next = _currentPlayingIndex.value + 1
                if (next in playlist.indices) next else 0
            }
        }
        _currentPlayingIndex.value = nextIndex
        loadAndPlaySurah(playlist[nextIndex], context)
    }

    fun playPrevious(context: Context) {
        val playlist = _currentPlaylist.value
        if (playlist.isEmpty()) return

        val prevIndex = {
            val prev = _currentPlayingIndex.value - 1
            if (prev in playlist.indices) prev else playlist.lastIndex
        }()
        _currentPlayingIndex.value = prevIndex
        loadAndPlaySurah(playlist[prevIndex], context)
    }

    private fun handlePlaybackCompletion() {
        val playlist = _currentPlaylist.value
        val index = _currentPlayingIndex.value
        
        if (playlist.isEmpty() || index !in playlist.indices) return

        when (_repeatMode.value) {
            QuranRepeatMode.OFF -> {
                val next = index + 1
                if (next in playlist.indices) {
                    _currentPlayingIndex.value = next
                } else {
                    mediaPlayer.seekTo(0)
                    _currentPositionMs.value = 0L
                    _playerState.value = PlayerState.Paused
                }
            }
            QuranRepeatMode.ONE -> {
                mediaPlayer.seekTo(0)
                mediaPlayer.start()
                _playerState.value = PlayerState.Playing
            }
            QuranRepeatMode.ALL -> {
                val next = (index + 1) % playlist.size
                _currentPlayingIndex.value = next
            }
            QuranRepeatMode.SHUFFLE -> {
                val next = playlist.indices.random()
                _currentPlayingIndex.value = next
            }
        }
    }

    // Bookmarking
    fun toggleFavorite(surahId: Int) {
        viewModelScope.launch {
            repository.toggleFavorite(surahId)
        }
    }

    // Sleep Timer Capabilities
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = minutes
        
        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            var left = minutes
            while (left > 0) {
                delay(60000)
                left--
                _sleepTimerMinutesLeft.value = left
            }
            // Sleep time ended: pause players
            if (_playerState.value is PlayerState.Playing) {
                togglePlayPause()
            }
            _sleepTimerMinutesLeft.value = null
        }
    }

    fun stopSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutesLeft.value = null
    }

    // Downloader implementation
    fun downloadSurah(context: Context, surahId: Int) {
        if (downloadJobs.containsKey(surahId)) return // Check active first

        val surah = SurahData.list.find { it.id == surahId } ?: return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateDownloadStatus(surahId, SurahState.STATUS_DOWNLOADING, 0)

                val request = okhttp3.Request.Builder()
                    .url(surah.audioUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    repository.updateDownloadStatus(surahId, SurahState.STATUS_FAILED, 0)
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    repository.updateDownloadStatus(surahId, SurahState.STATUS_FAILED, 0)
                    return@launch
                }

                val totalBytes = body.contentLength()
                val fileName = "surah_${surahId}.mp3"
                val file = File(context.filesDir, fileName)

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((totalRead * 100) / totalBytes).toInt()
                                val currentTime = System.currentTimeMillis()
                                if (progress % 5 == 0 || currentTime - lastProgressUpdate > 350) {
                                    repository.updateDownloadStatus(surahId, SurahState.STATUS_DOWNLOADING, progress)
                                    lastProgressUpdate = currentTime
                                }
                            }
                        }
                    }
                }

                repository.updateDownloadStatus(surahId, SurahState.STATUS_DOWNLOADED, 100, fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                repository.updateDownloadStatus(surahId, SurahState.STATUS_FAILED, 0)
            } finally {
                downloadJobs.remove(surahId)
            }
        }

        downloadJobs[surahId] = job
    }

    fun deleteDownload(context: Context, surahId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = "surah_${surahId}.mp3"
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            repository.updateDownloadStatus(surahId, SurahState.STATUS_NOT_DOWNLOADED, 0, null)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateFilter(filter: SurahFilter) {
        _activeFilter.value = filter
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer.release()
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        downloadJobs.forEach { (_, job) -> job.cancel() }
    }
}

class SurahViewModelFactory(private val repository: SurahRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SurahViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SurahViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
