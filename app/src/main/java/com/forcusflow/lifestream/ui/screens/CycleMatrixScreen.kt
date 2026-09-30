package com.forcusflow.lifestream.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_OF_WEEK_FORMATTER = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE)

/**
 * Calculates the current streak for a periodic template.
 * A "streak" is the number of consecutive intervals completed on time.
 */
fun calculateStreak(allItems: List<TimelineItemEntity>, template: TemplateEntity, zone: ZoneId): Int {
    val interval = template.intervalDays ?: 7
    val completionDates = allItems
        .filter { it.isDone && it.templateId == template.id && it.completedAt != null }
        .map { LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt!!), zone).toLocalDate() }
        .sortedDescending()
        .toList()

    if (completionDates.isEmpty()) return 0

    var streak = 1
    var prevDate = completionDates[0]
    for (i in 1 until completionDates.size) {
        val daysDiff = ChronoUnit.DAYS.between(completionDates[i], prevDate)
        if (daysDiff <= interval + 1) {
            streak++
            prevDate = completionDates[i]
        } else {
            break
        }
    }
    return streak
}

/**
 * Calculates completion rate for last 30 days (for templates with interval <= 30).
 */
fun calculateCompletionRate(allItems: List<TimelineItemEntity>, template: TemplateEntity, zone: ZoneId): Float {
    val today = LocalDate.now()
    val thirtyDaysAgo = today.minusDays(30)
    val interval = template.intervalDays ?: 7
    val expectedCount = (30 / interval).coerceAtLeast(1)

    val actualCount = allItems.count { item ->
        if (!item.isDone || item.completedAt == null) return@count false
        if (item.templateId != template.id) return@count false
        val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.completedAt), zone).toLocalDate()
        !date.isBefore(thirtyDaysAgo) && !date.isAfter(today)
    }
    return (actualCount.toFloat() / expectedCount).coerceIn(0f, 1f)
}

