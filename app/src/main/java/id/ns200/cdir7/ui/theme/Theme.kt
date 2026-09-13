package id.ns200.cdir7.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CdiDarkColorScheme = darkColorScheme(
    primary = MotecOrange,
    onPrimary = CarbonDark,
    primaryContainer = CardBackground,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = CarbonDark,
    secondaryContainer = CardHover,
    onSecondaryContainer = TextPrimary,
    tertiary = RacingLime,
    onTertiary = CarbonDark,
    background = CarbonDark,
    onBackground = TextPrimary,
    surface = SurfacePanel,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = RaceRedline
)

@Composable
fun CdiR7Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CdiDarkColorScheme,
        content = content
    )
}
