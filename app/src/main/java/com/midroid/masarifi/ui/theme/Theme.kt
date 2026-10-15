package com.midroid.masarifi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object Brand {
    val Coral = Color(0xFFF25C54)
    val CoralDark = Color(0xFFD9443D)
    val Blue = Color(0xFF4A90E2)
    val Orange = Color(0xFFF5A623)
    val Green = Color(0xFF3DBE7A)
}

data class ExtraColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val muted: Color,
    val divider: Color,
    val card: Color,
    val chip: Color,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Brand.Blue, Brand.Coral, Color.White, Color.Gray, Color.DarkGray, Color.DarkGray, Color.DarkGray)
}

private val DarkScheme = darkColorScheme(
    primary = Brand.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A2422),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Brand.Blue,
    onSecondary = Color.White,
    background = Color(0xFF1E1F22),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF1E1F22),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF2B2C30),
    onSurfaceVariant = Color(0xFFA3A4AA),
    surfaceContainer = Color(0xFF26272B),
    surfaceContainerHigh = Color(0xFF2E2F33),
    surfaceContainerHighest = Color(0xFF36373C),
    surfaceContainerLow = Color(0xFF232427),
    surfaceContainerLowest = Color(0xFF18191B),
    outline = Color(0xFF4A4B50),
    outlineVariant = Color(0xFF34353A),
    error = Brand.Coral,
)

private val LightScheme = lightColorScheme(
    primary = Brand.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Brand.Blue,
    background = Color(0xFFF6F6F8),
    surface = Color(0xFFF6F6F8),
    surfaceVariant = Color(0xFFE9E9EE),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F0F3),
    surfaceContainerHighest = Color(0xFFE6E6EA),
    surfaceContainerLow = Color(0xFFFFFFFF),
    error = Brand.CoralDark,
)

@Composable
fun MasarifiTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val extra = if (dark) {
        ExtraColors(Brand.Blue, Brand.Coral, Color(0xFFECECEE), Color(0xFF8E8F95), Color(0xFF303135), Color(0xFF26272B), Color(0xFF3A3B40))
    } else {
        ExtraColors(Color(0xFF2F7BD6), Brand.CoralDark, Color(0xFF222222), Color(0xFF7A7A80), Color(0xFFE2E2E6), Color.White, Color(0xFFE6E6EA))
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

object AppTheme {
    val colors: ExtraColors
        @Composable get() = LocalExtraColors.current
}
