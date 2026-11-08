package com.midroid.masarifi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.midroid.masarifi.R

/** IBM Plex Sans Arabic (SIL OFL 1.1 — see assets/licenses/IBMPlexSansArabic-OFL.txt). */
val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = PlexArabic, fontSize = size.sp, lineHeight = line.sp, fontWeight = w)

val AppTypography = Typography(
    displaySmall = s(36, 44, FontWeight.SemiBold),
    headlineMedium = s(28, 36, FontWeight.SemiBold),
    headlineSmall = s(24, 32, FontWeight.SemiBold),
    titleLarge = s(22, 28, FontWeight.SemiBold),
    titleMedium = s(16, 24, FontWeight.SemiBold),
    titleSmall = s(14, 20, FontWeight.Medium),
    bodyLarge = s(16, 24),
    bodyMedium = s(14, 20),
    bodySmall = s(12, 16),
    labelLarge = s(14, 20, FontWeight.Medium),
    labelMedium = s(12, 16, FontWeight.Medium),
    labelSmall = s(11, 16, FontWeight.Medium),
)

/** Tabular figures so amounts line up in columns. */
val Amount = TextStyle(fontFamily = PlexArabic, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
