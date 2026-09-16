package com.forcusflow.lifestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AppHeader
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
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val selectedDate by viewModel.selectedCalendarDate.collectAsState()
    val currentYearMonth by viewModel.calendarYearMonth.collectAsState()

    var itemToEdit by remember { mutableStateOf<TimelineItemEntity?>(null) }

    val currentMonthYearText = "${currentYearMonth.year}年 ${currentYearMonth.monthValue}月"

    // Determine days that have logged items
    val activeDays = remember(allItems) {
        allItems.mapNotNull { item ->
            val timestamp = item.completedAt ?: item.scheduledAt
            timestamp?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
            }
        }.toSet()
    }

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
                subtitle = "日別タイムライン再現 ＋ 事実ログ検索"
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
                    // Dynamic Calendar Card
                    item {
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
                                            onClick = { viewModel.previousMonth() },
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
                                            onClick = { viewModel.nextMonth() },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronRight, contentDescription = "次月", tint = colors.textPrimary)
                                        }
                                    }

                                    TextButton(
                                        onClick = { viewModel.goToToday() },
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

                                for (weekIndex in 0 until totalWeeks) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (dayOfWeekIndex in 0 until 7) {
                                            val slotIndex = weekIndex * 7 + dayOfWeekIndex
                                            val dayNumber = slotIndex - leadingBlanks + 1

                                            if (dayNumber in 1..daysInMonth) {
                                                val dayDate = currentYearMonth.atDay(dayNumber)
                                                val isSelected = selectedDate == dayDate
                                                val isCurrentToday = dayDate == today
                                                val hasLogs = activeDays.contains(dayDate)

                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isSelected) colors.primary else Color.Transparent
                                                        )
                                                        .border(
                                                            if (isCurrentToday && !isSelected) 1.dp else 0.dp,
                                                            if (isCurrentToday && !isSelected) colors.primary else Color.Transparent,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { viewModel.selectedCalendarDate.value = dayDate }
                                                        .padding(vertical = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "%02d".format(dayNumber),
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected || isCurrentToday) FontWeight.Bold else FontWeight.Medium,
                                                        color = when {
                                                            isSelected -> colors.onPrimary
                                                            else -> colors.textPrimary
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Dot indicator
                                                    if (hasLogs) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) colors.onPrimary else colors.statusDone)
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.size(4.dp))
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
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
                                    text = "この日の記録はありません",
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
                                isFirst = index == 0,
                                isLast = index == selectedDateItems.size - 1,
                                onClick = { itemToEdit = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Item Dialog
    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
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
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.background)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous Stem Column (32dp)
        Box(
            modifier = Modifier
                .width(32.dp)
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

            // Green Done Node Circle with Check
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, colors.statusDone, CircleShape)
                    .background(colors.statusDone),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "完了",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time, Title & Notes
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = timeStr,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Text(
                text = item.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            if (!item.note.isNullOrBlank()) {
                Text(
                    text = item.note,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.textSecondary
                )
            }
        }

        // Amount Badge (if present)
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
