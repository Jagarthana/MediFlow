package com.mediflow.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── MediFlow Light Color Scheme ─────────────────────────────────────────────
private val MediFlowLightColorScheme = lightColorScheme(
    primary          = ForestEmerald,
    onPrimary        = SurfaceWhite,
    primaryContainer = Color(0xFFD1FAE5),    // Emerald-50
    onPrimaryContainer = Color(0xFF064E3B),  // Emerald-900

    secondary        = HealthcareBlue,
    onSecondary      = SurfaceWhite,
    secondaryContainer = Color(0xFFDBEAFE), // Blue-100
    onSecondaryContainer = Color(0xFF1E3A8A), // Blue-900

    tertiary         = MintGreen,
    onTertiary       = SurfaceWhite,
    tertiaryContainer = Color(0xFFD1FAE5),
    onTertiaryContainer = Color(0xFF064E3B),

    error            = ErrorRed,
    onError          = SurfaceWhite,
    errorContainer   = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    background       = LightBackground,
    onBackground     = DarkSlate,
    surface          = SurfaceWhite,
    onSurface        = DarkSlate,
    surfaceVariant   = Color(0xFFF1F5F9),  // Slate-100
    onSurfaceVariant = SlateMedium,

    outline          = BorderColor,
    outlineVariant   = Color(0xFFCBD5E1),  // Slate-300
    scrim            = Color(0xFF000000),
    inverseSurface   = DarkSlate,
    inverseOnSurface = LightBackground,
    inversePrimary   = MintGreen,
)

// ─── MediFlow Dark Color Scheme ──────────────────────────────────────────────
private val MediFlowDarkColorScheme = darkColorScheme(
    primary          = MintGreen,
    onPrimary        = DarkSlate,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFD1FAE5),

    secondary        = Color(0xFF60A5FA),   // Blue-400
    onSecondary      = DarkSlate,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),

    tertiary         = ForestEmerald,
    onTertiary       = SurfaceWhite,

    error            = Color(0xFFF87171),   // Red-400
    onError          = Color(0xFF7F1D1D),
    errorContainer   = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),

    background       = DarkBackground,
    onBackground     = DarkOnSurface,
    surface          = DarkSurface,
    onSurface        = DarkOnSurface,
    surfaceVariant   = Color(0xFF1E293B),   // Slate-800
    onSurfaceVariant = SlateLight,

    outline          = Color(0xFF334155),   // Slate-700
    outlineVariant   = Color(0xFF1E293B),
)

// Helper to use Color in theme file without import collision
private fun Color(colorLong: Long) = androidx.compose.ui.graphics.Color(colorLong)

// ─── MediFlow Theme ───────────────────────────────────────────────────────────
@Composable
fun MediFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled to preserve MediFlow brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> MediFlowDarkColorScheme
        else -> MediFlowLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediFlowTypography,
        content = content
    )
}
