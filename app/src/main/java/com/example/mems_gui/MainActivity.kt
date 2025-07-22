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
                ""
            }) {
                Text("Connect")
            }
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
