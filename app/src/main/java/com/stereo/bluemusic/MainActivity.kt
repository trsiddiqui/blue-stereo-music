package com.stereo.bluemusic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stereo.bluemusic.data.HarnessDiagnosticUiState
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.ui.CarPlayerScreen
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), SensorEventListener {
    private val vm: MainViewModel by viewModels()
    private var diagnosticState by mutableStateOf(HarnessDiagnosticUiState())
    private var mediaPlayer: MediaPlayer? = null
    private var playerPrepared = false
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private lateinit var sensorManager: SensorManager
    private var obdSocket: BluetoothSocket? = null
    private var obdInput: InputStream? = null
    private var obdOutput: OutputStream? = null
    private var obdPollingJob: Job? = null
    private val obdLock = Any()

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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runHarnessDiagnostic("gps_speed")
        } else {
            showDiagnosticResult("GPS speed", "Location permission was not allowed. GPS speed cannot be read.")
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runHarnessDiagnostic("obd_connect")
        } else {
            showDiagnosticResult("OBD Bluetooth", "Bluetooth connect permission was not allowed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        registerDiagnosticsSensors()

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
                    diagnostics = diagnosticState,
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
                    },
                    onRunHarnessTest = { runHarnessDiagnostic(it) },
                    onDismissHarnessResult = { diagnosticState = diagnosticState.copy(resultOpen = false) }
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
        sensorManager.unregisterListener(this)
        closeObdConnection()
        releasePlayer()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val key = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic field"
            Sensor.TYPE_GRAVITY -> "Gravity"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
            Sensor.TYPE_LIGHT -> "Cabin light"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_PRESSURE -> "Pressure"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temp"
            else -> event.sensor.name
        }
        val value = when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> "${event.values.firstOrNull()?.format(1) ?: "--"} lx"
            Sensor.TYPE_PROXIMITY -> "${event.values.firstOrNull()?.format(1) ?: "--"} cm"
            Sensor.TYPE_PRESSURE -> "${event.values.firstOrNull()?.format(1) ?: "--"} hPa"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "${event.values.firstOrNull()?.format(1) ?: "--"} C"
            else -> event.values.take(3).joinToString(" / ") { it.format(2) }
        }
        diagnosticState = diagnosticState.copy(liveValues = diagnosticState.liveValues + (key to value))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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

    private fun registerDiagnosticsSensors() {
        val sensorTypes = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_AMBIENT_TEMPERATURE
        )
        sensorTypes.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
            }
        }
        val available = sensorTypes.mapNotNull { type ->
            sensorManager.getDefaultSensor(type)?.let { it.name }
        }
        diagnosticState = diagnosticState.copy(
            liveValues = diagnosticState.liveValues + ("Sensor count" to available.size.toString())
        )
    }

    private fun runHarnessDiagnostic(testId: String) {
        if (testId.startsWith("obd_") && !hasBluetoothConnectPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            showDiagnosticResult("OBD Bluetooth", "Requesting Bluetooth permission. Run the OBD test again after allowing it.")
            return
        }

        when (testId) {
            "obd_devices" -> showDiagnosticResult("Paired OBD devices", pairedObdDevicesDiagnostic())
            "obd_connect" -> connectObdDongle()
            "obd_disconnect" -> {
                closeObdConnection()
                showDiagnosticResult("OBD disconnected", "Closed the ELM327 Bluetooth connection.")
            }
            "obd_protocol" -> runObdCommandDiagnostic("OBD protocol", "ATDP")
            "obd_supported" -> readSupportedObdPids()
            "obd_read_once" -> readObdLiveValuesOnce(showResult = true)
            "obd_dtcs" -> runObdCommandDiagnostic("Diagnostic trouble codes", "03") { decodeDtcResponse(it) }
            "obd_clear_dtcs" -> runObdCommandDiagnostic("Clear DTCs", "04") { response ->
                "Sent Mode 04 clear command. This can clear stored emissions trouble codes and freeze-frame data if the vehicle accepts it.\n\nRaw response:\n$response"
            }
            "obd_vin" -> runObdCommandDiagnostic("VIN", "0902") { decodeVinResponse(it) }
            "ford_escape_2008" -> showDiagnosticResult(
                "2008 Ford Escape harness",
                "Likely hardwire/interface outputs: RAP/ACC, VSS speed-sense, illumination/dimmer, reverse trigger, parking brake, steering wheel controls, factory amp turn-on/retention, and SYNC/RSE/RSC retention when the interface supports it.\n\nWith the Bluetooth ELM327 OBD-II dongle, expect standard powertrain data: RPM, vehicle speed, coolant temp, throttle, engine load, intake temp, MAP, MAF, fuel trims, fuel level if supported, module voltage, VIN, emissions readiness, and diagnostic trouble codes. Door/lock/window/seat controls are not standard OBD-II and usually require Ford/MS-CAN hardware and manufacturer-specific PIDs."
            )
            "reverse" -> showHarnessProbe(
                title = "Reverse trigger",
                keywords = listOf("reverse", "backcar", "back_car", "rear", "gear"),
                note = "Many harnesses expose reverse as a 12 V trigger for backup camera. Android apps can only detect it if the head-unit MCU publishes a property/broadcast or switches the camera app."
            )
            "parking_brake" -> showHarnessProbe(
                title = "Parking brake",
                keywords = listOf("park", "brake", "parking"),
                note = "Navigation/video radios often receive a parking-brake input. On Android head units this is usually MCU-handled, not a standard Android API."
            )
            "illumination" -> showHarnessProbe(
                title = "Illumination / dimmer",
                keywords = listOf("illum", "dimmer", "light", "lamp", "night"),
                note = "The orange/white illumination input may dim the panel. If supported, it may appear as night mode, brightness, or MCU light status."
            )
            "vss" -> showHarnessProbe(
                title = "VSS / speed sense",
                keywords = listOf("vss", "speed", "vehicle_speed", "car_speed"),
                note = "Ford interfaces can provide VSS/speed-sense for navigation. A generic Android app cannot read the pulse wire unless the MCU/CAN decoder forwards it."
            )
            "swc" -> showHarnessProbe(
                title = "Steering wheel controls",
                keywords = listOf("swc", "key", "key1", "key2", "steer", "wheel"),
                note = "Hardwired Key1/Key2 or CAN steering controls usually arrive as media key events or through the vendor key-learning app. Press wheel buttons while this page is open and watch whether the media player reacts."
            )
            "acc_rap" -> showHarnessProbe(
                title = "ACC / RAP",
                keywords = listOf("acc", "ign", "ignition", "rap", "accessory"),
                note = "ACC/RAP is normally power-management handled by the radio MCU. Android may only see it indirectly through sleep/wake or vendor properties."
            )
            "amp_antenna" -> showHarnessProbe(
                title = "Amp / antenna remote",
                keywords = listOf("amp", "antenna", "remote", "ant"),
                note = "Blue/white amp turn-on and power antenna are usually outputs from the stereo, not readable inputs. This test checks whether the vendor exposes any related properties."
            )
            "getprop_car" -> showDiagnosticResult("MCU / CAN properties", readFilteredProperties())
            "device_nodes" -> showDiagnosticResult("Serial / CAN device nodes", scanDeviceNodes())
            "usb_devices" -> showDiagnosticResult("USB devices", scanUsbDevices())
            "common_packages" -> showDiagnosticResult("Common car packages", scanCommonPackages())
            "volume_max" -> {
                applyMaxOutput(true)
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                showDiagnosticResult("Media volume", "Requested media volume max.\nCurrent: $current / $max")
            }
            "bluetooth" -> showDiagnosticResult("Bluetooth", bluetoothDiagnostic())
            "gps_speed" -> gpsSpeedDiagnostic()
            "open_settings" -> openBestCarSettings()
            else -> showDiagnosticResult("Unknown test", "No diagnostic runner exists for $testId.")
        }
    }

    private fun showHarnessProbe(title: String, keywords: List<String>, note: String) {
        val properties = readFilteredProperties(keywords)
        val nodes = scanDeviceNodes(keywords)
        showDiagnosticResult(
            title,
            "$note\n\nProperty probe:\n$properties\n\nDevice-node probe:\n$nodes"
        )
    }

    private fun showDiagnosticResult(title: String, body: String) {
        diagnosticState = diagnosticState.copy(
            resultTitle = title,
            resultBody = body.ifBlank { "No matching result." },
            resultOpen = true
        )
    }

    private fun readFilteredProperties(
        keywords: List<String> = listOf("mcu", "can", "car", "reverse", "back", "gear", "speed", "vss", "acc", "illum", "light", "brake", "swc", "key", "door", "radar", "obd")
    ): String {
        val lowerKeywords = keywords.map { it.lowercase(Locale.US) }
        val output = runCatching {
            ProcessBuilder("getprop")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
        }.getOrElse { error -> return "Could not run getprop: ${error.message}" }

        val matches = output
            .lineSequence()
            .filter { line -> lowerKeywords.any { key -> line.lowercase(Locale.US).contains(key) } }
            .take(80)
            .toList()

        return matches.ifEmpty {
            listOf("No matching public Android properties found. The MCU/CAN decoder may not expose this signal to apps.")
        }.joinToString("\n")
    }

    private fun scanDeviceNodes(
        keywords: List<String> = listOf("can", "tty", "mcu", "uart", "i2c", "obd")
    ): String {
        val devDir = File("/dev")
        if (!devDir.exists()) return "/dev is not visible."
        val lowerKeywords = keywords.map { it.lowercase(Locale.US) }
        val matches = devDir.listFiles()
            ?.filter { file -> lowerKeywords.any { key -> file.name.lowercase(Locale.US).contains(key) } }
            ?.sortedBy { it.name }
            ?.take(80)
            .orEmpty()

        if (matches.isEmpty()) {
            return "No likely serial/CAN/MCU device nodes found in /dev."
        }

        return matches.joinToString("\n") { file ->
            "${file.absolutePath}  read=${file.canRead()} write=${file.canWrite()}"
        }
    }

    private fun scanUsbDevices(): String {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usbManager.deviceList.values.toList()
        if (devices.isEmpty()) {
            return "No USB devices currently exposed through Android USB host."
        }
        return devices.joinToString("\n") { device ->
            "VID=${device.vendorId} PID=${device.productId} class=${device.deviceClass} name=${device.deviceName} interfaces=${device.interfaceCount}"
        }
    }

    private fun scanCommonPackages(): String {
        val packages = listOf(
            "com.ts.MainUI",
            "com.ts.canbus",
            "com.syu.ms",
            "com.syu.canbus",
            "com.microntek.canbus",
            "com.microntek.controlsettings",
            "com.car.canbus",
            "com.canbus",
            "com.android.settings"
        )
        return packages.joinToString("\n") { packageName ->
            val installed = runCatching {
                packageManager.getPackageInfo(packageName, 0)
            }.isSuccess
            "$packageName: ${if (installed) "installed" else "not visible"}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDiagnostic(): String {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return "Bluetooth adapter not available."
        val canRead = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        return if (canRead) {
            "Enabled: ${adapter.isEnabled}\nName: ${runCatching { adapter.name }.getOrNull().orEmpty().ifBlank { "Unknown" }}\nBonded devices: ${runCatching { adapter.bondedDevices.size }.getOrDefault(0)}"
        } else {
            "Bluetooth permission is not granted, so only limited state can be read."
        }
    }

    @SuppressLint("MissingPermission")
    private fun gpsSpeedDiagnostic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            showDiagnosticResult("GPS speed", "Requesting location permission. Run this test again after allowing it.")
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = locationManager.getProviders(true)
        val locations = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        val best = locations.maxByOrNull { it.time }
        val speed = best?.takeIf { it.hasSpeed() }?.speed?.let { metersPerSecond ->
            val kmh = metersPerSecond * 3.6f
            val mph = metersPerSecond * 2.236936f
            "${kmh.format(1)} km/h / ${mph.format(1)} mph"
        } ?: "No speed fix yet"
        showDiagnosticResult(
            "GPS speed",
            "Enabled providers: ${providers.joinToString().ifBlank { "none" }}\nLast speed: $speed\nLast provider: ${best?.provider ?: "none"}\nNote: Ford VSS speed-sense is separate from GPS and requires MCU/CAN forwarding to become app-readable."
        )
    }

    private fun openBestCarSettings() {
        val packageCandidates = listOf(
            "com.microntek.controlsettings",
            "com.ts.MainUI",
            "com.syu.ms",
            "com.android.settings"
        )
        for (packageName in packageCandidates) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                runCatching {
                    startActivity(launchIntent)
                    showDiagnosticResult("Open settings", "Opened $packageName.")
                }.onSuccess { return }
            }
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            showDiagnosticResult("Open settings", "Opened Android system settings.")
        }.onFailure { error ->
            showDiagnosticResult("Open settings", "Could not open settings: ${error.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun pairedObdDevicesDiagnostic(): String {
        if (!hasBluetoothConnectPermission()) return "Bluetooth connect permission is not granted."
        val devices = pairedObdDevices()
        if (devices.isEmpty()) {
            return "No paired Bluetooth devices found. Pair the ELM327/OBDII adapter in Android Bluetooth settings first. Common PINs are 1234 or 0000."
        }
        return devices.joinToString("\n") { device ->
            "${device.name.orEmpty().ifBlank { "Unknown" }}  ${device.address}"
        }
    }

    @SuppressLint("MissingPermission")
    private fun pairedObdDevices(): List<BluetoothDevice> {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: return emptyList()
        if (!hasBluetoothConnectPermission()) return emptyList()
        val bonded = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
        val likelyObd = bonded.filter { device ->
            val label = "${device.name.orEmpty()} ${device.address}".lowercase(Locale.US)
            listOf("obd", "elm", "vlink", "v-link", "veepeak", "carista", "scanner", "scan", "diagnostic").any { label.contains(it) }
        }
        return likelyObd.ifEmpty { bonded }
    }

    private fun connectObdDongle() {
        lifecycleScope.launch {
            showDiagnosticResult("OBD connect", "Connecting to paired ELM327 Bluetooth adapter...")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    closeObdConnection()
                    val device = pairedObdDevices().firstOrNull()
                        ?: return@runCatching "No paired Bluetooth OBD device found. Pair the dongle in Android settings first, then run this test."

                    val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                        ?: BluetoothAdapter.getDefaultAdapter()
                    runCatching { adapter?.cancelDiscovery() }

                    val socket = runCatching {
                        device.createRfcommSocketToServiceRecord(OBD_SPP_UUID).apply { connect() }
                    }.getOrElse {
                        device.createInsecureRfcommSocketToServiceRecord(OBD_SPP_UUID).apply { connect() }
                    }

                    obdSocket = socket
                    obdInput = socket.inputStream
                    obdOutput = socket.outputStream

                    val init = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0").joinToString("\n") { command ->
                        "$command -> ${sendObdCommandBlocking(command, timeoutMs = 3_500).cleanObdResponse()}"
                    }
                    val protocol = sendObdCommandBlocking("ATDP").cleanObdResponse()
                    updateLiveValues(
                        "OBD status" to "Connected: ${device.name.orEmpty().ifBlank { device.address }}",
                        "OBD protocol" to protocol.ifBlank { "Auto" }
                    )
                    startObdPolling()
                    "Connected to ${device.name.orEmpty().ifBlank { device.address }}.\n\n$init\nATDP -> $protocol"
                }.getOrElse { error ->
                    closeObdConnection()
                    "Connection failed: ${error.message}\n\nPair the dongle first, keep ignition on, and make sure no other app is connected to the adapter."
                }
            }
            showDiagnosticResult("OBD connect", result)
        }
    }

    private fun startObdPolling() {
        obdPollingJob?.cancel()
        obdPollingJob = lifecycleScope.launch {
            while (isActive && obdSocket?.isConnected == true) {
                val values = withContext(Dispatchers.IO) { readObdLiveValuesBlocking() }
                updateLiveValues(*values.toList().toTypedArray())
                delay(2_000L)
            }
        }
    }

    private fun readObdLiveValuesOnce(showResult: Boolean) {
        lifecycleScope.launch {
            val values = withContext(Dispatchers.IO) { readObdLiveValuesBlocking() }
            updateLiveValues(*values.toList().toTypedArray())
            if (showResult) {
                showDiagnosticResult(
                    "OBD live read",
                    values.entries.joinToString("\n") { "${it.key}: ${it.value}" }.ifBlank { "No live PID values returned." }
                )
            }
        }
    }

    private fun readObdLiveValuesBlocking(): Map<String, String> {
        return runCatching {
            ensureObdReady()
            OBD_LIVE_PIDS.mapNotNull { pid ->
                val raw = sendObdCommandBlocking("01${pid.pid}")
                pid.decode(raw)?.let { decoded -> pid.liveKey to decoded }
            }.toMap()
        }.getOrElse { error ->
            mapOf("OBD status" to "Read failed: ${error.message}")
        }
    }

    private fun readSupportedObdPids() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ensureObdReady()
                    listOf("0100", "0120", "0140", "0160").joinToString("\n\n") { command ->
                        val raw = sendObdCommandBlocking(command)
                        "$command supported block:\n${raw.cleanObdResponse()}"
                    }
                }.getOrElse { error -> "Could not read supported PIDs: ${error.message}" }
            }
            showDiagnosticResult("Supported OBD PIDs", result)
        }
    }

    private fun runObdCommandDiagnostic(
        title: String,
        command: String,
        formatter: (String) -> String = { it.cleanObdResponse() }
    ) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    ensureObdReady()
                    formatter(sendObdCommandBlocking(command, timeoutMs = 4_500))
                }.getOrElse { error -> "OBD command failed: ${error.message}" }
            }
            showDiagnosticResult(title, result)
        }
    }

    private fun ensureObdReady() {
        if (obdSocket?.isConnected != true || obdInput == null || obdOutput == null) {
            throw IllegalStateException("OBD adapter is not connected. Tap Connect OBD first.")
        }
    }

    private fun sendObdCommandBlocking(command: String, timeoutMs: Long = 2_500): String {
        return synchronized(obdLock) {
            val input = obdInput ?: throw IllegalStateException("OBD input stream is closed")
            val output = obdOutput ?: throw IllegalStateException("OBD output stream is closed")
            output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
            output.flush()

            val deadline = System.currentTimeMillis() + timeoutMs
            val response = StringBuilder()
            while (System.currentTimeMillis() < deadline) {
                val available = runCatching { input.available() }.getOrDefault(0)
                if (available > 0) {
                    repeat(available) {
                        val byte = input.read()
                        if (byte >= 0) {
                            val char = byte.toChar()
                            if (char == '>') return@synchronized response.toString()
                            response.append(char)
                        }
                    }
                } else {
                    Thread.sleep(25L)
                }
            }
            response.toString()
        }
    }

    private fun closeObdConnection() {
        obdPollingJob?.cancel()
        obdPollingJob = null
        runCatching { obdInput?.close() }
        runCatching { obdOutput?.close() }
        runCatching { obdSocket?.close() }
        obdInput = null
        obdOutput = null
        obdSocket = null
        updateLiveValues("OBD status" to "Disconnected")
    }

    private fun updateLiveValues(vararg values: Pair<String, String>) {
        if (values.isEmpty()) return
        diagnosticState = diagnosticState.copy(liveValues = diagnosticState.liveValues + values.toMap())
    }

    private fun ObdPid.decode(raw: String): String? {
        val data = obdDataBytes(raw, pid) ?: return null
        return runCatching { decoder(data) }.getOrNull()
    }

    private fun obdDataBytes(raw: String, pid: String): List<Int>? {
        val bytes = raw.hexBytes()
        val pidValue = pid.toInt(16)
        val index = bytes.windowed(2).indexOfFirst { it[0] == 0x41 && it[1] == pidValue }
        if (index < 0) return null
        return bytes.drop(index + 2)
    }

    private fun decodeVinResponse(raw: String): String {
        val bytes = raw.hexBytes()
        val vinChars = mutableListOf<Int>()
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == 0x49 && i + 2 < bytes.size && bytes[i + 1] == 0x02) {
                val start = i + 3
                val end = minOf(i + 10, bytes.size)
                vinChars += bytes.subList(start, end).filter { it in 0x20..0x7E }
                i = end
            } else {
                i += 1
            }
        }
        val vin = vinChars.map { it.toChar() }.joinToString("").filter { it.isLetterOrDigit() }.takeLast(17)
        return if (vin.length >= 11) "VIN: $vin\n\nRaw:\n${raw.cleanObdResponse()}" else "VIN not returned.\n\nRaw:\n${raw.cleanObdResponse()}"
    }

    private fun decodeDtcResponse(raw: String): String {
        val bytes = raw.hexBytes()
        val index = bytes.indexOf(0x43)
        if (index < 0) return "No Mode 03 DTC response.\n\nRaw:\n${raw.cleanObdResponse()}"
        val codes = bytes.drop(index + 1)
            .chunked(2)
            .mapNotNull { pair ->
                if (pair.size < 2 || (pair[0] == 0 && pair[1] == 0)) null else decodeDtc(pair[0], pair[1])
            }
        return if (codes.isEmpty()) {
            "No stored powertrain DTCs returned.\n\nRaw:\n${raw.cleanObdResponse()}"
        } else {
            "Stored DTCs:\n${codes.joinToString("\n")}\n\nRaw:\n${raw.cleanObdResponse()}"
        }
    }

    private fun decodeDtc(a: Int, b: Int): String {
        val first = when ((a and 0xC0) shr 6) {
            0 -> "P"
            1 -> "C"
            2 -> "B"
            else -> "U"
        }
        val digit1 = (a and 0x30) shr 4
        val digit2 = a and 0x0F
        val digit3 = (b and 0xF0) shr 4
        val digit4 = b and 0x0F
        return "$first$digit1$digit2$digit3$digit4"
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
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

    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(Locale.US, this)
    }

    companion object {
        private const val TEN_SECONDS_MS = 10_000L
        private const val PROGRESS_TICK_MS = 500L
        private const val LOUDNESS_GAIN_MB = 1_100
        private const val MAX_OUTPUT_GAIN_MB = 2_000
        private val OBD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

private data class ObdPid(
    val pid: String,
    val liveKey: String,
    val decoder: (List<Int>) -> String
)

private val OBD_LIVE_PIDS = listOf(
    ObdPid("0C", "OBD RPM") { data -> "${(((data[0] * 256) + data[1]) / 4f).format(0)} rpm" },
    ObdPid("0D", "OBD speed") { data -> "${data[0]} km/h / ${(data[0] * 0.621371f).format(1)} mph" },
    ObdPid("05", "OBD coolant") { data -> "${data[0] - 40} C" },
    ObdPid("04", "OBD engine load") { data -> "${(data[0] / 2.55f).format(1)}%" },
    ObdPid("11", "OBD throttle") { data -> "${(data[0] / 2.55f).format(1)}%" },
    ObdPid("0F", "OBD intake temp") { data -> "${data[0] - 40} C" },
    ObdPid("0B", "OBD MAP") { data -> "${data[0]} kPa" },
    ObdPid("10", "OBD MAF") { data -> "${(((data[0] * 256) + data[1]) / 100f).format(2)} g/s" },
    ObdPid("06", "OBD STFT B1") { data -> "${(data[0] / 1.28f - 100f).format(1)}%" },
    ObdPid("07", "OBD LTFT B1") { data -> "${(data[0] / 1.28f - 100f).format(1)}%" },
    ObdPid("08", "OBD STFT B2") { data -> "${(data[0] / 1.28f - 100f).format(1)}%" },
    ObdPid("09", "OBD LTFT B2") { data -> "${(data[0] / 1.28f - 100f).format(1)}%" },
    ObdPid("0E", "OBD timing") { data -> "${(data[0] / 2f - 64f).format(1)} deg" },
    ObdPid("1F", "OBD run time") { data -> "${(data[0] * 256) + data[1]} s" },
    ObdPid("2F", "OBD fuel level") { data -> "${(data[0] / 2.55f).format(1)}%" },
    ObdPid("33", "OBD barometer") { data -> "${data[0]} kPa" },
    ObdPid("42", "OBD module volts") { data -> "${(((data[0] * 256) + data[1]) / 1000f).format(2)} V" },
    ObdPid("46", "OBD ambient temp") { data -> "${data[0] - 40} C" },
    ObdPid("5C", "OBD oil temp") { data -> "${data[0] - 40} C" }
)

private fun String.hexBytes(): List<Int> {
    return Regex("\\b[0-9A-Fa-f]{2}\\b")
        .findAll(this)
        .map { it.value.toInt(16) }
        .toList()
}

private fun String.cleanObdResponse(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { line ->
            val normalized = line.uppercase(Locale.US)
            normalized == ">" ||
                normalized == "SEARCHING..." ||
                normalized.startsWith("AT") ||
                normalized.matches(Regex("^01[0-9A-F]{2}$")) ||
                normalized.matches(Regex("^09[0-9A-F]{2}$"))
        }
        .joinToString("\n")
        .ifBlank { trim() }
}

private fun Float.format(decimals: Int): String {
    return "%.${decimals}f".format(Locale.US, this)
}
