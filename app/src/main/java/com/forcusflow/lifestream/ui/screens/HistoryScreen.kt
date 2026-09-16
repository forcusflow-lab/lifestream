package com.forcusflow.lifestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import java.time.DayOfWeek
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

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val selectedDate by viewModel.selectedCalendarDate.collectAsState()

    val currentMonthYear = remember(selectedDate) {
        "年 月"
    }

    // Determine days that have logged items
    val activeDays = remember(allItems) {
        allItems.mapNotNull { item ->
            val timestamp = item.completedAt ?: item.scheduledAt
            timestamp?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
            }
        }.toSet()
    }

    // Items for selected date
    val selectedDateItems = remember(allItems, selectedDate) {
        val (start, end) = viewModel.getDayRange(selectedDate)
        allItems.filter { item ->
            val timestamp = item.completedAt ?: item.scheduledAt
            timestamp != null && timestamp in start..end
        }.sortedByDescending { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    val totalAmount = remember(selectedDateItems) {
        selectedDateItems.mapNotNull { it.amount }.sum()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AppHeader(
            title = "2. 履歴とカレンダー",
            subtitle = "日別タイムライン再現 + 事実ログ集計検索"
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
                items(searchResults, key = { it.id }) { item ->
                    HistoryItemCard(item = item)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // Calendar Card
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
                            // Month row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentMonthYear,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "< 今月 >",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Days of week header
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

                            Spacer(modifier = Modifier.height(10.dp))

                            // Grid of 3 weeks (matching image dates 01 to 21 for Sep 2026)
                            val dayNumbers = (1..21).toList()
                            val rows = dayNumbers.chunked(7)

                            rows.forEach { week ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    week.forEach { dayNum ->
                                        val dayDate = LocalDate.of(2026, 9, dayNum)
                                        val isSelected = selectedDate == dayDate
                                        val isToday = dayNum == 15
                                        val hasLogs = activeDays.contains(dayDate)

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) colors.primary else Color.Transparent
                                                )
                                                .border(
                                                    if (isToday && !isSelected) 1.dp else 0.dp,
                                                    if (isToday && !isSelected) colors.primary else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.selectedCalendarDate.value = dayDate }
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = String.format("%02d", dayNum),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
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
                                    }
                                    // Fill empty slots if last week has fewer than 7
                                    for (i in 0 until (7 - week.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                            text = "選択した日の全記録: ${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 ($dayOfWeekStr)",
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

                // List of items for selected date
                items(selectedDateItems, key = { it.id }) { item ->
                    HistoryItemCard(item = item)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: TimelineItemEntity) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .background(colors.card)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary
                )
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (!item.note.isNullOrBlank()) {
                    Text(
                        text = item.note,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Badge
            when {
                item.amount != null -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "¥",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )
                    }
                }
                item.templateId in listOf(2L, 4L, 5L, 6L, 7L, 8L) -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.statusTarget, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "周期済",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.statusTarget
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, colors.waterBlue, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "完了",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.waterBlue
                        )
                    }
                }
            }
        }
    }
}
