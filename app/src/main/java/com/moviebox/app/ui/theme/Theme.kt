package com.moviebox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.ThemeVariant

val LocalThemeVariant = staticCompositionLocalOf { ThemeVariant.DEEP_RED }

private val DeepRedScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color.White,
    secondary = StatusBlue,
    onSecondary = Color.Black,
    tertiary = StarGold,
    background = DeepRedBg,
    onBackground = TextWhite,
    surface = DeepRedSurface,
    onSurface = TextWhite,
    surfaceVariant = DeepRedElevated,
    onSurfaceVariant = TextSecondary,
    outline = DeepRedBorder,
    outlineVariant = DeepRedBorder.copy(alpha = 0.6f),
    error = StatusRed,
    onError = Color.White
)

private val DeepBlackScheme = darkColorScheme(
    primary = CrimsonLight,
    onPrimary = Color.White,
    primaryContainer = CrimsonRed,
    onPrimaryContainer = Color.White,
    secondary = StatusBlue,
    onSecondary = Color.Black,
    tertiary = StarGold,
    background = DeepBlackBg,
    onBackground = TextWhite,
    surface = DeepBlackSurface,
    onSurface = TextWhite,
    surfaceVariant = DeepBlackElevated,
    onSurfaceVariant = TextSecondary,
    outline = DeepBlackBorder,
    outlineVariant = DeepBlackBorder.copy(alpha = 0.6f),
    error = StatusRed,
    onError = Color.White
)

private val MovieBoxTypography = Typography(
    headlineLarge = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

private val MovieBoxShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun MovieBoxTheme(
    variant: ThemeVariant = ThemeVariant.DEEP_RED,
    content: @Composable () -> Unit
) {
    val scheme = when (variant) {
        ThemeVariant.DEEP_RED -> DeepRedScheme
        ThemeVariant.DEEP_BLACK -> DeepBlackScheme
    }
    CompositionLocalProvider(LocalThemeVariant provides variant) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MovieBoxTypography,
            shapes = MovieBoxShapes,
            content = content
        )
    }
}
