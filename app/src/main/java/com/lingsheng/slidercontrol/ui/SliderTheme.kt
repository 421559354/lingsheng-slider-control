package com.lingsheng.slidercontrol.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SliderColors = lightColorScheme(
    primary = Color(0xFF087E73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F2EC),
    onPrimaryContainer = Color(0xFF073D38),
    secondary = Color(0xFF56636A),
    background = Color(0xFFF4F7F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EFED),
    error = Color(0xFFB3261E),
)

@Composable
fun SliderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SliderColors,
        content = content,
    )
}
