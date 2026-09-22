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
import androidx.compose.material.icons.filled.Refresh
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
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
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
                            Text(
                                text = "周期タスクがまだありません。\n右下の「＋」ボタンから追加してみましょう！",
                                fontSize = 14.sp,
                                color = colors.textSecondary,
                                lineHeight = 20.sp
                            )
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

                        CompactUnifiedCycleTaskCard(
                            template = template,
                            today = today,
                            weekDays = currentWeekDays,
                            completedDates = completedDates,
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

/**
 * 1行超コンパクト周期カード (高さ ~56dp)
 * 短期タスクも長期メンテナンスも同一フォーマットで統一し、
 * スクロール不要で約8〜10件が一望できる設計。
 */
@Composable
fun CompactUnifiedCycleTaskCard(
    template: TemplateEntity,
    today: LocalDate,
    weekDays: List<LocalDate>,
    completedDates: Set<LocalDate>,
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

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        border = BorderStroke(1.dp, if (isOverdue) statusBadgeColor.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Icon (20sp)
            Text(
                text = template.iconKey ?: "🧹",
                fontSize = 20.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

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
                        containerColor = colors.primary.copy(alpha = 0.12f),
                        contentColor = colors.primary
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

    val iconOptions = listOf("🧹", "🧖", "🛏️", "🧼", "🌀", "💨", "🌿", "💊", "🚗", "🪴", "🚿", "🧺")

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
