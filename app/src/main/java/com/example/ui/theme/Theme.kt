package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = SparkAmber,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF633F00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = SafetyGreen,
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005327),
    onTertiaryContainer = Color(0xFF69FF9E),
    background = TechDarkBg,
    onBackground = TextPrimaryDark,
    surface = TechSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = TechSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = HighVoltageRed
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF006877),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA1EFFF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = SparkAmberDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF2B1700),
    tertiary = Color(0xFF006D35),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF69FF9E),
    onTertiaryContainer = Color(0xFF00210B),
    background = TechLightBg,
    onBackground = Color(0xFF0F172A),
    surface = TechLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = TechLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = OutlineLight,
    error = Color(0xFFBA1A1A)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to sleek dark engineering theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

