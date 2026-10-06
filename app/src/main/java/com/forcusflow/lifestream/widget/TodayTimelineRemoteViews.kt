package com.forcusflow.lifestream.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.forcusflow.lifestream.MainActivity
import com.forcusflow.lifestream.R
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.domain.LifeDateProvider
import com.forcusflow.lifestream.domain.TodayTimelineCalculator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RemoteViews renderer for the home-screen widget.
 *
 * Full updates build the data once. Tray toggles never touch Room or the domain
 * calculator; they only change visibility and the arrow using a partial update.
 */
object TodayTimelineRemoteViews {
    private const val MAX_TRAY_ROWS = 8
    private const val REQUEST_REFRESH = 100
    private const val REQUEST_TOGGLE = 101
    private const val REQUEST_ADD = 102
    private const val REQUEST_OPEN = 103
    private const val REQUEST_ITEM = 10_000
    private const val REQUEST_TEMPLATE = 20_000

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, TodayTimelineWidgetReceiver::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val views = build(context)
        manager.updateAppWidget(ids, views)
    }

    fun updateTrayVisibility(context: Context, appWidgetId: Int) {
        val expanded = !WidgetSettingsManager.isFutureTrayExpanded(context)
        WidgetSettingsManager.setFutureTrayExpanded(context, expanded)

        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setViewVisibility(
            R.id.future_tray,
            if (expanded) View.VISIBLE else View.GONE
        )
        views.setTextViewText(R.id.future_toggle, if (expanded) "▲" else "▼")
        val manager = AppWidgetManager.getInstance(context)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            manager.partiallyUpdateAppWidget(appWidgetId, views)
        } else {
            val component = ComponentName(context, TodayTimelineWidgetReceiver::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
    }

    private suspend fun build(context: Context): RemoteViews {
        val database = AppDatabase.getInstance(context)
        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)
        val dateProvider = LifeDateProvider()
        val allItems = try {
            database.timelineItemDao().getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val templates = try {
            database.templateDao().getAll()
        } catch (_: Exception) {
            emptyList()
        }
        val data = TodayTimelineCalculator.calculate(allItems, templates, dateProvider, cutoffHour)
        val colors = WidgetSettingsManager.getThemeColors(context)
        val scale = WidgetSettingsManager.getFontSize(context).scale
        val zone = dateProvider.zoneId
        val views = RemoteViews(context.packageName, R.layout.widget_root)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_SHEET, true)
        }
        views.setTextViewText(R.id.widget_date, data.dateFormatted)
        views.setOnClickPendingIntent(R.id.widget_date, activityPending(context, openIntent, REQUEST_OPEN))
        views.setOnClickPendingIntent(R.id.widget_refresh, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_REFRESH, REQUEST_REFRESH))
        views.setOnClickPendingIntent(R.id.widget_add, activityPending(context, addIntent, REQUEST_ADD))
        views.setTextColor(R.id.widget_date, colors.textPrimary.toArgb())
        views.setTextColor(R.id.widget_refresh, colors.textSecondary.toArgb())
        views.setTextColor(R.id.widget_add, colors.primary.toArgb())

        val focusText = data.focusText(context)
        if (focusText != null) {
            views.setViewVisibility(R.id.widget_focus, View.VISIBLE)
            views.setTextViewText(R.id.widget_focus, "🎯 “$focusText”")
            views.setTextColor(R.id.widget_focus, colors.primary.toArgb())
            views.setOnClickPendingIntent(R.id.widget_focus, activityPending(context, openIntent, REQUEST_OPEN + 1))
        } else {
            views.setViewVisibility(R.id.widget_focus, View.GONE)
        }

        listOf(R.id.widget_done_area, R.id.widget_now_area, R.id.widget_surfaced_area, R.id.future_tray)
            .forEach { views.removeAllViews(it) }
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_future_header, View.GONE)

        val hasTasks = data.doneItems.isNotEmpty() ||
            data.activePeriodicTemplates.any { it.isDueToday } ||
            data.timelinePendingItems.isNotEmpty() ||
            data.trayPeriodicTemplates.any { it.isDueToday } ||
            data.trayPendingItems.isNotEmpty()

        val doneItems = data.doneItems.takeLast(3)
        if (data.doneItems.size > doneItems.size) {
            val summary = RemoteViews(context.packageName, R.layout.widget_summary_row)
            summary.setTextViewText(R.id.summary_text, "▲ 以前の実績 ${data.doneItems.size - doneItems.size}件（タップしてアプリで確認）")
            summary.setTextColor(R.id.summary_text, colors.textSecondary.toArgb())
            summary.setOnClickPendingIntent(R.id.summary_text, activityPending(context, openIntent, REQUEST_OPEN + 2))
            views.addView(R.id.widget_done_area, summary)
        }
        doneItems.forEach { item ->
            views.addView(R.id.widget_done_area, itemRow(context, item, zone, colors, scale, openIntent, done = true))
        }

        if (!hasTasks) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextColor(R.id.widget_empty, colors.textSecondary.toArgb())
        }

        val nowRow = RemoteViews(context.packageName, R.layout.widget_now_row)
        val nowText = dateProvider.formatTime(dateProvider.nowLocalDateTime().toLocalTime())
        nowRow.setTextViewText(R.id.now_label, "現在 $nowText")
        views.addView(R.id.widget_now_area, nowRow)

        data.activePeriodicTemplates.filter { it.isDueToday }.forEach { status ->
            views.addView(
                R.id.widget_surfaced_area,
                templateRow(context, status.template, colors, scale, openIntent, "習慣")
            )
        }
        data.timelinePendingItems.forEach { item ->
            val badge = item.scheduledAt?.let { formatTime(it, zone) } ?: if (item.showOnTimeline) "📌 今日" else ""
            views.addView(
                R.id.widget_surfaced_area,
                itemRow(context, item, zone, colors, scale, openIntent, done = false, badgeOverride = badge)
            )
        }

        val trayHabits = data.trayPeriodicTemplates.filter { it.isDueToday }.map { it.template }
        val trayCount = trayHabits.size + data.trayPendingItems.size
        if (trayCount > 0) {
            views.setViewVisibility(R.id.widget_future_header, View.VISIBLE)
            views.setTextViewText(R.id.future_title, "これからの歩み (${trayCount}件)")
            views.setTextColor(R.id.future_title, colors.textPrimary.toArgb())
            views.setOnClickPendingIntent(R.id.widget_future_header, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_TOGGLE, REQUEST_TOGGLE))
            views.setOnClickPendingIntent(R.id.future_toggle, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_TOGGLE, REQUEST_TOGGLE + 1))
            val expanded = WidgetSettingsManager.isFutureTrayExpanded(context)
            views.setTextViewText(R.id.future_toggle, if (expanded) "▲" else "▼")
            views.setViewVisibility(R.id.future_tray, if (expanded) View.VISIBLE else View.GONE)

            val trayRows = buildList {
                trayHabits.forEach { add(TrayRow.Habit(it)) }
                data.trayPendingItems.forEach { add(TrayRow.Item(it)) }
            }
            trayRows.take(MAX_TRAY_ROWS).forEach { row ->
                when (row) {
                    is TrayRow.Habit -> views.addView(
                        R.id.future_tray,
                        templateRow(context, row.template, colors, scale, openIntent, "習慣")
                    )
                    is TrayRow.Item -> views.addView(
                        R.id.future_tray,
                        itemRow(context, row.item, zone, colors, scale, openIntent, done = false)
                    )
                }
            }
            if (trayRows.size > MAX_TRAY_ROWS) {
                val summary = RemoteViews(context.packageName, R.layout.widget_summary_row)
                summary.setTextViewText(R.id.summary_text, "＋ ${trayRows.size - MAX_TRAY_ROWS}件はアプリで確認")
                summary.setTextColor(R.id.summary_text, colors.textSecondary.toArgb())
                summary.setOnClickPendingIntent(R.id.summary_text, activityPending(context, openIntent, REQUEST_OPEN + 3))
                views.addView(R.id.future_tray, summary)
            }
        }
        return views
    }

    private fun itemRow(
        context: Context,
        item: TimelineItemEntity,
        zone: ZoneId,
        colors: com.forcusflow.lifestream.ui.theme.LifeStreamColors,
        scale: Float,
        openIntent: Intent,
        done: Boolean,
        badgeOverride: String? = null
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_item_row)
        row.setImageViewResource(R.id.row_node, if (done) R.drawable.ic_widget_check else R.drawable.ic_widget_circle)
        if (done) {
            row.setInt(R.id.row_node, "setBackgroundResource", R.drawable.widget_done_node)
        } else {
            row.setInt(R.id.row_node, "setColorFilter", colors.textSecondary.toArgb())
        }
        val timestamp = if (done) item.completedAt ?: item.scheduledAt else item.scheduledAt
        row.setTextViewText(R.id.row_time, timestamp?.let { formatTime(it, zone) } ?: "")
        row.setTextViewText(R.id.row_title, cleanTitle(item.title))
        val badge = badgeOverride ?: if (item.durationSeconds != null) "⏱ ${(item.durationSeconds + 30) / 60}分" else if (item.countValue != null) "${item.countValue}回" else ""
        if (badge.isBlank()) row.setViewVisibility(R.id.row_badge, View.GONE) else {
            row.setViewVisibility(R.id.row_badge, View.VISIBLE)
            row.setTextViewText(R.id.row_badge, badge)
        }
        row.setTextColor(R.id.row_time, colors.textSecondary.toArgb())
        row.setTextColor(R.id.row_title, colors.textPrimary.toArgb())
        row.setTextColor(R.id.row_badge, colors.primary.toArgb())
        applyScale(row, scale)
        row.setOnClickPendingIntent(R.id.row_card, activityPending(context, openIntent, REQUEST_OPEN + item.id.toInt().coerceAtLeast(1)))
        row.setOnClickPendingIntent(R.id.row_node, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_COMPLETE_ITEM, REQUEST_ITEM + item.id.toInt().coerceAtLeast(1), "item_id", item.id))
        return row
    }

    private fun templateRow(
        context: Context,
        template: TemplateEntity,
        colors: com.forcusflow.lifestream.ui.theme.LifeStreamColors,
        scale: Float,
        openIntent: Intent,
        badge: String
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_item_row)
        row.setImageViewResource(R.id.row_node, R.drawable.ic_widget_circle)
        row.setTextViewText(R.id.row_time, "")
        row.setTextViewText(R.id.row_title, "${template.iconKey ?: "🌿"} ${template.title}")
        row.setTextViewText(R.id.row_badge, badge)
        row.setViewVisibility(R.id.row_badge, View.VISIBLE)
        row.setTextColor(R.id.row_title, colors.textPrimary.toArgb())
        row.setTextColor(R.id.row_badge, colors.primary.toArgb())
        applyScale(row, scale)
        row.setOnClickPendingIntent(R.id.row_card, activityPending(context, openIntent, REQUEST_OPEN + template.id.toInt().coerceAtLeast(1)))
        row.setOnClickPendingIntent(R.id.row_node, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_COMPLETE_TEMPLATE, REQUEST_TEMPLATE + template.id.toInt().coerceAtLeast(1), "template_id", template.id))
        return row
    }

    private fun applyScale(views: RemoteViews, scale: Float) {
        views.setTextViewTextSize(R.id.row_time, TypedValue.COMPLEX_UNIT_SP, 9f * scale)
        views.setTextViewTextSize(R.id.row_title, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
        views.setTextViewTextSize(R.id.row_badge, TypedValue.COMPLEX_UNIT_SP, 8f * scale)
    }

    private fun formatTime(timestamp: Long, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone).format(timeFormatter)

    private fun cleanTitle(title: String): String = title
        .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
        .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
        .trim()

    private fun activityPending(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun broadcastPending(
        context: Context,
        action: String,
        requestCode: Int,
        extraKey: String? = null,
        extraValue: Long? = null
    ): PendingIntent {
        val intent = Intent(context, TodayTimelineWidgetReceiver::class.java).setAction(action)
        if (extraKey != null && extraValue != null) intent.putExtra(extraKey, extraValue)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private sealed interface TrayRow {
        data class Habit(val template: TemplateEntity) : TrayRow
        data class Item(val item: TimelineItemEntity) : TrayRow
    }

    private suspend fun com.forcusflow.lifestream.domain.TodayTimelineData.focusText(context: Context): String? = try {
        AppDatabase.getInstance(context).dailyFocusDao().getByDate(logicalDate.toString())?.content
    } catch (_: Exception) {
        null
    }
}
