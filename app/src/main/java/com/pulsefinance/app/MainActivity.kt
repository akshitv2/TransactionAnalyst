package com.pulsefinance.app

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefinance.app.ui.DashboardScreen
import com.pulsefinance.app.ui.DashboardViewModel
import com.pulsefinance.app.ui.Pulse
import com.pulsefinance.app.ui.PulseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            PulseTheme {
                PulseApp(viewModel)
            }
        }
    }
}

@Composable
private fun PulseApp(vm: DashboardViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    // Config loaders
    val templateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readUri(context, it)?.let { json -> vm.importConfig("templates.json", json) } }
    }
    val storeMapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readUri(context, it)?.let { json -> vm.importConfig("store_map.json", json) } }
    }

    // "Live on open": every time the app comes to the foreground, re-read the inbox.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Ask once, automatically, on first launch.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.hasPermission) {
        if (!state.hasPermission && !asked) {
            asked = true
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    if (state.hasPermission) {
        DashboardScreen(
            state = state,
            vm = vm,
            onImportTemplates = { templateLauncher.launch("application/json") },
            onImportStoreMap = { storeMapLauncher.launch("application/json") },
        )
    } else {
        PermissionScreen(
            denied = state.permissionDenied,
            onRequest = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
        )
    }
}

private fun readUri(context: android.content.Context, uri: Uri): String? =
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }

@Composable
private fun PermissionScreen(denied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Pulse.Bg)
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Read your bank alerts", color = Pulse.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "PulseFinance reads the messages already on this phone, picks out bank and card alerts, " +
                "and turns them into your dashboard. The app has no internet permission, so nothing " +
                "leaves your device.",
            color = Pulse.TextSoft,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
        if (denied) {
            Spacer(Modifier.height(12.dp))
            Text(
                "SMS access was declined. Allow it in system settings to see your spending.",
                color = Pulse.Amber,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Pulse.BlueDeep, contentColor = androidx.compose.ui.graphics.Color.White),
        ) {
            Text("Allow SMS access")
        }
        if (denied) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }) {
                Text("Open app settings", color = Pulse.Blue)
            }
        }
    }
}
