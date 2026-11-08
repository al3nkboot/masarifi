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
    val Red = Color(0xFFEF5350)
    val Amber = Color(0xFFE6A23C)
    val ErrorDark = Color(0xFFFFB4AB)
    val ErrorLight = Color(0xFFBA1A1A)
    val Chart = listOf(
        Color(0xFFF25C54), Color(0xFFF5A623), Color(0xFFF8D347), Color(0xFF7ED321), Color(0xFF3DBE7A),
        Color(0xFF4A90E2), Color(0xFF7B61FF), Color(0xFFBD10E0), Color(0xFFFF7AA2), Color(0xFF50E3C2),
        Color(0xFF9B9B9B), Color(0xFFB8860B),
    )
}

data class ExtraColors(
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val muted: Color,
    val divider: Color,
    val card: Color,
    val chip: Color,
    val positive: Color = Brand.Green,
    val negative: Color = Brand.Red,
    val warning: Color = Brand.Amber,
    val chart: List<Color> = Brand.Chart,
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
    error = Brand.ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFF233A57),
    onSecondaryContainer = Color(0xFFD3E4FF),
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
    error = Brand.ErrorLight,
    errorContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFFD3E4FF),
    onSecondaryContainer = Color(0xFF001C38),
)

@Composable
fun MasarifiTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val extra = if (dark) {
        ExtraColors(Brand.Blue, Brand.Coral, Color(0xFFECECEE), Color(0xFF8E8F95), Color(0xFF303135), Color(0xFF26272B), Color(0xFF3A3B40), positive = Color(0xFF4CAF50), negative = Brand.Red, warning = Brand.Amber)
    } else {
        ExtraColors(Color(0xFF2F7BD6), Brand.CoralDark, Color(0xFF222222), Color(0xFF7A7A80), Color(0xFFE2E2E6), Color.White, Color(0xFFE6E6EA), positive = Color(0xFF2E7D32), negative = Color(0xFFC62828), warning = Color(0xFFB26A00))
    }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object AppTheme {
    val colors: ExtraColors
        @Composable get() = LocalExtraColors.current
}
