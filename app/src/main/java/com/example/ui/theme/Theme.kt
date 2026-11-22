package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentColorPreset
import com.example.data.model.ThemeMode

private data class AccentTones(
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
)

private fun tonesForPreset(preset: AccentColorPreset, customArgb: Long?): AccentTones {
    if (customArgb != null) {
        val seed = Color(customArgb.toULong() and 0xFFFFFFFFUL or 0xFF000000UL)
        val darkPrimary = lerp(seed, Color.White, 0.38f)
        val darkOnPrimary = if (darkPrimary.luminance() > 0.4f) Color(0xFF0D1120) else Color.White
        val darkContainer = lerp(seed, ChroniqDarkSurfaceContainer, 0.62f)
        val darkOnContainer = lerp(seed, Color.White, 0.80f)

        val lightPrimary = lerp(seed, Color.Black, 0.14f)
        val lightOnPrimary = if (lightPrimary.luminance() > 0.55f) Color(0xFF10131C) else Color.White
        val lightContainer = lerp(seed, Color.White, 0.82f)
        val lightOnContainer = lerp(seed, Color.Black, 0.65f)

        return AccentTones(
            darkPrimary = darkPrimary,
            darkOnPrimary = darkOnPrimary,
            darkContainer = darkContainer,
            darkOnContainer = darkOnContainer,
            lightPrimary = lightPrimary,
            lightOnPrimary = lightOnPrimary,
            lightContainer = lightContainer,
            lightOnContainer = lightOnContainer,
        )
    }

    return when (preset) {
        AccentColorPreset.BLUE -> AccentTones(
            darkPrimary = ChroniqLavenderPrimaryDark,
            darkOnPrimary = ChroniqLavenderOnPrimaryDark,
            darkContainer = ChroniqLavenderContainerDark,
            darkOnContainer = ChroniqLavenderOnContainerDark,
            lightPrimary = ChroniqLavenderPrimaryLight,
            lightOnPrimary = ChroniqLavenderOnPrimaryLight,
            lightContainer = ChroniqLavenderContainerLight,
            lightOnContainer = ChroniqLavenderOnContainerLight,
        )
        AccentColorPreset.INDIGO -> AccentTones(
            darkPrimary = Color(0xFFB4B0FF),
            darkOnPrimary = Color(0xFF1A1464),
            darkContainer = Color(0xFF312A85),
            darkOnContainer = Color(0xFFE4E2FF),
            lightPrimary = Color(0xFF4338CA),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFE0E0FF),
            lightOnContainer = Color(0xFF140D5E),
        )
        AccentColorPreset.PURPLE -> AccentTones(
            darkPrimary = Color(0xFFCBB2FF),
            darkOnPrimary = Color(0xFF33116B),
            darkContainer = Color(0xFF4A258A),
            darkOnContainer = Color(0xFFEDE0FF),
            lightPrimary = Color(0xFF7C3AED),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFEDE4FF),
            lightOnContainer = Color(0xFF280B5C),
        )
        AccentColorPreset.TEAL -> AccentTones(
            darkPrimary = Color(0xFF7ADAD0),
            darkOnPrimary = Color(0xFF003732),
            darkContainer = Color(0xFF0F4F48),
            darkOnContainer = Color(0xFFCCF5F0),
            lightPrimary = Color(0xFF0F766E),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFCCF3EE),
            lightOnContainer = Color(0xFF022B27),
        )
        AccentColorPreset.GREEN -> AccentTones(
            darkPrimary = Color(0xFF84DCA2),
            darkOnPrimary = Color(0xFF003919),
            darkContainer = Color(0xFF11522B),
            darkOnContainer = Color(0xFFCEF5DA),
            lightPrimary = Color(0xFF15803D),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFD0F5DC),
            lightOnContainer = Color(0xFF052E14),
        )
        AccentColorPreset.ORANGE -> AccentTones(
            darkPrimary = Color(0xFFFFB784),
            darkOnPrimary = Color(0xFF4D2100),
            darkContainer = Color(0xFF6E3208),
            darkOnContainer = Color(0xFFFFE1CC),
            lightPrimary = Color(0xFFD95B00),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFFFE2CC),
            lightOnContainer = Color(0xFF421900),
        )
        AccentColorPreset.ROSE -> AccentTones(
            darkPrimary = Color(0xFFFFB0C1),
            darkOnPrimary = Color(0xFF5D0C25),
            darkContainer = Color(0xFF7F1938),
            darkOnContainer = Color(0xFFFFDCE3),
            lightPrimary = Color(0xFFBE123C),
            lightOnPrimary = Color.White,
            lightContainer = Color(0xFFFFDCE4),
            lightOnContainer = Color(0xFF4A0619),
        )
    }
}

