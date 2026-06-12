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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
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
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
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
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.RepeatMode
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.data.formatDuration
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CarPlayerScreen(
    ui: PlayerUiState,
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
    onToggleMaxOutput: () -> Unit
) {
    var listModeName by rememberSaveable { mutableStateOf(ListMode.Library.name) }
    var query by rememberSaveable { mutableStateOf("") }
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
                        Color(0xFF05080B),
                        Color(0xFF091017),
                        Color(0xFF111820)
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
                            Color(0x3319E7D4),
                            Color.Transparent,
                            Color(0x22FFB84D)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CockpitHeader(ui = ui)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NowPlayingPanel(
                    ui = ui,
                    onScanLibrary = onScanLibrary,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onRewind = onRewind,
                    onForward = onForward,
                    onToggleFavorite = onToggleFavorite,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onRandomPlaylist = onRandomPlaylist,
                    onPlayFavorites = onPlayFavorites,
                    modifier = Modifier
                        .weight(1.04f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1.16f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                        modifier = Modifier.weight(1.08f)
                    )
                    EqualizerConsole(
                        ui = ui,
                        onEqBandChange = onEqBandChange,
                        onBassChange = onBassChange,
                        onMidChange = onMidChange,
                        onTrebleChange = onTrebleChange,
                        onToggleLoudness = onToggleLoudness,
                        onToggleMaxOutput = onToggleMaxOutput,
                        modifier = Modifier.weight(0.92f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CockpitHeader(ui: PlayerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Cyan, Color(0xFF177D89))))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("B", color = Color(0xFF031114), fontWeight = FontWeight.Black, fontSize = 26.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Blue Stereo Music",
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                maxLines = 1
            )
            Text(
                text = ui.actionMessage,
                fontSize = 13.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusTile(label = "Library", value = ui.library.size.toString())
        StatusTile(label = "Favourites", value = ui.favorites.size.toString())
        StatusTile(label = "Queue", value = ui.queue.size.toString())
    }
}

@Composable
private fun StatusTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NowPlayingPanel(
    ui: PlayerUiState,
    onScanLibrary: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onRandomPlaylist: () -> Unit,
    onPlayFavorites: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = ui.song
    val isFavourite = song?.id in ui.favorites
    Surface(
        modifier = modifier,
        color = Panel,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TrackStage(
                song = song,
                isPlaying = ui.isPlaying,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = song?.title ?: "No track selected",
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song?.let { "${it.artist} / ${it.album}" } ?: ui.scanSummary,
                    color = Muted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ProgressDeck(ui = ui, onSeek = onSeek)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        RoundIconButton(Icons.Rounded.FastRewind, "Back 10 seconds", onClick = onRewind)
                        RoundIconButton(Icons.Rounded.SkipPrevious, "Previous", onClick = onPrevious)
                        RoundIconButton(
                            icon = if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            label = if (ui.isPlaying) "Pause" else "Play",
                            size = 76.dp,
                            selected = true,
                            onClick = onPlayPause
                        )
                        RoundIconButton(Icons.Rounded.SkipNext, "Next", onClick = onNext)
                        RoundIconButton(Icons.Rounded.FastForward, "Forward 10 seconds", onClick = onForward)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionButton(
                            icon = Icons.Rounded.Shuffle,
                            label = "Shuffle",
                            selected = ui.shuffleEnabled,
                            onClick = onToggleShuffle,
                            modifier = Modifier.weight(1f)
                        )
                        ActionButton(
                            icon = if (ui.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            label = when (ui.repeatMode) {
                                RepeatMode.Off -> "Repeat"
                                RepeatMode.All -> "Repeat all"
                                RepeatMode.One -> "Repeat one"
                            },
                            selected = ui.repeatMode != RepeatMode.Off,
                            onClick = onCycleRepeat,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                FavouriteIgnitionButton(
                    isFavourite = isFavourite,
                    onClick = onToggleFavorite
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    icon = Icons.Rounded.Refresh,
                    label = if (ui.isScanningLibrary) "Scanning" else "Scan",
                    onClick = onScanLibrary,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    icon = Icons.Rounded.Shuffle,
                    label = "Random playlist",
                    selected = false,
                    onClick = onRandomPlaylist,
                    modifier = Modifier.weight(1.25f)
                )
                ActionButton(
                    icon = Icons.Rounded.Favorite,
                    label = "Play favourites",
                    selected = false,
                    onClick = onPlayFavorites,
                    modifier = Modifier.weight(1.25f)
                )
            }
        }
    }
}

@Composable
private fun TrackStage(song: Song?, isPlaying: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF111820),
                        Color(0xFF13262E),
                        Color(0xFF061014)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(18.dp)
    ) {
        VisualizerCanvas(
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill(song?.formatLabel ?: "LOCAL")
                MetaPill(song?.durationLabel ?: "--:--")
            }
            Text(
                text = song?.displayName?.ifBlank { song.title } ?: "Local media cockpit",
                color = Ink.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(54.dp)
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
        val count = 34
        val gap = size.width / (count * 1.65f)
        val stroke = gap * 0.72f
        repeat(count) { index ->
            val wave = ((index * 37) % 100) / 100f
            val energy = if (isPlaying) 0.35f + wave * 0.58f else 0.18f + wave * 0.24f
            val barHeight = size.height * energy
            val x = gap + index * gap * 1.65f
            val color = if (index % 5 == 0) Amber.copy(alpha = 0.82f) else Cyan.copy(alpha = 0.68f)
            drawLine(
                color = color,
                start = Offset(x, size.height * 0.62f + barHeight * 0.28f),
                end = Offset(x, size.height * 0.62f - barHeight * 0.54f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(0f, size.height * 0.63f),
            end = Offset(size.width, size.height * 0.63f),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun ProgressDeck(ui: PlayerUiState, onSeek: (Long) -> Unit) {
    val duration = maxOf(ui.durationMs, ui.song?.durationMs ?: 0L, 1L)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun FavouriteIgnitionButton(isFavourite: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (isFavourite) Amber else Color(0xFF20262E),
                            if (isFavourite) Color(0xFF6F3600) else Color(0xFF111820)
                        )
                    )
                )
                .border(2.dp, if (isFavourite) Amber.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                imageVector = if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (isFavourite) Color(0xFF251500) else Amber,
                modifier = Modifier.size(50.dp)
            )
        }
        Text("FAVOURITE", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
    Surface(modifier = modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Media library", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(ui.scanSummary, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
                    .height(50.dp),
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Cyan.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.045f))
            .border(
                width = 1.dp,
                color = if (selected) Cyan.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.055f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
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
private fun EqualizerConsole(
    ui: PlayerUiState,
    onEqBandChange: (Int, Float) -> Unit,
    onBassChange: (Float) -> Unit,
    onMidChange: (Float) -> Unit,
    onTrebleChange: (Float) -> Unit,
    onToggleLoudness: () -> Unit,
    onToggleMaxOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth(), color = Panel, shape = RoundedCornerShape(26.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Cyan)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Equalizer", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text("10-band tuning / cabin loudness", color = Muted, fontSize = 11.sp)
                }
                PowerSwitch("Loud", checked = ui.loudnessEnabled, onClick = onToggleLoudness)
                PowerSwitch("Max", checked = ui.maxVolumeEnabled, onClick = onToggleMaxOutput, accent = Amber)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ui.eqBands.forEachIndexed { index, value ->
                    BandSlider(
                        label = EQ_LABELS.getOrElse(index) { "${index + 1}" },
                        value = value,
                        onValueChange = { onEqBandChange(index, it) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToneSlider("Bass", ui.bassBoost, onBassChange, Modifier.weight(1f))
                ToneSlider("Mid", ui.midBoost, onMidChange, Modifier.weight(1f))
                ToneSlider("Treble", ui.trebleBoost, onTrebleChange, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BandSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.height(92.dp).width(42.dp), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -12f..12f,
                modifier = Modifier
                    .width(92.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = Cyan,
                    activeTrackColor = Cyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
        Text("${value.roundToInt()}", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToneSlider(label: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = if (checked) accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Black)
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
private fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 56.dp,
    selected: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) Cyan else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) Cyan.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color(0xFF021011) else Ink,
            modifier = Modifier.size(size * 0.46f)
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    val background = if (selected) Cyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = if (selected) Cyan else Ink
        ),
        border = BorderStroke(1.dp, if (selected) Cyan.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.1f)),
        contentPadding = PaddingValues(horizontal = 9.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetaPill(text: String) {
    Text(
        text = text,
        color = Cyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
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
