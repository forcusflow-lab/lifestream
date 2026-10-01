package com.forcusflow.lifestream.widget

import android.content.Context
import android.content.SharedPreferences

import com.forcusflow.lifestream.ui.theme.*

enum class WidgetFontSize(val label: String, val scale: Float) {
    COMPACT("コンパクト (手帳密度)", 0.85f),
    STANDARD("標準 (見やすさ重視)", 1.0f),
    LARGE("大きめ (視認性重視)", 1.15f)
}

object WidgetSettingsManager {
    private const val PREFS_NAME = "lifestream_widget_prefs"
    private const val KEY_OPACITY = "widget_opacity"
    private const val KEY_FONT_SIZE = "widget_font_size"
    private const val KEY_THEME_MODE = "widget_theme_mode"
    private const val KEY_CUTOFF_HOUR = "widget_cutoff_hour"
    private const val KEY_SHOW_STREAKS_AND_GOALS = "show_streaks_and_goals"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getShowStreaksAndGoals(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_STREAKS_AND_GOALS, false) // Default is FALSE as required
    }

    fun setShowStreaksAndGoals(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_STREAKS_AND_GOALS, enabled).apply()
    }

    fun resetAllPreferences(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    fun getOpacity(context: Context): Float {
        return getPrefs(context).getFloat(KEY_OPACITY, 0.85f)
    }

    fun setOpacity(context: Context, opacity: Float) {
        getPrefs(context).edit().putFloat(KEY_OPACITY, opacity.coerceIn(0.0f, 1.0f)).apply()
    }

    fun getFontSize(context: Context): WidgetFontSize {
        val name = getPrefs(context).getString(KEY_FONT_SIZE, WidgetFontSize.STANDARD.name)
        return try {
            WidgetFontSize.valueOf(name ?: WidgetFontSize.STANDARD.name)
        } catch (e: Exception) {
            WidgetFontSize.STANDARD
        }
    }

    fun setFontSize(context: Context, size: WidgetFontSize) {
        getPrefs(context).edit().putString(KEY_FONT_SIZE, size.name).apply()
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val name = getPrefs(context).getString(KEY_THEME_MODE, AppThemeMode.CLASSIC_WARM.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.CLASSIC_WARM.name)
        } catch (e: Exception) {
            AppThemeMode.CLASSIC_WARM
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun getCutoffHour(context: Context): Int {
        return getPrefs(context).getInt(KEY_CUTOFF_HOUR, 4)
    }

    fun setCutoffHour(context: Context, hour: Int) {
        getPrefs(context).edit().putInt(KEY_CUTOFF_HOUR, hour).apply()
    }

    fun getThemeColors(context: Context): LifeStreamColors {
        return when (getThemeMode(context)) {
            AppThemeMode.CLASSIC_WARM -> ClassicWarmColors
            AppThemeMode.FROSTED_GLASS -> FrostedGlassColors
            AppThemeMode.NORDIC_CLEAN -> NordicCleanColors
            AppThemeMode.TOKYO_MINIMAL -> TokyoMinimalColors
            AppThemeMode.SAGE_LINEN -> SageLinenColors
            AppThemeMode.DEEP_SLATE -> DeepSlateColors
            AppThemeMode.PURE_MINIMAL_OLED -> PureMinimalOledColors
        }
    }
}
