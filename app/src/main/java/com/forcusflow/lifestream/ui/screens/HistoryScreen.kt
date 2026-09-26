package com.forcusflow.lifestream.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.components.ItemDetailBottomSheet
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val selectedDate by viewModel.selectedCalendarDate.collectAsState()
    val currentYearMonth by viewModel.calendarYearMonth.collectAsState()

    var itemToEdit by remember { mutableStateOf<TimelineItemEntity?>(null) }
    var isMonthlySummaryExpanded by remember { mutableStateOf(false) }

    val currentMonthYearText = "${currentYearMonth.year}年 ${currentYearMonth.monthValue}月"


    // Items for selected date sorted chronologically
    val selectedDateItems = remember(allItems, selectedDate, viewModel.dayCutoffHour.collectAsState().value) {
        val (start, end) = viewModel.getDayRange(selectedDate)
        allItems.filter { item ->
            val timestamp = item.completedAt ?: item.scheduledAt
            timestamp != null && timestamp in start..end
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    val totalAmount = remember(selectedDateItems) {
        selectedDateItems.mapNotNull { it.amount }.sum()
    }

    val monthStart = remember(currentYearMonth) { currentYearMonth.atDay(1) }
    val monthEnd = remember(currentYearMonth) { currentYearMonth.atEndOfMonth() }
    val thisMonthItems = remember(allItems, monthStart, monthEnd) {
        allItems.filter { item ->
            val t = item.completedAt ?: item.scheduledAt ?: return@filter false
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone).toLocalDate()
            !date.isBefore(monthStart) && !date.isAfter(monthEnd)
        }
    }
    val habitMonthlyStats = remember(thisMonthItems, templates) {
        val doneItems = thisMonthItems.filter { it.isDone }
        val grouped = doneItems.groupBy { item ->
            templates.find { it.id == item.templateId } ?: templates.find { it.title == item.title }
        }
        grouped.entries.mapNotNull { (template, items) ->
            val title = template?.title ?: items.first().title
            val icon = template?.iconKey ?: "📌"
            val colorHex = template?.colorHex
            val actionType = template?.actionType ?: "CHECK"
            val unit = template?.unit?.ifBlank { null } ?: when (actionType) {
                "COUNT" -> "杯"
                "TIMER" -> "分"
                else -> "回"
            }
            val countValSum = items.mapNotNull { it.countValue }.sum()
            // If legacy data had cumulative sequence (1+2+3...), sanitize so totalCount is items.size
            val totalCount = if (countValSum > items.size * 5 && items.size > 2) {
                items.size
            } else if (countValSum > 0) {
                countValSum
            } else {
                items.size
            }
            val durationSecSum = items.mapNotNull { it.durationSeconds }.sum()

            val achievementText = when (actionType) {
                "TIMER" -> {
                    val mins = if (durationSecSum > 0) durationSecSum / 60 else items.size * 15
                    if (mins >= 60) "%.1f時間 (%d回)".format(mins / 60.0, items.size)
                    else "${mins}分 (${items.size}回)"
                }
                "COUNT" -> {
                    if (totalCount == items.size) {
                        "$totalCount $unit"
                    } else {
                        "$totalCount $unit (${items.size}回)"
                    }
                }
                else -> {
                    "${items.size} 回"
                }
            }

            HabitMonthlyStatItem(
                title = title,
                icon = icon,
                colorHex = colorHex,
                achievementText = achievementText,
                totalLogs = items.size
            )
        }.sortedByDescending { it.totalLogs }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            AppHeader(
                title = "履歴とカレンダー",
                subtitle = "日別タイムライン再現 ＋ 記録・予定の確認"
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("記録を検索 (例: カップ麺, パック, ランチ)", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "検索", tint = colors.textSecondary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
            ) {
                if (searchQuery.isNotBlank()) {
                    // Search results view
                    item {
                        val searchTotalAmt = searchResults.mapNotNull { it.amount }.sum()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "検索結果: 「$searchQuery」",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "計 ${searchResults.size}件 (¥$searchTotalAmt)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                    }
                    itemsIndexed(searchResults, key = { _, item -> item.id }) { index, item ->
                        TaskitoHistoryStemRow(
                            item = item,
                            isFirst = index == 0,
                            isLast = index == searchResults.size - 1,
                            onClick = { itemToEdit = item }
                        )
                    }
                } else {
                    // Minimalist Monthly Summary Bar
                    item {
                        val thisMonthDoneCount = thisMonthItems.count { it.isDone }
                        val thisMonthSpend = thisMonthItems.mapNotNull { it.amount }.sum()
                        val thisMonthFocusSeconds = thisMonthItems.mapNotNull { it.durationSeconds }.sum()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 完了記録
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.card),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "$thisMonthDoneCount 件",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.statusDone
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("完了記録", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }

                            // 集中・作業時間
                            val focusHours = thisMonthFocusSeconds / 3600.0
                            val focusText = when {
                                thisMonthFocusSeconds == 0 -> "0分"
                                focusHours >= 1.0 -> "%.1f時間".format(focusHours)
                                else -> "${thisMonthFocusSeconds / 60}分"
                            }
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.card),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = focusText,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("集中・作業", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }

                            // 今月の支出
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.card),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "¥%,d".format(thisMonthSpend),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.statusTarget
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("今月の支出", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }
                        }

                        // Dynamic Calendar Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .background(colors.card)
                                .padding(16.dp)
                        ) {
                            Column {
                                // Month Header with Prev/Next Controls & Today Jump
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.previousMonth()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = "前月", tint = colors.textPrimary)
                                        }
                                        Text(
                                            text = currentMonthYearText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.nextMonth()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "次月", tint = colors.textPrimary)
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.goToToday()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "今月・今日",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Days of week header (Mon - Sun)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val weekDays = listOf(
                                        Pair("月", colors.textSecondary),
                                        Pair("火", colors.textSecondary),
                                        Pair("水", colors.textSecondary),
                                        Pair("木", colors.textSecondary),
                                        Pair("金", colors.textSecondary),
                                        Pair("土", Color(0xFF0284C7)),
                                        Pair("日", Color(0xFFEF4444))
                                    )
                                    weekDays.forEach { (label, tint) ->
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = tint
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Dynamic Grid calculation
                                val firstDayOfMonth = currentYearMonth.atDay(1)
                                val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 = Mon, 7 = Sun
                                val daysInMonth = currentYearMonth.lengthOfMonth()

                                val leadingBlanks = firstDayOfWeek - 1
                                val totalSlots = leadingBlanks + daysInMonth
                                val totalWeeks = (totalSlots + 6) / 7

                                // Count logs per day for heatmap
                                val logCountPerDay = remember(allItems) {
                                    val map = mutableMapOf<LocalDate, Int>()
                                    allItems.forEach { item ->
                                        val timestamp = item.completedAt ?: item.scheduledAt
                                        timestamp?.let {
                                            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
                                            map[date] = (map[date] ?: 0) + 1
                                        }
                                    }
                                    map
                                }

                                for (weekIndex in 0 until totalWeeks) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (dayOfWeekIndex in 0 until 7) {
                                            val slotIndex = weekIndex * 7 + dayOfWeekIndex
                                            val dayNumber = slotIndex - leadingBlanks + 1

                                            if (dayNumber in 1..daysInMonth) {
                                                val dayDate = currentYearMonth.atDay(dayNumber)
                                                val isSelected = selectedDate == dayDate
                                                val isCurrentToday = dayDate == today
                                                val logCount = logCountPerDay[dayDate] ?: 0

                                                // Heatmap intensity: 0=none, 1-2=light, 3-5=medium, 6+=strong
                                                val heatAlpha = when {
                                                    isSelected -> 0f
                                                    logCount == 0 -> 0f
                                                    logCount <= 2 -> 0.25f
                                                    logCount <= 5 -> 0.55f
                                                    else -> 0.85f
                                                }

                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            when {
                                                                isSelected -> colors.primary
                                                                logCount > 0 -> colors.statusDone.copy(alpha = heatAlpha)
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        .border(
                                                            if (isCurrentToday && !isSelected) 1.dp else 0.dp,
                                                            if (isCurrentToday && !isSelected) colors.primary else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            viewModel.selectedCalendarDate.value = dayDate
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "%02d".format(dayNumber),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected || isCurrentToday) FontWeight.Bold else FontWeight.Medium,
                                                        color = when {
                                                            isSelected -> colors.onPrimary
                                                            logCount >= 6 -> Color.White
                                                            else -> colors.textPrimary
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Count label for days with logs (compact)
                                                    if (logCount > 0 && !isSelected) {
                                                        Text(
                                                            text = if (logCount < 10) "$logCount" else "9+",
                                                            fontSize = 7.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (logCount >= 3) Color.White.copy(alpha = 0.9f) else colors.statusDone
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.size(8.dp))
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Heatmap legend
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("少", fontSize = 9.sp, color = colors.textSecondary)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    listOf(0.15f, 0.35f, 0.6f, 0.85f).forEach { alpha ->
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 1.dp)
                                                .size(9.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(colors.statusDone.copy(alpha = alpha))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("多", fontSize = 9.sp, color = colors.textSecondary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected date summary row
                        val dayOfWeekStr = selectedDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPANESE)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "選択した日のタイムライン: ${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ($dayOfWeekStr)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "計 ${selectedDateItems.size}件 (¥$totalAmount)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                    }



                    if (selectedDateItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.card)
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedDate.isAfter(today)) "この日の予定はありません" else "この日の記録はありません",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        // Unified Taskito-style continuous stem line
                        itemsIndexed(selectedDateItems, key = { _, item -> item.id }) { index, item ->
                            TaskitoHistoryStemRow(
                                item = item,
                                templates = templates,
                                isFirst = index == 0,
                                isLast = index == selectedDateItems.size - 1,
                                onClick = { itemToEdit = item },
                                onToggleDone = { viewModel.toggleItemDone(item) }
                            )
                        }
                    }

                    // 今月の達成サマリー（アコーディオン開閉制御）
                    if (habitMonthlyStats.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.card),
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isMonthlySummaryExpanded = !isMonthlySummaryExpanded
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("📊", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "今月の達成サマリー (${currentYearMonth.monthValue}月)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "計 ${habitMonthlyStats.size}種",
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = if (isMonthlySummaryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isMonthlySummaryExpanded) "閉じる" else "展開する",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (isMonthlySummaryExpanded) {
                            items(habitMonthlyStats, key = { it.title }) { stat ->
                                HabitMonthlyBreakdownRow(stat = stat)
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Item Sheet
    itemToEdit?.let { item ->
        ItemDetailBottomSheet(
            item = item,
            templates = templates,
            onDismiss = { itemToEdit = null },
            onSave = { updated ->
                viewModel.updateTimelineItem(updated)
                itemToEdit = null
            },
            onDelete = {
                viewModel.deleteItem(item)
                itemToEdit = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "${item.title} を削除しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.restoreItem(item)
                    }
                }
            },
            onPromoteToPeriodic = { targetItem, intervalDays, iconKey ->
                viewModel.promoteItemToPeriodicTemplate(
                    item = targetItem,
                    intervalDays = intervalDays,
                    iconKey = iconKey
                ) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("「${targetItem.title}」を周期タスクに追加しました（${intervalDays}日ごと）")
                    }
                }
                itemToEdit = null
            }
        )
    }
}

/**
 * Taskito-style unified vertical continuous stem row for HistoryScreen.
 * Matches TimelineScreen's layout perfectly.
 */
@Composable
fun TaskitoHistoryStemRow(
    item: TimelineItemEntity,
    templates: List<TemplateEntity> = emptyList(),
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onToggleDone: (() -> Unit)? = null
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    val isDone = item.isDone
    val nodeColor = if (isDone) colors.statusDone else colors.card
    val nodeBorderColor = if (isDone) colors.statusDone else colors.textSecondary

    val checkScale by animateFloatAsState(
        targetValue = if (isDone) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "historyCheckScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.background)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous Stem Column (36dp)
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isFirst) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isLast) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // Interactive Checkbox / Done Node with 48.dp touch target & spring bounce
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(enabled = onToggleDone != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleDone?.invoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .scale(checkScale)
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, nodeBorderColor, CircleShape)
                        .background(nodeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "完了",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time & Title & Notes Card (matching TimelineScreen's TaskitoTimelineItemRow)
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.card)
                .border(0.5.dp, colors.border, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isDone) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "予定",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))

                val cleanTitle = remember(item.title) {
                    item.title
                        .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                        .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                        .trim()
                }
                val durationSec = remember(item.durationSeconds, item.title) {
                    item.durationSeconds
                        ?: "\\((\\d+)分\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
                        ?: "\\((\\d+)秒\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                }
                val countVal = remember(item.countValue, item.title) {
                    item.countValue
                        ?: "\\((\\d+)[杯回個本皿枚]目?\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cleanTitle,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (durationSec != null && durationSec > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val durationText = if (durationSec >= 60) "${(durationSec + 30) / 60}分" else "${durationSec}秒"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⏱ $durationText",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }
                    if (countVal != null && countVal > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val matched = templates.find { it.id == item.templateId }
                        val unit = matched?.unit?.ifBlank { "杯" } ?: "杯"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$countVal$unit",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }
                }

                val displayNote = remember(item.note) {
                    when {
                        item.note.isNullOrBlank() -> null
                        item.note in listOf("クイック記録完了", "時間計測完了", "デイリー習慣カウント") -> null
                        item.note.startsWith("計測時間: ") -> null
                        item.note.startsWith("時間計測完了 (") && item.note.endsWith(")") -> null
                        else -> item.note
                    }
                }
                if (displayNote != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayNote,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
            }

            if (item.amount != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                        .background(colors.card)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "¥${item.amount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
        }
    }
}

data class HabitMonthlyStatItem(
    val title: String,
    val icon: String,
    val colorHex: String?,
    val achievementText: String,
    val totalLogs: Int
)

@Composable
fun HabitMonthlyBreakdownRow(stat: HabitMonthlyStatItem) {
    val colors = LifeStreamTheme.colors
    val accentColor = stat.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: colors.primary

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(stat.icon, fontSize = 17.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = stat.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.background)
                    .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stat.achievementText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}
