package com.forcusflow.lifestream.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodayTimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTimelineGlanceWidget()

    companion object {
        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val glanceIds = manager.getGlanceIds(TodayTimelineGlanceWidget::class.java)
                    glanceIds.forEach { glanceId ->
                        TodayTimelineGlanceWidget().update(context, glanceId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
