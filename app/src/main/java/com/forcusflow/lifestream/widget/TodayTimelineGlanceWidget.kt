package com.forcusflow.lifestream.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.forcusflow.lifestream.MainActivity
import com.forcusflow.lifestream.R
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.domain.LifeDateProvider
import com.forcusflow.lifestream.domain.PeriodicTaskUseCase
import com.forcusflow.lifestream.domain.TodayTimelineCalculator
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TodayTimelineGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val opacity = WidgetSettingsManager.getOpacity(context)
        val fontSizePref = WidgetSettingsManager.getFontSize(context)
        val colors = WidgetSettingsManager.getThemeColors(context)
        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)
        val isTrayExpanded = WidgetSettingsManager.isFutureTrayExpanded(context)

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

            // Root container without clickable modifier to avoid RemoteViews touch event collisions
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ambientCardBg)
                    .cornerRadius(18.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize()
                ) {
            // Header: Date & [↻ 手動更新] [＋ 追加] Buttons
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
                            modifier = GlanceModifier
                                .defaultWeight()
                                .clickable(actionStartActivity(launchIntent))
                        )

                        // ↻ 手動更新 Button (タップで即時再描画)
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(colors.border.copy(alpha = 0.25f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .clickable(actionRunCallback<RefreshWidgetActionCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "↻",
                                style = TextStyle(
                                    fontSize = (16 * scale).sp,
                                    fontWeight = FontWeight.Bold,
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
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "🎯 “$focusText”",
                            style = TextStyle(
                                fontSize = (11.5f * scale).sp,
                                fontWeight = FontWeight.Medium,
                                fontStyle = FontStyle.Italic,
                                color = ColorProvider(colors.primary)
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.clickable(actionStartActivity(launchIntent))
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(5.dp))

                    // Thin Divider with high contrast
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.border.copy(alpha = 0.45f))
                    ) {}

                    Spacer(modifier = GlanceModifier.height(5.dp))

                    val hasAnyTasks = surfacedHabits.isNotEmpty() || surfacedPendingItems.isNotEmpty() || totalTrayCount > 0

                    // LazyColumn for vertical scrolling when items or future tray expand
                    LazyColumn(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    ) {
                        // Empty state when nothing today
                        if (doneItems.isEmpty() && !hasAnyTasks) {
                            item {
                                Spacer(modifier = GlanceModifier.height(12.dp))
                                Column(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .clickable(actionStartActivity(launchIntent)),
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

                        // 1. DONE items (実績ログ：NOWラインの上、最新1〜2件をカード表示 ＋ 以前のログサマリー)
                        val maxDoneToShow = if (hasAnyTasks) 1 else 2
                        if (doneItems.size > maxDoneToShow) {
                            item {
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 4.dp)
                                        .clickable(actionStartActivity(launchIntent)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "▲ 以前の実績 ${doneItems.size - maxDoneToShow}件 (タップしてアプリで確認)",
                                        style = TextStyle(
                                            fontSize = (10 * scale).sp,
                                            fontWeight = FontWeight.Medium,
                                            color = ColorProvider(colors.primary.copy(alpha = 0.85f))
                                        )
                                    )
                                }
                            }
                        }

                        val visibleDone = doneItems.takeLast(maxDoneToShow)
                        items(visibleDone) { item ->
                            val timeStr = (item.completedAt ?: item.scheduledAt)?.let { ts ->
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                            } ?: "--:--"

                            val cleanTitle = item.title
                            val durationSec = item.durationSeconds
                            val countVal = item.countValue

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 幹ライン ＋ 完了チェック丸ノード (緑✓: ベクター画像で完全中央配置)
                                Column(
                                    modifier = GlanceModifier.width(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(4.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                    Box(
                                        modifier = GlanceModifier
                                            .size(18.dp)
                                            .cornerRadius(9.dp)
                                            .background(colors.statusDone),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            provider = ImageProvider(R.drawable.ic_widget_check),
                                            contentDescription = "完了",
                                            modifier = GlanceModifier.size(11.dp)
                                        )
                                    }
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(4.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                }

                                Spacer(modifier = GlanceModifier.width(6.dp))

                                // コンパクト1行カード (NOWライン直下の浮上タスクと同一の高さ・余白ゼロの洗練デザイン)
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(8.dp)
                                        .background(colors.card.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable(actionStartActivity(launchIntent))
                                ) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // [10:30] 完了時刻バッジ
                                        Box(
                                            modifier = GlanceModifier
                                                .cornerRadius(3.dp)
                                                .background(colors.statusDone.copy(alpha = 0.14f))
                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                        ) {
                                            Text(
                                                text = timeStr,
                                                style = TextStyle(
                                                    fontSize = (9f * scale).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorProvider(colors.statusDone)
                                                )
                                            )
                                        }

                                        Spacer(modifier = GlanceModifier.width(5.dp))

                                        // タイトル
                                        Text(
                                            text = cleanTitle,
                                            style = TextStyle(
                                                fontSize = (12f * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(colors.textPrimary)
                                            ),
                                            maxLines = 1,
                                            modifier = GlanceModifier.defaultWeight()
                                        )

                                        // ⏱所要時間バッジ
                                        if (durationSec != null && durationSec > 0) {
                                            Spacer(modifier = GlanceModifier.width(4.dp))
                                            val durText = if (durationSec >= 60) "${(durationSec + 30) / 60}分" else "${durationSec}秒"
                                            Box(
                                                modifier = GlanceModifier
                                                    .cornerRadius(3.dp)
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "⏱ $durText",
                                                    style = TextStyle(
                                                        fontSize = (8f * scale).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(Color(0xFFEF4444))
                                                    )
                                                )
                                            }
                                        }

                                        // 回数バッジ
                                        if (countVal != null && countVal > 0) {
                                            Spacer(modifier = GlanceModifier.width(4.dp))
                                            Box(
                                                modifier = GlanceModifier
                                                    .cornerRadius(3.dp)
                                                    .background(Color(0xFF0284C7).copy(alpha = 0.14f))
                                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "${countVal}回",
                                                    style = TextStyle(
                                                        fontSize = (8f * scale).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(Color(0xFF0284C7))
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. NOW line (現在時刻)
                        item {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(actionStartActivity(launchIntent)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = GlanceModifier.width(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(14.dp)
                                            .background(colors.nowLine)
                                    ) {}
                                }
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(6.dp)
                                        .background(colors.nowLine)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
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
                                        .height(1.5.dp)
                                        .background(colors.nowLine.copy(alpha = 0.65f))
                                ) {}
                            }
                        }

                        // 3. NOWライン直下：タイムライン浮上アイテム (アクティブ周期習慣)
                        items(surfacedHabits) { habit ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 幹ライン ＋ 未完了丸ノード (○: タップで即時連動完了)
                                Column(
                                    modifier = GlanceModifier.width(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(3.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                    Box(
                                        modifier = GlanceModifier
                                            .size(22.dp)
                                            .clickable(
                                                actionRunCallback<RecordPeriodicGlanceActionCallback>(
                                                    actionParametersOf(RecordPeriodicGlanceActionCallback.templateIdKey to habit.id)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            provider = ImageProvider(R.drawable.ic_widget_circle),
                                            contentDescription = "未完了",
                                            modifier = GlanceModifier.size(17.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(colors.primary))
                                        )
                                    }
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(3.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                }

                                Spacer(modifier = GlanceModifier.width(6.dp))

                                // カード表面 (本体と同一の角丸カード)
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(8.dp)
                                        .background(colors.card.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable(actionStartActivity(launchIntent))
                                ) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .cornerRadius(3.dp)
                                                .background(colors.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                        ) {
                                            Text(
                                                text = "習慣",
                                                style = TextStyle(
                                                    fontSize = (8.5f * scale).sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorProvider(colors.primary)
                                                )
                                            )
                                        }
                                        Spacer(modifier = GlanceModifier.width(5.dp))
                                        Text(
                                            text = "${habit.iconKey ?: "🔄"} ${habit.title}",
                                            style = TextStyle(
                                                fontSize = (12f * scale).sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ColorProvider(colors.textPrimary)
                                            ),
                                            maxLines = 1,
                                            modifier = GlanceModifier.defaultWeight()
                                        )
                                    }
                                }
                            }
                        }

                        // 3-B. NOWライン直下：タイムライン浮上アイテム (接近/固定ToDo)
                        items(surfacedPendingItems) { item ->
                            val schedTimeStr = item.scheduledAt?.let { sched ->
                                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                                if (t != "00:00") t else null
                            }
                            val isPinnedToday = item.showOnTimeline && schedTimeStr == null

                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 幹ライン ＋ 未完了丸ノード (○: タップで即時連動完了)
                                Column(
                                    modifier = GlanceModifier.width(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(3.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                    Box(
                                        modifier = GlanceModifier
                                            .size(22.dp)
                                            .clickable(
                                                actionRunCallback<ToggleItemDoneActionCallback>(
                                                    actionParametersOf(ToggleItemDoneActionCallback.itemIdKey to item.id)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            provider = ImageProvider(R.drawable.ic_widget_circle),
                                            contentDescription = "未完了",
                                            modifier = GlanceModifier.size(17.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(colors.primary))
                                        )
                                    }
                                    Box(
                                        modifier = GlanceModifier
                                            .width(2.dp)
                                            .height(3.dp)
                                            .background(colors.border.copy(alpha = 0.45f))
                                    ) {}
                                }

                                Spacer(modifier = GlanceModifier.width(6.dp))

                                // カード表面 (本体と同一の角丸カード)
                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(8.dp)
                                        .background(colors.card.copy(alpha = 0.85f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable(actionStartActivity(launchIntent))
                                ) {
                                    Row(
                                        modifier = GlanceModifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (schedTimeStr != null) {
                                            Box(
                                                modifier = GlanceModifier
                                                    .cornerRadius(3.dp)
                                                    .background(colors.primary.copy(alpha = 0.16f))
                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = schedTimeStr,
                                                    style = TextStyle(
                                                        fontSize = (9f * scale).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(colors.primary)
                                                    )
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.width(5.dp))
                                        } else if (isPinnedToday) {
                                            Box(
                                                modifier = GlanceModifier
                                                    .cornerRadius(3.dp)
                                                    .background(colors.primary.copy(alpha = 0.12f))
                                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                            ) {
                                                Text(
                                                    text = "📌 今日",
                                                    style = TextStyle(
                                                        fontSize = (8.5f * scale).sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ColorProvider(colors.primary)
                                                    )
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.width(5.dp))
                                        }

                                        Text(
                                            text = item.title,
                                            style = TextStyle(
                                                fontSize = (12f * scale).sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ColorProvider(colors.textPrimary)
                                            ),
                                            maxLines = 1,
                                            modifier = GlanceModifier.defaultWeight()
                                        )
                                    }
                                }
                            }
                        }

                        // 4. 「これからの歩み」統一トレイ (ヘッダー ＋ 展開時の個別アイテム)
                        if (totalTrayCount > 0) {
                            // 見出し行：「これからの歩み (N件) ▲ / ▼」 (白枠なしのクリーンな見出し)
                            item {
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 2.dp)
                                        .clickable(actionRunCallback<ToggleWidgetTrayActionCallback>()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "これからの歩み (${totalTrayCount}件)",
                                        style = TextStyle(
                                            fontSize = (11.5f * scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorProvider(colors.textPrimary)
                                        )
                                    )
                                    Spacer(modifier = GlanceModifier.width(5.dp))
                                    Text(
                                        text = if (isTrayExpanded) "▲" else "▼",
                                        style = TextStyle(
                                            fontSize = (9.5f * scale).sp,
                                            color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                        )
                                    )
                                }
                            }

                            // 展開時：ネスト階層を完全フラット化し、LazyColumnネイティブのアイテムとして爆速描画
                            if (isTrayExpanded) {
                                // A. 🌿 今日のルーティン (周期習慣)
                                if (trayHabits.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "🌿 今日のルーティン",
                                            style = TextStyle(
                                                fontSize = (9.5f * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(colors.primary.copy(alpha = 0.85f))
                                            ),
                                            modifier = GlanceModifier.padding(start = 6.dp, top = 3.dp, bottom = 1.dp)
                                        )
                                    }

                                    items(trayHabits) { habit ->
                                        Row(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 幹の28dpと完全同期させたチェック枠（ズレのない一直線グリッド）
                                            Box(
                                                modifier = GlanceModifier
                                                    .width(28.dp)
                                                    .height(20.dp)
                                                    .clickable(
                                                        actionRunCallback<RecordPeriodicGlanceActionCallback>(
                                                            actionParametersOf(RecordPeriodicGlanceActionCallback.templateIdKey to habit.id)
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    provider = ImageProvider(R.drawable.ic_widget_circle),
                                                    contentDescription = "未完了",
                                                    modifier = GlanceModifier.size(16.dp),
                                                    colorFilter = ColorFilter.tint(ColorProvider(colors.textSecondary.copy(alpha = 0.7f)))
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.width(4.dp))
                                            Text(
                                                text = "${habit.iconKey ?: "🔄"} ${habit.title}",
                                                style = TextStyle(
                                                    fontSize = (11.5f * scale).sp,
                                                    color = ColorProvider(colors.textPrimary)
                                                ),
                                                maxLines = 1,
                                                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                                            )
                                        }
                                    }
                                }

                                // 区切り線
                                if (trayHabits.isNotEmpty() && trayPendingItems.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .padding(vertical = 2.dp)
                                                .background(colors.border.copy(alpha = 0.2f))
                                        ) {}
                                    }
                                }

                                // B. 📋 やること (ToDo)
                                if (trayPendingItems.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "📋 やること",
                                            style = TextStyle(
                                                fontSize = (9.5f * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(colors.primary.copy(alpha = 0.85f))
                                            ),
                                            modifier = GlanceModifier.padding(start = 6.dp, top = 3.dp, bottom = 1.dp)
                                        )
                                    }

                                    items(trayPendingItems) { item ->
                                        val schedTimeStr = item.scheduledAt?.let { sched ->
                                            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                                            if (t != "00:00") t else null
                                        }
                                        Row(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(vertical = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 幹の28dpと完全同期させたチェック枠（ズレのない一直線グリッド）
                                            Box(
                                                modifier = GlanceModifier
                                                    .width(28.dp)
                                                    .height(20.dp)
                                                    .clickable(
                                                        actionRunCallback<ToggleItemDoneActionCallback>(
                                                            actionParametersOf(ToggleItemDoneActionCallback.itemIdKey to item.id)
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Image(
                                                    provider = ImageProvider(R.drawable.ic_widget_circle),
                                                    contentDescription = "未完了",
                                                    modifier = GlanceModifier.size(16.dp),
                                                    colorFilter = ColorFilter.tint(ColorProvider(colors.textSecondary.copy(alpha = 0.7f)))
                                                )
                                            }
                                            Spacer(modifier = GlanceModifier.width(4.dp))
                                            if (schedTimeStr != null) {
                                                Box(
                                                    modifier = GlanceModifier
                                                        .cornerRadius(3.dp)
                                                        .background(colors.primary.copy(alpha = 0.12f))
                                                        .padding(horizontal = 4.dp, vertical = 0.5.dp)
                                                ) {
                                                    Text(
                                                        text = schedTimeStr,
                                                        style = TextStyle(
                                                            fontSize = (8.5f * scale).sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ColorProvider(colors.primary)
                                                        )
                                                    )
                                                }
                                                Spacer(modifier = GlanceModifier.width(4.dp))
                                            }
                                            Text(
                                                text = item.title,
                                                style = TextStyle(
                                                    fontSize = (11.5f * scale).sp,
                                                    color = ColorProvider(colors.textPrimary)
                                                ),
                                                maxLines = 1,
                                                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (surfacedHabits.isEmpty() && surfacedPendingItems.isEmpty() && doneItems.isNotEmpty()) {
                            item {
                                Spacer(modifier = GlanceModifier.height(4.dp))
                                Text(
                                    text = "手元のタスクはありません 🌿",
                                    style = TextStyle(
                                        fontSize = (11 * scale).sp,
                                        color = ColorProvider(colors.textSecondary.copy(alpha = 0.7f))
                                    ),
                                    modifier = GlanceModifier.clickable(actionStartActivity(launchIntent))
                                )
                            }
                        }

                        // Bottom padding inside LazyColumn
                        item {
                            Spacer(modifier = GlanceModifier.height(10.dp))
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
        val newDone = !item.isDone
        val updated = item.copy(
            isDone = newDone,
            completedAt = if (newDone) System.currentTimeMillis() else null
        )
        db.timelineItemDao().update(updated)
        try {
            // タップされたウィジェットを直接即時更新（最速レスポンス）
            TodayTimelineGlanceWidget().update(context, glanceId)
            // 他のインスタンスがあれば非同期バックグラウンドで同期
            TodayTimelineWidgetReceiver.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        try {
            // タップされたウィジェットを直接即時更新（最速レスポンス）
            TodayTimelineGlanceWidget().update(context, glanceId)
            // 他のインスタンスがあれば非同期バックグラウンドで同期
            TodayTimelineWidgetReceiver.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        val templateIdKey = ActionParameters.Key<Long>("templateId")
    }
}

class PostponePeriodicGlanceActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val templateId = parameters[templateIdKey] ?: return
        val db = AppDatabase.getInstance(context)
        val template = db.templateDao().getById(templateId) ?: return
        val cutoffHour = WidgetSettingsManager.getCutoffHour(context)
        PeriodicTaskUseCase(db, LifeDateProvider()).postponeTask(
            template,
            cutoffHour,
            delayDays = 1
        )
        try {
            // タップされたウィジェットを直接即時更新（最速レスポンス）
            TodayTimelineGlanceWidget().update(context, glanceId)
            // 他のインスタンスがあれば非同期バックグラウンドで同期
            TodayTimelineWidgetReceiver.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        WidgetSettingsManager.toggleFutureTrayExpanded(context)
        try {
            // トレイ開閉は最速で当該ウィジェットのみを直接更新（もっさり感を完全解消）
            TodayTimelineGlanceWidget().update(context, glanceId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        try {
            TodayTimelineGlanceWidget().update(context, glanceId)
            TodayTimelineWidgetReceiver.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
