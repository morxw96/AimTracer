package de.aimtracer.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.aimtracer.android.ui.AimTracerTheme
import de.aimtracer.android.ui.LiveScreen
import de.aimtracer.android.ui.SessionsScreen
import de.aimtracer.android.ui.SettingsScreen
import de.aimtracer.android.ui.Text

class MainActivity : ComponentActivity() {
    private val viewModel: AimTracerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AimTracerTheme {
                AimTracerRoot(viewModel)
            }
        }
    }
}

private enum class MainTab(val title: String, val glyph: String) {
    LIVE("Live", "◎"),
    SESSIONS("Sessions", "◷"),
    SETTINGS("Kalibrierung", "≡")
}

@Composable
private fun AimTracerRoot(viewModel: AimTracerViewModel) {
    var selectedTab by remember { mutableStateOf(MainTab.LIVE) }
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { !it }) {
            viewModel.ble.message =
                "Ohne Bluetooth-Berechtigung kann AimTracer das XIAO nicht suchen."
        }
    }
    LaunchedEffect(Unit) {
        if (!viewModel.ble.hasRequiredPermissions()) {
            permissionLauncher.launch(permissions)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.glyph) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            MainTab.LIVE -> LiveScreen(
                viewModel = viewModel,
                contentPadding = padding,
                requestPermissions = {
                    permissionLauncher.launch(permissions)
                }
            )

            MainTab.SESSIONS -> SessionsScreen(
                viewModel = viewModel,
                contentPadding = padding
            )

            MainTab.SETTINGS -> SettingsScreen(
                viewModel = viewModel,
                contentPadding = padding
            )
        }
    }

    viewModel.ble.message?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.ble.message = null },
            title = { Text("AimTracer") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.ble.message = null }) {
                    Text("OK")
                }
            }
        )
    }
}
