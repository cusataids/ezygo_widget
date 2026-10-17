package com.ezygo.attendance.ui.theme

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ezygo.attendance.domain.model.RiskBand
import com.ezygo.attendance.domain.model.ThemeChoice

/** Status colours resolved for the active theme. */
data class StatusColors(
    val safe: Color,
    val watch: Color,
    val risk: Color,
    val neutral: Color,
) {
    fun forBand(band: RiskBand): Color = when (band) {
        RiskBand.SAFE -> safe
        RiskBand.WATCH -> watch
        RiskBand.AT_RISK -> risk
        RiskBand.NO_DATA -> neutral
    }
}

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(StatusSafe, StatusWatch, StatusRisk, StatusNeutral)
}

private val LightScheme = lightColorScheme(primary = BrandBlue)
private val DarkScheme = darkColorScheme(primary = BrandBlueDark)

/**
 * The user's choice, turned into the boolean [EzygoTheme] actually needs.
 *
 * Deliberately the only caller of `isSystemInDarkTheme()` in the app. Reading it
 * anywhere else would give a second answer to the same question, and the two would
 * disagree the moment the user picks anything but System.
 */
@Composable
fun ThemeChoice.resolveDark(): Boolean = when (this) {
    ThemeChoice.DARK -> true
    ThemeChoice.LIGHT -> false
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun EzygoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You keeps the app feeling native on Android 12+.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Material You only exists from API 31; below that the app falls back to its
    // own palette rather than crashing.
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val statusColors = if (darkTheme) {
        StatusColors(StatusSafeDark, StatusWatchDark, StatusRiskDark, StatusNeutralDark)
    } else {
        StatusColors(StatusSafe, StatusWatch, StatusRisk, StatusNeutral)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            // Both bars, not just the status bar. enableEdgeToEdge() decides the icon
            // appearance from the *system* night mode at the moment it is called, and
            // nothing corrected the navigation bar afterwards. That was invisible
            // while the app always agreed with the system; once the theme can be
            // chosen it means dark icons on a dark nav bar the moment the two differ.
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
