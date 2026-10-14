package app.appfredo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Appfredo's fixed palette. Hand-picked rather than generated: a tonal palette turns banana yellow into mustard. */
internal object Banana {
    val Peel = Color(0xFFFFD43B)
    val Ripe = Color(0xFFF2B50C)
    val Butter = Color(0xFFFFF4CC)
    val Cream = Color(0xFFFFFBEB)
    val Linen = Color(0xFFFFFDF5)
    val Tailcoat = Color(0xFF1F1A10)
    val Night = Color(0xFF17140C)
}

// Light is a butler in tails: ink does the talking, yellow is the bow tie. Dark swaps the two.
internal val LightScheme = lightColorScheme(
    primary = Banana.Tailcoat, onPrimary = Banana.Peel,
    primaryContainer = Banana.Peel, onPrimaryContainer = Banana.Tailcoat,
    inversePrimary = Banana.Peel,
    secondary = Color(0xFF6B5514), onSecondary = Banana.Cream,
    secondaryContainer = Color(0xFFFFE78F), onSecondaryContainer = Color(0xFF2A2204),
    tertiary = Color(0xFF2B6E45), onTertiary = Banana.Linen,
    tertiaryContainer = Color(0xFFD5EFC9), onTertiaryContainer = Color(0xFF0E2F1B),
    error = Color(0xFFB43B1F), onError = Color.White,
    errorContainer = Color(0xFFFFDBD0), onErrorContainer = Color(0xFF3D0D00),
    background = Banana.Butter, onBackground = Banana.Tailcoat,
    surface = Banana.Cream, onSurface = Banana.Tailcoat,
    surfaceVariant = Color(0xFFF6E9B4), onSurfaceVariant = Color(0xFF5F5433),
    surfaceTint = Color(0xFF6B5514),
    inverseSurface = Banana.Tailcoat, inverseOnSurface = Banana.Butter,
    outline = Color(0xFF8A7A45), outlineVariant = Color(0xFFE8D89A),
    scrim = Color.Black,
    surfaceBright = Banana.Linen, surfaceDim = Color(0xFFEFE3B0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFF8DF),
    surfaceContainer = Banana.Butter, surfaceContainerHigh = Color(0xFFFFEFB5),
    surfaceContainerHighest = Color(0xFFFFE99A),
)

internal val DarkScheme = darkColorScheme(
    primary = Banana.Peel, onPrimary = Color(0xFF2A2100),
    primaryContainer = Color(0xFF3D3200), onPrimaryContainer = Color(0xFFFFE68A),
    inversePrimary = Color(0xFF6B5514),
    secondary = Color(0xFFE2C766), onSecondary = Color(0xFF3A2F00),
    secondaryContainer = Color(0xFF4A3F18), onSecondaryContainer = Color(0xFFFFE9A0),
    tertiary = Color(0xFF9BD8A8), onTertiary = Color(0xFF00391C),
    tertiaryContainer = Color(0xFF1F4D33), onTertiaryContainer = Color(0xFFB7F1C3),
    error = Color(0xFFFFB4A1), onError = Color(0xFF5F1600),
    errorContainer = Color(0xFF7D2A12), onErrorContainer = Color(0xFFFFDBD0),
    background = Banana.Night, onBackground = Color(0xFFF3EBD2),
    surface = Banana.Night, onSurface = Color(0xFFF3EBD2),
    surfaceVariant = Color(0xFF4B4428), onSurfaceVariant = Color(0xFFCFC49E),
    surfaceTint = Banana.Peel,
    inverseSurface = Color(0xFFF3EBD2), inverseOnSurface = Color(0xFF332F20),
    outline = Color(0xFF9A8F68), outlineVariant = Color(0xFF4B4428),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3E3929), surfaceDim = Banana.Night,
    surfaceContainerLowest = Color(0xFF100E07), surfaceContainerLow = Color(0xFF1E1B12),
    surfaceContainer = Color(0xFF231F15), surfaceContainerHigh = Color(0xFF2D291D),
    surfaceContainerHighest = Color(0xFF383326),
)

/** Roles Material has no slot for. Each pair is checked for at least 4.5:1. */
@Immutable
internal class AppfredoColors(
    val page: Color,
    val card: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /** The pale disc the illustrations stand on. The same in both themes, so the art never needs a dark variant. */
    val spotlight: Color,
    val tray: Color,
    val onTray: Color,
    val onTrayMuted: Color,
    /** Banana yellow wherever it must stay yellow regardless of theme. */
    val accent: Color,
    val onAccent: Color,
)

internal val LightAppfredoColors = AppfredoColors(
    page = Banana.Butter, card = Banana.Cream,
    hero = Banana.Peel, onHero = Banana.Tailcoat, onHeroMuted = Color(0xFF4D4005),
    spotlight = Banana.Butter,
    tray = Banana.Tailcoat, onTray = Banana.Linen, onTrayMuted = Color(0xFFCFC6A8),
    accent = Banana.Peel, onAccent = Banana.Tailcoat,
)

internal val DarkAppfredoColors = AppfredoColors(
    page = Banana.Night, card = Color(0xFF231F15),
    hero = Color(0xFF2B2510), onHero = Banana.Peel, onHeroMuted = Color(0xFFD9CB98),
    spotlight = Banana.Butter,
    tray = Color(0xFF2D291D), onTray = Color(0xFFF3EBD2), onTrayMuted = Color(0xFFCFC49E),
    accent = Banana.Peel, onAccent = Banana.Tailcoat,
)

internal val LocalAppfredoColors = staticCompositionLocalOf { LightAppfredoColors }
