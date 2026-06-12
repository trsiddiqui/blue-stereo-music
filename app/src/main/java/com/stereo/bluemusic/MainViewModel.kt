package com.stereo.bluemusic

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.database.Cursor
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.RepeatMode
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.data.SupportedAudioExtensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            favorites = loadFavorites()
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState

    fun scanLocalLibrary() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningLibrary = true,
                    actionMessage = "Scanning local storage"
                )
            }

            val songs = withContext(Dispatchers.IO) { queryLocalSongs() }

            _uiState.update { state ->
                val availableIds = songs.map { it.id }.toSet()
                val cleanedFavorites = state.favorites.intersect(availableIds)
                saveFavorites(cleanedFavorites)

                val retainedQueue = state.queue.filter { it.id in availableIds }
                val queue = retainedQueue.ifEmpty { songs }
                val currentSong = state.song?.takeIf { it.id in availableIds } ?: songs.firstOrNull()
                val currentIndex = currentSong?.let { song ->
                    queue.indexOfFirst { it.id == song.id }
                } ?: -1

                state.copy(
                    song = currentSong,
                    library = songs,
                    queue = queue,
                    currentIndex = currentIndex,
                    favorites = cleanedFavorites,
                    durationMs = currentSong?.durationMs ?: 0L,
                    progressMs = if (currentSong?.id == state.song?.id) state.progressMs else 0L,
                    isScanningLibrary = false,
                    actionMessage = if (songs.isEmpty()) {
                        "No supported local audio found"
                    } else {
                        "Loaded ${songs.size} local tracks"
                    },
                    scanSummary = if (songs.isEmpty()) {
                        "No MP3, FLAC, WAV, AAC, M4A, OGG, OPUS, WMA, or similar audio found"
                    } else {
                        "${songs.size} tracks / ${cleanedFavorites.size} favourites / ${SupportedAudioExtensions.size} formats watched"
                    }
                )
            }
        }
    }

    fun startSong(song: Song): Song {
        _uiState.update { state ->
            val queue = when {
                state.queue.any { it.id == song.id } -> state.queue
                state.library.isNotEmpty() -> state.library
                else -> listOf(song)
            }
            val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            state.copy(
                song = song,
                queue = queue,
                currentIndex = index,
                isPlaying = true,
                progressMs = 0L,
                durationMs = song.durationMs,
                history = (listOf(song) + state.history.filterNot { it.id == song.id }).take(30),
                actionMessage = "Playing ${song.title}"
            )
        }
        return song
    }

    fun toggleFavorite(song: Song? = null) {
        val target = song ?: _uiState.value.song ?: return
        _uiState.update { state ->
            val updated = if (target.id in state.favorites) {
                state.favorites - target.id
            } else {
                state.favorites + target.id
            }
            saveFavorites(updated)
            state.copy(
                favorites = updated,
                actionMessage = if (target.id in updated) {
                    "Added to favourites"
                } else {
                    "Removed from favourites"
                }
            )
        }
    }

    fun nextTrack(userInitiated: Boolean = true): Song? {
        return moveTrack(forward = true, userInitiated = userInitiated)
    }

    fun previousTrack(): Song? {
        return moveTrack(forward = false, userInitiated = true)
    }

    fun trackAfterCompletion(): Song? {
        val state = _uiState.value
        if (state.repeatMode == RepeatMode.One) {
            return state.song?.also { startSong(it) }
        }
        return nextTrack(userInitiated = false)
    }

    fun togglePlayState(): Boolean {
        val shouldPlay = !_uiState.value.isPlaying
        _uiState.update {
            it.copy(
                isPlaying = shouldPlay,
                actionMessage = if (shouldPlay) "Playback resumed" else "Playback paused"
            )
        }
        return shouldPlay
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun stopAtQueueEnd() {
        _uiState.update {
            it.copy(
                isPlaying = false,
                progressMs = 0L,
                actionMessage = "Queue finished"
            )
        }
    }

    fun updateProgress(progressMs: Long, durationMs: Long) {
        _uiState.update {
            it.copy(
                progressMs = progressMs.coerceAtLeast(0L),
                durationMs = durationMs.takeIf { value -> value > 0L } ?: it.durationMs
            )
        }
    }

    fun markPrepared(durationMs: Long) {
        _uiState.update {
            it.copy(durationMs = durationMs.takeIf { value -> value > 0L } ?: it.durationMs)
        }
    }

    fun toggleShuffle() {
        _uiState.update {
            it.copy(
                shuffleEnabled = !it.shuffleEnabled,
                actionMessage = if (!it.shuffleEnabled) "Shuffle on" else "Shuffle off"
            )
        }
    }

    fun cycleRepeatMode() {
        _uiState.update {
            val next = when (it.repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            }
            it.copy(
                repeatMode = next,
                actionMessage = when (next) {
                    RepeatMode.Off -> "Repeat off"
                    RepeatMode.All -> "Repeating all tracks"
                    RepeatMode.One -> "Repeating current track"
                }
            )
        }
    }

    fun startRandomPlaylist(): Song? {
        val state = _uiState.value
        if (state.library.isEmpty()) {
            showAction("Scan local audio before random play")
            return null
        }

        val shuffled = state.library.shuffled().take(MAX_RANDOM_QUEUE)
        val first = shuffled.first()
        _uiState.update {
            it.copy(
                queue = shuffled,
                currentIndex = 0,
                song = first,
                isPlaying = true,
                progressMs = 0L,
                durationMs = first.durationMs,
                history = (listOf(first) + it.history.filterNot { song -> song.id == first.id }).take(30),
                actionMessage = "Random playlist ready"
            )
        }
        return first
    }

    fun playFavorites(): Song? {
        val state = _uiState.value
        val favourites = state.library.filter { it.id in state.favorites }
        if (favourites.isEmpty()) {
            showAction("No favourite songs yet")
            return null
        }

        val first = favourites.first()
        _uiState.update {
            it.copy(
                queue = favourites,
                currentIndex = 0,
                song = first,
                isPlaying = true,
                progressMs = 0L,
                durationMs = first.durationMs,
                history = (listOf(first) + it.history.filterNot { song -> song.id == first.id }).take(30),
                actionMessage = "Playing all favourites"
            )
        }
        return first
    }

    fun setEqBand(index: Int, value: Float) {
        _uiState.update { state ->
            val updated = state.eqBands.toMutableList()
            if (index in updated.indices) {
                updated[index] = value.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
            }
            state.copy(eqBands = updated)
        }
    }

    fun setTone(bass: Float? = null, mid: Float? = null, treble: Float? = null) {
        _uiState.update {
            it.copy(
                bassBoost = bass?.coerceIn(0f, 1f) ?: it.bassBoost,
                midBoost = mid?.coerceIn(0f, 1f) ?: it.midBoost,
                trebleBoost = treble?.coerceIn(0f, 1f) ?: it.trebleBoost
            )
        }
    }

    fun toggleLoudness(): Boolean {
        val enabled = !_uiState.value.loudnessEnabled
        _uiState.update {
            it.copy(
                loudnessEnabled = enabled,
                actionMessage = if (enabled) "Loudness enhancer on" else "Loudness enhancer off"
            )
        }
        return enabled
    }

    fun toggleMaxOutput(): Boolean {
        val enabled = !_uiState.value.maxVolumeEnabled
        _uiState.update {
            it.copy(
                maxVolumeEnabled = enabled,
                loudnessEnabled = enabled,
                actionMessage = if (enabled) "Maximum output enabled" else "Maximum output disabled"
            )
        }
        return enabled
    }

    fun showAction(message: String) {
        _uiState.update { it.copy(actionMessage = message) }
    }

    private fun moveTrack(forward: Boolean, userInitiated: Boolean): Song? {
        val state = _uiState.value
        val queue = activeQueue(state)
        if (queue.isEmpty()) {
            showAction("Scan local audio first")
            return null
        }

        val safeIndex = state.currentIndex.takeIf { it in queue.indices } ?: 0
        val targetIndex = when {
            forward && state.shuffleEnabled && queue.size > 1 -> randomIndexExcept(queue.size, safeIndex)
            forward && safeIndex < queue.lastIndex -> safeIndex + 1
            forward && (state.repeatMode == RepeatMode.All || userInitiated) -> 0
            forward -> -1
            !forward && safeIndex > 0 -> safeIndex - 1
            else -> queue.lastIndex
        }

        if (targetIndex !in queue.indices) {
            stopAtQueueEnd()
            return null
        }

        val song = queue[targetIndex]
        _uiState.update {
            it.copy(
                queue = queue,
                currentIndex = targetIndex,
                song = song,
                isPlaying = true,
                progressMs = 0L,
                durationMs = song.durationMs,
                history = (listOf(song) + it.history.filterNot { item -> item.id == song.id }).take(30),
                actionMessage = if (forward) "Next track" else "Previous track"
            )
        }
        return song
    }

    private fun activeQueue(state: PlayerUiState): List<Song> {
        return state.queue.ifEmpty { state.library }
    }

    private fun randomIndexExcept(size: Int, current: Int): Int {
        if (size <= 1) return 0
        var next = Random.nextInt(size)
        while (next == current) {
            next = Random.nextInt(size)
        }
        return next
    }

    @SuppressLint("Range")
    private fun queryLocalSongs(): List<Song> {
        val resolver = getApplication<Application>().contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val songs = mutableListOf<Song>()

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.safeString(displayNameColumn)
                val title = cleanMediaText(cursor.safeString(titleColumn), displayName.ifBlank { "Track $id" })
                val artist = cleanMediaText(cursor.safeString(artistColumn), "Unknown artist")
                val album = cleanMediaText(cursor.safeString(albumColumn), "Local storage")
                val durationMs = cursor.safeLong(durationColumn)
                val mimeType = cursor.safeString(mimeColumn)
                val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
                val isSupported = extension in SupportedAudioExtensions || mimeType.startsWith("audio/")

                if (isSupported) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs += Song(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        uri = uri.toString(),
                        displayName = displayName,
                        extension = extension,
                        mimeType = mimeType
                    )
                }
            }
        }

        return songs.sortedWith(
            compareBy<Song> { it.artist.lowercase(Locale.US) }
                .thenBy { it.album.lowercase(Locale.US) }
                .thenBy { it.title.lowercase(Locale.US) }
        )
    }

    private fun Cursor.safeString(index: Int): String {
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    private fun Cursor.safeLong(index: Int): Long {
        return if (index < 0 || isNull(index)) 0L else getLong(index)
    }

    private fun cleanMediaText(value: String, fallback: String): String {
        val cleaned = value.trim()
        return if (
            cleaned.isBlank() ||
            cleaned.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) ||
            cleaned.equals("<unknown>", ignoreCase = true)
        ) {
            fallback
        } else {
            cleaned
        }
    }

    private fun loadFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet().orEmpty()
    }

    private fun saveFavorites(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    companion object {
        private const val PREFS_NAME = "blue_stereo_music"
        private const val KEY_FAVORITES = "favorite_song_ids"
        private const val MAX_RANDOM_QUEUE = 75
        const val EQ_MIN_DB = -12f
        const val EQ_MAX_DB = 12f
    }
}
