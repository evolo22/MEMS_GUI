package com.example.mems_gui

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mems_gui.ui.theme.MEMS_GUITheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var allPermissionsGranted by mutableStateOf(false)
    private var isBluetoothEnabled by mutableStateOf(false)

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Log.e("Bluetooth", "Device does not support Bluetooth.")
            return
        }

        isBluetoothEnabled = bluetoothAdapter.isEnabled

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                allPermissionsGranted = permissions.all { it.value }
                checkBluetoothAndScanReadiness()
            }

        enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                isBluetoothEnabled = result.resultCode == RESULT_OK
                checkBluetoothAndScanReadiness()
            }

        setContent {
            MEMS_GUITheme {
                val context = this
                val deviceTextState = remember { mutableStateOf("") }
                val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
                val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }

                LaunchedEffect(Unit) {
                    checkBluetoothAndScanReadiness()
                }

                LaunchedEffect(allPermissionsGranted, isBluetoothEnabled) {
                    if (allPermissionsGranted && isBluetoothEnabled) {
                        discoverNearbyDevices(context, bluetoothAdapter, deviceTextState, discoveredDevices)
                    } else {
                        val statusMessages = mutableListOf<String>()
                        if (!allPermissionsGranted) statusMessages.add("Permissions not granted.")
                        if (!isBluetoothEnabled) statusMessages.add("Bluetooth is not enabled.")
                        deviceTextState.value = "Status: ${statusMessages.joinToString(" ")}"
                    }
                }

                TwoPageSwipeUI(deviceTextState, bluetoothAdapter, discoveredDevices, selectedDevice)
            }
        }
    }

    private fun checkBluetoothAndScanReadiness() {
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            allPermissionsGranted = true
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                isBluetoothEnabled = true
            }
        }
    }
}

// === NEWLY UPDATED BLUETOOTH FUNCTIONS ===

fun discoverNearbyDevices(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    textState: MutableState<String>,
    discoveredDevices: MutableList<BluetoothDevice>
) {
    discoveredDevices.clear()

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    textState.value = "Scanning nearby devices..."
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null &&
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (!discoveredDevices.contains(device)) {
                            discoveredDevices.add(device)
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    context.unregisterReceiver(this)
                    if (discoveredDevices.isEmpty()) {
                        textState.value = "No nearby devices found"
                    } else {
                        textState.value = "Scan complete. Select a device to connect."
                    }
                }
            }
        }
    }

    val filter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    context.registerReceiver(receiver, filter)

    if (bluetoothAdapter.isDiscovering) bluetoothAdapter.cancelDiscovery()
    bluetoothAdapter.startDiscovery()
}

fun connectToDevice(context: Context, device: BluetoothDevice) {
    Thread {
        try {
            val deviceName = getSafeDeviceName(context, device)

            // Cancel discovery safely
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val hasScan = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

                val hasConnect = ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                if (hasScan && hasConnect) {
                    if (bluetoothAdapter.isDiscovering) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                } else {
                    Log.e("BluetoothConnect", "Missing required permissions: BLUETOOTH_SCAN or BLUETOOTH_CONNECT")
                    return@Thread
                }
            } else {
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
            }

            // Reflection workaround for classic Bluetooth
            val socket = device.javaClass
                .getMethod("createRfcommSocket", Int::class.java)
                .invoke(device, 1) as BluetoothSocket

            socket.connect()

            Log.d("BluetoothConnect", "Connected to $deviceName")
        } catch (e: Exception) {
            Log.e("BluetoothConnect", "Connection failed", e)
        }
    }.start()
}



@OptIn(ExperimentalPagerApi::class)
@Composable
fun TwoPageSwipeUI(
    deviceTextState: MutableState<String>,
    bluetoothAdapter: BluetoothAdapter,
    discoveredDevices: SnapshotStateList<BluetoothDevice>,
    selectedDevice: MutableState<BluetoothDevice?>
) {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = 2,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ConnectScreen(deviceTextState, bluetoothAdapter, discoveredDevices, selectedDevice)
            1 -> GraphScreen()
        }
    }
}


fun getSafeDeviceName(context: Context, device: BluetoothDevice): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            device.name ?: "Unnamed Device"
        } else {
            "Unknown Device (No Permission)"
        }
    } else {
        device.name ?: "Unnamed Device"
    }
}

@Composable
fun ConnectScreen(
    textState: MutableState<String>,
    bluetoothAdapter: BluetoothAdapter?,
    discoveredDevices: SnapshotStateList<BluetoothDevice>,
    selectedDevice: MutableState<BluetoothDevice?>
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                FrameLayout(it).apply {
                    background = ContextCompat.getDrawable(it, R.drawable.background_layout)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Connect to Arduino",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(textState.value, color = Color.White)

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(discoveredDevices) { device ->
                    val deviceName = getSafeDeviceName(context, device)
                    val isSelected = selectedDevice.value == device

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDevice.value = device }
                            .background(if (isSelected) Color.DarkGray else Color.Transparent)
                            .padding(12.dp)
                    ) {
                        Text(text = deviceName, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                selectedDevice.value?.let {
                    connectToDevice(context, it)
                    textState.value = "Attempting connection to ${getSafeDeviceName(context, it)}"
                } ?: run {
                    textState.value = "Please select a device first."
                }
            }) {
                Text("Connect")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                bluetoothAdapter?.let {
                    discoverNearbyDevices(context, it, textState, discoveredDevices)
                }
            }) {
                Text("Scan Again")
            }
        }
    }
}

@Composable
fun GraphScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                FrameLayout(it).apply {
                    background = ContextCompat.getDrawable(it, R.drawable.background_layout)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Display",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}


