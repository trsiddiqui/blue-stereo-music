package com.stereo.bluemusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stereo.bluemusic.data.HarnessDiagnosticUiState
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.RepeatMode
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.data.formatDuration
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CarPlayerScreen(
    ui: PlayerUiState,
    diagnostics: HarnessDiagnosticUiState,
    onScanLibrary: () -> Unit,
    onSongSelected: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleSongFavorite: (Song) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRandomPlaylist: () -> Unit,
    onPlayFavorites: () -> Unit,
    onEqBandChange: (Int, Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onToggleLoudness: () -> Unit,
    onToggleMaxOutput: () -> Unit,
    onRunHarnessTest: (String) -> Unit,
    onDismissHarnessResult: () -> Unit
) {
    var listModeName by rememberSaveable { mutableStateOf(ListMode.Library.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var showHarnessDiagnostics by rememberSaveable { mutableStateOf(false) }
    val listMode = remember(listModeName) { ListMode.valueOf(listModeName) }
    val favourites = remember(ui.library, ui.favorites) { ui.library.filter { it.id in ui.favorites } }
    val songs = remember(ui.library, ui.queue, favourites, listMode, query) {
        val source = when (listMode) {
            ListMode.Library -> ui.library
            ListMode.Favorites -> favourites
            ListMode.Queue -> ui.queue
        }
        source.filterBy(query)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020407),
                        Color(0xFF071017),
                        Color(0xFF101821)
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0x2219E7D4),
                            Color.Transparent,
                            Color(0x18FFB84D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NowPlayingPanel(
                    ui = ui,
                    onSeek = onSeek,
                    modifier = Modifier
                        .weight(0.86f)
                        .fillMaxHeight()
                )
                LibraryConsole(
                    ui = ui,
                    listMode = listMode,
                    songs = songs,
                    query = query,
                    onQueryChange = { query = it },
                    onModeChange = { listModeName = it.name },
                    onScanLibrary = onScanLibrary,
                    onSongSelected = onSongSelected,
                    onToggleSongFavorite = onToggleSongFavorite,
                    modifier = Modifier
                        .weight(1.34f)
                        .fillMaxHeight()
                )
                EqualizerRail(
                    ui = ui,
                    onEqBandChange = onEqBandChange,
                    onBassChange = onBassChange,
                    onMidChange = onMidChange,
                    onTrebleChange = onTrebleChange,
                    onToggleLoudness = onToggleLoudness,
                    onToggleMaxOutput = onToggleMaxOutput,
                    modifier = Modifier
                        .weight(0.74f)
                        .fillMaxHeight()
                )
            }

            TransportBar(
                ui = ui,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onRewind = onRewind,
                onForward = onForward,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onRandomPlaylist = onRandomPlaylist,
                onPlayFavorites = onPlayFavorites,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
            )
        }

        FloatingDiagnosticsButton(
            onClick = { showHarnessDiagnostics = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
        )

        if (showHarnessDiagnostics) {
            HarnessDiagnosticsPage(
                diagnostics = diagnostics,
                onRunTest = onRunHarnessTest,
                onDismissResult = onDismissHarnessResult,
                onClose = { showHarnessDiagnostics = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FloatingDiagnosticsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Amber, Color(0xFF472700), Color(0xFF080D12))))
            .border(1.dp, Amber.copy(alpha = 0.78f), CircleShape)
    ) {
        Icon(
            Icons.Rounded.DirectionsCar,
            contentDescription = "Open harness diagnostics",
            tint = Color(0xFF201200),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun HarnessDiagnosticsPage(
    diagnostics: HarnessDiagnosticUiState,
    onRunTest: (String) -> Unit,
    onDismissResult: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tiles = remember(diagnostics.liveValues) { diagnosticTiles(diagnostics.liveValues) }

    Surface(modifier = modifier, color = Color(0xF805080B)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = Amber, modifier = Modifier.size(34.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Harness and OBD diagnostics", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Ford Escape 2008 plus Bluetooth ELM327 and Android stereo MCU/CAN probes",
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close diagnostics", tint = Ink)
                }
            }

            if (diagnostics.resultOpen) {
                DiagnosticResultPanel(
                    title = diagnostics.resultTitle,
                    body = diagnostics.resultBody,
                    onDismiss = onDismissResult
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 178.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(tiles, key = { it.id }) { tile ->
                    DiagnosticTileCell(tile = tile, onRunTest = onRunTest)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticResultPanel(title: String, body: String, onDismiss: () -> Unit) {
    Surface(color = Color(0xEE101820), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Amber, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close result", tint = Muted)
                }
            }
            Text(
                body,
                color = Ink,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticTileCell(tile: DiagnosticTile, onRunTest: (String) -> Unit) {
    val testId = tile.testId
    val clickable = testId != null
    Surface(
        modifier = Modifier
            .height(124.dp)
            .then(if (clickable) Modifier.clickable { onRunTest(testId.orEmpty()) } else Modifier),
        color = if (tile.liveValue != null) Color(0xDD10222A) else Color(0xDD101820),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (tile.liveValue != null) Cyan.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(tile.icon, contentDescription = null, tint = tile.accent, modifier = Modifier.size(22.dp))
                Text(tile.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(tile.subtitle, color = Muted, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                tile.liveValue ?: "Tap to test",
                color = tile.accent,
                fontSize = if (tile.liveValue != null) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun diagnosticTiles(liveValues: Map<String, String>): List<DiagnosticTile> {
    val liveIcon = Icons.Rounded.GraphicEq
    val testIcon = Icons.Rounded.Settings
    return listOf(
        DiagnosticTile("obd_status", "OBD status", "ELM327 Bluetooth connection state", Icons.Rounded.DirectionsCar, liveValue = liveValues["OBD status"], accent = Amber),
        DiagnosticTile("obd_devices", "Paired OBD", "Show paired ELM327 / OBDII Bluetooth devices", Icons.Rounded.DirectionsCar, testId = "obd_devices", accent = Amber),
        DiagnosticTile("obd_connect", "Connect OBD", "Connect to paired Bluetooth ELM327 adapter", Icons.Rounded.DirectionsCar, testId = "obd_connect", accent = Amber),
        DiagnosticTile("obd_disconnect", "Disconnect OBD", "Close the Bluetooth OBD connection", Icons.Rounded.Close, testId = "obd_disconnect", accent = Amber),
        DiagnosticTile("obd_protocol", "OBD protocol", "Ask ELM327 which protocol is active", Icons.Rounded.DirectionsCar, testId = "obd_protocol", accent = Amber),
        DiagnosticTile("obd_supported", "Supported PIDs", "Read 0100/0120/0140/0160 support blocks", Icons.Rounded.DirectionsCar, testId = "obd_supported", accent = Amber),
        DiagnosticTile("obd_read_once", "Read OBD now", "Poll common Ford Escape standard OBD-II values", Icons.Rounded.DirectionsCar, testId = "obd_read_once", accent = Amber),
        DiagnosticTile("obd_dtcs", "Read DTCs", "Read stored diagnostic trouble codes", Icons.Rounded.DirectionsCar, testId = "obd_dtcs", accent = Amber),
        DiagnosticTile("obd_clear_dtcs", "Clear DTCs", "Send Mode 04 clear command", Icons.Rounded.DirectionsCar, testId = "obd_clear_dtcs", accent = Amber),
        DiagnosticTile("obd_vin", "Read VIN", "Read Mode 09 VIN if supported", Icons.Rounded.DirectionsCar, testId = "obd_vin", accent = Amber),
        DiagnosticTile("obd_protocol_live", "Live protocol", "Current ELM327 protocol", liveIcon, liveValue = liveValues["OBD protocol"], accent = Cyan),
        DiagnosticTile("obd_rpm", "RPM", "Engine speed from PID 010C", liveIcon, liveValue = liveValues["OBD RPM"], accent = Cyan),
        DiagnosticTile("obd_speed", "Vehicle speed", "OBD vehicle speed from PID 010D", liveIcon, liveValue = liveValues["OBD speed"], accent = Cyan),
        DiagnosticTile("obd_coolant", "Coolant temp", "Engine coolant from PID 0105", liveIcon, liveValue = liveValues["OBD coolant"], accent = Cyan),
        DiagnosticTile("obd_load", "Engine load", "Calculated load from PID 0104", liveIcon, liveValue = liveValues["OBD engine load"], accent = Cyan),
        DiagnosticTile("obd_throttle", "Throttle", "Throttle position from PID 0111", liveIcon, liveValue = liveValues["OBD throttle"], accent = Cyan),
        DiagnosticTile("obd_intake", "Intake temp", "Intake air temp from PID 010F", liveIcon, liveValue = liveValues["OBD intake temp"], accent = Cyan),
        DiagnosticTile("obd_map", "MAP", "Manifold pressure from PID 010B", liveIcon, liveValue = liveValues["OBD MAP"], accent = Cyan),
        DiagnosticTile("obd_maf", "MAF", "Airflow from PID 0110", liveIcon, liveValue = liveValues["OBD MAF"], accent = Cyan),
        DiagnosticTile("obd_stft1", "STFT B1", "Short fuel trim bank 1 PID 0106", liveIcon, liveValue = liveValues["OBD STFT B1"], accent = Cyan),
        DiagnosticTile("obd_ltft1", "LTFT B1", "Long fuel trim bank 1 PID 0107", liveIcon, liveValue = liveValues["OBD LTFT B1"], accent = Cyan),
        DiagnosticTile("obd_stft2", "STFT B2", "Short fuel trim bank 2 PID 0108", liveIcon, liveValue = liveValues["OBD STFT B2"], accent = Cyan),
        DiagnosticTile("obd_ltft2", "LTFT B2", "Long fuel trim bank 2 PID 0109", liveIcon, liveValue = liveValues["OBD LTFT B2"], accent = Cyan),
        DiagnosticTile("obd_timing", "Timing", "Ignition timing advance PID 010E", liveIcon, liveValue = liveValues["OBD timing"], accent = Cyan),
        DiagnosticTile("obd_runtime", "Run time", "Engine run time from PID 011F", liveIcon, liveValue = liveValues["OBD run time"], accent = Cyan),
        DiagnosticTile("obd_fuel", "Fuel level", "Fuel tank level from PID 012F if supported", liveIcon, liveValue = liveValues["OBD fuel level"], accent = Cyan),
        DiagnosticTile("obd_baro", "Barometer", "Barometric pressure PID 0133", liveIcon, liveValue = liveValues["OBD barometer"], accent = Cyan),
        DiagnosticTile("obd_volts", "Module volts", "Control module voltage PID 0142", liveIcon, liveValue = liveValues["OBD module volts"], accent = Cyan),
        DiagnosticTile("obd_ambient", "OBD ambient", "Ambient air temp PID 0146 if supported", liveIcon, liveValue = liveValues["OBD ambient temp"], accent = Cyan),
        DiagnosticTile("obd_oil", "Oil temp", "Engine oil temp PID 015C if supported", liveIcon, liveValue = liveValues["OBD oil temp"], accent = Cyan),
        DiagnosticTile("ford_escape_2008", "Ford Escape 2008", "Known interface outputs and retention options", testIcon, testId = "ford_escape_2008", accent = Amber),
        DiagnosticTile("reverse", "Reverse trigger", "Backup-camera reverse wire / CAN reverse", testIcon, testId = "reverse", accent = Amber),
        DiagnosticTile("parking_brake", "Parking brake", "Navigation/video safety input", testIcon, testId = "parking_brake", accent = Amber),
        DiagnosticTile("illumination", "Illumination", "Dimmer / headlight status", testIcon, testId = "illumination", accent = Amber),
        DiagnosticTile("vss", "VSS speed", "Speed-sense wire or CAN vehicle speed", testIcon, testId = "vss", accent = Amber),
        DiagnosticTile("swc", "Steering wheel", "Key1/Key2 or CAN wheel controls", testIcon, testId = "swc", accent = Amber),
        DiagnosticTile("acc_rap", "ACC / RAP", "Ignition, accessory, retained accessory power", testIcon, testId = "acc_rap", accent = Amber),
        DiagnosticTile("amp_antenna", "Amp / antenna", "Remote output visibility", testIcon, testId = "amp_antenna", accent = Amber),
        DiagnosticTile("getprop_car", "MCU properties", "Search Android properties for car/CAN keys", testIcon, testId = "getprop_car"),
        DiagnosticTile("device_nodes", "Serial nodes", "Scan /dev for CAN, MCU, UART, OBD, tty", testIcon, testId = "device_nodes"),
        DiagnosticTile("usb_devices", "USB adapters", "Look for USB OBD/CAN/serial devices", testIcon, testId = "usb_devices"),
        DiagnosticTile("common_packages", "Car packages", "Detect common CAN/MCU vendor apps", testIcon, testId = "common_packages"),
        DiagnosticTile("volume_max", "Max volume", "Set Android media stream to maximum", testIcon, testId = "volume_max"),
        DiagnosticTile("bluetooth", "Bluetooth", "Adapter name, enabled state, bonded devices", testIcon, testId = "bluetooth"),
        DiagnosticTile("gps_speed", "GPS speed", "Read last Android location speed if available", testIcon, testId = "gps_speed"),
        DiagnosticTile("open_settings", "Open settings", "Try car/factory settings, then Android settings", testIcon, testId = "open_settings"),
        DiagnosticTile("sensor_count", "Sensor count", "Android sensors visible to this app", liveIcon, liveValue = liveValues["Sensor count"]),
        DiagnosticTile("accelerometer", "Accelerometer", "Head-unit motion / vehicle acceleration proxy", liveIcon, liveValue = liveValues["Accelerometer"]),
        DiagnosticTile("linear_accel", "Linear accel", "Gravity-filtered acceleration if sensor exists", liveIcon, liveValue = liveValues["Linear acceleration"]),
        DiagnosticTile("gyroscope", "Gyroscope", "Rotation rate if sensor exists", liveIcon, liveValue = liveValues["Gyroscope"]),
        DiagnosticTile("magnetic", "Magnetic field", "Compass / magnetic sensor if present", liveIcon, liveValue = liveValues["Magnetic field"]),
        DiagnosticTile("gravity", "Gravity", "Device tilt/gravity vector", liveIcon, liveValue = liveValues["Gravity"]),
        DiagnosticTile("rotation", "Rotation vector", "Orientation fusion sensor", liveIcon, liveValue = liveValues["Rotation vector"]),
        DiagnosticTile("light", "Cabin light", "Ambient light sensor if present", liveIcon, liveValue = liveValues["Cabin light"]),
        DiagnosticTile("proximity", "Proximity", "Near/far sensor if present", liveIcon, liveValue = liveValues["Proximity"]),
        DiagnosticTile("pressure", "Pressure", "Barometer if present", liveIcon, liveValue = liveValues["Pressure"]),
        DiagnosticTile("ambient_temp", "Ambient temp", "Android ambient temperature sensor if present", liveIcon, liveValue = liveValues["Ambient temp"])
    )
}

private data class DiagnosticTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testId: String? = null,
    val liveValue: String? = null,
    val accent: Color = Cyan
)

@Composable
private fun NowPlayingPanel(
    ui: PlayerUiState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val song = ui.song
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrackStage(
                song = song,
                isPlaying = ui.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = song?.title ?: "No track selected",
                    color = Ink,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.let { "${it.artist} / ${it.album}" } ?: ui.scanSummary,
                    color = Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProgressDeck(ui = ui, onSeek = onSeek)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetaPill(song?.formatLabel ?: "LOCAL")
                MetaPill(song?.durationLabel ?: "--:--")
            }
            Text(
                text = ui.actionMessage,
                color = Cyan.copy(alpha = 0.86f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackStage(song: Song?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0A1117),
                        Color(0xFF13262E),
                        Color(0xFF05090D)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        VisualizerCanvas(
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = song?.displayName?.ifBlank { song.title } ?: "Local media cockpit",
                color = Ink.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = Cyan)
        }
    }
}

@Composable
private fun VisualizerCanvas(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val count = 30
        val gap = size.width / (count * 1.7f)
        val stroke = gap * 0.68f
        repeat(count) { index ->
            val wave = ((index * 37) % 100) / 100f
            val energy = if (isPlaying) 0.35f + wave * 0.58f else 0.18f + wave * 0.24f
            val barHeight = size.height * energy
            val x = gap + index * gap * 1.7f
            val color = if (index % 5 == 0) Amber.copy(alpha = 0.84f) else Cyan.copy(alpha = 0.68f)
            drawLine(
                color = color,
                start = Offset(x, size.height * 0.64f + barHeight * 0.26f),
                end = Offset(x, size.height * 0.64f - barHeight * 0.54f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(0f, size.height * 0.65f),
            end = Offset(size.width, size.height * 0.65f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun ProgressDeck(ui: PlayerUiState, onSeek: (Long) -> Unit) {
    val duration = maxOf(ui.durationMs, ui.song?.durationMs ?: 0L, 1L)
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Slider(
            value = ui.progressMs.coerceIn(0L, duration).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Cyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.14f)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(ui.progressMs), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(formatDuration(duration), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LibraryConsole(
    ui: PlayerUiState,
    listMode: ListMode,
    songs: List<Song>,
    query: String,
    onQueryChange: (String) -> Unit,
    onModeChange: (ListMode) -> Unit,
    onScanLibrary: () -> Unit,
    onSongSelected: (Song) -> Unit,
    onToggleSongFavorite: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Media library", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(ui.scanSummary, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusTile(label = "Library", value = ui.library.size.toString())
                StatusTile(label = "Favourites", value = ui.favorites.size.toString())
                StatusTile(label = "Queue", value = ui.queue.size.toString())
                Button(
                    onClick = onScanLibrary,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Color(0xFF041113)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (ui.isScanningLibrary) "Scanning" else "Scan", fontWeight = FontWeight.Bold)
                }
            }
            ModeTabs(selected = listMode, onSelected = onModeChange)
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Muted)
                },
                singleLine = true,
                placeholder = { Text("Search songs, artists, albums", color = Muted) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    focusedContainerColor = Color.White.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Cyan
                ),
                shape = RoundedCornerShape(16.dp)
            )
            if (songs.isEmpty()) {
                EmptyLibraryState(
                    mode = listMode,
                    supportedFormats = ui.supportedExtensions.take(10).joinToString(" / ") { it.uppercase(Locale.US) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            selected = song.id == ui.song?.id,
                            favourite = song.id in ui.favorites,
                            onClick = { onSongSelected(song) },
                            onToggleFavorite = { onToggleSongFavorite(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ModeTabs(selected: ListMode, onSelected: (ListMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ListMode.entries.forEach { mode ->
            val active = mode == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelected(mode) }
                    .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(mode.icon(), contentDescription = null, tint = if (active) Cyan else Muted, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(mode.label, color = if (active) Ink else Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Cyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.045f))
            .border(
                width = 1.dp,
                color = if (selected) Cyan.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.055f),
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1A3038), Color(0xFF0A1117)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = if (selected) Cyan else Muted)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} / ${song.durationLabel}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(song.formatLabel, color = Amber, fontSize = 10.sp, fontWeight = FontWeight.Black)
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Toggle favourite",
                tint = if (favourite) Amber else Muted
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(mode: ListMode, supportedFormats: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(mode.icon(), contentDescription = null, tint = Cyan.copy(alpha = 0.82f), modifier = Modifier.size(42.dp))
            Text(
                text = when (mode) {
                    ListMode.Library -> "No local songs loaded"
                    ListMode.Favorites -> "No favourites yet"
                    ListMode.Queue -> "Queue is empty"
                },
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = supportedFormats,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun EqualizerRail(
    ui: PlayerUiState,
    onEqBandChange: (Int, Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onToggleLoudness: () -> Unit,
    onToggleMaxOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Cyan, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equalizer", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("Cabin tuning", color = Muted, fontSize = 10.sp, maxLines = 1)
                }
            }
            PowerSwitch("Loudness", checked = ui.loudnessEnabled, onClick = onToggleLoudness)
            PowerSwitch("Max output", checked = ui.maxVolumeEnabled, onClick = onToggleMaxOutput, accent = Amber)
            ToneSlider("Bass", ui.bassBoost, onBassChange)
            ToneSlider("Mid", ui.midBoost, onMidChange)
            ToneSlider("Treble", ui.trebleBoost, onTrebleChange)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ui.eqBands.take(5).forEachIndexed { index, value ->
                        EqBandRow(
                            label = EQ_LABELS.getOrElse(index) { "${index + 1}" },
                            value = value,
                            onValueChange = { onEqBandChange(index, it) }
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ui.eqBands.drop(5).forEachIndexed { offset, value ->
                        val index = offset + 5
                        EqBandRow(
                            label = EQ_LABELS.getOrElse(index) { "${index + 1}" },
                            value = value,
                            onValueChange = { onEqBandChange(index, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqBandRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -12f..12f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Cyan,
                activeTrackColor = Cyan,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
        Text("${value.roundToInt()}", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(18.dp))
    }
}

@Composable
private fun ToneSlider(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("${(value * 100).roundToInt()}%", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Amber,
                activeTrackColor = Amber,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
private fun PowerSwitch(label: String, checked: Boolean, onClick: () -> Unit, accent: Color = Cyan) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.065f), RoundedCornerShape(14.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = if (checked) accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onClick() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.28f),
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.09f)
            )
        )
    }
}

@Composable
private fun TransportBar(
    ui: PlayerUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRandomPlaylist: () -> Unit,
    onPlayFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFavourite = ui.song?.id in ui.favorites
    Surface(modifier = modifier, color = Color(0xEE080D12), shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconOnlyButton(
                icon = Icons.Rounded.Shuffle,
                label = "Shuffle",
                selected = ui.shuffleEnabled,
                size = 68.dp,
                onClick = onToggleShuffle
            )
            IconOnlyButton(
                icon = if (ui.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                label = "Repeat",
                selected = ui.repeatMode != RepeatMode.Off,
                size = 68.dp,
                onClick = onCycleRepeat
            )
            Spacer(modifier = Modifier.width(2.dp))
            IconOnlyButton(Icons.Rounded.FastRewind, "Back 10 seconds", size = 62.dp, onClick = onRewind)
            IconOnlyButton(Icons.Rounded.SkipPrevious, "Previous", size = 84.dp, onClick = onPrevious)
            IconOnlyButton(
                icon = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label = if (ui.isPlaying) "Pause" else "Play",
                size = 92.dp,
                selected = true,
                onClick = onPlayPause
            )
            IconOnlyButton(Icons.Rounded.SkipNext, "Next", size = 84.dp, onClick = onNext)
            IconOnlyButton(Icons.Rounded.FastForward, "Forward 10 seconds", size = 62.dp, onClick = onForward)
            IconOnlyButton(
                icon = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = "Favourite",
                size = 84.dp,
                selected = isFavourite,
                accent = Amber,
                onClick = onToggleFavorite
            )
            Spacer(modifier = Modifier.weight(1f))
            CompactCommandButton(Icons.Rounded.QueueMusic, "Random", onRandomPlaylist)
            CompactCommandButton(Icons.Rounded.Favorite, "Play favourites", onPlayFavorites, accent = Amber)
        }
    }
}

@Composable
private fun IconOnlyButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 60.dp,
    selected: Boolean = false,
    accent: Color = Cyan
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.radialGradient(listOf(accent, accent.copy(alpha = 0.24f), Color(0xFF080D12)))
                } else {
                    Brush.radialGradient(listOf(Color(0xFF1B242C), Color(0xFF080D12)))
                }
            )
            .border(1.dp, if (selected) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected && accent == Amber) Color(0xFF221400) else if (selected) Color(0xFF021011) else Ink,
            modifier = Modifier.size(size * 0.48f)
        )
    }
}

@Composable
private fun CompactCommandButton(icon: ImageVector, label: String, onClick: () -> Unit, accent: Color = Cyan) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(68.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.055f),
            contentColor = accent
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text = text,
        color = Cyan,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

private fun List<Song>.filterBy(query: String): List<Song> {
    val cleaned = query.trim()
    if (cleaned.isBlank()) return this
    return filter { song ->
        song.title.contains(cleaned, ignoreCase = true) ||
            song.artist.contains(cleaned, ignoreCase = true) ||
            song.album.contains(cleaned, ignoreCase = true) ||
            song.displayName.contains(cleaned, ignoreCase = true)
    }
}

private enum class ListMode(val label: String) {
    Library("Library"),
    Favorites("Favourites"),
    Queue("Queue")
}

@Composable
private fun ListMode.icon(): ImageVector {
    return when (this) {
        ListMode.Library -> Icons.Rounded.LibraryMusic
        ListMode.Favorites -> Icons.Rounded.Favorite
        ListMode.Queue -> Icons.Rounded.QueueMusic
    }
}

private val EQ_LABELS = listOf("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

private val Ink = Color(0xFFF3FBFF)
private val Muted = Color(0xFF8EA2AB)
private val Cyan = Color(0xFF69F7E4)
private val Amber = Color(0xFFFFB84D)
private val Panel = Color(0xCC101820)
