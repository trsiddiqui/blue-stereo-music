package com.stereo.bluemusic.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String
)

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val paired: Boolean
)

data class PlayerUiState(
    val connectedDevice: String = "No BT device",
    val song: Song = Song("0", "Waiting for media", "Bluetooth source", "", "--:--"),
    val isPlaying: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val stereoName: String = "Blue Stereo Music",
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val discoveredDevices: List<BluetoothDeviceInfo> = emptyList(),
    val isScanning: Boolean = false,
    val actionMessage: String = "Ready",
    val favorites: Set<String> = emptySet(),
    val playlist: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val bassBoost: Float = 0.5f,
    val trebleBoost: Float = 0.5f,
    val midBoost: Float = 0.5f
)
