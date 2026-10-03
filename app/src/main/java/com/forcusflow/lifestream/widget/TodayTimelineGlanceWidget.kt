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
import com.forcusflow.lifestream.domain.LifeDateProvider
import com.forcusflow.lifestream.domain.PeriodicTaskUseCase
import com.forcusflow.lifestream.domain.TodayTimelineCalculator
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
        val isTrayExpanded = WidgetSettingsManager.isWidgetTrayExpanded(context)

        val dateProvider = LifeDateProvider()
        val allItems = try { db.timelineItemDao().getAll() } catch (e: Exception) { emptyList() }
        val templates = try { db.templateDao().getAll() } catch (e: Exception) { emptyList() }

        val timelineData = TodayTimelineCalculator.calculate(
            allItems = allItems,
            templates = templates,
            dateProvider = dateProvider,
            cutoffHour = cutoffHour
        )

        val today = timelineData.logicalDate
        val dateFormatted = timelineData.dateFormatted
        val now = dateProvider.nowLocalDateTime()
        val zone = dateProvider.zoneId
        val nowTimeFormatted = dateProvider.formatTime(now.toLocalTime())

        val todayDateKey = today.toString()
        val dailyFocusEntity = try { db.dailyFocusDao().getByDate(todayDateKey) } catch (e: Exception) { null }
        val focusText = dailyFocusEntity?.content

        // 1. DONE items today (実績ログ：NOWラインの上)
        val doneItems = timelineData.doneItems

        // 2. Surfaced items today (NOWライン直下に浮上：現在の時間帯に合致した周期習慣 ＋ 2時間以内のToDo/固定ToDo)
        val surfacedHabits = timelineData.activePeriodicTemplates.filter { it.isDueToday }.map { it.template }
        val surfacedPendingItems = timelineData.timelinePendingItems

        // 3. Tray items (「これからの歩み」トレイ：時間帯が過ぎた習慣 ＋ 2時間以上先のToDo/未固定ToDo)
        val trayHabits = timelineData.trayPeriodicTemplates.filter { it.isDueToday }.map { it.template }
        val trayPendingItems = timelineData.trayPendingItems
        val totalTrayCount = trayHabits.size + trayPendingItems.size

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val addIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_ADD_SHEET, true)
        }

        provideContent {
            val scale = fontSizePref.scale
            // Ambient subtle color shift matching morning, daytime, twilight, and night based on theme background
            val baseColor = colors.background
            val lumFactor = when (now.hour) {
                in 5..10 -> if (colors.isDark) 1.04f else 1.02f
                in 11..16 -> if (colors.isDark) 1.02f else 1.00f
                in 17..19 -> if (colors.isDark) 0.98f else 0.99f
                else -> if (colors.isDark) 0.94f else 0.97f
            }
            val ambientCardBg = Color(
                red = (baseColor.red * lumFactor).coerceIn(0f, 1f),
                green = (baseColor.green * lumFactor).coerceIn(0f, 1f),
                blue = (baseColor.blue * lumFactor).coerceIn(0f, 1f),
                alpha = baseColor.alpha
            ).copy(alpha = opacity.coerceIn(0.1f, 1.0f))

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
                    // Header: Date & [🔄 手動更新] [＋ 追加] Buttons
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateFormatted,
                            style = TextStyle(
                                fontSize = (15.5f * scale).sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(colors.textPrimary)
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )

                        // 🔄 手動更新 Button (タップで即時再描画)
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(colors.border.copy(alpha = 0.25f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔄",
                                style = TextStyle(
                                    fontSize = (11 * scale).sp,
                                    color = ColorProvider(colors.textSecondary)
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

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

                    val hasAnyTasks = surfacedHabits.isNotEmpty() || surfacedPendingItems.isNotEmpty() || totalTrayCount > 0

                    // Empty state when nothing today
                    if (doneItems.isEmpty() && !hasAnyTasks) {
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

                    // 1. DONE items (空間に余裕がある場合のみ表示)
                    // 今日期限の未完了ToDoを優先し、下部のクリップで未来の歩みが消えないようにする。
                    if (pendingItems.isEmpty() && doneItems.size > 2) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ 他 ${doneItems.size - 2}件完了",
                                style = TextStyle(
                                    fontSize = (10 * scale).sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.65f))
                                )
                            )
                        }
                    }

                    val visibleDone = if (pendingItems.isEmpty()) doneItems.takeLast(2) else emptyList()
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
                                .padding(vertical = 3.dp)
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

                    // 3. NOWライン直下：タイムライン浮上アイテム (アクティブ周期習慣 ＋ 接近/固定ToDo)
                    for (habit in surfacedHabits) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // タスク完了チェック（○）: タップで即時連動完了
                            Box(
                                modifier = GlanceModifier
                                    .size(24.dp)
                                    .clickable(
                                        actionRunCallback<RecordPeriodicGlanceActionCallback>(
                                            actionParametersOf(RecordPeriodicGlanceActionCallback.templateIdKey to habit.id)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "○",
                                    style = TextStyle(
                                        fontSize = (14 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(colors.primary)
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(4.dp))
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
                                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                            )
                        }
                    }

                    for ((index, item) in surfacedPendingItems.withIndex()) {
                        val isFirstTask = (index == 0 && surfacedHabits.isEmpty())
                        val schedTimeStr = item.scheduledAt?.let { sched ->
                            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                            if (t != "00:00") t else null
                        }
                        val isPinnedToday = item.showOnTimeline && schedTimeStr == null

                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // タスク完了チェック（○）: タップで即時連動完了
                            Box(
                                modifier = GlanceModifier
                                    .size(24.dp)
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
                                        fontSize = (14 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(colors.primary)
                                    )
                                )
                            }
                            Spacer(modifier = GlanceModifier.width(4.dp))

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
                            } else if (isPinnedToday) {
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(4.dp)
                                        .background(colors.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "📌 今日",
                                        style = TextStyle(
                                            fontSize = (9.5f * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(colors.primary)
                                        )
                                    )
                                }
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
                                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                            )
                        }
                    }

                    // 4. 「これからの歩み」折り畳み/展開トレイ
                    if (totalTrayCount > 0) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        // ヘッダー1行：「これからの歩み (N件) ▼ / ▲」
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .cornerRadius(6.dp)
                                .background(colors.card.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .clickable(actionRunCallback<ToggleWidgetTrayActionCallback>()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "これからの歩み (${totalTrayCount}件)",
                                style = TextStyle(
                                    fontSize = (11.5f * scale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(colors.textPrimary)
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = if (isTrayExpanded) "▲" else "▼",
                                style = TextStyle(
                                    fontSize = (10 * scale).sp,
                                    color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                )
                            )
                        }

                        if (isTrayExpanded) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            // トレイ内の時間帯経過習慣
                            for (habit in trayHabits.take(3)) {
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(24.dp)
                                            .clickable(
                                                actionRunCallback<RecordPeriodicGlanceActionCallback>(
                                                    actionParametersOf(RecordPeriodicGlanceActionCallback.templateIdKey to habit.id)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "○",
                                            style = TextStyle(
                                                fontSize = (14 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(4.dp)
                                            .background(colors.card.copy(alpha = 0.6f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "習慣",
                                            style = TextStyle(
                                                fontSize = (9 * scale).sp,
                                                color = ColorProvider(colors.textSecondary)
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.width(6.dp))
                                    Text(
                                        text = "${habit.iconKey ?: "🔄"} ${habit.title}",
                                        style = TextStyle(
                                            fontSize = (12 * scale).sp,
                                            color = ColorProvider(colors.textPrimary)
                                        ),
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                                    )
                                }
                            }

                            // トレイ内の未到来/未固定ToDo
                            val remainingSlot = (5 - trayHabits.take(3).size).coerceAtLeast(1)
                            for (item in trayPendingItems.take(remainingSlot)) {
                                val schedTimeStr = item.scheduledAt?.let { sched ->
                                    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                    if (t != "00:00") t else null
                                }
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .size(24.dp)
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
                                                fontSize = (14 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.width(4.dp))
                                    if (schedTimeStr != null) {
                                        Box(
                                            modifier = GlanceModifier
                                                .cornerRadius(4.dp)
                                                .background(colors.card.copy(alpha = 0.6f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = schedTimeStr,
                                                style = TextStyle(
                                                    fontSize = (9.5f * scale).sp,
                                                    color = ColorProvider(colors.textSecondary)
                                                )
                                            )
                                        }
                                        Spacer(modifier = GlanceModifier.width(6.dp))
                                    }
                                    Text(
                                        text = item.title,
                                        style = TextStyle(
                                            fontSize = (12 * scale).sp,
                                            color = ColorProvider(colors.textPrimary)
                                        ),
                                        maxLines = 1,
                                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                                    )
                                }
                            }

                            val hiddenCount = (trayHabits.size + trayPendingItems.size) - (trayHabits.take(3).size + trayPendingItems.take(remainingSlot).size)
                            if (hiddenCount > 0) {
                                Text(
                                    text = "... 他 ${hiddenCount} 件",
                                    style = TextStyle(
                                        fontSize = (10 * scale).sp,
                                        color = ColorProvider(colors.textSecondary.copy(alpha = 0.65f))
                                    ),
                                    modifier = GlanceModifier.padding(start = 28.dp, top = 1.dp)
                                )
                            }
                        }
                    } else if (surfacedHabits.isEmpty() && surfacedPendingItems.isEmpty() && doneItems.isNotEmpty()) {
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
        val newDone = !item.isDone
        val updated = item.copy(
            isDone = newDone,
            completedAt = if (newDone) System.currentTimeMillis() else null
        )
        db.timelineItemDao().update(updated)
        TodayTimelineWidgetReceiver.updateAll(context)
    }

    companion object {
        val itemIdKey = ActionParameters.Key<Long>("itemId")
    }
}

class RecordPeriodicGlanceActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val templateId = parameters[templateIdKey] ?: return
        val db = AppDatabase.getInstance(context)
        val template = db.templateDao().getById(templateId) ?: return
        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)
        val dateProvider = LifeDateProvider()
        val logicalToday = dateProvider.getLogicalDate(cutoffHour = cutoffHour)
        val useCase = PeriodicTaskUseCase(db, dateProvider)
        useCase.recordCompletion(template, logicalToday, cutoffHour)
        TodayTimelineWidgetReceiver.updateAll(context)
    }

    companion object {
        val templateIdKey = ActionParameters.Key<Long>("templateId")
    }
}

class ToggleWidgetTrayActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetSettingsManager.toggleWidgetTrayExpanded(context)
        TodayTimelineWidgetReceiver.updateAll(context)
    }
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        TodayTimelineWidgetReceiver.updateAll(context)
    }
}
