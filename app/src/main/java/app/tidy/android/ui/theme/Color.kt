package app.tidy.android.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Cool neutral greys so other apps' icons carry the color, with one clear
// blue for actions. Matches the approved design mock.

val TidyBlue = Color(0xFF2F6FEB)

internal val LightColors = lightColorScheme(
    primary = TidyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3ECFD),
    onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    background = Color(0xFFEEF0F4),
    onBackground = Color(0xFF16181D),
    surface = Color.White,
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFDDE1E8),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE6E9EE),
    inverseSurface = Color(0xFF1F2329),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF8FB4FF),
    outline = Color(0xFFC3C8D1),
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFC4384A),
    onError = Color.White,
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0B2A66),
    primaryContainer = Color(0xFF1C3570),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFA9B0BB),
    onSecondary = Color(0xFF16181D),
    background = Color(0xFF121418),
    onBackground = Color(0xFFEEF0F4),
    surface = Color(0xFF1E2127),
    onSurface = Color(0xFFEEF0F4),
    surfaceVariant = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFA9B0BB),
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Color(0xFF181B20),
    surfaceContainer = Color(0xFF1E2127),
    surfaceContainerHigh = Color(0xFF252930),
    surfaceContainerHighest = Color(0xFF2E333B),
    inverseSurface = Color(0xFFEEF0F4),
    inverseOnSurface = Color(0xFF16181D),
    inversePrimary = TidyBlue,
    outline = Color(0xFF4A505A),
    outlineVariant = Color(0xFF30353D),
    error = Color(0xFFFF8A95),
    onError = Color(0xFF4A0711),
)
