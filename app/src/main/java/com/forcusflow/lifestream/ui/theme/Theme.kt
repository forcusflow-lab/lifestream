package com.forcusflow.lifestream.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemeMode(val displayName: String, val description: String) {
    CLASSIC_WARM("クラシック・ウォーム", "旧Wunderlist調の紙の温もりと木目調アクセント"),
    DEEP_SLATE("ディープ・スレート (Dark)", "Taskito風の洗練されたダークスレート & スカイブルー"),
    PURE_MINIMAL_OLED("ピュア・ミニマル (OLED Black)", "Niagara風の完全純黒・エメラルドグリーン (省電力)")
}

data class LifeStreamColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val primary: Color,
    val onPrimary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val divider: Color,
    val nowLine: Color,
    val statusDone: Color,
    val statusOverdue: Color,
    val statusWarning: Color,
    val statusTarget: Color,
    val waterBlue: Color,
    val isDark: Boolean
)

val ClassicWarmColors = LifeStreamColors(
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF8C5A3C),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF2D241E),
    textSecondary = Color(0xFF786B61),
    border = Color(0xFFE2DDD5),
    divider = Color(0xFFEFECE6),
    nowLine = Color(0xFFDC2626),
    statusDone = Color(0xFF16A34A),
    statusOverdue = Color(0xFFDC2626),
    statusWarning = Color(0xFFF59E0B),
    statusTarget = Color(0xFF8B5CF6),
    waterBlue = Color(0xFF0284C7),
    isDark = false
)

val DeepSlateColors = LifeStreamColors(
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    card = Color(0xFF1E293B),
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0xFF334155),
    divider = Color(0xFF1E293B),
    nowLine = Color(0xFFF87171),
    statusDone = Color(0xFF4ADE80),
    statusOverdue = Color(0xFFEF4444),
    statusWarning = Color(0xFFFBBF24),
    statusTarget = Color(0xFFA78BFA),
    waterBlue = Color(0xFF38BDF8),
    isDark = true
)

val PureMinimalOledColors = LifeStreamColors(
    background = Color(0xFF000000),
    surface = Color(0xFF121212),
    card = Color(0xFF121212),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    border = Color(0xFF27272A),
    divider = Color(0xFF1E1E1E),
    nowLine = Color(0xFFEF4444),
    statusDone = Color(0xFF10B981),
    statusOverdue = Color(0xFFEF4444),
    statusWarning = Color(0xFFF59E0B),
    statusTarget = Color(0xFFA855F7),
    waterBlue = Color(0xFF10B981),
    isDark = true
)

val LocalLifeStreamColors = staticCompositionLocalOf { ClassicWarmColors }

@Composable
fun LifeStreamTheme(
    themeMode: AppThemeMode = AppThemeMode.CLASSIC_WARM,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        AppThemeMode.CLASSIC_WARM -> ClassicWarmColors
        AppThemeMode.DEEP_SLATE -> DeepSlateColors
        AppThemeMode.PURE_MINIMAL_OLED -> PureMinimalOledColors
    }

    CompositionLocalProvider(
        LocalLifeStreamColors provides colors,
        content = content
    )
}

object LifeStreamTheme {
    val colors: LifeStreamColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLifeStreamColors.current
}
