package com.forcusflow.lifestream.widget

import android.content.Context
import android.content.SharedPreferences
import com.forcusflow.lifestream.ui.theme.*

typealias WidgetFontSize = AppFontSize

object WidgetSettingsManager {
    private const val PREFS_NAME = "lifestream_widget_prefs"
    private const val KEY_OPACITY = "widget_opacity"
    private const val KEY_FONT_SIZE = "widget_font_size"
    private const val KEY_FONT_FAMILY = "widget_font_family"
    private const val KEY_THEME_MODE = "widget_theme_mode"
    private const val KEY_CUTOFF_HOUR = "widget_cutoff_hour"
    private const val KEY_SHOW_STREAKS_AND_GOALS = "show_streaks_and_goals"
    private const val KEY_FUTURE_TRAY_EXPANDED = "future_tray_expanded"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFutureTrayExpanded(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FUTURE_TRAY_EXPANDED, true)
    }

    fun setFutureTrayExpanded(context: Context, expanded: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FUTURE_TRAY_EXPANDED, expanded).apply()
    }

    fun toggleFutureTrayExpanded(context: Context): Boolean {
        val next = !isFutureTrayExpanded(context)
        setFutureTrayExpanded(context, next)
        return next
    }

    // Backwards-compatible aliases
    fun isWidgetTrayExpanded(context: Context): Boolean = isFutureTrayExpanded(context)
    fun setWidgetTrayExpanded(context: Context, expanded: Boolean) = setFutureTrayExpanded(context, expanded)
    fun toggleWidgetTrayExpanded(context: Context): Boolean = toggleFutureTrayExpanded(context)

    fun getShowStreaksAndGoals(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_STREAKS_AND_GOALS, false)
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

    fun getFontSize(context: Context): AppFontSize {
        val name = getPrefs(context).getString(KEY_FONT_SIZE, AppFontSize.STANDARD.name)
        return try {
            AppFontSize.valueOf(name ?: AppFontSize.STANDARD.name)
        } catch (_: Exception) {
            AppFontSize.STANDARD
        }
    }

    fun setFontSize(context: Context, size: AppFontSize) {
        getPrefs(context).edit().putString(KEY_FONT_SIZE, size.name).apply()
    }

    fun getFontFamily(context: Context): AppFontFamily {
        val name = getPrefs(context).getString(KEY_FONT_FAMILY, AppFontFamily.SANS_SERIF.name)
        return try {
            AppFontFamily.valueOf(name ?: AppFontFamily.SANS_SERIF.name)
        } catch (_: Exception) {
            AppFontFamily.SANS_SERIF
        }
    }

    fun setFontFamily(context: Context, family: AppFontFamily) {
        getPrefs(context).edit().putString(KEY_FONT_FAMILY, family.name).apply()
    }

    fun getThemeMode(context: Context): AppThemeMode {
        val name = getPrefs(context).getString(KEY_THEME_MODE, AppThemeMode.CLASSIC_WARM.name)
        return try {
            AppThemeMode.valueOf(name ?: AppThemeMode.CLASSIC_WARM.name)
        } catch (_: Exception) {
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
