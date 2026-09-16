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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AddItemBottomSheet
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
    data class ActiveTimer(val template: TemplateEntity, val elapsedSeconds: Long) : TimelineRowItem()
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
    val allItems by viewModel.allItems.collectAsState()
    val anytimePending by viewModel.anytimePendingItems.collectAsState()
    val activeTimerTemplate by viewModel.activeTimerTemplate.collectAsState()
    val timerSeconds by viewModel.timerElapsedSeconds.collectAsState()

    var showAddSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TimelineItemEntity?>(null) }
    var showTemplateManagerDialog by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()
    val now = LocalDateTime.now()
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val nowTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

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

    // Build timeline entries with NOW line, Stopwatch, Anytime ToDos, and Surfaced periodic tasks
    val timelineRowItems = remember(todayItems, nowMillis, nowTimeStr, anytimePending, activeTimerTemplate, timerSeconds, dueOrOverduePeriodic) {
        val list = mutableListOf<TimelineRowItem>()
        var nowInserted = false

        todayItems.forEach { item ->
            val itemTime = item.completedAt ?: item.scheduledAt ?: 0L
            if (!nowInserted && itemTime > nowMillis) {
                list.add(TimelineRowItem.NowMarker(nowTimeStr))
                // Active Stopwatch
                activeTimerTemplate?.let {
                    list.add(TimelineRowItem.ActiveTimer(it, timerSeconds))
                }
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
            activeTimerTemplate?.let {
                list.add(TimelineRowItem.ActiveTimer(it, timerSeconds))
            }
            anytimePending.forEach {
                list.add(TimelineRowItem.AnytimeToDo(it))
            }
            dueOrOverduePeriodic.forEach { (tmpl, isOverdue, elapsed) ->
                list.add(TimelineRowItem.PeriodicSurfaced(tmpl, isOverdue, elapsed))
            }
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
            // Simplified Header: Only date in elegant font
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp)
            ) {
                Text(
                    text = todayDateFormatted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    letterSpacing = (-0.5).sp
                )
            }

            // Quick Record Bar (ActionTypes: COUNT, TIMER, CHECK)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Determine quick action items
                val waterTmpl = templates.find { it.title.contains("水") }
                val timerTmpl = templates.find { it.actionType == "TIMER" || it.title.contains("勉強") }
                val packTmpl = templates.find { it.title.contains("パック") }
                val nightMealTmpl = templates.find { it.title.contains("夜食") }
                val drainTmpl = templates.find { it.title.contains("排水") }

                val quickList = listOfNotNull(waterTmpl, timerTmpl, packTmpl, nightMealTmpl, drainTmpl)

                quickList.forEach { t ->
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
                        isCount -> {
                            val count = todayItems.count { it.templateId == t.id || it.title.startsWith(t.title) }
                            val unitStr = if (t.unit.isNotBlank()) t.unit else "杯"
                            "${t.iconKey ?: "💧"} ${t.title} (${count}${unitStr})"
                        }
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
                            }
                            is TimelineRowItem.ActiveTimer -> {
                                TaskitoActiveTimerRow(
                                    template = rowItem.template,
                                    elapsedSeconds = rowItem.elapsedSeconds,
                                    isFirst = isFirst,
                                    isLast = isLast,
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
                            is TimelineRowItem.AnytimeToDo -> {
                                val item = rowItem.entity
                                TaskitoAnytimeItemRow(
                                    item = item,
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    onToggle = { viewModel.toggleItemDone(item) },
                                    onClick = { itemToEdit = item }
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
                                    }
                                )
                            }
                            is TimelineRowItem.LogItem -> {
                                val item = rowItem.entity
                                TaskitoTimelineItemRow(
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
                        }
                    }
                }
            }
        }
    }

    // Add Sheet
    if (showAddSheet) {
        AddItemBottomSheet(
            onDismiss = { showAddSheet = false },
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId ->
                viewModel.addTimelineItem(title, isDone, scheduledAt, completedAt, amount, note, templateId)
            }
        )
    }

    // Template Manager Dialog
    if (showTemplateManagerDialog) {
        com.forcusflow.lifestream.ui.components.TemplateManagerDialog(
            viewModel = viewModel,
            onDismiss = { showTemplateManagerDialog = false }
        )
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
fun TaskitoActiveTimerRow(
    template: TemplateEntity,
    elapsedSeconds: Long,
    isFirst: Boolean,
    isLast: Boolean,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val timerStr = "%02d:%02d".format(mins, secs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous stem line with pulsing timer dot
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
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timer Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = template.iconKey ?: "⏱️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${template.title} (計測中)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timerStr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFEF4444)
                    )
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
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
}

@Composable
fun TaskitoAnytimeItemRow(
    item: TimelineItemEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
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

            // Hollow ToDo node
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(2.dp, colors.primary, CircleShape)
                    .background(colors.card)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (item.isDone) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "完了",
                        tint = colors.statusDone,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Title & Anytime indicator
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .background(colors.card)
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
                    Text(
                        text = item.title,
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

@Composable
fun TaskitoPeriodicSurfacedRow(
    template: TemplateEntity,
    isOverdue: Boolean,
    elapsedDays: Long,
    isFirst: Boolean,
    isLast: Boolean,
    onCompletedNow: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val badgeColor = if (isOverdue) colors.statusOverdue else colors.statusTarget
    val badgeText = if (isOverdue) "期限超過" else "本日推奨"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
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
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${elapsedDays}日前)",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoTimelineItemRow(
    item: TimelineItemEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    val isDone = item.isDone
    val nodeColor = if (isDone) colors.statusDone else colors.card
    val nodeBorderColor = if (isDone) colors.statusDone else colors.textSecondary

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
                .clickable { onClick() }
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

                // Node Circle (Interactive - tap to toggle done)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, nodeBorderColor, CircleShape)
                        .background(nodeColor)
                        .clickable { onToggle() },
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

            Spacer(modifier = Modifier.width(8.dp))

            // Time & Title & Notes (Decluttered: no redundant "完了" / "予定" badges)
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
                val displayNote = remember(item.note) {
                    when {
                        item.note.isNullOrBlank() -> null
                        item.note in listOf("クイック記録完了", "時間計測完了", "デイリー水分補給", "デイリー習慣カウント") -> null
                        item.note.startsWith("時間計測完了 (") && item.note.endsWith(")") -> {
                            val inner = item.note.removePrefix("時間計測完了 (").removeSuffix(")")
                            "計測時間: $inner"
                        }
                        else -> item.note
                    }
                }
                if (displayNote != null) {
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

@Composable
fun EditItemDialog(
    item: TimelineItemEntity,
    onDismiss: () -> Unit,
    onSave: (TimelineItemEntity) -> Unit,
    onDelete: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf(item.title) }
    var note by remember { mutableStateOf(item.note ?: "") }
    var amountText by remember { mutableStateOf(item.amount?.toString() ?: "") }
    var isDone by remember { mutableStateOf(item.isDone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = "記録の編集",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column {
                // Done toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDone = !isDone }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDone,
                        onCheckedChange = { isDone = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isDone) "完了済み (Done)" else "予定 (ToDo)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("メモ・言い訳") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                    label = { Text("支出金額 (¥)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = item.copy(
                        title = title,
                        note = note.ifBlank { null },
                        amount = amountText.toLongOrNull(),
                        isDone = isDone,
                        completedAt = if (isDone && item.completedAt == null) System.currentTimeMillis() else item.completedAt
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("削除", color = colors.statusOverdue)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
}
