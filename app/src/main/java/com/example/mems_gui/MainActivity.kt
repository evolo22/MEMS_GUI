package com.example.mems_gui

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
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
import java.util.*

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
                        discoverNearbyDevicesBLE(context, bluetoothAdapter, deviceTextState, discoveredDevices)
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

fun discoverNearbyDevicesBLE(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    textState: MutableState<String>,
    discoveredDevices: MutableList<BluetoothDevice>
) {
    val scanner = bluetoothAdapter.bluetoothLeScanner

    // Check permissions first
    val hasScanPermission = ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_SCAN
    ) == PackageManager.PERMISSION_GRANTED

    val hasConnectPermission = ActivityCompat.checkSelfPermission(
        context, Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED

    val hasLocationPermission = ActivityCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasScanPermission || !hasConnectPermission || !hasLocationPermission) {
        textState.value = "Missing BLE permissions"
        return
    }

    discoveredDevices.clear()

    val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            textState.value = "BLE Scan failed with error: $errorCode"
        }
    }

    val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    textState.value = "Scanning BLE devices..."
    scanner.startScan(null, scanSettings, scanCallback)

    Handler(Looper.getMainLooper()).postDelayed({
        scanner.stopScan(scanCallback)
        if (discoveredDevices.isEmpty()) {
            textState.value = "No BLE devices found."
        } else {
            textState.value = "BLE scan complete. Select a device."
        }
    }, 30000)
}


fun connectToBLEDevice(context: Context, device: BluetoothDevice) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e("BLE", "Missing BLUETOOTH_CONNECT permission. Cannot connect.")
        // Optionally update UI to request permission from user here
        return
    }

    val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to GATT server.")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from GATT server.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    Log.d("BLE", "Service: ${service.uuid}")
                    for (char in service.characteristics) {
                        Log.d("BLE", "Characteristic: ${char.uuid}")
                    }
                }
            }
        }
    }

    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Log.e("BLE", "Missing BLUETOOTH_CONNECT permission.")
        return
    }

    device.connectGatt(context, false, gattCallback)
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

fun getSafeDeviceName(context: Context, device: BluetoothDevice?): String {
    if (device == null) return "Unnamed Device"
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Device"
        } else {
            "Unknown Device (No Permission)"
        }
    } else {
        device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Device"
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
            Text("Connect to Arduino", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(textState.value, color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(discoveredDevices) { device ->
                    val context = LocalContext.current
                    val deviceName = getSafeDeviceName(context, device)
                    val isSelected = selectedDevice.value == device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDevice.value = device }
                            .background(if (isSelected) Color.DarkGray else Color.Transparent)
                            .padding(12.dp)
                    ) {
                        Text(deviceName, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                selectedDevice.value?.let {
                    connectToBLEDevice(context, it)
                    textState.value = "Connecting to ${getSafeDeviceName(context, it)}"
                } ?: run {
                    textState.value = "Please select a device first."
                }
            }) {
                Text("Connect")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                bluetoothAdapter?.let {
                    discoverNearbyDevicesBLE(context, it, textState, discoveredDevices)
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
            Text("Display", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }
    }
}


