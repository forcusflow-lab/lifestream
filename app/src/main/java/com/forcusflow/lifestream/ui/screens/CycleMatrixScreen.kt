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
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    // Sort periodic tasks by urgency: Overdue first, then Due Soon, then On Track
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

    val weeklyTemplates = remember(periodicTemplates) {
        periodicTemplates.filter { (it.intervalDays ?: 7) <= 14 }
    }
    val monthlyTemplates = remember(periodicTemplates) {
        periodicTemplates.filter { (it.intervalDays ?: 7) > 14 }
    }

    // 7 days of the current week (Monday to Sunday)
    val currentWeekDays = remember(today) {
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        (0..6).map { monday.plusDays(it.toLong()) }
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (periodicTemplates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "周期タスクがまだありません。\n右下の「＋」ボタンから追加してみましょう！",
                                fontSize = 14.sp,
                                color = colors.textSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                } else {
                    // Section 1: Routine / Short-cycle tasks (<= 14 days)
                    if (weeklyTemplates.isNotEmpty()) {
                        items(weeklyTemplates, key = { it.id }) { template ->
                            val completedDates = remember(allItems, template.id) {
                                allItems.filter {
                                    it.isDone &&
                                    (it.templateId == template.id || it.title.startsWith(template.title)) &&
                                    it.completedAt != null
                                }.map {
                                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it.completedAt!!), zone).toLocalDate()
                                }.toSet()
                            }

                            UnifiedCycleTaskCard(
                                template = template,
                                today = today,
                                weekDays = currentWeekDays,
                                completedDates = completedDates,
                                onCompletedNow = {
                                    viewModel.recordCycleTask(template, today)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("「${template.title}」の完了を記録しました！")
                                    }
                                }
                            )
                        }
                    }

                    // Section 2: Long-Term Maintenance Tasks (> 14 days)
                    if (monthlyTemplates.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "長期メンテナンス (進捗メーター)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                            }
                        }

                        items(monthlyTemplates, key = { "monthly_${it.id}" }) { template ->
                            MonthlyTaskProgressCard(
                                template = template,
                                onCompletedNow = {
                                    viewModel.recordCycleTask(template, today)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("「${template.title}」の完了を記録しました！")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog to add custom periodic task
    if (showAddPeriodicDialog) {
        AddPeriodicTaskDialog(
            onDismiss = { showAddPeriodicDialog = false },
            onAdd = { title, intervalDays, iconKey ->
                viewModel.addTemplate(
                    title = title,
                    type = "INTERVAL",
                    intervalDays = intervalDays,
                    defaultAmount = null,
                    iconKey = iconKey,
                    colorHex = "#8C5A3C"
                )
                showAddPeriodicDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("「$title」を追加しました！")
                }
            }
        )
    }
}

@Composable
fun UnifiedCycleTaskCard(
    template: TemplateEntity,
    today: LocalDate,
    weekDays: List<LocalDate>,
    completedDates: Set<LocalDate>,
    onCompletedNow: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()

    val lastDoneDate = template.lastCompletedAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
    }
    val elapsedDays = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today).toInt() else null
    val interval = template.intervalDays ?: 7
    val isOverdue = elapsedDays != null && elapsedDays >= interval

    val lastDoneText = if (lastDoneDate != null && elapsedDays != null) {
        "前回: ${lastDoneDate.monthValue}/${lastDoneDate.dayOfMonth} (${elapsedDays}日前)"
    } else {
        "未実施 (記録なし)"
    }

    val nextTargetDate = lastDoneDate?.plusDays(interval.toLong())
    val nextTargetText = if (nextTargetDate != null) {
        "次回: ${nextTargetDate.monthValue}/${nextTargetDate.dayOfMonth}"
    } else {
        "次回: いつでも"
    }

    val statusBadgeColor = when {
        isOverdue -> colors.statusOverdue
        elapsedDays != null && elapsedDays >= (interval - 1) -> colors.statusWarning
        lastDoneDate != null -> colors.statusDone
        else -> colors.textSecondary
    }

    val statusBadgeText = when {
        isOverdue -> "❗ ${elapsedDays!! - interval}日遅れ"
        elapsedDays != null && elapsedDays >= (interval - 1) -> "⚠ 本日推奨"
        lastDoneDate != null -> "✓ 順調"
        else -> "未着手"
    }

    val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Icon + Title + Interval + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = template.iconKey ?: "🧹", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = template.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.border.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${interval}日ごと",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                    }
                }

                // Compact Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusBadgeColor.copy(alpha = 0.12f))
                        .border(1.dp, statusBadgeColor.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusBadgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 7-day mini activity dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEachIndexed { idx, date ->
                    val isDoneOnDate = completedDates.contains(date) || (lastDoneDate == date)
                    val isToday = (date == today)
                    val isFuture = date.isAfter(today)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = dayNames[idx],
                            fontSize = 10.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (isToday) colors.primary else colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDoneOnDate -> Color(0xFF16A34A)
                                        isToday -> colors.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    width = if (isToday || isDoneOnDate) 1.5.dp else 1.dp,
                                    color = when {
                                        isDoneOnDate -> Color(0xFF16A34A)
                                        isToday -> colors.primary
                                        isFuture -> colors.border.copy(alpha = 0.4f)
                                        else -> colors.border
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDoneOnDate) {
                                Text(
                                    text = "✓",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            } else if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 9.sp,
                            color = if (isToday) colors.primary else colors.textSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            // Footer Row: Dates & Complete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$lastDoneText | $nextTargetText",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedButton(
                    onClick = onCompletedNow,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    border = BorderStroke(1.dp, colors.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "✓ 記録",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MonthlyTaskProgressCard(
    template: TemplateEntity,
    onCompletedNow: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    val lastDoneDate = template.lastCompletedAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
    }
    val elapsedDays = if (lastDoneDate != null) ChronoUnit.DAYS.between(lastDoneDate, today).toInt() else null
    val interval = template.intervalDays ?: 30
    val isOverdue = elapsedDays != null && elapsedDays >= interval

    val progress = if (elapsedDays != null) {
        (elapsedDays.toFloat() / interval.toFloat()).coerceIn(0f, 1f)
    } else {
        1.0f
    }

    val progressColor = when {
        isOverdue -> colors.statusOverdue
        elapsedDays != null && elapsedDays >= (interval * 0.8f) -> colors.statusWarning
        else -> colors.primary
    }

    val nextTargetDate = lastDoneDate?.plusDays(interval.toLong())
    val remainingDays = if (nextTargetDate != null) ChronoUnit.DAYS.between(today, nextTargetDate).toInt() else null

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(text = template.iconKey ?: "🧼", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "周期: ${interval}日ごと",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                val badgeText = when {
                    isOverdue -> "❗ ${elapsedDays!! - interval}日遅れ"
                    remainingDays != null && remainingDays <= 3 -> "⚠ あと${remainingDays}日"
                    lastDoneDate != null -> "あと${remainingDays}日"
                    else -> "未着手"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(progressColor.copy(alpha = 0.12f))
                        .border(1.dp, progressColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = colors.border
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val datesSummary = if (lastDoneDate != null) {
                    "前回: ${lastDoneDate.monthValue}/${lastDoneDate.dayOfMonth} → 次回: ${nextTargetDate?.monthValue}/${nextTargetDate?.dayOfMonth}"
                } else {
                    "前回: なし → いつでも実施可"
                }
                Text(
                    text = datesSummary,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )

                OutlinedButton(
                    onClick = onCompletedNow,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    border = BorderStroke(1.dp, colors.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("✓ 記録", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddPeriodicTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, intervalDays: Int, iconKey: String) -> Unit
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf("7") }
    var iconKey by remember { mutableStateOf("🧹") }

    val iconOptions = listOf("🧹", "🧖", "🛏️", "🧼", "🌀", "💨", "🌿", "💊", "🚗", "🪴")

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
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iconOptions.forEach { ic ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = intervalText.toIntOrNull() ?: 7
                    if (title.isNotBlank()) {
                        onAdd(title, days, iconKey)
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
