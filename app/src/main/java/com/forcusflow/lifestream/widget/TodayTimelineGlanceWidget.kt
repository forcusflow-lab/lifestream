package com.forcusflow.lifestream.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.forcusflow.lifestream.MainActivity
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.TimelineItemEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayTimelineGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val opacity = WidgetSettingsManager.getOpacity(context)
        val fontSizePref = WidgetSettingsManager.getFontSize(context)
        val colors = WidgetSettingsManager.getThemeColors(context)
        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)

        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val today = if (now.hour < cutoffHour) now.toLocalDate().minusDays(1) else now.toLocalDate()

        val startDateTime = LocalDateTime.of(today, LocalTime.of(cutoffHour, 0))
        val endDateTime = startDateTime.plusDays(1).minusNanos(1)
        val todayStart = startDateTime.atZone(zone).toInstant().toEpochMilli()
        val todayEnd = endDateTime.atZone(zone).toInstant().toEpochMilli()

        val dateFormatted = today.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
        val nowTimeFormatted = now.format(DateTimeFormatter.ofPattern("HH:mm"))

        val todayDateKey = today.toString()
        val dailyFocusEntity = try { db.dailyFocusDao().getByDate(todayDateKey) } catch (e: Exception) { null }
        val focusText = dailyFocusEntity?.content

        val allItems = try { db.timelineItemDao().getAll() } catch (e: Exception) { emptyList() }

        // 1. DONE items today (completed during today's window)
        val doneItems = allItems.filter { item ->
            item.isDone && (
                (item.completedAt != null && item.completedAt in todayStart..todayEnd) ||
                (item.completedAt == null && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd)
            )
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }

        // 2. Pending items today (scheduled today or anytime)
        val scheduledToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd
        }
        val anytimePending = allItems.filter { item ->
            !item.isDone && item.scheduledAt == null
        }
        val pendingItems = (scheduledToday + anytimePending).distinctBy { it.id }.sortedWith(
            compareBy<TimelineItemEntity> { it.scheduledAt == null }
                .thenBy { it.scheduledAt ?: 0L }
                .thenBy { it.id }
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            val scale = fontSizePref.scale
            val cardBg = if (colors.isDark) {
                Color(0xFF1E293B).copy(alpha = opacity)
            } else {
                Color(0xFFFFFFFF).copy(alpha = opacity)
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(cardBg)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(launchIntent))
            ) {
                LazyColumn(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Header: Date & App launch hint
                    item {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateFormatted,
                                style = TextStyle(
                                    fontSize = (18 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(colors.textPrimary)
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "FocusFlow ↗",
                                style = TextStyle(
                                    fontSize = (10 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                )
                            )
                        }
                    }

                    // Focus section
                    item {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!focusText.isNullOrBlank()) {
                                Text(
                                    text = "🎯 “$focusText”",
                                    style = TextStyle(
                                        fontSize = (12 * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                        fontStyle = FontStyle.Italic,
                                        color = ColorProvider(colors.primary)
                                    ),
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    text = "🎯 今日のフォーカスを設定...",
                                    style = TextStyle(
                                        fontSize = (11.5f * scale).sp,
                                        color = ColorProvider(colors.textSecondary.copy(alpha = 0.6f))
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = GlanceModifier.height(6.dp))
                        // Divider
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.border.copy(alpha = 0.4f))
                        ) {}
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }

                    // Empty state when nothing today
                    if (doneItems.isEmpty() && pendingItems.isEmpty()) {
                        item {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Column(
                                modifier = GlanceModifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🌿 清々しい1日のはじまり",
                                    style = TextStyle(
                                        fontSize = (13 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(colors.textPrimary)
                                    )
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "タップして最初の行動を記録しましょう",
                                    style = TextStyle(
                                        fontSize = (11 * scale).sp,
                                        color = ColorProvider(colors.textSecondary)
                                    )
                                )
                            }
                        }
                    }

                    // 1. DONE items (過去の実績ログ：NOWラインの上)
                    if (doneItems.isNotEmpty()) {
                        items(doneItems) { item ->
                            val timeStr = (item.completedAt ?: item.scheduledAt)?.let { ts ->
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            } ?: "--:--"

                            val cleanTitle = item.title
                                .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                .trim()

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timeStr,
                                    style = TextStyle(
                                        fontSize = (10.5f * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorProvider(colors.textSecondary)
                                    ),
                                    modifier = GlanceModifier.width(36.dp)
                                )
                                Text(
                                    text = "✓",
                                    style = TextStyle(
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(colors.statusDone)
                                    ),
                                    modifier = GlanceModifier.padding(horizontal = 4.dp)
                                )
                                Text(
                                    text = cleanTitle,
                                    style = TextStyle(
                                        fontSize = (12.5f * scale).sp,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = ColorProvider(colors.textSecondary)
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight()
                                )
                            }
                        }
                    }

                    // 2. NOW line (現在時刻)
                    item {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(6.dp)
                                    .background(colors.nowLine)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "現在 $nowTimeFormatted",
                                    style = TextStyle(
                                        fontSize = (10 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(Color.White)
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(1.dp)
                                    .background(colors.nowLine.copy(alpha = 0.7f))
                            ) {}
                        }
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }

                    // 3. Pending items (これからの歩み)
                    if (pendingItems.isNotEmpty()) {
                        items(pendingItems) { item ->
                            val schedTimeStr = item.scheduledAt?.let { sched ->
                                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                if (t != "00:00") t else null
                            }

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.5f.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1-tap completion circle (○)
                                Box(
                                    modifier = GlanceModifier
                                        .size(24.dp)
                                        .cornerRadius(12.dp)
                                        .background(colors.card.copy(alpha = 0.3f))
                                        .clickable(
                                            actionRunCallback<ToggleItemDoneActionCallback>(
                                                actionParametersOf(ToggleItemDoneActionCallback.itemIdKey to item.id)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "○",
                                        style = TextStyle(
                                            fontSize = (15 * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(colors.textSecondary.copy(alpha = 0.8f))
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(6.dp))

                                Text(
                                    text = item.title,
                                    style = TextStyle(
                                        fontSize = (13 * scale).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorProvider(colors.textPrimary)
                                    ),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight()
                                )

                                if (schedTimeStr != null) {
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                    Text(
                                        text = schedTimeStr,
                                        style = TextStyle(
                                            fontSize = (10 * scale).sp,
                                            color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                        )
                                    )
                                }
                            }
                        }
                    } else if (doneItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "手元のタスクはありません 🌿",
                                style = TextStyle(
                                    fontSize = (11 * scale).sp,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class ToggleItemDoneActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val itemId = parameters[itemIdKey] ?: return
        val db = AppDatabase.getInstance(context)
        val item = db.timelineItemDao().getById(itemId) ?: return
        val updated = item.copy(
            isDone = !item.isDone,
            completedAt = if (!item.isDone) System.currentTimeMillis() else null
        )
        db.timelineItemDao().update(updated)
        TodayTimelineWidgetReceiver.updateAll(context)
    }

    companion object {
        val itemIdKey = ActionParameters.Key<Long>("itemId")
    }
}
