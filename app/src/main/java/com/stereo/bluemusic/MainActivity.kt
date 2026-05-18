package com.stereo.bluemusic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.stereo.bluemusic.data.BluetoothDeviceInfo
import com.stereo.bluemusic.data.PlayerUiState
import com.stereo.bluemusic.data.Song
import com.stereo.bluemusic.service.MediaSessionBridgeService

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var mediaBridge: MediaSessionBridgeService? = null
    private var serviceBound = false
    private var receiverRegistered = false
    private var pendingPermissionAction: (() -> Unit)? = null
    private val discoveredDevices = linkedMapOf<String, BluetoothDeviceInfo>()

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            mediaBridge = (binder as? MediaSessionBridgeService.LocalBinder)?.service()
            serviceBound = mediaBridge != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaBridge = null
            serviceBound = false
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = bluetoothDeviceFrom(intent) ?: return
                    val info = device.toInfo(pairedOverride = false)
                    discoveredDevices[info.address] = info
                    vm.updateDiscoveredDevices(discoveredDevices.values.sortedBy { it.name }, scanning = true)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    vm.updateDiscoveredDevices(discoveredDevices.values.sortedBy { it.name }, scanning = false)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceBound = bindService(
            Intent(this, MediaSessionBridgeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        refreshBluetoothSnapshot()

        setContent {
            val ui by vm.uiState.collectAsState()
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF37D5C6),
                    secondary = Color(0xFF9AD7FF),
                    background = Color(0xFF071116),
                    surface = Color(0xFF0D1C24),
                    onPrimary = Color(0xFF031315),
                    onSecondary = Color(0xFF041019),
                    onBackground = Color(0xFFEAF7FA),
                    onSurface = Color(0xFFEAF7FA)
                )
            ) {
                CarStereoApp(
                    ui = ui,
                    onPrevious = { sendMediaAction("Previous") { previous() } },
                    onPlayPause = { sendMediaAction("Play/Pause") { playPause() } },
                    onNext = { sendMediaAction("Next") { next() } },
                    onFavorite = { vm.toggleFavorite() },
                    onAddToPlaylist = { vm.addCurrentToPlaylist() },
                    onHistory = { vm.addToHistory(it) },
                    onEq = { bass, mid, treble -> vm.setEq(bass, mid, treble) },
                    onMakeDiscoverable = { makeStereoDiscoverable() },
                    onScan = { startBluetoothScan() },
                    onOpenSettings = { openBluetoothSettings() },
                    onPairDevice = { pairDevice(it) },
                    onNativeBluetoothSource = { openNativeBluetoothMusicSource() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBluetoothSnapshot()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { bluetoothAdapter?.cancelDiscovery() }
        if (receiverRegistered) {
            runCatching { unregisterReceiver(bluetoothReceiver) }
            receiverRegistered = false
        }
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_REQUEST_CODE) {
            refreshBluetoothSnapshot()
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                pendingPermissionAction?.invoke()
            } else {
                vm.showAction("Bluetooth permission was not allowed")
            }
            pendingPermissionAction = null
        }
    }

    private fun sendMediaAction(action: String, command: MediaSessionBridgeService.() -> Boolean) {
        val success = mediaBridge?.command() ?: false
        if (!success) {
            openNativeBluetoothMusicSource(showSuccess = false)
        }
        vm.recordMediaAction(action, success)
    }

    private fun openNativeBluetoothMusicSource(showSuccess: Boolean = true): Boolean {
        val candidates = listOf(
            ComponentName("com.ts.MainUI", "com.ts.bt.BtMusicActivity"),
            ComponentName("com.ts.MainUI", "com.ts.bt.BtActivity"),
            ComponentName("com.ts.bt", "com.ts.bt.BtMusicActivity"),
            ComponentName("com.ts.bt", "com.ts.bt.BtActivity")
        )

        candidates.forEach { component ->
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            val opened = runCatching {
                startActivity(intent)
                true
            }.getOrDefault(false)
            if (opened) {
                if (showSuccess) {
                    vm.showAction("8227L Bluetooth music source opened")
                }
                return true
            }
        }

        vm.showAction("Could not open 8227L Bluetooth source")
        return false
    }

    @SuppressLint("MissingPermission")
    private fun makeStereoDiscoverable() {
        ensureBluetoothPermissions(includeScan = false) {
            val adapter = bluetoothAdapter
            if (adapter == null) {
                vm.showAction("Bluetooth is not available")
                return@ensureBluetoothPermissions
            }
            if (!adapter.isEnabled) {
                runCatching { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                vm.showAction("Turn on Bluetooth")
                return@ensureBluetoothPermissions
            }
            runCatching {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                startActivity(intent)
            }.onSuccess {
                vm.showAction("Stereo discoverable for 5 minutes")
            }.onFailure {
                openBluetoothSettings()
            }
            refreshBluetoothSnapshot()
        }
    }

    private fun openBluetoothSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .onFailure { vm.showAction("Bluetooth settings could not open") }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothScan() {
        ensureBluetoothPermissions(includeScan = true) {
            val adapter = bluetoothAdapter
            if (adapter == null) {
                vm.showAction("Bluetooth is not available")
                return@ensureBluetoothPermissions
            }
            if (!adapter.isEnabled) {
                runCatching { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                vm.showAction("Turn on Bluetooth")
                return@ensureBluetoothPermissions
            }

            registerBluetoothReceiver()
            runCatching { adapter.cancelDiscovery() }
            discoveredDevices.clear()
            vm.updateDiscoveredDevices(emptyList(), scanning = true)
            val started = runCatching { adapter.startDiscovery() }.getOrDefault(false)
            if (!started) {
                vm.updateDiscoveredDevices(emptyList(), scanning = false)
                vm.showAction("Scan could not start")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pairDevice(address: String) {
        ensureBluetoothPermissions(includeScan = false) {
            val adapter = bluetoothAdapter ?: return@ensureBluetoothPermissions
            val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
            if (device == null) {
                vm.showAction("Device not found")
                return@ensureBluetoothPermissions
            }
            val alreadyPaired = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
            if (alreadyPaired) {
                vm.showAction("Device already paired")
                openBluetoothSettings()
                return@ensureBluetoothPermissions
            }
            val requested = runCatching { device.createBond() }.getOrDefault(false)
            vm.showAction(if (requested) "Pair request opened" else "Open Bluetooth settings to pair")
            refreshBluetoothSnapshot()
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBluetoothSnapshot() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            vm.updateBluetooth(enabled = false, stereoName = "No Bluetooth", pairedDevices = emptyList())
            return
        }
        val canReadDevices = hasConnectPermission()
        val paired = if (canReadDevices) {
            runCatching { adapter.bondedDevices.map { it.toInfo(pairedOverride = true) } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val stereoName = if (canReadDevices) {
            runCatching { adapter.name }.getOrNull().orEmpty().ifBlank { "Car Stereo" }
        } else {
            "Car Stereo"
        }
        vm.updateBluetooth(adapter.isEnabled, stereoName, paired.sortedBy { it.name })
    }

    @SuppressLint("InlinedApi")
    private fun ensureBluetoothPermissions(includeScan: Boolean, onReady: () -> Unit) {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missing += Manifest.permission.BLUETOOTH_CONNECT
            }
            if (includeScan && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missing += Manifest.permission.BLUETOOTH_SCAN
            }
        } else if (includeScan && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }

        if (missing.isEmpty()) {
            onReady()
        } else {
            pendingPermissionAction = onReady
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), BLUETOOTH_REQUEST_CODE)
        }
    }

    @SuppressLint("InlinedApi")
    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(bluetoothReceiver, filter)
        receiverRegistered = true
    }

    @Suppress("DEPRECATION")
    private fun bluetoothDeviceFrom(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toInfo(pairedOverride: Boolean? = null): BluetoothDeviceInfo {
        val safeName = runCatching { name }.getOrNull().orEmpty().ifBlank { "Unknown device" }
        val safeAddress = runCatching { address }.getOrNull().orEmpty().ifBlank { "Unknown address" }
        val paired = pairedOverride ?: runCatching { bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
        return BluetoothDeviceInfo(name = safeName, address = safeAddress, paired = paired)
    }

    companion object {
        private const val BLUETOOTH_REQUEST_CODE = 42
    }
}

@Composable
private fun CarStereoApp(
    ui: PlayerUiState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHistory: (Song) -> Unit,
    onEq: (Float?, Float?, Float?) -> Unit,
    onMakeDiscoverable: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onPairDevice: (String) -> Unit,
    onNativeBluetoothSource: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Music", "Bluetooth", "Game")

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blue Stereo Music", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(ui.actionMessage, color = Color(0xFFA6C8D1), fontSize = 14.sp)
                }
                StatusPill(if (ui.bluetoothEnabled) "Bluetooth On" else "Bluetooth Off")
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = Color(0xFF0A171E), contentColor = Color.White) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> MusicScreen(ui, onPrevious, onPlayPause, onNext, onFavorite, onAddToPlaylist, onHistory, onEq, onNativeBluetoothSource)
                1 -> BluetoothScreen(ui, onMakeDiscoverable, onScan, onOpenSettings, onPairDevice, onNativeBluetoothSource)
                else -> GameScreen()
            }
        }
    }
}

