package com.example.mems_gui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import android.content.BroadcastReceiver
import android.content.IntentFilter

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter

    // Mutable states for permissions and Bluetooth status
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
            // Disable Bluetooth functionality or show message as needed
            return
        }

        isBluetoothEnabled = bluetoothAdapter.isEnabled

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                allPermissionsGranted = permissions.all { it.value }
                Log.d("Permissions", "Permissions result: $permissions, All granted: $allPermissionsGranted")
                checkBluetoothAndScanReadiness()
            }

        enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    isBluetoothEnabled = bluetoothAdapter.isEnabled
                    Log.d("Bluetooth", "Bluetooth enabled by user.")
                } else {
                    isBluetoothEnabled = false
                    Log.w("Bluetooth", "Bluetooth not enabled by user or action cancelled.")
                }
                checkBluetoothAndScanReadiness()
            }

        setContent {
            MEMS_GUITheme {
                val context = this
                val deviceTextState = remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    checkBluetoothAndScanReadiness()
                }

                LaunchedEffect(allPermissionsGranted, isBluetoothEnabled) {
                    Log.d("AppFlow", "Permissions granted: $allPermissionsGranted, Bluetooth enabled: $isBluetoothEnabled")
                    if (allPermissionsGranted && isBluetoothEnabled) {
                        Log.d("AppFlow", "All conditions met. Discovering devices...")
                        discoverNearbyDevices(context, bluetoothAdapter, deviceTextState)
                    } else {
                        val statusMessages = mutableListOf<String>()
                        if (!allPermissionsGranted) statusMessages.add("Permissions not granted.")
                        if (!isBluetoothEnabled) statusMessages.add("Bluetooth is not enabled.")
                        deviceTextState.value = "Status: ${statusMessages.joinToString(" ")}"
                    }
                }

                TwoPageSwipeUI(deviceTextState, bluetoothAdapter)
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
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
            Log.d("Permissions", "Requesting permissions: $neededPermissions")
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            allPermissionsGranted = true
            Log.d("Permissions", "All required permissions granted.")

            if (!bluetoothAdapter.isEnabled) {
                Log.d("Bluetooth", "Bluetooth not enabled, requesting enablement.")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                isBluetoothEnabled = true
            }
        }
    }

    // This method was outside your class - moved inside and cleaned up
    private fun askForConnectivity() {
        val neededPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }

        if (neededPermissions.isNotEmpty()) {
            Log.d("Permissions", "Requesting permissions: $neededPermissions")
            permissionLauncher.launch(neededPermissions.toTypedArray())
        } else {
            allPermissionsGranted = true
            Log.d("Permissions", "All required permissions already granted")
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableIntent)
        }
    }
}

// Other functions remain largely unchanged but with minor cleanups:

fun queueAvailableDevices(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    textState: MutableState<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        textState.value = "Permission required to access Bluetooth devices"
        return
    }

    val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

    val deviceList = if (pairedDevices.isEmpty()) {
        "No paired devices found"
    } else {
        pairedDevices.joinToString(separator = "\n") { device -> device.name ?: "Unnamed Device" }
    }

    Log.d("Text State", "Device list set to:\n$deviceList")
    textState.value = deviceList
}

fun discoverNearbyDevices(
    context: Context,
    bluetoothAdapter: BluetoothAdapter,
    textState: MutableState<String>
) {
    val discoveredDevices = mutableSetOf<String>()

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("BluetoothScan", "Discovery started")
                    textState.value = "Scanning nearby devices..."
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (context != null &&
                            ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val name = it.name?.takeIf { n -> n.isNotBlank() } ?: "Unnamed Device"
                            val address = it.address
                            val entry = "$name "
                            if (discoveredDevices.add(entry)) {
                                Log.d("BluetoothScan", "Device found: $entry")
                                textState.value = "Scanning nearby devices...\n" + discoveredDevices.joinToString("\n")
                            }
                        } else {
                            Log.w("Bluetooth", "Missing BLUETOOTH_CONNECT permission during discovery")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("BluetoothScan", "Discovery finished")
                    if (context != null) {
                        context.unregisterReceiver(this)
                    }
                    if (discoveredDevices.isEmpty()) {
                        textState.value = "No nearby devices found"
                    } else {
                        textState.value = "Scan complete:\n" + discoveredDevices.joinToString("\n")
                    }
                }
            }
        }
    }

    val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    if (!hasScanPermission) {
        textState.value = "Missing BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission for discovery."
        Log.e("BluetoothScan", "Failed to start discovery: Missing required scan permission.")
        return
    }

    val filter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    context.registerReceiver(receiver, filter)

    if (bluetoothAdapter.isDiscovering) {
        Log.d("BluetoothScan", "Discovery already in progress, cancelling existing discovery.")
        bluetoothAdapter.cancelDiscovery()
    }

    if (!bluetoothAdapter.isEnabled) {
        textState.value = "Bluetooth is not enabled."
        Log.e("BluetoothScan", "Failed to start discovery: Bluetooth is not enabled.")
        return
    }

    val started = bluetoothAdapter.startDiscovery()

    if (started) {
        Log.d("BluetoothScan", "Discovery started successfully")
    } else {
        Log.e(
            "BluetoothScan",
            "Failed to start discovery. " +
                    "Bluetooth enabled: ${bluetoothAdapter.isEnabled}, " +
                    "Permissions granted: $hasScanPermission. " +
                    "Possible reasons: Adapter busy, device not ready, or a deeper system issue."
        )
        textState.value = "Failed to start discovery. Check logs."
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun TwoPageSwipeUI(
    deviceTextState: MutableState<String>,
    bluetoothAdapter: BluetoothAdapter
) {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = 2,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ConnectScreen(deviceTextState, bluetoothAdapter)
            1 -> GraphScreen()
        }
    }
}

@Composable
fun ConnectScreen(
    textState: MutableState<String>,
    bluetoothAdapter: BluetoothAdapter?
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
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Connect to Arduino",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = textState.value,
                enabled = false,
                onValueChange = { newText -> textState.value = newText },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Detected Devices") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                // Add your Bluetooth connection logic here
            }) {
                Text("Connect")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                context.startActivity(discoverableIntent)
            }) {
                Text("Make Device Discoverable")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        textState.value = "Requesting Bluetooth scan permission..."
                        ActivityCompat.requestPermissions(
                            context as ComponentActivity,
                            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
                            100
                        )
                        return@Button
                    }
                } else {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        textState.value = "Requesting location permission..."
                        ActivityCompat.requestPermissions(
                            context as ComponentActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            101
                        )
                        return@Button
                    }
                }

                bluetoothAdapter?.let {
                    discoverNearbyDevices(context, it, textState)
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

//@Preview(showBackground = true)
//@Composable
//fun PreviewConnectScreen() {
//    val dummyState = remember { mutableStateOf("Preview Device") }
//    ConnectScreen(dummyState, null)
//}
//
//@Preview(showBackground = true)
//@Composable
//fun PreviewGraphScreen() {
//    GraphScreen()
//}


