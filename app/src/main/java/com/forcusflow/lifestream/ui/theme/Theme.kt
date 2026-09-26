package com.forcusflow.lifestream.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class WallpaperType {
    MISTY_FOREST,
    PARIS_DUSK,
    TOKYO_NIGHT
}

enum class AppThemeMode(val displayName: String, val description: String) {
    CLASSIC_WARM("クラシック・ウォーム", "旧Wunderlist調の紙の温もりと木目調アクセント"),
    FROSTED_GLASS("フロステッド・グラス (Glass)", "最新すりガラス調・インディゴ＆アイススレートの透明感"),
    NORDIC_CLEAN("ノルディック・スノー", "北欧ミニマリズム・純白＆クリーンなスカイブルー"),
    TOKYO_MINIMAL("トーキョー・モダン (Flat)", "Notion/Linear風の研ぎ澄まされたソリッドモノトーン"),
    SAGE_LINEN("セージ＆リネン (Organic)", "くすみグリーンと生成りリネンが心地よい癒やし系"),
    DEEP_SLATE("ディープ・スレート (Dark)", "Taskito風の洗練されたダークスレート & スカイブルー"),
    PURE_MINIMAL_OLED("ピュア・ミニマル (OLED Black)", "Niagara風の完全純黒・エメラルドグリーン (省電力)"),
    MISTY_FOREST("アルプスの大自然 (Photo)", "朝霧漂う深緑の針葉樹林と大自然の静寂"),
    PARIS_DUSK("黄昏のヨーロッパ街並み (Photo)", "夕暮れのパリ・温かな街灯とセピアトワイライト"),
    TOKYO_NIGHT("煌めく夜景・メトロポリス (Photo)", "深夜の都会の光彩・サイバーグラス＆ネオン")
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
    val isDark: Boolean,
    val wallpaperType: WallpaperType? = null
)

val ClassicWarmColors = LifeStreamColors(
    background = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF8C5A3C),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF2D241E),
    textSecondary = Color(0xFF786B61),
    border = Color(0xFFE8E4DC),
    divider = Color(0xFFEFECE6),
    nowLine = Color(0xFFDC2626),
    statusDone = Color(0xFF16A34A),
    statusOverdue = Color(0xFFDC2626),
    statusWarning = Color(0xFFF59E0B),
    statusTarget = Color(0xFF8B5CF6),
    waterBlue = Color(0xFF0284C7),
    isDark = false
)

val FrostedGlassColors = LifeStreamColors(
    background = Color(0xFFF1F5F9),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF6366F1),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    border = Color(0xFFCBD5E1),
    divider = Color(0xFFE2E8F0),
    nowLine = Color(0xFFF43F5E),
    statusDone = Color(0xFF10B981),
    statusOverdue = Color(0xFFEF4444),
    statusWarning = Color(0xFFF59E0B),
    statusTarget = Color(0xFF8B5CF6),
    waterBlue = Color(0xFF0284C7),
    isDark = false
)

val NordicCleanColors = LifeStreamColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF0284C7),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF18181B),
    textSecondary = Color(0xFF71717A),
    border = Color(0xFFE4E4E7),
    divider = Color(0xFFF4F4F5),
    nowLine = Color(0xFFE11D48),
    statusDone = Color(0xFF16A34A),
    statusOverdue = Color(0xFFDC2626),
    statusWarning = Color(0xFFD97706),
    statusTarget = Color(0xFF7C3AED),
    waterBlue = Color(0xFF0284C7),
    isDark = false
)

val TokyoMinimalColors = LifeStreamColors(
    background = Color(0xFFF4F4F5),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF09090B),
    textSecondary = Color(0xFF71717A),
    border = Color(0xFFE4E4E7),
    divider = Color(0xFFF4F4F5),
    nowLine = Color(0xFF18181B),
    statusDone = Color(0xFF059669),
    statusOverdue = Color(0xFFE11D48),
    statusWarning = Color(0xFFD97706),
    statusTarget = Color(0xFF4F46E5),
    waterBlue = Color(0xFF2563EB),
    isDark = false
)

