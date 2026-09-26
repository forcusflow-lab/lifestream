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
    val today = LocalDate.now()

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
                        items(periodicTemplates, key = { it.id }) { template ->
                            val isDoneToday = remember(allItems, template.id, today) {
                                allItems.any {
                                    it.isDone && it.templateId == template.id && it.completedAt != null &&
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt), zone).toLocalDate() == today
                                }
                            }
                            val isDoneInCycle = remember(allItems, template.id, today) {
                                val interval = template.intervalDays ?: 7
                                allItems.any { item ->
                                    if (!item.isDone || item.templateId != template.id || item.completedAt == null) return@any false
                                    val itemDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.completedAt), zone).toLocalDate()
                                    val days = ChronoUnit.DAYS.between(itemDate, today)
                                    days in 0 until interval
                                }
                            }
                            val streak = remember(allItems, template.id) {
                                calculateStreak(allItems, template, zone)
                            }
                            MainTaskStyleCycleCard(
                                template = template,
                                today = today,
                                isDoneToday = isDoneToday,
                                isDoneInCycle = isDoneInCycle,
                                streak = streak,
                                onClick = { editingTemplate = template },
                                onToggleToday = {
                                    if (isDoneInCycle && !isDoneToday) {
                                        val interval = template.intervalDays ?: 7
                                        val lastDoneDate = template.lastCompletedAt?.let {
                                            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
                                        }
                                        val nextDate = lastDoneDate?.plusDays(interval.toLong()) ?: today
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("「${template.title}」は今期達成済みです (次回: ${nextDate.format(DAY_OF_WEEK_FORMATTER)})")
                                        }
                                    } else {
                                        viewModel.toggleCycleTask(template, today)
                                        coroutineScope.launch {
                                            val msg = if (isDoneToday) "「${template.title}」の本日の達成を取り消しました"
                                                      else "「${template.title}」を完了しました！🎉"
                                            snackbarHostState.showSnackbar(msg)
                                        }
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

                    if (somedayTemplates.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SOMEDAY (日付未定 · いつか)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                    letterSpacing = 0.5.sp
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
                            val isDoneToday = remember(allItems, template.id, today) {
                                allItems.any {
                                    it.isDone && it.templateId == template.id && it.completedAt != null &&
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt), zone).toLocalDate() == today
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
            onAdd = { title, intervalDays, iconKey, colorHex ->
                viewModel.addTemplate(
                    title = title,
                    type = "INTERVAL",
                    intervalDays = intervalDays,
                    defaultAmount = null,
                    iconKey = iconKey,
                    colorHex = colorHex
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

    val isChecked = if (isSomeday) isDoneToday else isDoneInCycle

    val statusColor = when {
        isSomeday -> Color(0xFF9CA3AF)
        isOverdue -> Color(0xFFEF4444)
        isDueToday -> Color(0xFFF59E0B)
        isSkippedToday -> Color(0xFF6B7280)
        else -> Color(0xFF10B981)
    }

    val daysUntilDue = if (elapsedDays != null) interval - elapsedDays else 0
    val nextDueDate = if (lastDoneDate != null) lastDoneDate.plusDays(interval.toLong()) else today

    val timingText = when {
        isSomeday -> if (lastDoneDate != null) "前回 ${lastDoneDate.format(DAY_OF_WEEK_FORMATTER)}" else "日付未定 · いつでも"
        isDoneToday -> "本日完了 · 次回まであと${daysUntilDue}日 (${nextDueDate.format(DAY_OF_WEEK_FORMATTER)})"
        isDoneInCycle -> "今期達成済 · 次回まであと${daysUntilDue}日 (${nextDueDate.format(DAY_OF_WEEK_FORMATTER)})"
        isSkippedToday -> "スキップ済 · 次回 ${nextDueDate.format(DAY_OF_WEEK_FORMATTER)}"
        isOverdue -> "${-daysUntilDue}日超過 · 前回 ${lastDoneDate!!.format(DAY_OF_WEEK_FORMATTER)}"
        isDueToday -> if (lastDoneDate == null) "今日から開始予定" else "今日期日"
        else -> "あと${daysUntilDue}日 · ${nextDueDate.format(DAY_OF_WEEK_FORMATTER)}"
    }

    val timingColor = when {
        isSomeday -> colors.textSecondary
        isSkippedToday -> colors.textSecondary
        isOverdue -> Color(0xFFEF4444)
        isDueToday -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    val cycleText = if (isSomeday) {
        "Someday"
    } else {
        if (interval % 7 == 0) "${interval / 7}週間ごと" else "${interval}日ごと"
    }

    val accentColor = template.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: statusColor

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, if (isOverdue) statusColor.copy(alpha = 0.45f) else colors.border),
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
                        Text(
                            text = "🔥$streak",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timingText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = timingColor
                    )
                    Text(
                        text = "·",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = cycleText,
                        fontSize = 10.5.sp,
                        color = colors.textSecondary
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

            // Circular Checkbox
            val checkBorderColor = when {
                isChecked -> Color.Transparent
                isOverdue -> Color(0xFFEF4444)
                isDueToday -> Color(0xFFF59E0B)
                else -> colors.border
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isChecked) Color(0xFF10B981) else Color.Transparent)
                    .border(
                        width = if (isChecked) 0.dp else 2.dp,
                        color = checkBorderColor,
                        shape = CircleShape
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleToday()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = if (isDoneToday) "本日完了" else "今期達成済",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 実施周期セレクター（ステッパー ＋ プリセットピル ＋ 自然言語プレビュー）
 * 日付未定 (Someday) の設定にも完全対応
 */
@Composable
fun PeriodicIntervalSelector(
    intervalDays: Int?,
    onIntervalChange: (Int?) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    val presets = listOf<Pair<Int?, String>>(
        Pair(null, "日付未定 (Someday)"),
        Pair(1, "1日 (毎日)"),
        Pair(2, "2日"),
        Pair(3, "3日"),
        Pair(7, "7日 (毎週)"),
        Pair(14, "14日 (隔週)"),
        Pair(30, "30日 (毎月)")
    )

    val isSomeday = intervalDays == null || intervalDays <= 0
    val explanation = when {
        isSomeday -> "周期を設定せず、空いた時間や気が向いた時にやるタスク（Someday）として管理します"
        intervalDays == 1 -> "毎日（1日ごと）に推奨されます"
        intervalDays == 7 -> "毎週（前回完了から7日後）に自動で今日タブに推奨・浮上します"
        intervalDays == 14 -> "隔週（前回完了から14日後）に自動で今日タブに推奨・浮上します"
        intervalDays == 30 -> "毎月（前回完了から30日後）に自動で今日タブに推奨・浮上します"
        else -> "前回完了から【${intervalDays}日後】に自動で今日タブに推奨・浮上します"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "実施周期:",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Center Stepper Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.background)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (intervalDays != null && intervalDays > 1) {
                        onIntervalChange(intervalDays - 1)
                    } else {
                        onIntervalChange(null)
                    }
                },
                enabled = !isSomeday,
                modifier = Modifier.size(36.dp)
            ) {
                Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isSomeday) "日付未定 (Someday)" else "${intervalDays} 日ごと",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = if (isSomeday) "いつでも取り組める" else if (intervalDays!! % 7 == 0) "${intervalDays / 7}週間ごと" else "サイクル: ${intervalDays}日間",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isSomeday) {
                        onIntervalChange(1)
                    } else if (intervalDays != null && intervalDays < 365) {
                        onIntervalChange(intervalDays + 1)
                    }
                },
                enabled = isSomeday || (intervalDays != null && intervalDays < 365),
                modifier = Modifier.size(36.dp)
            ) {
                Text("＋", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Preset Chips (Horizontal Scroll)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { (days, label) ->
                val isSelected = if (days == null) isSomeday else intervalDays == days
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.primary else colors.card)
                        .border(
                            1.dp,
                            if (isSelected) colors.primary else colors.border,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onIntervalChange(days)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.onPrimary else colors.textPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Natural Language Explanation Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.primary.copy(alpha = 0.08f))
                .border(0.5.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "💡 $explanation",
                fontSize = 11.sp,
                color = colors.primary,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * 周期タスクの詳細・編集・過去実績カレンダー BottomSheet
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
    var iconKey by remember { mutableStateOf(template.iconKey ?: "🧹") }
    var selectedColorHex by remember { mutableStateOf(template.colorHex ?: "#10B981") }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                }
                Text(
                    text = "周期タスクの編集・履歴",
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
                            colorHex = selectedColorHex
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

            Spacer(modifier = Modifier.height(16.dp))

            // タスク名
            Text("タスク名", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 周期設定セレクター
            PeriodicIntervalSelector(
                intervalDays = intervalDays,
                onIntervalChange = { intervalDays = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // アイコン
            Text("アイコン", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
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
                            .size(36.dp)
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
                        Text(text = ic, fontSize = 17.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // カラー
            Text("テーマカラー", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
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

            Spacer(modifier = Modifier.height(20.dp))

            // === 過去の達成記録・月間カレンダー ===
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                            fontSize = 15.sp,
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

                    Spacer(modifier = Modifier.height(8.dp))

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

                    // 日付グリッド (1 to lengthOfMonth)
                    val firstDay = currentMonth.atDay(1)
                    val startOffset = firstDay.dayOfWeek.value - 1 // 0 (Mon) to 6 (Sun)
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 押し忘れた過去の日付をタップして、いつでも達成記録を追加・解除できます",
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 削除ボタン
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
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("周期タスクの削除", fontWeight = FontWeight.Bold) },
            text = { Text("「${template.title}」を削除しますか？\n過去に記録されたログはそのまま残りますが、周期一覧からは削除されます。") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete(template)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusOverdue)
                ) {
                    Text("削除する", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
fun AddPeriodicTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, intervalDays: Int?, iconKey: String, colorHex: String) -> Unit
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf("") }
    var intervalDays by remember { mutableStateOf<Int?>(7) }
    var iconKey by remember { mutableStateOf("🧹") }
    var selectedColorHex by remember { mutableStateOf("#8C5A3C") }
    val iconOptions = DEFAULT_ICON_OPTIONS
    val colorOptions = DEFAULT_COLOR_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = "新しい周期タスクを追加",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "アイコンを選択:",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
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

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "カラー:",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
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
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タスク名 (例: エアコン清掃)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                PeriodicIntervalSelector(
                    intervalDays = intervalDays,
                    onIntervalChange = { intervalDays = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(
                            title.trim(),
                            if (intervalDays != null && intervalDays!! > 0) intervalDays else null,
                            iconKey,
                            selectedColorHex
                        )
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
