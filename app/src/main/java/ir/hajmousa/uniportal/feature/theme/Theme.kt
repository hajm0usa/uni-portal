package ir.hajmousa.uniportal.feature.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import ir.hajmousa.uniportal.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EF2E3),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF446179),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9E6FF),
    onTertiaryContainer = Color(0xFF001E30),
    background = Color(0xFFF5FAF7),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FAF7),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDAE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBEC9C4),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFECF2EE),
    inversePrimary = Color(0xFF82D5C7),
    surfaceDim = Color(0xFFD5DBD7),
    surfaceBright = Color(0xFFF5FAF7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF5F1),
    surfaceContainer = Color(0xFFE9EFEB),
    surfaceContainerHigh = Color(0xFFE3EAE6),
    surfaceContainerHighest = Color(0xFFDEE4E0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C7),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9EF2E3),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFACCAE5),
    onTertiary = Color(0xFF133348),
    tertiaryContainer = Color(0xFF2C4A60),
    onTertiaryContainer = Color(0xFFC9E6FF),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDEE4E0),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDEE4E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF88938E),
    outlineVariant = Color(0xFF3F4945),
    inverseSurface = Color(0xFFDEE4E0),
    inverseOnSurface = Color(0xFF2B3230),
    inversePrimary = Color(0xFF006A60),
    surfaceDim = Color(0xFF0E1512),
    surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF09100D),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252C29),
    surfaceContainerHighest = Color(0xFF303633),
)

private val PersianFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val PersianTypography = Typography().let {
    Typography(
        displayLarge = it.displayLarge.copy(fontFamily = PersianFont),
        displayMedium = it.displayMedium.copy(fontFamily = PersianFont),
        displaySmall = it.displaySmall.copy(fontFamily = PersianFont),
        headlineLarge = it.headlineLarge.copy(fontFamily = PersianFont),
        headlineMedium = it.headlineMedium.copy(fontFamily = PersianFont),
        headlineSmall = it.headlineSmall.copy(fontFamily = PersianFont),
        titleLarge = it.titleLarge.copy(fontFamily = PersianFont),
        titleMedium = it.titleMedium.copy(fontFamily = PersianFont),
        titleSmall = it.titleSmall.copy(fontFamily = PersianFont),
        bodyLarge = it.bodyLarge.copy(fontFamily = PersianFont),
        bodyMedium = it.bodyMedium.copy(fontFamily = PersianFont),
        bodySmall = it.bodySmall.copy(fontFamily = PersianFont),
        labelLarge = it.labelLarge.copy(fontFamily = PersianFont),
        labelMedium = it.labelMedium.copy(fontFamily = PersianFont),
        labelSmall = it.labelSmall.copy(fontFamily = PersianFont),
    )
}

@Composable
fun PortalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, typography = PersianTypography, content = content)
    }
}
