package com.forcusflow.lifestream.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AddItemBottomSheet
import com.forcusflow.lifestream.ui.components.ItemDetailBottomSheet
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

sealed class TimelineRowItem {
    data class LogItem(val entity: TimelineItemEntity) : TimelineRowItem()
    data class NowMarker(val timeStr: String) : TimelineRowItem()
    data class AnytimeToDo(val entity: TimelineItemEntity) : TimelineRowItem()
    data class PeriodicSurfaced(val template: TemplateEntity, val isOverdue: Boolean, val elapsedDays: Long) : TimelineRowItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val templates by viewModel.templates.collectAsState()
    val pinnedTemplates by viewModel.pinnedTemplates.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val anytimePending by viewModel.anytimePendingItems.collectAsState()
    val activeTimerTemplate by viewModel.activeTimerTemplate.collectAsState()
    val timerSeconds by viewModel.timerElapsedSeconds.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showAddSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TimelineItemEntity?>(null) }
    var showTemplateManagerDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            currentTime = LocalDateTime.now()
        }
    }
    val nowMillis = currentTime.atZone(zone).toInstant().toEpochMilli()
    val nowTimeStr = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    val today = LocalDate.now()
    val todayDateFormatted = remember(today) {
        today.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
    }

    val (todayStart, todayEnd) = remember(today, viewModel.dayCutoffHour.collectAsState().value) {
        viewModel.getDayRange(today)
    }

    // Today's scheduled or completed items
    val todayItems = remember(allItems, todayStart, todayEnd) {
        allItems.filter { item ->
            val t = item.completedAt ?: item.scheduledAt
            t != null && t in todayStart..todayEnd
        }.sortedBy { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    // Periodic tasks due today or overdue
    val dueOrOverduePeriodic = remember(templates, today) {
        templates.filter { it.type == "INTERVAL" }.mapNotNull { tmpl ->
            val lastDoneDate = tmpl.lastCompletedAt?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
            }
            val elapsed = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today) else 999L
            val interval = tmpl.intervalDays ?: 7
            if (elapsed >= (interval - 1)) {
                Triple(tmpl, elapsed >= interval, elapsed)
            } else null
        }
    }

    // Build timeline entries with NOW line, Anytime ToDos, and Surfaced periodic tasks
    val timelineRowItems = remember(todayItems, nowMillis, nowTimeStr, anytimePending, dueOrOverduePeriodic) {
        val list = mutableListOf<TimelineRowItem>()
        var nowInserted = false

        todayItems.forEach { item ->
            val itemTime = item.completedAt ?: item.scheduledAt ?: 0L
            if (!nowInserted && itemTime > nowMillis) {
                list.add(TimelineRowItem.NowMarker(nowTimeStr))
                // Anytime ToDos placed directly below NOW line
                anytimePending.forEach {
                    list.add(TimelineRowItem.AnytimeToDo(it))
                }
                // Periodic tasks surfaced right below NOW line
                dueOrOverduePeriodic.forEach { (tmpl, isOverdue, elapsed) ->
                    list.add(TimelineRowItem.PeriodicSurfaced(tmpl, isOverdue, elapsed))
                }
                nowInserted = true
            }
            list.add(TimelineRowItem.LogItem(item))
        }

        if (!nowInserted) {
            list.add(TimelineRowItem.NowMarker(nowTimeStr))
            anytimePending.forEach {
                list.add(TimelineRowItem.AnytimeToDo(it))
            }
            dueOrOverduePeriodic.forEach { (tmpl, isOverdue, elapsed) ->
                list.add(TimelineRowItem.PeriodicSurfaced(tmpl, isOverdue, elapsed))
            }
        }

        list
    }

    val listState = rememberLazyListState()
    var hasScrolledToNow by remember { mutableStateOf(false) }

    LaunchedEffect(timelineRowItems.size) {
        if (!hasScrolledToNow && timelineRowItems.isNotEmpty()) {
            val nowIdx = timelineRowItems.indexOfFirst { it is TimelineRowItem.NowMarker }
            if (nowIdx >= 0) {
                listState.scrollToItem((nowIdx - 1).coerceAtLeast(0))
                hasScrolledToNow = true
            }
        }
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
            // Pure Minimal & Elegant Header: Date + Search Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = todayDateFormatted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showSearchSheet = true
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "検索",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }


            // Quick Record Bar (ActionTypes: COUNT, TIMER, CHECK)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pinnedTemplates.forEach { t ->
                    val isTimer = t.actionType == "TIMER"
                    val isTimerActive = isTimer && activeTimerTemplate?.id == t.id
                    val isCount = t.actionType == "COUNT"

                    val chipLabel = when {
                        isTimerActive -> {
                            val mins = timerSeconds / 60
                            val secs = timerSeconds % 60
                            "${t.iconKey ?: "⏱️"} %02d:%02d 計測中".format(mins, secs)
                        }
                        isTimer -> "${t.iconKey ?: "⏱️"} ${t.title}"
                        isCount -> "${t.iconKey ?: "💧"} ${t.title}"
                        else -> "${t.iconKey ?: "📌"} ${t.title}"
                    }

                    val chipBorderColor = when {
                        isTimerActive -> Color(0xFFEF4444)
                        isCount -> Color(0xFF0284C7)
                        t.colorHex != null -> Color(android.graphics.Color.parseColor(t.colorHex))
                        else -> colors.primary
                    }

                    val chipBgColor = when {
                        isTimerActive -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        isCount -> Color(0xFF0284C7).copy(alpha = 0.08f)
                        else -> chipBorderColor.copy(alpha = 0.08f)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isTimerActive) 1.5.dp else 1.dp,
                                color = chipBorderColor.copy(alpha = if (isTimerActive) 1f else 0.6f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .background(chipBgColor)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = chipLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = chipBorderColor
                        )
                    }
                }

                // "+ 管理" Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .background(colors.card)
                        .clickable { showTemplateManagerDialog = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "管理",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Taskito-style Vertical Continuous Timeline
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                if (timelineRowItems.isEmpty()) {
                    item {
                        TaskitoNowLineRow(
                            timeStr = nowTimeStr,
                            isFirst = true,
                            isLast = true
                        )
                    }
                } else {
                    itemsIndexed(timelineRowItems) { index, rowItem ->
                        val isFirst = index == 0
                        val isLast = index == timelineRowItems.size - 1

                        when (rowItem) {
                            is TimelineRowItem.NowMarker -> {
                                TaskitoNowLineRow(
                                    timeStr = rowItem.timeStr,
                                    isFirst = isFirst,
                                    isLast = isLast
                                )
                                if (todayItems.isEmpty() && anytimePending.isEmpty() && dueOrOverduePeriodic.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 12.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.card)
                                            .border(0.5.dp, colors.border, RoundedCornerShape(14.dp))
                                            .padding(18.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "🌿 清々しい1日のはじまり",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "今日の最初の行動をワンタップで記録しましょう",
                                                fontSize = 12.sp,
                                                color = colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState())
                                            ) {
                                                pinnedTemplates.take(4).forEach { t ->
                                                    OutlinedButton(
                                                        onClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            viewModel.quickRecordTemplate(t) { saved ->
                                                                coroutineScope.launch {
                                                                    snackbarHostState.showSnackbar("「${saved.title}」を記録しました！")
                                                                }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        border = BorderStroke(1.dp, colors.border),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                                                    ) {
                                                        Text(
                                                            text = "${t.iconKey ?: "📌"} ${t.title}",
                                                            fontSize = 12.sp,
                                                            color = colors.textPrimary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            is TimelineRowItem.AnytimeToDo -> {
                                val item = rowItem.entity
                                TaskitoAnytimeItemRow(
                                    item = item,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    onToggle = { viewModel.toggleItemDone(item) },
                                    onClick = { itemToEdit = item },
                                    onDelete = {
                                        viewModel.deleteItem(item)
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
                            is TimelineRowItem.PeriodicSurfaced -> {
                                TaskitoPeriodicSurfacedRow(
                                    template = rowItem.template,
                                    isOverdue = rowItem.isOverdue,
                                    elapsedDays = rowItem.elapsedDays,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    onCompletedNow = {
                                        viewModel.recordCycleTask(rowItem.template, today)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("「${rowItem.template.title}」の完了を記録しました！")
                                        }
                                    },
                                    onSkip = {
                                        viewModel.skipCycleTask(rowItem.template)
                                        coroutineScope.launch {
                                            val interval = rowItem.template.intervalDays ?: 7
                                            val nextDate = today.plusDays(interval.toLong())
                                            snackbarHostState.showSnackbar("「${rowItem.template.title}」をスキップしました (次回: ${nextDate.monthValue}/${nextDate.dayOfMonth})")
                                        }
                                    }
                                )
                            }
                            is TimelineRowItem.LogItem -> {
                                val item = rowItem.entity
                                TaskitoTimelineItemRow(
                                    item = item,
                                    templates = templates,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    onToggle = { viewModel.toggleItemDone(item) },
                                    onClick = { itemToEdit = item },
                                    onDelete = {
                                        viewModel.deleteItem(item)
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
                    }
                }
            }

            // Fixed Active Timer Mini-Player at bottom (does not scroll with timeline)
            if (activeTimerTemplate != null) {
                ActiveTimerBottomBar(
                    template = activeTimerTemplate!!,
                    elapsedSeconds = timerSeconds,
                    onStop = {
                        viewModel.stopAndSaveTimer { saved ->
                            coroutineScope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "${saved.title} を記録しました",
                                    actionLabel = "元に戻す",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.undoLastItem()
                                }
                            }
                        }
                    },
                    onCancel = { viewModel.cancelTimer() }
                )
            }
        }
    }

    // Add Sheet
    if (showAddSheet) {
        AddItemBottomSheet(
            templates = templates,
            onDismiss = { showAddSheet = false },
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue ->
                viewModel.addTimelineItem(title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue)
            }
        )
    }

    // Full-Text Search Bottom Sheet
    if (showSearchSheet) {
        SearchBottomSheet(
            allItems = allItems,
            onDismiss = { showSearchSheet = false },
            onSelectItem = { itemToEdit = it }
        )
    }
    if (showTemplateManagerDialog) {
        com.forcusflow.lifestream.ui.components.TemplateManagerDialog(
            viewModel = viewModel,
            onDismiss = { showTemplateManagerDialog = false }
        )
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
        // Continuous stem line
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
                .padding(vertical = 10.dp),
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

@Composable
fun ActiveTimerBottomBar(
    template: TemplateEntity,
    elapsedSeconds: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val timerStr = "%02d:%02d".format(mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = template.iconKey ?: "⏱️", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = template.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$timerStr 計測中...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("破棄", fontSize = 12.sp, color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("完了・保存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoAnytimeItemRow(
    item: TimelineItemEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    val checkScale by animateFloatAsState(
        targetValue = if (item.isDone) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "anytimeCheckScale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    .background(Color(0xFFEF4444).copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("削除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.background)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Continuous stem line
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

                // Hollow ToDo node with generous 48.dp touch target & spring animation
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .scale(checkScale)
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, colors.primary, CircleShape)
                            .background(colors.card),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.isDone) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "完了",
                                tint = colors.statusDone,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Title & Anytime indicator (Clicking card opens edit modal)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .background(colors.card)
                    .clickable { onClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "今日中",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        val cleanTitle = remember(item.title) {
                            item.title
                                .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                .trim()
                        }
                        Text(
                            text = cleanTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null
                        )
                    }
                    if (!item.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.note,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoPeriodicSurfacedRow(
    template: TemplateEntity,
    isOverdue: Boolean,
    elapsedDays: Long,
    isFirst: Boolean,
    isLast: Boolean,
    onCompletedNow: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val badgeColor = if (isOverdue) colors.statusOverdue else colors.statusTarget
    val badgeText = if (isOverdue) "期限超過" else "本日推奨"

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onSkip()
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
                    .background(Color(0xFFF59E0B).copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("↷ スキップ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.background)
                .clickable { onCompletedNow() }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Continuous stem line
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

                // Compact Node Circle (Red or Purple)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, badgeColor, CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .clickable { onCompletedNow() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOverdue) "!" else "•",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 1-line integrated row matching Taskito style
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.05f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${template.iconKey ?: "🧹"} ${template.title}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${elapsedDays}日前)",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
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

                    OutlinedButton(
                        onClick = onCompletedNow,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("✓ 記録", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoTimelineItemRow(
    item: TimelineItemEntity,
    templates: List<TemplateEntity> = emptyList(),
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
        label = "timelineCheckScale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    .background(Color(0xFFEF4444).copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("削除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Taskito Continuous Stem Column
                Box(
                    modifier = Modifier
                        .width(36.dp)
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

                    // Node Circle (Interactive - tap to toggle done, 48.dp touch target & spring bounce)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle()
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

                // Time & Title & Notes (Clicking card opens edit modal)
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
                        Text(
                            text = timeStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        // Clean title without synthetic brackets
                        val cleanTitle = remember(item.title) {
                            item.title
                                .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                .trim()
                        }

                        // Extracted duration in seconds (from column or fallback parsing of legacy title)
                        val durationSec = remember(item.durationSeconds, item.title) {
                            item.durationSeconds
                                ?: "\\((\\d+)分\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
                                ?: "\\((\\d+)秒\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                        }

                        // Extracted count value (from column or fallback parsing of legacy title)
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
                                val unit = matched?.unit?.ifBlank { "回" } ?: "回"
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
                                item.note.startsWith("計測時間: ") -> null // 二重表示防止
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

                    // Only show useful Amount badge (if present)
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
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    allItems: List<TimelineItemEntity>,
    onDismiss: () -> Unit,
    onSelectItem: (TimelineItemEntity) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableStateOf(0) } // 0: すべて, 1: できたこと, 2: 予定, 3: 支出のみ
    val filterLabels = listOf("すべて", "📝 できたこと", "📋 予定", "💴 支出のみ")

    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }

    val filteredItems = remember(allItems, searchQuery, selectedFilterIndex) {
        allItems.filter { item ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.note?.contains(searchQuery, ignoreCase = true) == true)
            }

            val matchesFilter = when (selectedFilterIndex) {
                1 -> item.isDone
                2 -> !item.isDone
                3 -> (item.amount != null && item.amount > 0)
                else -> true
            }

            matchesQuery && matchesFilter
        }.sortedByDescending { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "記録・予定の全文検索",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "閉じる",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "タイトル、メモ、内容で検索...",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "クリア",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterLabels.forEachIndexed { index, label ->
                    val isSelected = selectedFilterIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.primary else colors.card)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.primary else colors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilterIndex = index
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${filteredItems.size}件のアイテム",
                fontSize = 12.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔍",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "一致するアイテムは見つかりませんでした" else "記録がありません",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredItems.size) { idx ->
                        val item = filteredItems[idx]
                        val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
                        val dateTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
                            .format(dateFormatter)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectItem(item)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Pill
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isDone) colors.statusDone.copy(alpha = 0.15f) else colors.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isDone) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.statusDone,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val cleanTitle = item.title
                                            .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                            .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                            .trim()
                                        Text(
                                            text = cleanTitle,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = dateTimeStr,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    if (!item.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.note,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (item.amount != null && item.amount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.statusTarget.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "¥%,d".format(item.amount),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.statusTarget
                                        )
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