@Composable
private fun MusicScreen(
    ui: PlayerUiState,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHistory: (Song) -> Unit,
    onEq: (Float?, Float?, Float?) -> Unit,
    onNativeBluetoothSource: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.weight(1.1f).fillMaxSize(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F222B)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF12323B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("BT", fontSize = 82.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37D5C6))
                }
                Text(ui.song.title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${ui.song.artist} / ${ui.song.album}", color = Color(0xFFA6C8D1), fontSize = 17.sp)
                Text("Source: ${ui.connectedDevice}", color = Color(0xFF9AD7FF), fontSize = 16.sp)
                Button(onClick = onNativeBluetoothSource, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Activate 8227L BT Source")
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        LargeControl("Prev", onPrevious, Modifier.weight(1f))
                        LargeControl(if (ui.isPlaying) "Pause" else "Play", onPlayPause, Modifier.weight(1.35f))
                        LargeControl("Next", onNext, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onFavorite, modifier = Modifier.weight(1f)) { Text("Favorite") }
                        Button(onClick = onAddToPlaylist, modifier = Modifier.weight(1f)) { Text("Save") }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Equalizer", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    EqSlider("Bass", ui.bassBoost) { onEq(it, null, null) }
                    EqSlider("Mid", ui.midBoost) { onEq(null, it, null) }
                    EqSlider("Treble", ui.trebleBoost) { onEq(null, null, it) }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick Queue", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    ui.history.ifEmpty { ui.playlist.take(4) }.forEach { song ->
                        Button(onClick = { onHistory(song) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${song.title} - ${song.duration}", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothScreen(
    ui: PlayerUiState,
    onMakeDiscoverable: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onPairDevice: (String) -> Unit,
    onNativeBluetoothSource: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.weight(0.9f).fillMaxSize()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Phone Connection", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                StatusRow("Stereo name", ui.stereoName)
                StatusRow("Bluetooth", if (ui.bluetoothEnabled) "On" else "Off")
                StatusRow("Paired phones", ui.pairedDevices.size.toString())
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = onMakeDiscoverable, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Make Stereo Discoverable")
                }
                Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text(if (ui.isScanning) "Scanning..." else "Scan Nearby Phones")
                }
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Open Bluetooth Settings")
                }
                Button(onClick = onNativeBluetoothSource, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Open 8227L BT Music")
                }
            }
        }

        Column(modifier = Modifier.weight(1.25f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DeviceList(
                title = "Paired Devices",
                devices = ui.pairedDevices,
                emptyText = "No paired phones yet",
                buttonText = "Settings",
                onClick = { onOpenSettings() },
                modifier = Modifier.weight(1f)
            )
            DeviceList(
                title = "Nearby Devices",
                devices = ui.discoveredDevices,
                emptyText = if (ui.isScanning) "Searching..." else "Run scan to find phones",
                buttonText = "Pair",
                onClick = { onPairDevice(it.address) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DeviceList(
    title: String,
    devices: List<BluetoothDeviceInfo>,
    emptyText: String,
    buttonText: String,
    onClick: (BluetoothDeviceInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (devices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, color = Color(0xFFA6C8D1))
                }
            } else {
                devices.take(5).forEach { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(device.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(device.address, color = Color(0xFFA6C8D1), fontSize = 12.sp, maxLines = 1)
                        }
                        Button(onClick = { onClick(device) }) { Text(buttonText) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameScreen() {
    var board by rememberSaveable { mutableStateOf(List(9) { "" }) }
    var turn by rememberSaveable { mutableStateOf("X") }
    var xScore by rememberSaveable { mutableStateOf(0) }
    var oScore by rememberSaveable { mutableStateOf(0) }
    val winner = remember(board) { winnerFor(board) }
    val full = board.all { it.isNotEmpty() }

    fun resetBoard() {
        board = List(9) { "" }
        turn = "X"
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.weight(0.8f).fillMaxSize()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Offline Game", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Tic Tac Toe", fontSize = 20.sp, color = Color(0xFF9AD7FF))
                StatusRow("Turn", if (winner == null && !full) turn else "Round over")
                StatusRow("X score", xScore.toString())
                StatusRow("O score", oScore.toString())
                Button(onClick = { resetBoard() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("New Round")
                }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F222B), modifier = Modifier.weight(1.2f).fillMaxSize()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when {
                        winner != null -> "$winner wins"
                        full -> "Draw"
                        else -> "$turn to move"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(modifier = Modifier.weight(1f).aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { row ->
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { col ->
                                val index = row * 3 + col
                                Button(
                                    onClick = {
                                        if (board[index].isEmpty() && winner == null) {
                                            board = board.toMutableList().also { it[index] = turn }
                                            val nextWinner = winnerFor(board)
                                            if (nextWinner == "X") xScore += 1
                                            if (nextWinner == "O") oScore += 1
                                            turn = if (turn == "X") "O" else "X"
                                        }
                                    },
                                    enabled = board[index].isEmpty() && winner == null,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF163541))
                                ) {
                                    Text(board[index], fontSize = 34.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun winnerFor(board: List<String>): String? {
    val lines = listOf(
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),
        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),
        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )
    return lines.firstNotNullOfOrNull { line ->
        val value = board[line[0]]
        if (value.isNotEmpty() && line.all { board[it] == value }) value else null
    }
}

@Composable
private fun LargeControl(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(74.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EqSlider(title: String, value: Float, onValueChanged: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
        Slider(value = value, onValueChange = onValueChanged, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatusPill(text: String) {
    Text(
        text = text,
        color = Color(0xFFBFFFF7),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, Color(0xFF37D5C6), RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFFA6C8D1), modifier = Modifier.weight(1f))
        Text(value, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}
