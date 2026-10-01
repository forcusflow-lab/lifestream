package com.forcusflow.lifestream.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

class TodayTimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTimelineGlanceWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SCHEDULED_WIDGET_REFRESH -> {
                updateAll(context)
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULED_WIDGET_REFRESH = "com.forcusflow.lifestream.ACTION_SCHEDULED_WIDGET_REFRESH"

        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(TodayTimelineGlanceWidget::class.java)
                    if (glanceIds.isNotEmpty()) {
                        val widget = TodayTimelineGlanceWidget()
                        glanceIds.forEach { glanceId ->
                            widget.update(context, glanceId)
                        }
                    }
                    scheduleNextRefresh(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun scheduleNextRefresh(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val now = LocalDateTime.now()
                val zone = ZoneId.systemDefault()
                val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
                val triggerMillis = nextHour.atZone(zone).toInstant().toEpochMilli()

                val intent = Intent(context, TodayTimelineWidgetReceiver::class.java).apply {
                    action = ACTION_SCHEDULED_WIDGET_REFRESH
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC, triggerMillis, pendingIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
