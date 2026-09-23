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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Calculates the current streak for a periodic template.
 * A "streak" is the number of consecutive intervals completed on time.
 */
fun calculateStreak(allItems: List<TimelineItemEntity>, template: TemplateEntity, zone: ZoneId): Int {
    val interval = template.intervalDays ?: 7
    val completionDates = allItems
        .filter { it.isDone && (it.templateId == template.id || it.title.startsWith(template.title)) && it.completedAt != null }
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
        if (item.templateId != template.id && !item.title.startsWith(template.title)) return@count false
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

    // Sort periodic tasks: Overdue first, then Due Soon, then On Track
    val periodicTemplates = remember(templates, today) {
        templates.filter { it.type == "INTERVAL" }.sortedByDescending { template ->
            val lastDoneDate = template.lastCompletedAt?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
            }
            val elapsed = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today) else 999L
            val interval = template.intervalDays ?: 7
            elapsed - interval
        }
    }

    // 7 days of current week (Monday to Sunday)
    val currentWeekDays = remember(today) {
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        (0..6).map { monday.plusDays(it.toLong()) }
    }

    // This week's completed periodic tasks count
    val thisWeekCompletedCount = remember(allItems, currentWeekDays, templates) {
        val weekStart = currentWeekDays.first()
        val weekEnd = currentWeekDays.last()
        allItems.count { item ->
            if (!item.isDone || item.completedAt == null || item.templateId == null) return@count false
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(item.completedAt), zone).toLocalDate()
            val t = templates.find { it.id == item.templateId }
            t?.type == "INTERVAL" && !date.isBefore(weekStart) && !date.isAfter(weekEnd)
        }
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
                title = "周期・ルーティン"
            )

            // Weekly achievement banner
            if (thisWeekCompletedCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.statusDone.copy(alpha = 0.10f))
                        .border(1.dp, colors.statusDone.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎯", fontSize = 16.sp)
                    Text(
                        text = "今週の完了: $thisWeekCompletedCount 件",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.statusDone
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "よく頑張っています！",
                        fontSize = 11.sp,
                        color = colors.statusDone.copy(alpha = 0.7f)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (periodicTemplates.isEmpty()) {
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
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                } else {
                    items(periodicTemplates, key = { it.id }) { template ->
                        val completedDates = remember(allItems, template.id) {
                            allItems.filter {
                                it.isDone &&
                                (it.templateId == template.id || it.title.startsWith(template.title)) &&
                                it.completedAt != null
                            }.map {
                                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt!!), zone).toLocalDate()
                            }.toSet()
                        }

                        val streak = remember(allItems, template.id) {
                            calculateStreak(allItems, template, zone)
                        }

                        val completionRate = remember(allItems, template.id) {
                            calculateCompletionRate(allItems, template, zone)
                        }

                        CompactUnifiedCycleTaskCard(
                            template = template,
                            today = today,
                            weekDays = currentWeekDays,
                            completedDates = completedDates,
                            streak = streak,
                            completionRate = completionRate,
                            onCompletedNow = {
                                viewModel.recordCycleTask(template, today)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("「${template.title}」の完了を記録しました")
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
}

/**
 * 1行超コンパクト周期カード (高さ ~60dp) — 商用レベル拡張版
 * ストリーク表示 + 達成率インジケーター追加
 */
@Composable
fun CompactUnifiedCycleTaskCard(
    template: TemplateEntity,
    today: LocalDate,
    weekDays: List<LocalDate>,
    completedDates: Set<LocalDate>,
    streak: Int = 0,
    completionRate: Float = 0f,
    onCompletedNow: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()

    val lastDoneDate = template.lastCompletedAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
    }
    val elapsedDays = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today).toInt() else null
    val interval = template.intervalDays ?: 7
    val isOverdue = elapsedDays != null && elapsedDays >= interval
    val isDueToday = elapsedDays != null && elapsedDays == (interval - 1)
    val isLongTerm = interval > 14

    val nextTargetDate = lastDoneDate?.plusDays(interval.toLong())

    val statusBadgeColor = when {
        isOverdue -> colors.statusOverdue
        isDueToday -> colors.statusWarning
        lastDoneDate != null -> colors.statusDone
        else -> colors.textSecondary
    }

    val statusSubtitle = when {
        isOverdue -> "${elapsedDays!! - interval}日超過"
        isDueToday -> "今日予定"
        lastDoneDate != null && elapsedDays != null -> "前回 ${lastDoneDate.monthValue}/${lastDoneDate.dayOfMonth}"
        else -> "未着手"
    }

    val accentColor = template.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: statusBadgeColor

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, if (isOverdue) statusBadgeColor.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Icon (22sp)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = template.iconKey ?: "🧹",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 2. Title & Status (Left Column)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = template.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusBadgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${interval}日",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusBadgeColor
                            )
                        }
                        // Streak badge
                        if (streak >= 2) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "🔥${streak}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                    }
                    Text(
                        text = statusSubtitle,
                        fontSize = 10.sp,
                        color = if (isOverdue) statusBadgeColor else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 3. Center: 7-day mini indicator OR "··· 次回 M/D" (for long term)
                if (isLongTerm) {
                    val nextStr = if (nextTargetDate != null) "${nextTargetDate.monthValue}/${nextTargetDate.dayOfMonth}" else "未定"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.background.copy(alpha = 0.8f))
                            .border(0.5.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "··· 次回 $nextStr",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOverdue) colors.statusOverdue else colors.textSecondary
                        )
                    }
                } else {
                    // Inline 7-day compact dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dayInitial = listOf("月", "火", "水", "木", "金", "土", "日")
                        weekDays.forEachIndexed { idx, date ->
                            val isDone = completedDates.contains(date) || (lastDoneDate == date)
                            val isCurDay = (date == today)
                            val isFuture = date.isAfter(today)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = dayInitial[idx],
                                    fontSize = 8.sp,
                                    color = if (isCurDay) colors.primary else colors.textSecondary.copy(alpha = 0.7f),
                                    fontWeight = if (isCurDay) FontWeight.Bold else FontWeight.Normal
                                )
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isDone -> Color(0xFF16A34A)
                                                isCurDay -> colors.primary.copy(alpha = 0.2f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isCurDay || isDone) 1.dp else 0.5.dp,
                                            color = when {
                                                isDone -> Color(0xFF16A34A)
                                                isCurDay -> colors.primary
                                                isFuture -> colors.border.copy(alpha = 0.3f)
                                                else -> colors.border
                                            },
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 4. Actions: Skip [↷] & Complete [✓]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Skip Button
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "↷",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary
                        )
                    }

                    // Complete Button
                    FilledTonalButton(
                        onClick = onCompletedNow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = accentColor.copy(alpha = 0.15f),
                            contentColor = accentColor
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "✓",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Completion rate bar (thin, at bottom of card)
            if (completionRate > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(colors.border.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(completionRate)
                            .fillMaxHeight()
                            .background(
                                when {
                                    completionRate >= 0.8f -> colors.statusDone
                                    completionRate >= 0.5f -> colors.statusWarning
                                    else -> colors.statusOverdue.copy(alpha = 0.6f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun AddPeriodicTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, intervalDays: Int, iconKey: String, colorHex: String) -> Unit
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf("7") }
    var iconKey by remember { mutableStateOf("🧹") }
    var selectedColorHex by remember { mutableStateOf("#8C5A3C") }

    val iconOptions = listOf(
        "🧹", "🧖", "🛏️", "🧼", "🌀", "💨", "🌿", "💊", "🚗", "🪴",
        "🚿", "🧺", "🏃", "🐶", "📚", "💪", "🍱", "🪥", "🌙", "☀️",
        "🌊", "🧘", "🎵", "🏠"
    )

    val colorOptions = listOf(
        Pair("#10B981", "グリーン"),
        Pair("#3B82F6", "ブルー"),
        Pair("#A855F7", "パープル"),
        Pair("#F59E0B", "オレンジ"),
        Pair("#EF4444", "レッド"),
        Pair("#8C5A3C", "ブラウン"),
        Pair("#EC4899", "ピンク"),
        Pair("#6366F1", "インディゴ")
    )

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
            Column {
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

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) intervalText = it },
                    label = { Text("実施周期の日数 (例: 7, 14, 30)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick presets for interval
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("3日" to 3, "7日" to 7, "14日" to 14, "30日" to 30).forEach { (label, days) ->
                        val isSelected = intervalText == days.toString()
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                                .clickable { intervalText = days.toString() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else colors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = intervalText.toIntOrNull() ?: 7
                    if (title.isNotBlank()) {
                        onAdd(title, days, iconKey, selectedColorHex)
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
