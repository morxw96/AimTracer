package de.aimtracer.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Mint = Color(0xFF128C78)

private val LightColors = lightColorScheme(
    primary = Mint,
    secondary = Color(0xFF50665F),
    background = Color(0xFFF7F8F8),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF63D5BD),
    secondary = Color(0xFFB4CCC4)
)

@Composable
fun AimTracerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
