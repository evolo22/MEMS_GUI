package com.example.mems_gui

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.mems_gui.ui.theme.MEMS_GUITheme
import com.google.accompanist.pager.*
import androidx.compose.ui.tooling.preview.Preview


import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MEMS_GUITheme {
                TwoPageSwipeUI()
            }
        }
    }
}

object BluetoothHelper {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val TAG = "BluetoothHelper"

    private val UUID_SERIAL_PORT: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun enableBluetooth(activity: Activity, requestCode: Int) {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(enableBtIntent, requestCode)
    }

    fun getPairedDevices(): Set<BluetoothDevice>? {
        return bluetoothAdapter?.bondedDevices
    }

    fun connectToDevice(device: BluetoothDevice): Boolean {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT)
            bluetoothSocket?.connect()
            Log.d(TAG, "Connected to device")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            return false
        }
    }

    fun sendData(data: String) {
        bluetoothSocket?.outputStream?.write(data.toByteArray())
    }

    fun disconnect() {
        bluetoothSocket?.close()
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun TwoPageSwipeUI() {
    val pagerState = rememberPagerState()

    HorizontalPager(
        count = 2,
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ConnectScreen()
            1 -> GraphScreen()
        }
    }
}


fun connectToArduino(activity: Activity)
{
    if (!BluetoothHelper.isBluetoothSupported()) {
        Log.e("Connect", "Bluetooth not supported.")
        return
    }

    if (!BluetoothHelper.isBluetoothEnabled()) {
        BluetoothHelper.enableBluetooth(activity, 1)
        return
    }

    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 100)
        return
    }

    val devices = BluetoothHelper.getPairedDevices()
    val arduinoDevice = devices?.find { it.name == "Your_Arduino_Name" }

    if (arduinoDevice != null) {
        val connected = BluetoothHelper.connectToDevice(arduinoDevice)
        if (connected) {
            Log.d("Connect", "Successfully connected to Arduino.")
        } else {
            Log.e("Connect", "Failed to connect.")
        }
    } else {
        Log.e("Connect", "Arduino device not found.")
    }
}

@Composable
fun ConnectScreen() {
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
            Button(onClick = {
                connectToArduino(context as Activity)
            }) {
                Text("Connect")
            }
            Text(
                //
                text = "Added text"
            )
        }
    }
}

@Composable
fun GraphScreen() {
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
                text = "Display",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // TODO: Add graph display logic here
            }) {
                Text("Connect")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewConnectScreen() {
    MEMS_GUITheme {
        ConnectScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGraphScreen() {
    MEMS_GUITheme {
        GraphScreen()
    }
}
