package com.locqar.locker.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LocQarBlue = Color(0xFF1565C0)
val LocQarBlueLight = Color(0xFF5E92F3)
val LocQarBlueDark = Color(0xFF003C8F)
val LocQarGreen = Color(0xFF2E7D32)
val LocQarRed = Color(0xFFC62828)
val LocQarOrange = Color(0xFFEF6C00)
val LocQarGray = Color(0xFF757575)
val LocQarLightGray = Color(0xFFF5F5F5)

// Door state colors
val DoorClosedColor = Color(0xFF2E7D32)    // Green
val DoorOpenColor = Color(0xFFC62828)       // Red
val DoorUnknownColor = Color(0xFF757575)    // Gray
val DoorDisabledColor = Color(0xFFBDBDBD)   // Light gray
val DoorReservedColor = Color(0xFFEF6C00)   // Orange
val DoorOccupiedColor = Color(0xFF1565C0)   // Blue
val DoorOutOfServiceColor = Color(0xFF424242) // Dark gray

private val LightColorScheme = lightColorScheme(
    primary = LocQarBlue,
    onPrimary = Color.White,
    primaryContainer = LocQarBlueLight,
    secondary = LocQarGreen,
    onSecondary = Color.White,
    error = LocQarRed,
    onError = Color.White,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = LocQarLightGray
)

@Composable
fun LocQarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
