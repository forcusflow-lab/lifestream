package com.forcusflow.lifestream.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.domain.LifeDateProvider
import com.forcusflow.lifestream.domain.PeriodicTaskUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import android.os.Bundle

class TodayTimelineWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayTimelineRemoteViews.updateWidgets(context.applicationContext, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayTimelineRemoteViews.updateWidgets(context.applicationContext, intArrayOf(appWidgetId))
            } finally {
                pendingResult.finish()
            }
        }
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TodayTimelineRemoteViews.updateAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                TodayTimelineRemoteViews.updateTrayVisibility(context, id)
                return
            }
            ACTION_REFRESH,
            ACTION_COMPLETE_ITEM,
            ACTION_COMPLETE_TEMPLATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_SCHEDULED_WIDGET_REFRESH -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        handleAsyncAction(context.applicationContext, intent)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    private suspend fun handleAsyncAction(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE_ITEM -> {
                val itemId = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
                if (itemId != 0L) {
                    val dao = AppDatabase.getInstance(context).timelineItemDao()
                    dao.getById(itemId)?.let { item ->
                        dao.update(
                            item.copy(
                                isDone = !item.isDone,
                                completedAt = if (!item.isDone) System.currentTimeMillis() else null
                            )
                        )
                    }
                }
                TodayTimelineRemoteViews.updateAll(context)
            }
            ACTION_COMPLETE_TEMPLATE -> {
                val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, 0L)
                if (templateId != 0L) {
                    val db = AppDatabase.getInstance(context)
                    db.templateDao().getById(templateId)?.let { template ->
                        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)
                        val dateProvider = LifeDateProvider()
                        val logicalToday = dateProvider.getLogicalDate(cutoffHour = cutoffHour)
                        PeriodicTaskUseCase(db, dateProvider).recordCompletion(template, logicalToday, cutoffHour)
                    }
                }
                TodayTimelineRemoteViews.updateAll(context)
            }
            else -> TodayTimelineRemoteViews.updateAll(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.forcusflow.lifestream.ACTION_TOGGLE_WIDGET_TRAY"
        const val ACTION_REFRESH = "com.forcusflow.lifestream.ACTION_REFRESH_WIDGET"
        const val ACTION_COMPLETE_ITEM = "com.forcusflow.lifestream.ACTION_COMPLETE_WIDGET_ITEM"
        const val ACTION_COMPLETE_TEMPLATE = "com.forcusflow.lifestream.ACTION_COMPLETE_WIDGET_TEMPLATE"
        const val ACTION_SCHEDULED_WIDGET_REFRESH = "com.forcusflow.lifestream.ACTION_SCHEDULED_WIDGET_REFRESH"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TEMPLATE_ID = "template_id"

        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                TodayTimelineRemoteViews.updateAll(context.applicationContext)
            }
        }
    }
}
