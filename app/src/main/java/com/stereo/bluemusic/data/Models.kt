package com.stereo.bluemusic.data

import java.util.Locale

val SupportedAudioExtensions = listOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "flac",
    "ogg",
    "opus",
    "wma",
    "amr",
    "3gp",
    "mp4",
    "m4b",
    "mid",
    "midi",
    "aif",
    "aiff",
    "ape"
)

enum class RepeatMode {
    Off,
    One,
    All
}

data class Song(
    val id: String,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val durationMs: Long = 0L,
    val uri: String = "",
    val displayName: String = "",
    val extension: String = "",
    val mimeType: String = ""
) {
    val durationLabel: String
        get() = formatDuration(durationMs)

    val formatLabel: String
        get() = extension.ifBlank { mimeType.substringAfter('/', "audio") }.uppercase(Locale.US)
}

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val paired: Boolean
)

data class PlayerUiState(
    val song: Song? = null,
    val library: List<Song> = emptyList(),
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val history: List<Song> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val isPlaying: Boolean = false,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val isScanningLibrary: Boolean = false,
    val actionMessage: String = "Ready to scan local audio",
    val scanSummary: String = "No local library loaded",
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val eqBands: List<Float> = List(10) { 0f },
    val bassBoost: Float = 0.5f,
    val midBoost: Float = 0.5f,
    val trebleBoost: Float = 0.5f,
    val loudnessEnabled: Boolean = false,
    val maxVolumeEnabled: Boolean = false,
    val supportedExtensions: List<String> = SupportedAudioExtensions
)

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}
