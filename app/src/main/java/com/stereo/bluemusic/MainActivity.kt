package com.stereo.bluemusic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.ui.CarPlayerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var mediaPlayer: MediaPlayer? = null
    private var playerPrepared = false
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val audioManager: AudioManager
        get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.scanLocalLibrary()
        } else {
            vm.showAction("Audio permission was not allowed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val ui by vm.uiState.collectAsState()
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF69F7E4),
                    secondary = Color(0xFFFFB84D),
                    background = Color(0xFF05080B),
                    surface = Color(0xFF101820),
                    onPrimary = Color(0xFF02100F),
                    onSecondary = Color(0xFF1A1000),
                    onBackground = Color(0xFFF3FBFF),
                    onSurface = Color(0xFFF3FBFF)
                )
            ) {
                CarPlayerScreen(
                    ui = ui,
                    onScanLibrary = { scanLocalAudio() },
                    onSongSelected = { playSong(it) },
                    onPlayPause = { togglePlayPause() },
                    onPrevious = { previousTrack() },
                    onNext = { nextTrack() },
                    onSeek = { seekTo(it) },
                    onRewind = { jumpBy(-TEN_SECONDS_MS) },
                    onForward = { jumpBy(TEN_SECONDS_MS) },
                    onToggleFavorite = { vm.toggleFavorite() },
                    onToggleSongFavorite = { vm.toggleFavorite(it) },
                    onToggleShuffle = { vm.toggleShuffle() },
                    onCycleRepeat = { vm.cycleRepeatMode() },
                    onRandomPlaylist = { playRandomPlaylist() },
                    onPlayFavorites = { playFavorites() },
                    onEqBandChange = { index, value ->
                        vm.setEqBand(index, value)
                        applyAudioEffects()
                    },
                    onBassChange = {
                        vm.setTone(bass = it)
                        applyAudioEffects()
                    },
                    onMidChange = {
                        vm.setTone(mid = it)
                        applyAudioEffects()
                    },
                    onTrebleChange = {
                        vm.setTone(treble = it)
                        applyAudioEffects()
                    },
                    onToggleLoudness = {
                        vm.toggleLoudness()
                        applyAudioEffects()
                    },
                    onToggleMaxOutput = {
                        val enabled = vm.toggleMaxOutput()
                        applyMaxOutput(enabled)
                        applyAudioEffects()
                    }
                )
            }
        }

        if (hasMediaPermission()) {
            vm.scanLocalLibrary()
        }
        startProgressTicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    private fun scanLocalAudio() {
        if (hasMediaPermission()) {
            vm.scanLocalLibrary()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPermissionLauncher.launch(mediaPermission())
        } else {
            vm.scanLocalLibrary()
        }
    }

    private fun playSong(song: Song) {
        vm.startSong(song)
        prepareAndPlay(song)
    }

    private fun togglePlayPause() {
        val player = mediaPlayer
        if (playerPrepared && player != null) {
            val isPlaying = runCatching { player.isPlaying }.getOrDefault(false)
            if (isPlaying) {
                runCatching { player.pause() }
                vm.setPlaying(false)
            } else {
                runCatching { player.start() }
                vm.setPlaying(true)
            }
            return
        }

        val firstPlayable = vm.uiState.value.song
            ?: vm.uiState.value.queue.firstOrNull()
            ?: vm.uiState.value.library.firstOrNull()

        if (firstPlayable != null) {
            playSong(firstPlayable)
        } else {
            scanLocalAudio()
        }
    }

    private fun previousTrack() {
        vm.previousTrack()?.let { prepareAndPlay(it) }
    }

    private fun nextTrack() {
        vm.nextTrack(userInitiated = true)?.let { prepareAndPlay(it) }
    }

    private fun playRandomPlaylist() {
        vm.startRandomPlaylist()?.let { prepareAndPlay(it) }
    }

    private fun playFavorites() {
        vm.playFavorites()?.let { prepareAndPlay(it) }
    }

    private fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        if (!playerPrepared) return

        val duration = runCatching { player.duration }.getOrDefault(0).coerceAtLeast(0)
        val clamped = positionMs.coerceIn(0L, duration.toLong())
        runCatching { player.seekTo(clamped.toInt()) }
        vm.updateProgress(clamped, duration.toLong())
    }

    private fun jumpBy(deltaMs: Long) {
        val player = mediaPlayer ?: return
        if (!playerPrepared) return

        val current = runCatching { player.currentPosition }.getOrDefault(0).toLong()
        seekTo(current + deltaMs)
    }

    private fun prepareAndPlay(song: Song) {
        if (song.uri.isBlank()) {
            vm.showAction("Track has no playable local URI")
            return
        }

        releasePlayer()
        playerPrepared = false

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener { player ->
                playerPrepared = true
                vm.markPrepared(player.duration.toLong())
                attachAudioEffects(player.audioSessionId)
                applyMaxOutput(vm.uiState.value.maxVolumeEnabled)
                runCatching { player.start() }
                    .onSuccess { vm.setPlaying(true) }
                    .onFailure {
                        vm.setPlaying(false)
                        vm.showAction("Could not start playback")
                    }
            }
            setOnCompletionListener {
                val next = vm.trackAfterCompletion()
                if (next != null) {
                    prepareAndPlay(next)
                } else {
                    vm.stopAtQueueEnd()
                    releasePlayer()
                }
            }
            setOnErrorListener { _, _, _ ->
                vm.showAction("Could not play ${song.title}")
                vm.setPlaying(false)
                true
            }

            runCatching {
                setDataSource(this@MainActivity, Uri.parse(song.uri))
                prepareAsync()
            }.onFailure {
                vm.showAction("Could not open ${song.title}")
                vm.setPlaying(false)
                releasePlayer()
            }
        }
    }

    private fun startProgressTicker() {
        lifecycleScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (playerPrepared && player != null) {
                    runCatching {
                        vm.updateProgress(
                            progressMs = player.currentPosition.toLong(),
                            durationMs = player.duration.toLong()
                        )
                    }
                }
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        equalizer = runCatching {
            Equalizer(0, audioSessionId).apply { enabled = true }
        }.getOrNull()
        bassBoost = runCatching {
            BassBoost(0, audioSessionId).apply { enabled = true }
        }.getOrNull()
        loudnessEnhancer = runCatching {
            LoudnessEnhancer(audioSessionId)
        }.getOrNull()
        applyAudioEffects()
    }

    private fun applyAudioEffects() {
        val state = vm.uiState.value
        applyEqualizer(state)
        applyBassBoost(state)
        applyLoudness(state)
    }

    private fun applyEqualizer(state: PlayerUiState) {
        val effect = equalizer ?: return
        runCatching {
            effect.enabled = true
            val bandCount = effect.numberOfBands.toInt().coerceAtLeast(1)
            val levelRange = effect.bandLevelRange
            val minLevel = levelRange[0].toInt()
            val maxLevel = levelRange[1].toInt()

            repeat(bandCount) { band ->
                val uiBandIndex = ((band.toFloat() / (bandCount - 1).coerceAtLeast(1)) * (state.eqBands.lastIndex))
                    .roundToInt()
                    .coerceIn(state.eqBands.indices)
                val toneOffset = toneOffsetFor(uiBandIndex, state)
                val targetDb = (state.eqBands[uiBandIndex] + toneOffset).coerceIn(MainViewModel.EQ_MIN_DB, MainViewModel.EQ_MAX_DB)
                val normalized = (targetDb - MainViewModel.EQ_MIN_DB) / (MainViewModel.EQ_MAX_DB - MainViewModel.EQ_MIN_DB)
                val level = (minLevel + (maxLevel - minLevel) * normalized).roundToInt().toShort()
                effect.setBandLevel(band.toShort(), level)
            }
        }
    }

    private fun applyBassBoost(state: PlayerUiState) {
        val boost = bassBoost ?: return
        runCatching {
            boost.enabled = true
            boost.setStrength((state.bassBoost * 1000f).roundToInt().coerceIn(0, 1000).toShort())
        }
    }

    private fun applyLoudness(state: PlayerUiState) {
        val enhancer = loudnessEnhancer ?: return
        runCatching {
            val enabled = state.loudnessEnabled || state.maxVolumeEnabled
            enhancer.enabled = enabled
            enhancer.setTargetGain(if (state.maxVolumeEnabled) MAX_OUTPUT_GAIN_MB else LOUDNESS_GAIN_MB)
        }
    }

    private fun toneOffsetFor(uiBandIndex: Int, state: PlayerUiState): Float {
        return when (uiBandIndex) {
            0, 1, 2 -> (state.bassBoost - 0.5f) * 10f
            3, 4, 5, 6 -> (state.midBoost - 0.5f) * 8f
            else -> (state.trebleBoost - 0.5f) * 10f
        }
    }

    private fun applyMaxOutput(enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI)
        }
    }

    private fun releasePlayer() {
        releaseAudioEffects()
        runCatching {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        playerPrepared = false
    }

    private fun releaseAudioEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
    }

    private fun hasMediaPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return ContextCompat.checkSelfPermission(this, mediaPermission()) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("InlinedApi")
    private fun mediaPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    companion object {
        private const val TEN_SECONDS_MS = 10_000L
        private const val PROGRESS_TICK_MS = 500L
        private const val LOUDNESS_GAIN_MB = 1_100
        private const val MAX_OUTPUT_GAIN_MB = 2_000
    }
}
