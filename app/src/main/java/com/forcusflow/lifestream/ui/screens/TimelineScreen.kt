package com.forcusflow.lifestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AddItemBottomSheet
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class TimelineEntry {
    data class Item(val entity: TimelineItemEntity) : TimelineEntry()
    data class NowLine(val timeStr: String) : TimelineEntry()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val templates by viewModel.templates.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val anytimePending by viewModel.anytimePendingItems.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val nowTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

    val today = LocalDate.now()
    val (todayStart, todayEnd) = remember(today) { viewModel.getDayRange(today) }

    // Collect today's items sorted chronologically
    val todayItems = remember(allItems, todayStart, todayEnd) {
        allItems.filter { item ->
            val t = item.completedAt ?: item.scheduledAt
            t != null && t in todayStart..todayEnd
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    // Build interleaved entries with NOW line
    val timelineEntries = remember(todayItems, nowMillis, nowTimeStr) {
        val list = mutableListOf<TimelineEntry>()
        var nowInserted = false

        todayItems.forEach { item ->
            val itemTime = item.completedAt ?: item.scheduledAt ?: 0L
            if (!nowInserted && itemTime > nowMillis) {
                list.add(TimelineEntry.NowLine(nowTimeStr))
                nowInserted = true
            }
            list.add(TimelineEntry.Item(item))
        }

        if (!nowInserted) {
            list.add(TimelineEntry.NowLine(nowTimeStr))
        }

        list
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "記録・ToDo追加", modifier = Modifier.size(28.dp))
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
                title = "今日のタイムライン",
                subtitle = "やったこと事実ログ ＋ これからToDo ＋ 周期推奨"
            )

            // Anytime ToDo Carousel (Top card)
            if (anytimePending.isNotEmpty()) {
                val anytimeItem = anytimePending.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .clickable { viewModel.toggleItemDone(anytimeItem) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .border(1.5.dp, colors.primary, RoundedCornerShape(5.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (anytimeItem.isDone) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.statusDone,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = anytimeItem.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                textDecoration = if (anytimeItem.isDone) TextDecoration.LineThrough else null
                            )
                            Text(
                                text = "時間指定なしToDo（タップで完了）",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Quick Record Bar (1-tap fact logging)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickConfigs = listOf(
                    Triple("💧 水", "水", Color(0xFF0284C7)),
                    Triple("🧖 パック", "パック", Color(0xFF8B5CF6)),
                    Triple("🍜 夜食", "夜食", Color(0xFFEF4444)),
                    Triple("🧹 排水口", "排水", Color(0xFF8C5A3C))
                )

                quickConfigs.forEach { (label, keyword, chipColor) ->
                    val t = templates.find { it.title.contains(keyword) }
                    val displayLabel = if (keyword == "水" && t != null) {
                        "💧 水 (杯)"
                    } else label

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, chipColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .background(chipColor.copy(alpha = 0.08f))
                            .clickable {
                                if (t != null) {
                                    viewModel.quickRecordTemplate(t) { savedItem ->
                                        coroutineScope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "${savedItem.title} を記録しました",
                                                actionLabel = "元に戻す",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoLastItem()
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = displayLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = chipColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Taskito-style Vertical Continuous Timeline
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(timelineEntries) { index, entry ->
                    val isFirst = index == 0
                    val isLast = index == timelineEntries.size - 1

                    when (entry) {
                        is TimelineEntry.NowLine -> {
                            TaskitoNowLineRow(
                                timeStr = entry.timeStr,
                                isFirst = isFirst,
                                isLast = isLast
                            )
                        }
                        is TimelineEntry.Item -> {
                            val item = entry.entity
                            TaskitoTimelineItemRow(
                                item = item,
                                isFirst = isFirst,
                                isLast = isLast,
                                onToggle = { viewModel.toggleItemDone(item) },
                                onDelete = {
                                    viewModel.deleteItem(item)
                                    coroutineScope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = " を削除しました",
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
                }
            }
        }
    }

    if (showAddSheet) {
        AddItemBottomSheet(
            templates = templates,
            onDismiss = { showAddSheet = false },
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId ->
                viewModel.addTimelineItem(title, isDone, scheduledAt, completedAt, amount, note, templateId)
            }
        )
    }
}

@Composable
fun TaskitoNowLineRow(
    timeStr: String,
    isFirst: Boolean,
    isLast: Boolean
) {
    val colors = LifeStreamTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical track with continuous stem line
        Box(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Continuous stem line
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
            // Center red dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.nowLine)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Red NOW Line with Pill Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.nowLine)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "現在 $timeStr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.nowLine)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoTimelineItemRow(
    item: TimelineItemEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    val isPeriodic = item.templateId != null && item.templateId in listOf(2L, 4L, 5L, 6L, 7L, 8L)

    val nodeColor = when {
        item.isDone -> colors.statusDone
        isPeriodic && item.templateId == 4L -> colors.statusOverdue // 排水ネット交換
        isPeriodic && item.templateId == 2L -> colors.statusTarget  // フェイスパック
        else -> colors.card
    }
    val nodeBorderColor = when {
        item.isDone -> colors.statusDone
        isPeriodic && item.templateId == 4L -> colors.statusOverdue
        isPeriodic && item.templateId == 2L -> colors.statusTarget
        else -> colors.textSecondary
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFEF4444).copy(alpha = 0.8f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("削除", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.background)
                .clickable { onToggle() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Taskito Continuous Stem Column
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // Vertical Continuous Line
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

                // Node Circle (Interactive)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(2.dp, nodeBorderColor, CircleShape)
                        .background(nodeColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Time & Details
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
                    color = colors.textPrimary,
                    textDecoration = if (item.isDone && item.scheduledAt != null) TextDecoration.LineThrough else null
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

            Spacer(modifier = Modifier.width(8.dp))

            // Status Badges
            when {
                item.amount != null -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                            .background(colors.card)
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
                item.templateId == 1L -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.waterBlue, RoundedCornerShape(8.dp))
                            .background(colors.waterBlue.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "習慣",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.waterBlue
                        )
                    }
                }
                item.templateId == 4L && !item.isDone -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.statusOverdue, RoundedCornerShape(8.dp))
                            .background(colors.statusOverdue.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "7日周期",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.statusOverdue
                        )
                    }
                }
                item.templateId == 2L && !item.isDone -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.statusTarget, RoundedCornerShape(8.dp))
                            .background(colors.statusTarget.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "3日周期",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.statusTarget
                        )
                    }
                }
                item.isDone -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.statusDone, RoundedCornerShape(8.dp))
                            .background(colors.statusDone.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "完了",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.statusDone
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .background(colors.card)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "予定",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}
