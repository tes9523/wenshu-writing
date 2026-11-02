package com.wenshu.writing.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.wenshu.writing.domain.model.ThemeMode

private val ColorTextDark = androidx.compose.ui.graphics.Color(0xFF2A2218)
private val ColorTextOnDarkPrimary = androidx.compose.ui.graphics.Color(0xFF18202F)

private val LightColors = lightColorScheme(
    primary = InkBlue,
    onPrimary = PaperSurface,
    primaryContainer = InkBlueContainerLight,
    onPrimaryContainer = InkBlue,
    secondary = AccentGold,
    onSecondary = PaperSurface,
    secondaryContainer = AccentGoldContainer,
    onSecondaryContainer = ColorTextDark,
    tertiary = InkBlueLight,
    background = PaperBackground,
    onBackground = TextPrimary,
    surface = PaperSurface,
    onSurface = TextPrimary,
    surfaceVariant = PaperVariant,
    onSurfaceVariant = TextSecondary,
    outline = OutlineWarm,
    outlineVariant = OutlineWarm.copy(alpha = 0.6f)
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = ColorTextOnDarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkSecondary,
    onSecondary = ColorTextOnDarkPrimary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutline.copy(alpha = 0.6f)
)

/** 统一圆角规范：卡片、输入框、对话框与抽屉保持一致的书卷气质。 */
private val WenShuShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WenShuTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WenShuTypography,
        shapes = WenShuShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
