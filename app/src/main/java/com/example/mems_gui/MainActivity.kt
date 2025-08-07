package com.example.mems_gui

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.PlotType
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.SelectionHighlightPoint
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import co.yml.charts.ui.linechart.model.ShadowUnderLine
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
                val receivedDataState = remember { mutableStateOf("Waiting for data...") }
                val x_data = remember { mutableStateOf(0.0)}
                val y_data = remember { mutableStateOf(0.0) }

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

                TwoPageSwipeUI(deviceTextState, bluetoothAdapter, discoveredDevices, selectedDevice, receivedDataState, x_data, y_data)
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
                // Access name only if permission allows
                val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (hasConnectPermission) device.name else null
                } else {
                    device.name
                }

                if (!name.isNullOrBlank() && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            textState.value = "BLE Scan failed with error: $errorCode"
        }
    }

    val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    textState.value = "Scanning BLE devices..."
    scanner.startScan(null, scanSettings, scanCallback)

    Handler(Looper.getMainLooper()).postDelayed({
        scanner.stopScan(scanCallback)
        if (discoveredDevices.isEmpty()) {
            textState.value = "No BLE devices found."
        } else {
            textState.value = "BLE scan complete. Select a device."
        }
    }, 10_000)
}


fun connectToBLEDevice(context: Context, device: BluetoothDevice, receivedDataState: MutableState<String>, textState: MutableState<String>, xData: MutableState<Double>, yData: MutableState<Double>)
{
    textState.value = "Connecting to ${getSafeDeviceName(context, device)}"
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
                textState.value = "Connected to ${getSafeDeviceName(context, device)}"
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from GATT server.")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    for (characteristic in service.characteristics) {
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            gatt.setCharacteristicNotification(characteristic, true)

                            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }

                            Log.d("BLE", "Subscribed to ${characteristic.uuid}")
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value?.let { String(it) } ?: "null"
            val parts = data.split(",")
            xData.value = data[0].toDouble()
            yData.value = data[1].toDouble()


            Log.d("BLE", "Received data")
            receivedDataState.value = data
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
    selectedDevice: MutableState<BluetoothDevice?>,
    receivedDataState: MutableState<String>,
    xData: MutableState<Double>,
    yData: MutableState<Double>
) {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = 2,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ConnectScreen(deviceTextState, bluetoothAdapter, discoveredDevices, selectedDevice, receivedDataState, xData, yData)
            1 -> GraphScreen(receivedDataState)
        }
    }
}

fun getSafeDeviceName(context: Context, device: BluetoothDevice?): String {
    if (device == null) return ""
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            device.name ?: ""
        } else {
            "Unknown Device"
        }
    } else {
        device.name ?: ""
    }
}

@Composable
fun ConnectScreen(
    textState: MutableState<String>,
    bluetoothAdapter: BluetoothAdapter?,
    discoveredDevices: SnapshotStateList<BluetoothDevice>,
    selectedDevice: MutableState<BluetoothDevice?>,
    receivedDataState: MutableState<String>,
    xData: MutableState<Double>,
    yData: MutableState<Double>
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

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .fillMaxWidth()
            ) {
                items(discoveredDevices) { device ->
                    val context = LocalContext.current
                    val deviceName = getSafeDeviceName(context, device)
                    val isSelected = selectedDevice.value == device
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDevice.value = device }
                            .background(if (isSelected) Color.DarkGray else Color.Transparent)
                    ) {
                        Text(deviceName, color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))


            Row ()
            {
                Button(onClick = {
                    selectedDevice.value?.let {
                        connectToBLEDevice(context, it, receivedDataState, textState, xData, yData)
                    } ?: run {
                        textState.value = "Please select a device first."
                    }
                }, modifier = Modifier.size(width = 150.dp, height = 40.dp)) {
                    Text(
                        text = "Connect",
                        fontSize = 17.sp
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Button(onClick = {
                    bluetoothAdapter?.let {
                        discoverNearbyDevicesBLE(context, it, textState, discoveredDevices)
                    }
                }, modifier = Modifier.size(width = 150.dp, height = 40.dp)) {
                    Text(
                        text = "Scan Again",
                        fontSize = 17.sp
                    )
                }

            }

        }
    }
}




@SuppressLint("DefaultLocale")
@Composable
fun GraphScreen(receivedDataState: MutableState<String>) {

    val pointsData = remember { mutableStateListOf(Point(0f, 0f)) }

    LaunchedEffect(receivedDataState.value) {
        try {
            val splitData = receivedDataState.value.split(",")
            if (splitData.size == 2) {
                val xValue = splitData[0].toFloat()
                val yValue = splitData[1].toFloat()

                if (pointsData.size == 1 && pointsData.first() == Point(0f, 0f)) {
                    pointsData[0] = Point(xValue, yValue)
                } else {
                    pointsData.add(Point(xValue, yValue))
                }
            }
        } catch (_: Exception) {
        }
    }

    val xAxisData = AxisData.Builder()
        .axisStepSize(100.dp)
        .backgroundColor(Color.Blue)
        .labelData { i -> i.toString() }
        .labelAndAxisLinePadding(15.dp)
        .build()

    val yAxisData = AxisData.Builder()
        .backgroundColor(Color.Red)
        .labelAndAxisLinePadding(20.dp)
        .labelData { i ->
            val yScale = 100f
            String.format("%.1f", i * yScale)
        }.build()

    val lineChartData = remember(pointsData.toList()) {
        LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = pointsData.toList(),
                        LineStyle(),
                        IntersectionPoint(),
                        SelectionHighlightPoint(),
                        ShadowUnderLine(),
                        SelectionHighlightPopUp()
                    )
                )
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(),
            backgroundColor = Color.White
        )
    }

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
            Text("Display", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Received: ${receivedDataState.value}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                lineChartData = lineChartData
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewGraphScreen() {
    val dummyState = remember { mutableStateOf("123,123") }

    GraphScreen(receivedDataState = dummyState)
}


//@Preview(showBackground = true)
//@Composable
//fun ConnectScreenPreview() {
//    // Mock states for preview
//    val textState = remember { mutableStateOf("Ready to connect") }
//    val bluetoothAdapter: BluetoothAdapter? = null // No adapter in preview
//    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
//    val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
//    val receivedDataState = remember { mutableStateOf("") }
//    val x = remember { mutableStateOf(0.0) }
//    val y = remember { mutableStateOf(0.0) }
//
//    ConnectScreen(
//        textState = textState,
//        bluetoothAdapter = bluetoothAdapter,
//        discoveredDevices = discoveredDevices,
//        selectedDevice = selectedDevice,
//        receivedDataState = receivedDataState,
//        xData = x,
//        yData = y
//    )
//}

