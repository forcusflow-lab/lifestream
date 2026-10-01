package com.forcusflow.lifestream.data

import android.content.Context
import android.content.SharedPreferences

data class PersistedTimerState(
    val templateId: Long? = null,
    val itemId: Long? = null,
    val startTimeMillis: Long = 0L,
    val isRunning: Boolean = false
)

/**
 * Manages persisting active timer state across process death, orientation changes,
 * and app reboots.
 */
object TimerStateManager {
    private const val PREFS_NAME = "lifestream_active_timer"
    private const val KEY_TEMPLATE_ID = "timer_template_id"
    private const val KEY_ITEM_ID = "timer_item_id"
    private const val KEY_START_TIME = "timer_start_time"
    private const val KEY_IS_RUNNING = "timer_is_running"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTimer(context: Context, templateId: Long?, itemId: Long?, startTimeMillis: Long) {
        getPrefs(context).edit()
            .putLong(KEY_TEMPLATE_ID, templateId ?: -1L)
            .putLong(KEY_ITEM_ID, itemId ?: -1L)
            .putLong(KEY_START_TIME, startTimeMillis)
            .putBoolean(KEY_IS_RUNNING, true)
            .apply()
    }

    fun getTimer(context: Context): PersistedTimerState? {
        val prefs = getPrefs(context)
        val isRunning = prefs.getBoolean(KEY_IS_RUNNING, false)
        if (!isRunning) return null

        val templateId = prefs.getLong(KEY_TEMPLATE_ID, -1L).takeIf { it != -1L }
        val itemId = prefs.getLong(KEY_ITEM_ID, -1L).takeIf { it != -1L }
        val startTime = prefs.getLong(KEY_START_TIME, 0L)

        return if (startTime > 0L) {
            PersistedTimerState(
                templateId = templateId,
                itemId = itemId,
                startTimeMillis = startTime,
                isRunning = true
            )
        } else null
    }

    fun clearTimer(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
