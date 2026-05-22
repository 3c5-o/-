package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Emerald80,
    secondary = Amber80,
    tertiary = Mint80,
    background = IslamicDarkBg,
    surface = IslamicDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = LightText,
    onSurface = LightText,
    primaryContainer = Color(0xFF2D3135),
    onPrimaryContainer = LightText
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald80,
    secondary = Amber80,
    tertiary = Mint80,
    background = IslamicDarkBg,
    surface = IslamicDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = LightText,
    onSurface = LightText,
    primaryContainer = Color(0xFF2D3135),
    onPrimaryContainer = LightText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set false to preserve our gorgeous curated Islamic theme colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