@Composable
fun CycleMatrixScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val templates by viewModel.templates.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val cutoffHour by viewModel.dayCutoffHour.collectAsState()
    val today = remember(cutoffHour) {
        viewModel.getLogicalDate(LocalDateTime.now(), cutoffHour)
    }
    val (todayStart, todayEnd) = remember(today, cutoffHour) {
        viewModel.getDayRange(today, cutoffHour)
    }

    var showAddPeriodicDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }

    // Sort periodic tasks: Overdue first, then Due Soon, then On Track
    val periodicTemplates = remember(templates, today) {
        templates.filter { it.type == "INTERVAL" && it.intervalDays != null && it.intervalDays > 0 }
            .sortedByDescending { template ->
                val lastDoneDate = template.lastCompletedAt?.let {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
                }
                val elapsed = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today) else 999L
                val interval = template.intervalDays ?: 7
                elapsed - interval
            }
    }

    val somedayTemplates = remember(templates) {
        templates.filter { it.type == "INTERVAL" && (it.intervalDays == null || it.intervalDays <= 0) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPeriodicDialog = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "周期タスク追加", modifier = Modifier.size(26.dp))
            }
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AppHeader(
                title = "周期"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (periodicTemplates.isEmpty() && somedayTemplates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔄", fontSize = 40.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "周期タスクがまだありません。\n右下の「＋」ボタンから追加してみましょう！",
                                    fontSize = 14.sp,
                                    color = colors.textSecondary,
                                    lineHeight = 20.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    if (periodicTemplates.isNotEmpty()) {
                        val dueTemplates = mutableListOf<TemplateEntity>()
                        val upcomingTemplates = mutableListOf<TemplateEntity>()
                        val doneTodayTemplates = mutableListOf<TemplateEntity>()

                        periodicTemplates.forEach { template ->
                            val isDoneToday = allItems.any {
                                it.isDone && it.templateId == template.id && it.completedAt != null &&
                                it.completedAt in todayStart..todayEnd
                            }
                            if (isDoneToday) {
                                doneTodayTemplates.add(template)
                            } else {
                                val lastDoneDate = template.lastCompletedAt?.let {
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
                                }
                                val elapsed = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today) else 999L
                                val interval = template.intervalDays ?: 7
                                if (elapsed >= interval) {
                                    dueTemplates.add(template)
                                } else {
                                    upcomingTemplates.add(template)
                                }
                            }
                        }

                        // 1. そろそろ（おすすめ）ゾーン
                        if (dueTemplates.isNotEmpty()) {
                            item(key = "header_due") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🌿 そろそろ（おすすめ）",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${dueTemplates.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            items(dueTemplates, key = { it.id }) { template ->
                                val streak = calculateStreak(allItems, template, zone)
                                MainTaskStyleCycleCard(
                                    template = template,
                                    today = today,
                                    isDoneToday = false,
                                    isDoneInCycle = false,
                                    streak = streak,
                                    onClick = { editingTemplate = template },
                                    onToggleToday = {
                                        viewModel.toggleCycleTask(template, today)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("「${template.title}」を完了しました！ 🌿")
                                        }
                                    },
                                    onSkip = {
                                        viewModel.skipCycleTask(template)
                                        coroutineScope.launch {
                                            val interval = template.intervalDays ?: 7
                                            val nextDate = today.plusDays(interval.toLong())
                                            snackbarHostState.showSnackbar("「${template.title}」をスキップしました (次回: ${nextDate.monthValue}/${nextDate.dayOfMonth})")
                                        }
                                    }
                                )
                            }
                        }

                        // 2. まだ先（おだやかに準備）ゾーン
                        if (upcomingTemplates.isNotEmpty()) {
                            item(key = "header_upcoming") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "☕ まだ先（おだやかに準備）",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${upcomingTemplates.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textSecondary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            items(upcomingTemplates, key = { it.id }) { template ->
                                val streak = calculateStreak(allItems, template, zone)
                                MainTaskStyleCycleCard(
                                    template = template,
                                    today = today,
                                    isDoneToday = false,
                                    isDoneInCycle = true,
                                    streak = streak,
                                    onClick = { editingTemplate = template },
                                    onToggleToday = {
                                        viewModel.toggleCycleTask(template, today)
                                        coroutineScope.launch {
                                            val nextDate = today.plusDays(template.intervalDays?.toLong() ?: 7L)
                                            snackbarHostState.showSnackbar("「${template.title}」を前倒し完了しました ✨ (次回: ${nextDate.format(DAY_OF_WEEK_FORMATTER)})")
                                        }
                                    },
                                    onSkip = {
                                        viewModel.skipCycleTask(template)
                                        coroutineScope.launch {
                                            val interval = template.intervalDays ?: 7
                                            val nextDate = today.plusDays(interval.toLong())
                                            snackbarHostState.showSnackbar("「${template.title}」をスキップしました (次回: ${nextDate.monthValue}/${nextDate.dayOfMonth})")
                                        }
                                    }
                                )
                            }
                        }

                        // 3. 本日完了ゾーン
                        if (doneTodayTemplates.isNotEmpty()) {
                            item(key = "header_done_today") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "✨ 本日完了（満たされた習慣）",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${doneTodayTemplates.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF10B981).copy(alpha = 0.7f)
                                    )
                                }
                            }
                            items(doneTodayTemplates, key = { it.id }) { template ->
                                val streak = calculateStreak(allItems, template, zone)
                                MainTaskStyleCycleCard(
                                    template = template,
                                    today = today,
                                    isDoneToday = true,
                                    isDoneInCycle = true,
                                    streak = streak,
                                    onClick = { editingTemplate = template },
                                    onToggleToday = {
                                        viewModel.toggleCycleTask(template, today)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("「${template.title}」の達成を取り消しました")
                                        }
                                    },
                                    onSkip = {}
                                )
                            }
                        }
                    }

                    if (somedayTemplates.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌱 いつかやりたいこと（余白）",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.card)
                                        .border(0.5.dp, colors.border, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${somedayTemplates.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                        items(somedayTemplates, key = { it.id }) { template ->
                            val isDoneToday = remember(allItems, template.id, todayStart, todayEnd) {
                                allItems.any {
                                    it.isDone && it.templateId == template.id && it.completedAt != null &&
                                    it.completedAt in todayStart..todayEnd
                                }
                            }
                            MainTaskStyleCycleCard(
                                template = template,
                                today = today,
                                isDoneToday = isDoneToday,
                                streak = 0,
                                onClick = { editingTemplate = template },
                                onToggleToday = {
                                    viewModel.toggleCycleTask(template, today)
                                    coroutineScope.launch {
                                        val msg = if (isDoneToday) "「${template.title}」の本日の達成を取り消しました"
                                                  else "「${template.title}」を完了しました！🎉"
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                },
                                onSkip = {}
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddPeriodicDialog) {
        AddPeriodicTaskDialog(
            onDismiss = { showAddPeriodicDialog = false },
            onAdd = { title, intervalDays, iconKey, colorHex, timeOfDayZone ->
                viewModel.addTemplate(
                    title = title,
                    type = "INTERVAL",
                    intervalDays = intervalDays,
                    defaultAmount = null,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    timeOfDayZone = timeOfDayZone
                )
                showAddPeriodicDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("「$title」を追加しました！")
                }
            }
        )
    }

    if (editingTemplate != null) {
        EditPeriodicTaskBottomSheet(
            template = editingTemplate!!,
            allItems = allItems,
            onDismiss = { editingTemplate = null },
            onSave = { updated ->
                viewModel.updateTemplate(updated)
                editingTemplate = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("「${updated.title}」を更新しました")
                }
            },
            onDelete = { toDelete ->
                viewModel.deleteTemplate(toDelete)
                editingTemplate = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("「${toDelete.title}」を削除しました")
                }
            },
            onToggleDate = { date ->
                viewModel.toggleCycleTask(editingTemplate!!, date)
            }
        )
    }
}

val DEFAULT_ICON_OPTIONS = listOf(
    "🧹", "🧖", "🛏️", "🧼", "🌀", "💨", "🌿", "💊", "🚗", "🪴",
    "🚿", "🧺", "🏃", "🐶", "📚", "💪", "🍱", "🪥", "🌙", "☀️",
    "🌊", "🧘", "🎵", "🏠"
)

val DEFAULT_COLOR_OPTIONS = listOf(
    Pair("#10B981", "グリーン"),
    Pair("#3B82F6", "ブルー"),
    Pair("#A855F7", "パープル"),
    Pair("#F59E0B", "オレンジ"),
    Pair("#EF4444", "レッド"),
    Pair("#8C5A3C", "ブラウン"),
    Pair("#EC4899", "ピンク"),
    Pair("#6366F1", "インディゴ")
)

/**
 * MainTaskスタイルの周期・習慣カード
 * 16dp角丸、左側カラーインジケーターバー、残日数・予定表示、右側ワンタップ達成トグル
 */
@Composable
fun MainTaskStyleCycleCard(
    template: TemplateEntity,
    today: LocalDate,
    isDoneToday: Boolean,
    isDoneInCycle: Boolean = isDoneToday,
    streak: Int = 0,
    onClick: () -> Unit,
    onToggleToday: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val haptic = LocalHapticFeedback.current

    val isSomeday = template.intervalDays == null || template.intervalDays <= 0
    val interval = template.intervalDays ?: 7

    val lastDoneDate = template.lastCompletedAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
    }
    val elapsedDays = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today).toInt() else null

    val isOverdue = !isSomeday && elapsedDays != null && elapsedDays > interval
    val isDueToday = !isSomeday && ((elapsedDays != null && elapsedDays == interval) || lastDoneDate == null)
    val isSkippedToday = !isSomeday && elapsedDays != null && elapsedDays == 0 && !isDoneToday && !isDoneInCycle

    val statusColor = when {
        isSomeday -> Color(0xFF9CA3AF)
        isDoneToday -> Color(0xFF10B981)
        isDueToday || isOverdue -> colors.primary
        isSkippedToday -> Color(0xFF6B7280)
        else -> colors.primary.copy(alpha = 0.4f)
    }

    val daysUntilDue = if (elapsedDays != null) interval - elapsedDays else 0
    val nextDueDate = if (lastDoneDate != null) lastDoneDate.plusDays(interval.toLong()) else today

    val overdueDays = if (elapsedDays != null && elapsedDays > interval) elapsedDays - interval else 0
    val timingText = when {
        isSomeday -> if (lastDoneDate != null) "前回 ${lastDoneDate.format(DAY_OF_WEEK_FORMATTER)}" else "いつかやりたい余白"
        isDoneToday -> "本日完了 ✨ · 次回 ${nextDueDate.format(DAY_OF_WEEK_FORMATTER)}"
        isSkippedToday -> "お休み中 · 次回 ${nextDueDate.format(DAY_OF_WEEK_FORMATTER)}"
        isOverdue && overdueDays > 3 -> "${overdueDays}日ぶり · 前回 ${lastDoneDate?.format(DAY_OF_WEEK_FORMATTER) ?: "なし"}"
        isOverdue -> "そろそろ · 前回 ${lastDoneDate?.format(DAY_OF_WEEK_FORMATTER)}"
        isDueToday -> "今日おすすめ · 前回 ${lastDoneDate?.format(DAY_OF_WEEK_FORMATTER) ?: "なし"}"
        else -> "あと${daysUntilDue}日 · 次回 ${nextDueDate.format(DAY_OF_WEEK_FORMATTER)}"
    }

    val timingColor = when {
        isSomeday -> colors.textSecondary
        isDoneToday -> Color(0xFF10B981)
        isOverdue && overdueDays > 3 -> colors.textSecondary
        isDueToday || isOverdue -> colors.primary
        else -> colors.textSecondary
    }

    val cycleText = if (isSomeday) {
        "いつか"
    } else {
        if (interval % 7 == 0) "${interval / 7}週間ごと" else "${interval}日ごと"
    }

    val accentColor = template.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: statusColor

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(0.8.dp, colors.border.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Status Pill Indicator
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Emoji / Icon circle
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.iconKey ?: "🧹",
                    fontSize = 17.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Info Column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = template.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (streak >= 2 && !isSomeday) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.primary.copy(alpha = 0.1f),
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = "🌿 ${streak}巡目",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = timingText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = timingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "·",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = cycleText,
                        fontSize = 10.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Quick Actions: Skip & Complete Checkbox
            if (!isSomeday) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSkip()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = "↷",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
            } else {
                Spacer(modifier = Modifier.width(2.dp))
            }

            // Circular Action Button
            val isActionUrgent = isOverdue || isDueToday
            val buttonBorderColor = when {
                isDoneToday -> Color.Transparent
                isOverdue -> colors.primary
                isDueToday -> Color(0xFFF59E0B)
                else -> colors.border.copy(alpha = 0.6f)
            }
            val buttonBgColor = when {
                isDoneToday -> Color(0xFF10B981)
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(buttonBgColor)
                    .border(
                        width = if (isDoneToday) 0.dp else if (isActionUrgent) 2.dp else 1.dp,
                        color = buttonBorderColor,
                        shape = CircleShape
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleToday()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isDoneToday) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "本日完了（タップで取消）",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (!isActionUrgent && !isSomeday) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "前倒し完了",
                        tint = colors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 実施周期セレクター（シンプルチップ ＋ カスタム時ステッパー展開）
 */
@Composable
fun PeriodicIntervalSelector(
    intervalDays: Int?,
    onIntervalChange: (Int?) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    val isSomeday = intervalDays == null || intervalDays <= 0
    val isPreset = intervalDays in listOf(1, 7, 14, 30) || isSomeday
    var isCustomMode by remember(intervalDays) { mutableStateOf(!isPreset && intervalDays != null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "実施周期",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Preset Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf<Triple<String, Int?, Boolean>>(
                Triple("毎日", 1, !isCustomMode && intervalDays == 1),
                Triple("毎週", 7, !isCustomMode && intervalDays == 7),
                Triple("隔週", 14, !isCustomMode && intervalDays == 14),
                Triple("毎月", 30, !isCustomMode && intervalDays == 30),
                Triple("未定 (いつでも)", null, !isCustomMode && isSomeday),
                Triple("カスタム", intervalDays ?: 3, isCustomMode)
            ).forEach { (label, value, isSelected) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.border,
                            RoundedCornerShape(8.dp)
                        )
                        .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (label == "カスタム") {
                                isCustomMode = true
                                if (intervalDays == null || intervalDays <= 0) {
                                    onIntervalChange(3)
                                }
                            } else {
                                isCustomMode = false
                                onIntervalChange(value)
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.primary else colors.textPrimary
                    )
                }
            }
        }

        // Custom Stepper (Only when Custom is selected)
        if (isCustomMode) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val cur = intervalDays ?: 3
                        if (cur > 1) onIntervalChange(cur - 1)
                    },
                    enabled = (intervalDays ?: 3) > 1,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("-1日", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "${intervalDays ?: 3} 日ごと",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val cur = intervalDays ?: 3
                        if (cur < 365) onIntervalChange(cur + 1)
                    },
                    enabled = (intervalDays ?: 3) < 365,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("+1日", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 周期タスクの詳細・編集 BottomSheet（ToDo編集画面と完全統一）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPeriodicTaskBottomSheet(
    template: TemplateEntity,
    allItems: List<TimelineItemEntity>,
    onDismiss: () -> Unit,
    onSave: (TemplateEntity) -> Unit,
    onDelete: (TemplateEntity) -> Unit,
    onToggleDate: (LocalDate) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    var title by remember { mutableStateOf(template.title) }
    var intervalDays by remember { mutableStateOf<Int?>(template.intervalDays) }
    var timeOfDayZone by remember { mutableStateOf(template.timeOfDayZone) }
    var iconKey by remember { mutableStateOf(template.iconKey ?: "🧹") }
    var selectedColorHex by remember { mutableStateOf(template.colorHex ?: "#10B981") }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Accordions
    var showAdvancedOptions by remember { mutableStateOf(false) }
    var showHistoryCalendar by remember { mutableStateOf(false) }

    val completedDates = remember(allItems, template.id) {
        allItems.filter {
            it.isDone &&
            it.templateId == template.id &&
            it.completedAt != null
        }.map {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt!!), zone).toLocalDate()
        }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header: Cancel - Title - Save Button (Unified with ItemDetailBottomSheet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                }
                Text(
                    text = "周期タスクの編集",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Button(
                    onClick = {
                        val updated = template.copy(
                            title = title.trim(),
                            intervalDays = if (intervalDays != null && intervalDays!! > 0) intervalDays else null,
                            iconKey = iconKey,
                            colorHex = selectedColorHex,
                            timeOfDayZone = timeOfDayZone
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    enabled = title.isNotBlank()
                ) {
                    Text("保存", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 1. タスク名
            Text("タスク名", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. 実施周期
            PeriodicIntervalSelector(
                intervalDays = intervalDays,
                onIntervalChange = { intervalDays = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. 生活リズム・時間帯ゾーン
            Text("時間帯ゾーン（いつやる？）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL_DAY" to "いつでも",
                    "MORNING" to "朝 ☀️",
                    "AFTERNOON" to "昼 🍴",
                    "EVENING_NIGHT" to "夜 🌙"
                ).forEach { (code, label) ->
                    val isSelected = timeOfDayZone == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background)
                            .clickable { timeOfDayZone = code }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. アコーディオン：アイコン・カラー設定（任意）
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, if (showAdvancedOptions) colors.primary.copy(alpha = 0.5f) else colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedOptions = !showAdvancedOptions },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = iconKey, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "アイコン・カラー設定（任意）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = if (showAdvancedOptions) "▲ 閉じる" else "▼ 設定する",
                            fontSize = 12.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showAdvancedOptions) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // アイコン選択
                        Text("アイコン", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DEFAULT_ICON_OPTIONS.forEach { ic ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.5.dp,
                                            if (iconKey == ic) colors.primary else colors.border,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(if (iconKey == ic) colors.primary.copy(alpha = 0.15f) else colors.card)
                                        .clickable { iconKey = ic },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = ic, fontSize = 18.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // カラー選択
                        Text("テーマカラー", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DEFAULT_COLOR_OPTIONS.forEach { (hex, _) ->
                                val parsedColor = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { colors.primary }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            width = if (selectedColorHex == hex) 2.5.dp else 0.dp,
                                            color = if (selectedColorHex == hex) colors.textPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColorHex == hex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. アコーディオン：過去の達成記録・月間カレンダー
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, if (showHistoryCalendar) colors.primary.copy(alpha = 0.5f) else colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistoryCalendar = !showHistoryCalendar },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "過去の記録を確認・修正",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = if (showHistoryCalendar) "▲ 閉じる" else "▼ カレンダーを見る",
                            fontSize = 12.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showHistoryCalendar) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // 月ナビゲーション
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { currentMonth = currentMonth.minusMonths(1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "前月", tint = colors.textPrimary)
                            }
                            Text(
                                text = "${currentMonth.year}年 ${currentMonth.monthValue}月",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            IconButton(
                                onClick = {
                                    if (currentMonth.isBefore(YearMonth.now())) {
                                        currentMonth = currentMonth.plusMonths(1)
                                    }
                                },
                                enabled = currentMonth.isBefore(YearMonth.now()),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "次月",
                                    tint = if (currentMonth.isBefore(YearMonth.now())) colors.textPrimary else colors.textSecondary.copy(alpha = 0.3f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 曜日ヘッダー
                        val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            dayNames.forEach { d ->
                                Text(
                                    text = d,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 日付グリッド
                        val firstDay = currentMonth.atDay(1)
                        val startOffset = firstDay.dayOfWeek.value - 1
                        val daysInMonth = currentMonth.lengthOfMonth()
                        val totalCells = startOffset + daysInMonth
                        val rows = (totalCells + 6) / 7

                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (c in 0 until 7) {
                                    val cellIdx = r * 7 + c
                                    val dayNum = cellIdx - startOffset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val date = currentMonth.atDay(dayNum)
                                        val isDone = completedDates.contains(date)
                                        val isCurDay = (date == today)
                                        val isFuture = date.isAfter(today)

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        isDone -> Color(0xFF16A34A)
                                                        isCurDay -> colors.primary.copy(alpha = 0.15f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = if (isCurDay) 1.5.dp else 0.dp,
                                                    color = if (isCurDay) colors.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable(enabled = !isFuture) {
                                                    onToggleDate(date)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "$dayNum",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isDone || isCurDay) FontWeight.Bold else FontWeight.Normal,
                                                    color = when {
                                                        isDone -> Color.White
                                                        isCurDay -> colors.primary
                                                        isFuture -> colors.textSecondary.copy(alpha = 0.35f)
                                                        else -> colors.textPrimary
                                                    }
                                                )
                                                if (isDone) {
                                                    Text("✓", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(34.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 押し忘れた過去の日付をタップして、達成記録を追加・解除できます",
                            fontSize = 10.5.sp,
                            color = colors.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. 削除ボタン
            OutlinedButton(
                onClick = { showDeleteConfirmDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.statusOverdue),
                border = BorderStroke(1.dp, colors.statusOverdue.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("この周期タスクを削除", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (showDeleteConfirmDialog) {
        ModalBottomSheet(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "周期タスクの削除",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "「${template.title}」を削除しますか？\n過去に記録されたログはそのまま残りますが、周期一覧からは削除されます。",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text("キャンセル", color = colors.textPrimary)
                    }
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDelete(template)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.statusOverdue)
                    ) {
                        Text("削除する", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 新規周期タスク追加シート（ToDo画面と完全統一）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPeriodicTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, intervalDays: Int?, iconKey: String, colorHex: String, timeOfDayZone: String) -> Unit,
    initialTitle: String = ""
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf(initialTitle) }
    var intervalDays by remember { mutableStateOf<Int?>(7) }
    var timeOfDayZone by remember { mutableStateOf("ALL_DAY") }
    var iconKey by remember { mutableStateOf("🧹") }
    var selectedColorHex by remember { mutableStateOf("#8C5A3C") }
    var showAdvancedOptions by remember { mutableStateOf(false) }

    val iconOptions = DEFAULT_ICON_OPTIONS
    val colorOptions = DEFAULT_COLOR_OPTIONS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header: Cancel - Title - Add Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                }
                Text(
                    text = "新しい周期タスクを追加",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAdd(
                                title.trim(),
                                if (intervalDays != null && intervalDays!! > 0) intervalDays else null,
                                iconKey,
                                selectedColorHex,
                                timeOfDayZone
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    enabled = title.isNotBlank()
                ) {
                    Text("追加", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 1. タスク名
            Text("タスク名", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("例: エアコン清掃、布団干し...", color = colors.textSecondary.copy(alpha = 0.5f)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. 実施周期
            PeriodicIntervalSelector(
                intervalDays = intervalDays,
                onIntervalChange = { intervalDays = it }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. 生活リズム・時間帯ゾーン
            Text("時間帯ゾーン（いつやる？）", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL_DAY" to "いつでも",
                    "MORNING" to "朝 ☀️",
                    "AFTERNOON" to "昼 🍴",
                    "EVENING_NIGHT" to "夜 🌙"
                ).forEach { (code, label) ->
                    val isSelected = timeOfDayZone == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background)
                            .clickable { timeOfDayZone = code }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. アコーディオン：アイコン・カラー設定（任意）
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, if (showAdvancedOptions) colors.primary.copy(alpha = 0.5f) else colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedOptions = !showAdvancedOptions },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = iconKey, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "アイコン・カラー設定（任意）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = if (showAdvancedOptions) "▲ 閉じる" else "▼ 設定する",
                            fontSize = 12.sp,
                            color = colors.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (showAdvancedOptions) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // アイコン選択
                        Text("アイコン", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            iconOptions.forEach { ic ->
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.5.dp,
                                            if (iconKey == ic) colors.primary else colors.border,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .background(if (iconKey == ic) colors.primary.copy(alpha = 0.15f) else colors.card)
                                        .clickable { iconKey = ic },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = ic, fontSize = 18.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // カラー選択
                        Text("テーマカラー", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            colorOptions.forEach { (hex, _) ->
                                val parsedColor = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { colors.primary }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            width = if (selectedColorHex == hex) 2.5.dp else 0.dp,
                                            color = if (selectedColorHex == hex) colors.textPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColorHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedColorHex == hex) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