val SageLinenColors = LifeStreamColors(
    background = Color(0xFFF5F4EE),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF2E6F52),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF2B2C27),
    textSecondary = Color(0xFF6B705C),
    border = Color(0xFFE0DDD2),
    divider = Color(0xFFEAE7DC),
    nowLine = Color(0xFFB56576),
    statusDone = Color(0xFF2E6F52),
    statusOverdue = Color(0xFFC84B31),
    statusWarning = Color(0xFFDDA15E),
    statusTarget = Color(0xFF606C38),
    waterBlue = Color(0xFF457B9D),
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
    textPrimary = Color(0xFFF4F4F5),
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

val MistyForestColors = LifeStreamColors(
    background = Color.Transparent,
    surface = Color(0xF2F0FDF4),
    card = Color(0xF2FFFFFF),
    primary = Color(0xFF15803D),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF143022),
    textSecondary = Color(0xFF3F6250),
    border = Color(0x6686EFAC),
    divider = Color(0x44BBF7D0),
    nowLine = Color(0xFFDC2626),
    statusDone = Color(0xFF15803D),
    statusOverdue = Color(0xFFDC2626),
    statusWarning = Color(0xFFD97706),
    statusTarget = Color(0xFF047857),
    waterBlue = Color(0xFF0284C7),
    isDark = false,
    wallpaperType = WallpaperType.MISTY_FOREST
)

val ParisDuskColors = LifeStreamColors(
    background = Color.Transparent,
    surface = Color(0xF2FFFBEB),
    card = Color(0xF2FFFFFF),
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF2E1908),
    textSecondary = Color(0xFF6C4830),
    border = Color(0x66FED7AA),
    divider = Color(0x44FFEDD5),
    nowLine = Color(0xFFE11D48),
    statusDone = Color(0xFF16A34A),
    statusOverdue = Color(0xFFDC2626),
    statusWarning = Color(0xFFD97706),
    statusTarget = Color(0xFFC2410C),
    waterBlue = Color(0xFF0284C7),
    isDark = false,
    wallpaperType = WallpaperType.PARIS_DUSK
)

val TokyoNightColors = LifeStreamColors(
    background = Color.Transparent,
    surface = Color(0xEE1E293B),
    card = Color(0xEE1E293B),
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0x4438BDF8),
    divider = Color(0x33334155),
    nowLine = Color(0xFFF43F5E),
    statusDone = Color(0xFF34D399),
    statusOverdue = Color(0xFFF87171),
    statusWarning = Color(0xFFFBBF24),
    statusTarget = Color(0xFFA78BFA),
    waterBlue = Color(0xFF38BDF8),
    isDark = true,
    wallpaperType = WallpaperType.TOKYO_NIGHT
)

val LocalLifeStreamColors = staticCompositionLocalOf { ClassicWarmColors }

@Composable
fun LifeStreamTheme(
    themeMode: AppThemeMode = AppThemeMode.CLASSIC_WARM,
    content: @Composable () -> Unit
) {
    val colors = when (themeMode) {
        AppThemeMode.CLASSIC_WARM -> ClassicWarmColors
        AppThemeMode.FROSTED_GLASS -> FrostedGlassColors
        AppThemeMode.NORDIC_CLEAN -> NordicCleanColors
        AppThemeMode.TOKYO_MINIMAL -> TokyoMinimalColors
        AppThemeMode.SAGE_LINEN -> SageLinenColors
        AppThemeMode.DEEP_SLATE -> DeepSlateColors
        AppThemeMode.PURE_MINIMAL_OLED -> PureMinimalOledColors
        AppThemeMode.MISTY_FOREST -> MistyForestColors
        AppThemeMode.PARIS_DUSK -> ParisDuskColors
        AppThemeMode.TOKYO_NIGHT -> TokyoNightColors
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
