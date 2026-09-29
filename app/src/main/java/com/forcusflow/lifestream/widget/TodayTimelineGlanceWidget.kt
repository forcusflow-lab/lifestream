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
        val templates = try { db.templateDao().getAll() } catch (e: Exception) { emptyList() }

        // Due periodic habits today
        val duePeriodicHabits = templates.filter { it.type == "INTERVAL" }.filter { tmpl ->
            val lastDoneDate = tmpl.lastCompletedAt?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
            }
            val elapsed = if (lastDoneDate != null) java.time.temporal.ChronoUnit.DAYS.between(lastDoneDate, today) else 999L
            val interval = tmpl.intervalDays ?: 7
            elapsed >= interval
        }

        // 1. DONE items today (completed during today's window)
        val doneItems = allItems.filter { item ->
            item.isDone && (
                (item.completedAt != null && item.completedAt in todayStart..todayEnd) ||
                (item.completedAt == null && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd)
            )
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }

        // 2. Pending items today (timed tasks first, then anytime tasks created today)
        val scheduledToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt != null && item.scheduledAt in todayStart..todayEnd
        }.sortedBy { it.scheduledAt ?: 0L }

        val anytimeToday = allItems.filter { item ->
            !item.isDone && item.scheduledAt == null && (item.createdAt >= todayStart || item.createdAt == 0L)
        }.sortedBy { it.id }

        val pendingItems = (scheduledToday + anytimeToday).distinctBy { it.id }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_SHEET, true)
        }

        provideContent {
            val scale = fontSizePref.scale
            // Ambient subtle color shift matching morning, daytime, twilight, and night
            val ambientCardBg = if (colors.isDark) {
                when (now.hour) {
                    in 5..10 -> Color(0xFF13202E) // Morning blue-tint
                    in 11..16 -> Color(0xFF221F1B) // Daytime warm amber-tint
                    in 17..19 -> Color(0xFF261920) // Twilight apricot-tint
                    else -> Color(0xFF151826) // Night deep indigo-tint
                }.copy(alpha = opacity.coerceIn(0.1f, 1.0f))
            } else {
                when (now.hour) {
                    in 5..10 -> Color(0xFFF1F6FA) // Morning fresh mist
                    in 11..16 -> Color(0xFFFAF7F2) // Daytime warm sunlight
                    in 17..19 -> Color(0xFFFAF2EE) // Twilight peach glow
                    else -> Color(0xFFF2F4F9) // Night calm slate
                }.copy(alpha = opacity.coerceIn(0.1f, 1.0f))
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ambientCardBg)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .clickable(actionStartActivity(launchIntent))
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
                    // Header: Date & ＋ 追加 Button
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateFormatted,
                            style = TextStyle(
                                fontSize = (16 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(colors.textPrimary)
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )

                        // ＋ 追加 Button (ワンタップで直接アプリを開いてタスク追加シートを起動)
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(colors.primary.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .clickable(actionStartActivity(addIntent)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "＋ 追加",
                                style = TextStyle(
                                    fontSize = (11 * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(colors.primary)
                                )
                            )
                        }
                    }

                    // Focus section
                    if (!focusText.isNullOrBlank()) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Text(
                            text = "🎯 “$focusText”",
                            style = TextStyle(
                                fontSize = (11.5f * scale).sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic,
                                color = ColorProvider(colors.primary)
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Thin Divider with high contrast
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.border.copy(alpha = 0.45f))
                    ) {}

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    // Empty state when nothing today
                    if (doneItems.isEmpty() && pendingItems.isEmpty()) {
                        Spacer(modifier = GlanceModifier.height(10.dp))
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

                    // 1. DONE items (過去の実績ログ：NOWラインの上、最大3件)
                    val visibleDone = doneItems.takeLast(3)
                    for (item in visibleDone) {
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
                                .padding(vertical = 2.dp)
                                .clickable(actionStartActivity(launchIntent)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeStr,
                                style = TextStyle(
                                    fontSize = (10.5f * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.75f))
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
                                    fontSize = (12 * scale).sp,
                                    textDecoration = TextDecoration.LineThrough,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    // 2. NOW line (現在時刻)
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
                                    fontSize = (9.5f * scale).sp,
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
                                .background(colors.nowLine.copy(alpha = 0.6f))
                        ) {}
                    }
                    Spacer(modifier = GlanceModifier.height(4.dp))

                    // 3. Pending items (これからの歩み：ルーティン＋ToDo、最大4件)
                    // 習慣（周期タスク）が到来していれば表示
                    val visibleHabits = duePeriodicHabits.take(2)
                    for (habit in visibleHabits) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable(actionStartActivity(launchIntent)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(4.dp)
                                    .background(colors.primary.copy(alpha = 0.12f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "習慣",
                                    style = TextStyle(
                                        fontSize = (9.5f * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(colors.primary)
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Text(
                                text = "${habit.iconKey ?: "🔄"} ${habit.title}",
                                style = TextStyle(
                                    fontSize = (12 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(colors.textPrimary)
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    // 手元タスク（ToDo）
                    val maxPendingCount = (4 - visibleHabits.size).coerceAtLeast(1)
                    val visiblePending = pendingItems.take(maxPendingCount)
                    for ((index, item) in visiblePending.withIndex()) {
                        val isFirstTask = (index == 0 && visibleHabits.isEmpty())
                        val schedTimeStr = item.scheduledAt?.let { sched ->
                            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            if (t != "00:00") t else null
                        }

                        // 先頭タスクの「チラ見ハイライト（Glanceability）」
                        val rowModifier = if (isFirstTask) {
                            GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(8.dp)
                                .background(colors.primary.copy(alpha = 0.08f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .clickable(actionStartActivity(launchIntent))
                        } else {
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable(actionStartActivity(launchIntent))
                        }

                        Row(
                            modifier = rowModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 時刻指定があれば太字バッジ、なければ端正なドット
                            if (schedTimeStr != null) {
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(4.dp)
                                        .background(colors.primary.copy(alpha = 0.16f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = schedTimeStr,
                                        style = TextStyle(
                                            fontSize = (10 * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(colors.primary)
                                        )
                                    )
                                }
                                Spacer(modifier = GlanceModifier.width(6.dp))
                            } else {
                                Box(
                                    modifier = GlanceModifier
                                        .size(13.dp)
                                        .cornerRadius(6.5.dp)
                                        .background(if (isFirstTask) colors.primary.copy(alpha = 0.5f) else colors.border.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {}
                                Spacer(modifier = GlanceModifier.width(6.dp))
                            }

                            Text(
                                text = item.title,
                                style = TextStyle(
                                    fontSize = ((if (isFirstTask) 13f else 12.5f) * scale).sp,
                                    fontWeight = if (isFirstTask) FontWeight.Bold else FontWeight.Medium,
                                    color = ColorProvider(colors.textPrimary)
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    val totalRemaining = (duePeriodicHabits.size - visibleHabits.size) + (pendingItems.size - visiblePending.size)
                    if (totalRemaining > 0) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "... 他 ${totalRemaining} 件",
                            style = TextStyle(
                                fontSize = (10 * scale).sp,
                                color = ColorProvider(colors.textSecondary.copy(alpha = 0.65f))
                            )
                        )
                    } else if (pendingItems.isEmpty() && duePeriodicHabits.isEmpty() && doneItems.isNotEmpty()) {
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
