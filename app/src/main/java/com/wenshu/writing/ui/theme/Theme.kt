package com.wenshu.writing.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.wenshu.writing.domain.model.ThemeMode

private val ColorTextDark = Color(0xFF2A2218)
private val ColorTextOnDarkPrimary = Color(0xFF18202F)

/** 统一圆角规范：卡片、输入框、对话框与抽屉保持一致的书卷气质。 */
private val WenShuShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** 应用字体选项：system=系统默认。 */
fun appFontFamily(fontName: String): FontFamily? = when (fontName) {
    "serif" -> FontFamily.Serif
    "sans" -> FontFamily.SansSerif
    "mono" -> FontFamily.Monospace
    else -> null
}

/** 依据自定义外观配置生成主题。背景图模式下背景色透明，由底层 Box 绘制图片。 */
@Composable
fun WenShuTheme(
    themeMode: ThemeMode,
    themeColorName: String = "INK",
    backgroundColorName: String = "",
    hasBackgroundImage: Boolean = false,
    fontName: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val preset = themeColorPreset(themeColorName)
    val background = when {
        hasBackgroundImage -> Color.Transparent
        darkTheme -> DarkBackground
        else -> backgroundColorPreset(backgroundColorName).color
    }
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = preset.darkPrimary,
            onPrimary = ColorTextOnDarkPrimary,
            primaryContainer = preset.darkPrimaryContainer,
            onPrimaryContainer = DarkTextPrimary,
            secondary = DarkSecondary,
            onSecondary = ColorTextOnDarkPrimary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkTextPrimary,
            tertiary = preset.darkPrimary,
            background = background,
            onBackground = DarkTextPrimary,
            surface = DarkSurface,
            onSurface = DarkTextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            outline = DarkOutline,
            outlineVariant = DarkOutline.copy(alpha = 0.6f)
        )
    } else {
        lightColorScheme(
            primary = preset.lightPrimary,
            onPrimary = PaperSurface,
            primaryContainer = preset.lightPrimaryContainer,
            onPrimaryContainer = preset.lightPrimary,
            secondary = AccentGold,
            onSecondary = PaperSurface,
            secondaryContainer = AccentGoldContainer,
            onSecondaryContainer = ColorTextDark,
            tertiary = preset.lightPrimary,
            background = background,
            onBackground = TextPrimary,
            surface = PaperSurface,
            onSurface = TextPrimary,
            surfaceVariant = PaperVariant,
            onSurfaceVariant = TextSecondary,
            outline = OutlineWarm,
            outlineVariant = OutlineWarm.copy(alpha = 0.6f)
        )
    }

    val appFont = appFontFamily(fontName)
    val typography = if (appFont == null) {
        WenShuTypography
    } else {
        Typography(
            headlineSmall = WenShuTypography.headlineSmall.copy(fontFamily = appFont),
            titleLarge = WenShuTypography.titleLarge.copy(fontFamily = appFont),
            titleMedium = WenShuTypography.titleMedium.copy(fontFamily = appFont),
            titleSmall = WenShuTypography.titleSmall.copy(fontFamily = appFont),
            bodyLarge = WenShuTypography.bodyLarge.copy(fontFamily = appFont),
            bodyMedium = WenShuTypography.bodyMedium.copy(fontFamily = appFont),
            labelMedium = WenShuTypography.labelMedium.copy(fontFamily = appFont)
        )
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
        colorScheme = colors,
        typography = typography,
        shapes = WenShuShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
