package app.appfredo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

private val AppfredoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Motion decisions the Material scheme has no token for. Everything else resolves to
 * `MaterialTheme.motionScheme`, which [AppfredoTheme] sets to Expressive.
 */
internal object AppfredoMotion {
    /**
     * The Standard scheme, for what must settle without overshooting: the library's rows, which are read as
     * data, and the docked tray, whose lower edge is pinned to the screen's.
     */
    val steady: MotionScheme = MotionScheme.standard()

    /** How long a placeholder waits before appearing, so a fast answer never flashes one. */
    const val PlaceholderDelayMillis = 150L

    /** Once a placeholder has appeared it stays at least this long, so it reads as loading rather than a glitch. */
    const val PlaceholderMinimumMillis = 500L
}

@Composable
fun AppfredoTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppfredoColors provides if (dark) DarkAppfredoColors else LightAppfredoColors) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppfredoShapes,
            typography = AppfredoTypography,
            content = content,
        )
    }
}

internal object AppfredoTheme {
    val colors: AppfredoColors
        @Composable @ReadOnlyComposable get() = LocalAppfredoColors.current
}
