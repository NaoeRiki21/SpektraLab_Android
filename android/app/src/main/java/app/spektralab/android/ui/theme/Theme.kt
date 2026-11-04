package app.spektralab.android.ui.theme

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

enum class ThemeMode {
    DARK, LIGHT, SYSTEM
}

private val DarkroomDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3E2200),
    primaryContainer = Color(0xFF5A3400),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFE0BFA0),
    onSecondary = Color(0xFF3E2B16),
    secondaryContainer = Color(0xFF57402A),
    onSecondaryContainer = Color(0xFFFEDBBA),
    tertiary = Color(0xFFFF8A80),
    onTertiary = Color(0xFF561310),
    tertiaryContainer = Color(0xFF742924),
    onTertiaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE5E2E1),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFE5E2E1),
    surfaceVariant = Color(0xFF25282F),
    onSurfaceVariant = Color(0xFFC8C5CA),
    outline = Color(0xFF929095),
    outlineVariant = Color(0xFF47464B)
)

private val ArchivalPaperLightColorScheme = lightColorScheme(
    primary = Color(0xFF8D4E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF6F5940),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFADEBC),
    onSecondaryContainer = Color(0xFF271905),
    tertiary = Color(0xFF9C413B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFF4F1EC),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFE7E2D8),
    onSurfaceVariant = Color(0xFF4B463F),
    outline = Color(0xFF7C766E),
    outlineVariant = Color(0xFFCEC6BC)
)

@Composable
fun SpektraLabTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkroomDarkColorScheme
        else -> ArchivalPaperLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
