package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = ZoyaNeonPink,
    secondary = ZoyaNeonCyan,
    tertiary = ZoyaNeonPurple,
    background = ZoyaObsidian,
    surface = ZoyaSurfaceDark,
    surfaceVariant = ZoyaSurfaceElevated,
    onPrimary = ZoyaObsidian,
    onSecondary = ZoyaObsidian,
    onTertiary = ZoyaObsidian,
    onBackground = ZoyaTextPrimary,
    onSurface = ZoyaTextPrimary,
    onSurfaceVariant = ZoyaTextSecondary,
    outline = ZoyaBorder,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = ZoyaObsidian.toArgb()
      window.navigationBarColor = ZoyaObsidian.toArgb()
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
      WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
