package com.forcusflow.lifestream.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.util.Log
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
import com.forcusflow.lifestream.ui.theme.AppFontFamily
import com.forcusflow.lifestream.ui.theme.LifeStreamColors
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
        updateWidgets(context, null)
    }

    suspend fun updateWidgets(context: Context, appWidgetIds: IntArray? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
            appWidgetIds
        } else {
            val component = ComponentName(context, TodayTimelineWidgetReceiver::class.java)
            manager.getAppWidgetIds(component)
        }
        if (ids.isEmpty()) return
        val views = try {
            build(context)
        } catch (error: Throwable) {
            Log.e("byLifeWidget", "RemoteViews build failed; showing fallback", error)
            val fallback = RemoteViews(context.packageName, R.layout.widget_fallback)
            val colors = WidgetSettingsManager.getThemeColors(context)
            val opacity = WidgetSettingsManager.getOpacity(context)
            val alphaInt = (opacity.coerceIn(0f, 1f) * 255).toInt()
            fallback.setInt(R.id.widget_bg, "setColorFilter", colors.background.toArgb())
            fallback.setInt(R.id.widget_bg, "setImageAlpha", alphaInt)
            fallback.setTextColor(R.id.fallback_title, colors.textPrimary.toArgb())
            fallback.setTextColor(R.id.fallback_text, colors.textSecondary.toArgb())

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            fallback.setOnClickPendingIntent(R.id.fallback_text, activityPending(context, openIntent, REQUEST_OPEN))
            fallback
        }
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
        val fontFamily = WidgetSettingsManager.getFontFamily(context)
        val scale = WidgetSettingsManager.getFontSize(context).scale
        val opacity = WidgetSettingsManager.getOpacity(context)
        val zone = dateProvider.zoneId

        val views = RemoteViews(context.packageName, R.layout.widget_root)

        // 1. Dynamic background tint & alpha opacity (preserves 18dp rounded corners)
        val alphaInt = (opacity.coerceIn(0f, 1f) * 255).toInt()
        views.setInt(R.id.widget_bg, "setColorFilter", colors.background.toArgb())
        views.setInt(R.id.widget_bg, "setImageAlpha", alphaInt)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_SHEET, true)
        }

        // Header
        views.setTextWithFont(R.id.widget_date, data.dateFormatted, fontFamily, 12f * scale)
        views.setTextColor(R.id.widget_date, colors.textPrimary.toArgb())
        views.setOnClickPendingIntent(R.id.widget_date, activityPending(context, openIntent, REQUEST_OPEN))

        views.setInt(R.id.widget_refresh, "setColorFilter", colors.textSecondary.toArgb())
        views.setOnClickPendingIntent(R.id.widget_refresh, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_REFRESH, REQUEST_REFRESH))

        views.setTextWithFont(R.id.widget_add, "＋ 追加", fontFamily, 11f * scale)
        views.setTextColor(R.id.widget_add, colors.primary.toArgb())
        views.setOnClickPendingIntent(R.id.widget_add, activityPending(context, addIntent, REQUEST_ADD))

        // Daily focus
        val focusText = data.focusText(context)
        if (focusText != null) {
            views.setViewVisibility(R.id.widget_focus, View.VISIBLE)
            views.setTextWithFont(R.id.widget_focus, "✦ $focusText", fontFamily, 11f * scale)
            views.setTextColor(R.id.widget_focus, colors.primary.toArgb())
            views.setOnClickPendingIntent(R.id.widget_focus, activityPending(context, openIntent, REQUEST_OPEN + 1))
        } else {
            views.setViewVisibility(R.id.widget_focus, View.GONE)
        }

        listOf(R.id.widget_done_area, R.id.widget_now_area, R.id.widget_surfaced_area, R.id.future_tray)
            .forEach { views.removeAllViews(it) }
        views.setViewVisibility(R.id.widget_empty, View.GONE)
        views.setViewVisibility(R.id.widget_all_done, View.GONE)
        views.setViewVisibility(R.id.widget_future_header, View.GONE)

        val hasPending = data.activePeriodicTemplates.any { it.isDueToday } ||
            data.timelinePendingItems.isNotEmpty() ||
            data.trayPeriodicTemplates.any { it.isDueToday } ||
            data.trayPendingItems.isNotEmpty()
        val hasDone = data.doneItems.isNotEmpty()

        val doneItems = data.doneItems.takeLast(4)
        if (data.doneItems.size > doneItems.size) {
            val summary = RemoteViews(context.packageName, R.layout.widget_summary_row)
            summary.setTextWithFont(
                R.id.summary_text,
                "▲ 以前の実績 ${data.doneItems.size - doneItems.size}件（タップしてアプリで確認）",
                fontFamily,
                10f * scale
            )
            summary.setTextColor(R.id.summary_text, colors.textSecondary.toArgb())
            summary.setOnClickPendingIntent(R.id.summary_text, activityPending(context, openIntent, REQUEST_OPEN + 2))
            views.addView(R.id.widget_done_area, summary)
        }
        doneItems.forEach { item ->
            views.addView(R.id.widget_done_area, itemRow(context, item, zone, colors, fontFamily, scale, openIntent, done = true))
        }

        if (!hasDone && !hasPending) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextWithFont(R.id.widget_empty, "🌿 清々しい1日のはじまり", fontFamily, 12f * scale)
            views.setTextColor(R.id.widget_empty, colors.textSecondary.toArgb())
        }

        val nowRow = RemoteViews(context.packageName, R.layout.widget_now_row)
        val nowText = dateProvider.formatTime(dateProvider.nowLocalDateTime().toLocalTime())
        nowRow.setTextWithFont(R.id.now_label, "現在 $nowText", fontFamily, 9.5f * scale)
        nowRow.setTextColor(R.id.now_label, colors.nowLine.toArgb())
        nowRow.setInt(R.id.now_stem, "setColorFilter", colors.nowLine.toArgb())
        nowRow.setInt(R.id.now_line, "setBackgroundColor", colors.nowLine.toArgb())
        views.addView(R.id.widget_now_area, nowRow)

        if (hasDone && !hasPending) {
            views.setViewVisibility(R.id.widget_all_done, View.VISIBLE)
            views.setTextWithFont(R.id.widget_all_done, "✨ 今日のタスクはすべて完了しました", fontFamily, 11f * scale)
            views.setTextColor(R.id.widget_all_done, colors.textSecondary.toArgb())
            views.setOnClickPendingIntent(R.id.widget_all_done, activityPending(context, openIntent, REQUEST_OPEN + 4))
        }

        data.activePeriodicTemplates.filter { it.isDueToday }.forEach { status ->
            views.addView(
                R.id.widget_surfaced_area,
                templateRow(context, status.template, colors, fontFamily, scale, openIntent, isTray = false)
            )
        }
        data.timelinePendingItems.forEach { item ->
            val badge = item.scheduledAt?.let { formatTime(it, zone) } ?: if (item.showOnTimeline) "📌 今日" else ""
            views.addView(
                R.id.widget_surfaced_area,
                itemRow(context, item, zone, colors, fontFamily, scale, openIntent, done = false, badgeOverride = badge, isTray = false)
            )
        }

        val trayHabits = data.trayPeriodicTemplates.filter { it.isDueToday }.map { it.template }
        val trayCount = trayHabits.size + data.trayPendingItems.size
        if (trayCount > 0) {
            views.setViewVisibility(R.id.widget_future_header, View.VISIBLE)
            views.setTextWithFont(R.id.future_title, "これからの歩み (${trayCount}件)", fontFamily, 11.5f * scale)
            views.setTextColor(R.id.future_title, colors.textSecondary.toArgb())
            views.setOnClickPendingIntent(R.id.widget_future_header, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_TOGGLE, REQUEST_TOGGLE))
            views.setOnClickPendingIntent(R.id.future_toggle, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_TOGGLE, REQUEST_TOGGLE + 1))
            val expanded = WidgetSettingsManager.isFutureTrayExpanded(context)
            views.setTextViewText(R.id.future_toggle, if (expanded) "▲" else "▼")
            views.setTextColor(R.id.future_toggle, colors.primary.toArgb())
            views.setTextViewTextSize(R.id.future_toggle, TypedValue.COMPLEX_UNIT_SP, 9f * scale)
            views.setViewVisibility(R.id.future_tray, if (expanded) View.VISIBLE else View.GONE)

            val trayRows = buildList {
                trayHabits.forEach { add(TrayRow.Habit(it)) }
                data.trayPendingItems.forEach { add(TrayRow.Item(it)) }
            }
            trayRows.take(MAX_TRAY_ROWS).forEach { row ->
                when (row) {
                    is TrayRow.Habit -> views.addView(
                        R.id.future_tray,
                        templateRow(context, row.template, colors, fontFamily, scale, openIntent, isTray = true)
                    )
                    is TrayRow.Item -> views.addView(
                        R.id.future_tray,
                        itemRow(context, row.item, zone, colors, fontFamily, scale, openIntent, done = false, isTray = true)
                    )
                }
            }
            if (trayRows.size > MAX_TRAY_ROWS) {
                val summary = RemoteViews(context.packageName, R.layout.widget_summary_row)
                summary.setTextWithFont(
                    R.id.summary_text,
                    "＋ ${trayRows.size - MAX_TRAY_ROWS}件はアプリで確認",
                    fontFamily,
                    10f * scale
                )
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
        colors: LifeStreamColors,
        fontFamily: AppFontFamily,
        scale: Float,
        openIntent: Intent,
        done: Boolean,
        badgeOverride: String? = null,
        isTray: Boolean = false
    ): RemoteViews {
        val row = RemoteViews(
            context.packageName,
            if (isTray) R.layout.widget_item_tray_row else R.layout.widget_item_row
        )

        row.setImageViewResource(
            R.id.row_node,
            if (done) R.drawable.widget_done_node else R.drawable.ic_widget_circle_dark
        )
        val timestamp = if (done) item.completedAt ?: item.scheduledAt else item.scheduledAt
        val timeStr = timestamp?.let { formatTime(it, zone) } ?: ""
        if (timeStr.isNotBlank()) {
            row.setViewVisibility(R.id.row_time, View.VISIBLE)
            row.setTextWithFont(R.id.row_time, timeStr, fontFamily, 9f * scale)
        } else {
            row.setViewVisibility(R.id.row_time, View.GONE)
        }
        val titleTextSize = if (isTray) 11f else 12.5f
        row.setTextWithFont(R.id.row_title, cleanTitle(item.title), fontFamily, titleTextSize * scale)

        val badge = badgeOverride ?: if (item.durationSeconds != null) "⏱ ${(item.durationSeconds + 30) / 60}分" else if (item.countValue != null) "${item.countValue}回" else ""
        if (badge.isBlank()) {
            row.setViewVisibility(R.id.row_badge, View.GONE)
        } else {
            row.setViewVisibility(R.id.row_badge, View.VISIBLE)
            row.setTextWithFont(R.id.row_badge, badge, fontFamily, 8f * scale)
        }
        if (timeStr.isBlank() && badge.isBlank()) {
            row.setViewVisibility(R.id.row_sub_container, View.GONE)
        } else {
            row.setViewVisibility(R.id.row_sub_container, View.VISIBLE)
        }
        row.setViewVisibility(R.id.row_repeat, View.GONE)
        row.setTextColor(R.id.row_time, colors.textSecondary.toArgb())
        val titleColor = if (done || isTray) colors.textSecondary.toArgb() else colors.textPrimary.toArgb()
        row.setTextColor(R.id.row_title, titleColor)
        row.setTextColor(R.id.row_badge, colors.primary.toArgb())
        row.setOnClickPendingIntent(R.id.row_card, activityPending(context, openIntent, REQUEST_OPEN + item.id.toInt().coerceAtLeast(1)))
        row.setOnClickPendingIntent(R.id.row_node, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_COMPLETE_ITEM, REQUEST_ITEM + item.id.toInt().coerceAtLeast(1), "item_id", item.id))
        return row
    }

    private fun templateRow(
        context: Context,
        template: TemplateEntity,
        colors: LifeStreamColors,
        fontFamily: AppFontFamily,
        scale: Float,
        openIntent: Intent,
        isTray: Boolean = false
    ): RemoteViews {
        val row = RemoteViews(
            context.packageName,
            if (isTray) R.layout.widget_item_tray_row else R.layout.widget_item_row
        )

        row.setImageViewResource(R.id.row_node, R.drawable.ic_widget_circle_dark)
        row.setViewVisibility(R.id.row_time, View.GONE)
        row.setViewVisibility(R.id.row_badge, View.GONE)
        row.setViewVisibility(R.id.row_sub_container, View.GONE)

        val titleTextSize = if (isTray) 11f else 12.5f
        row.setTextWithFont(R.id.row_title, template.title, fontFamily, titleTextSize * scale)

        row.setViewVisibility(R.id.row_repeat, View.VISIBLE)
        row.setTextWithFont(R.id.row_repeat, "↻", fontFamily, 12f * scale)
        row.setTextColor(R.id.row_repeat, colors.textSecondary.toArgb())

        val titleColor = if (isTray) colors.textSecondary.toArgb() else colors.textPrimary.toArgb()
        row.setTextColor(R.id.row_title, titleColor)
        row.setOnClickPendingIntent(R.id.row_card, activityPending(context, openIntent, REQUEST_OPEN + template.id.toInt().coerceAtLeast(1)))
        row.setOnClickPendingIntent(R.id.row_node, broadcastPending(context, TodayTimelineWidgetReceiver.ACTION_COMPLETE_TEMPLATE, REQUEST_TEMPLATE + template.id.toInt().coerceAtLeast(1), "template_id", template.id))
        return row
    }

    private fun CharSequence.withFont(fontFamily: AppFontFamily): CharSequence {
        val span = SpannableString(this)
        span.setSpan(TypefaceSpan(fontFamily.androidTypefaceFamily), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return span
    }

    private fun RemoteViews.setTextWithFont(
        viewId: Int,
        text: CharSequence,
        fontFamily: AppFontFamily,
        sizeSp: Float? = null
    ) {
        setTextViewText(viewId, text.withFont(fontFamily))
        if (sizeSp != null) {
            setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, sizeSp)
        }
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