private fun buildChroniqColorScheme(
    darkTheme: Boolean,
    accentColor: AccentColorPreset,
    customAccentArgb: Long?,
): ColorScheme {
    val tones = tonesForPreset(accentColor, customAccentArgb)
    return if (darkTheme) {
        darkColorScheme(
            primary = tones.darkPrimary,
            onPrimary = tones.darkOnPrimary,
            primaryContainer = tones.darkContainer,
            onPrimaryContainer = tones.darkOnContainer,
            secondary = Color(0xFFA8C7FA),
            onSecondary = Color(0xFF062E6F),
            secondaryContainer = Color(0xFF1B3A5C),
            onSecondaryContainer = Color(0xFFD3E3FD),
            tertiary = Color(0xFF81C995),
            onTertiary = Color(0xFF00391B),
            tertiaryContainer = Color(0xFF0F3D25),
            onTertiaryContainer = Color(0xFFC4EED0),
            error = DangerCrimson,
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF471B1A),
            onErrorContainer = Color(0xFFF9DEDC),
            background = ChroniqDarkBackground,
            onBackground = ChroniqDarkOnSurface,
            surface = ChroniqDarkSurface,
            onSurface = ChroniqDarkOnSurface,
            surfaceVariant = ChroniqDarkSurfaceContainer,
            onSurfaceVariant = ChroniqDarkOnSurfaceVariant,
            surfaceContainerLowest = ChroniqDarkSurfaceLowest,
            surfaceContainerLow = ChroniqDarkSurfaceLow,
            surfaceContainer = ChroniqDarkSurfaceContainer,
            surfaceContainerHigh = ChroniqDarkSurfaceHigh,
            surfaceContainerHighest = ChroniqDarkSurfaceHighest,
            outline = ChroniqDarkOutline,
            outlineVariant = ChroniqDarkOutlineVariant,
        )
    } else {
        lightColorScheme(
            primary = tones.lightPrimary,
            onPrimary = tones.lightOnPrimary,
            primaryContainer = tones.lightContainer,
            onPrimaryContainer = tones.lightOnContainer,
            secondary = Color(0xFF1A73E8),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD3E3FD),
            onSecondaryContainer = Color(0xFF041E49),
            tertiary = Color(0xFF188038),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFC4EED0),
            onTertiaryContainer = Color(0xFF072711),
            error = DangerDarkText,
            onError = Color.White,
            errorContainer = Color(0xFFFCE8E6),
            onErrorContainer = Color(0xFF5F1410),
            background = ChroniqLightBackground,
            onBackground = ChroniqLightOnSurface,
            surface = ChroniqLightSurface,
            onSurface = ChroniqLightOnSurface,
            surfaceVariant = ChroniqLightSurfaceContainer,
            onSurfaceVariant = ChroniqLightOnSurfaceVariant,
            surfaceContainerLowest = ChroniqLightSurfaceLowest,
            surfaceContainerLow = ChroniqLightSurfaceLow,
            surfaceContainer = ChroniqLightSurfaceContainer,
            surfaceContainerHigh = ChroniqLightSurfaceHigh,
            surfaceContainerHighest = ChroniqLightSurfaceHighest,
            outline = ChroniqLightOutline,
            outlineVariant = ChroniqLightOutlineVariant,
        )
    }
}

val ChronoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ChronoDateTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentColor: AccentColorPreset = AccentColorPreset.BLUE,
    customAccentArgb: Long? = null,
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val resolvedDark = darkTheme ?: when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = buildChroniqColorScheme(
        darkTheme = resolvedDark,
        accentColor = accentColor,
        customAccentArgb = customAccentArgb,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ChronoShapes,
        content = content,
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    ChronoDateTheme(darkTheme = darkTheme, content = content)
}
