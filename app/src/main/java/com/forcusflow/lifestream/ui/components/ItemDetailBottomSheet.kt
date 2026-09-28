package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 統一された広々とした詳細編集モーダルボトムシート
 * Done（事実ログ）と ToDo（未完了タスク）の双方を美しく直感的に編集できる商用レベルのUI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailBottomSheet(
    item: TimelineItemEntity,
    templates: List<TemplateEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (TimelineItemEntity) -> Unit,
    onDelete: () -> Unit,
    onPromoteToPeriodic: ((TimelineItemEntity, Int, String) -> Unit)? = null
) {
    val colors = LifeStreamTheme.colors

    // Clean initial title without synthetic brackets
    val initialCleanTitle = remember(item.title) {
        item.title
            .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
            .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
            .trim()
    }
    var title by remember { mutableStateOf(initialCleanTitle) }

    // Clean initial note without system generated prefixes
    val initialCleanNote = remember(item.note) {
        when {
            item.note == null -> ""
            item.note.startsWith("計測時間: ") -> ""
            item.note in listOf("クイック記録完了", "時間計測完了", "デイリー習慣カウント") -> ""
            else -> item.note
        }
    }
    var note by remember { mutableStateOf(initialCleanNote) }
    var amountText by remember { mutableStateOf(item.amount?.toString() ?: "") }
    var isDone by remember { mutableStateOf(item.isDone) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Routine promotion accordion state
    var showRoutineSection by remember { mutableStateOf(false) }
    var selectedInterval by remember { mutableStateOf(7) }
    var selectedRoutineIcon by remember { mutableStateOf("🔄") }

    val matchedTemplate = remember(templates, item) {
        templates.find { it.id == item.templateId }
    }

    var selectedActionType by remember {
        mutableStateOf(
            when {
                item.countValue != null || matchedTemplate?.actionType == "COUNT" || "\\d+[杯回個本皿枚]".toRegex().containsMatchIn(item.title) -> "COUNT"
                item.durationSeconds != null || matchedTemplate?.actionType == "TIMER" || "\\d+分".toRegex().containsMatchIn(item.title) -> "TIMER"
                else -> "CHECK"
            }
        )
    }

    var selectedUnit by remember {
        mutableStateOf(
            matchedTemplate?.unit?.ifBlank { null }
                ?: "\\d+([杯回個本皿枚])".toRegex().find(item.title)?.groupValues?.get(1)
                ?: "回"
        )
    }

    // Direct numerical state for duration (in minutes)
    val initialDurationMinutes = remember(item.durationSeconds, item.title) {
        if (item.durationSeconds != null && item.durationSeconds > 0) {
            val mins = (item.durationSeconds + 30) / 60
            mins.coerceAtLeast(1)
        } else {
            val parsedMin = "\\((\\d+)分\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
            val parsedSec = "\\((\\d+)秒\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
            when {
                parsedMin != null -> parsedMin
                parsedSec != null -> ((parsedSec + 30) / 60).coerceAtLeast(1)
                else -> 15
            }
        }
    }
    var durationMinutes by remember { mutableStateOf(initialDurationMinutes) }

    // Direct numerical state for count
    val initialCount = remember(item.countValue, item.title) {
        item.countValue
            ?: "\\((\\d+)[杯回個本皿枚]目?\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
            ?: 1
    }
    var countValue by remember { mutableStateOf(initialCount) }

    val zone = ZoneId.systemDefault()
    var selectedDonePreset by remember { mutableStateOf("今") }
    var selectedTodoPreset by remember {
        mutableStateOf(
            if (item.scheduledAt == null) "今日中" else "📅"
        )
    }
    var customDateTime by remember {
        mutableStateOf(
            if (item.isDone) {
                item.completedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            } else {
                item.scheduledAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
            }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tempPickedDate by remember {
        mutableStateOf(customDateTime?.toLocalDate() ?: LocalDate.now())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header bar: Cancel - Title - Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                }
                Text(
                    text = if (isDone) "記録の詳細・編集" else "予定の詳細・編集",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Button(
                    onClick = {
                        val parsedAmount = amountText.toLongOrNull()
                        val finalDurationSeconds = if (selectedActionType == "TIMER") durationMinutes * 60 else null
                        val finalCountValue = if (selectedActionType == "COUNT") countValue else null
                        val now = LocalDateTime.now()

                        val finalCompletedAt = if (isDone) {
                            when {
                                selectedDonePreset == "15分前" -> now.minusMinutes(15).atZone(zone).toInstant().toEpochMilli()
                                selectedDonePreset == "1時間前" -> now.minusHours(1).atZone(zone).toInstant().toEpochMilli()
                                selectedDonePreset == "昨晩" -> now.minusDays(1).withHour(23).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                customDateTime != null -> customDateTime!!.atZone(zone).toInstant().toEpochMilli()
                                else -> item.completedAt ?: System.currentTimeMillis()
                            }
                        } else null

                        val finalScheduledAt = if (!isDone) {
                            when {
                                selectedTodoPreset == "今日中" -> null
                                selectedTodoPreset == "明日" -> now.plusDays(1).withHour(9).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                selectedTodoPreset == "今週末" -> {
                                    val daysUntilWeekend = (6 - now.dayOfWeek.value).let { if (it <= 0) it + 7 else it }
                                    now.plusDays(daysUntilWeekend.toLong()).withHour(10).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                }
                                selectedTodoPreset == "来週月曜" -> {
                                    val daysUntilNextMonday = (8 - now.dayOfWeek.value).let { if (it <= 0) it + 7 else it }
                                    now.plusDays(daysUntilNextMonday.toLong()).withHour(9).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                }
                                customDateTime != null -> customDateTime!!.atZone(zone).toInstant().toEpochMilli()
                                else -> null
                            }
                        } else null

                        val updated = item.copy(
                            title = title.trim(),
                            note = note.trim().ifBlank { null },
                            amount = parsedAmount,
                            isDone = isDone,
                            scheduledAt = finalScheduledAt,
                            completedAt = finalCompletedAt,
                            durationSeconds = finalDurationSeconds,
                            countValue = finalCountValue
                        )
                        onSave(updated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("保存", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // 1. Status Toggle Card (Done vs ToDo)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDone = !isDone }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isDone) colors.statusDone else colors.primary, CircleShape)
                            .background(if (isDone) colors.statusDone else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isDone) "記録済み (できたこと)" else "未完了の予定 (やること)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) colors.statusDone else colors.primary
                        )
                        Text(
                            text = if (isDone) "タイムラインに実行実績として記録されています" else "タップして記録済みに切り替えられます",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Title Input
            Text("タイトル", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 2-A. タイミング設定（登録画面と完全統一）
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isDone) "記録日時" else "予定日時",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))

            val customPresetLabel = customDateTime?.let {
                "📅 %d/%d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute)
            } ?: "📅 日時指定..."

            val timingPresets = if (isDone) {
                listOf("今", "15分前", "1時間前", "昨晩", customPresetLabel)
            } else {
                listOf("今日中", "明日", "今週末", "来週月曜", customPresetLabel)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                timingPresets.forEach { preset ->
                    val isSelected = if (isDone) {
                        selectedDonePreset == preset || (preset == customPresetLabel && customDateTime != null && selectedDonePreset.startsWith("📅"))
                    } else {
                        selectedTodoPreset == preset || (preset == customPresetLabel && customDateTime != null && selectedTodoPreset.startsWith("📅"))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (isSelected) colors.primary else colors.border,
                                RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background)
                            .clickable {
                                if (isDone) {
                                    if (preset.startsWith("📅")) {
                                        showDatePicker = true
                                    } else {
                                        selectedDonePreset = preset
                                        customDateTime = null
                                    }
                                } else {
                                    if (preset.startsWith("📅")) {
                                        showDatePicker = true
                                    } else {
                                        selectedTodoPreset = preset
                                        customDateTime = null
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.textPrimary
                        )
                    }
                }
            }

            // 2-B. 記録タイプ（チェック / カウント / 時間）
            Spacer(modifier = Modifier.height(14.dp))
            Text("記録タイプ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                listOf(
                    Pair("CHECK", "☑ チェック"),
                    Pair("COUNT", "🔢 カウント"),
                    Pair("TIMER", "⏱ 時間")
                ).forEach { (typeKey, label) ->
                    val isSelected = selectedActionType == typeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.primary else Color.Transparent)
                            .clickable { selectedActionType = typeKey }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onPrimary else colors.textSecondary
                        )
                    }
                }
            }

            // カウント用ステッパー
            if (selectedActionType == "COUNT") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "数量 ($selectedUnit):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { countValue = (countValue - 1).coerceAtLeast(1) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$countValue $selectedUnit",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { countValue += 1 },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("杯", "回", "個", "本", "皿", "枚").forEach { u ->
                        val isSel = selectedUnit == u
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) colors.primary.copy(alpha = 0.15f) else colors.card)
                                .border(1.dp, if (isSel) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                .clickable { selectedUnit = u }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(u, fontSize = 11.sp, color = if (isSel) colors.primary else colors.textPrimary)
                        }
                    }
                }
            } else if (selectedActionType == "TIMER") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "作業時間:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { durationMinutes = (durationMinutes - 5).coerceAtLeast(1) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-5分", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$durationMinutes 分",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = { durationMinutes += 5 },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+5分", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Amount Input (¥)
            Text("支出金額 (任意)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                leadingIcon = { Text("¥", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.primary) },
                placeholder = { Text("0", color = colors.textSecondary.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Note Input
            Text("メモ・補足 (任意)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background,
                    unfocusedContainerColor = colors.background
                ),
                placeholder = { Text("気づきや詳細をメモ...", color = colors.textSecondary.copy(alpha = 0.5f)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Promote to Routine / Periodic Task (Accordion Expander)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.background,
                border = BorderStroke(1.dp, if (showRoutineSection) colors.primary.copy(alpha = 0.5f) else colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRoutineSection = !showRoutineSection },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "定期的にやる（ルーティン・周期に追加）",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showRoutineSection) colors.primary else colors.textPrimary
                            )
                        }
                        Text(
                            text = if (showRoutineSection) "▲" else "▼",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }

                    if (showRoutineSection) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "実行周期を選択:",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Preset interval chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                3 to "3日ごと",
                                7 to "7日ごと (毎週)",
                                14 to "14日ごと (隔週)",
                                30 to "30日ごと (毎月)"
                            ).forEach { (days, label) ->
                                val isSel = selectedInterval == days
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) colors.primary else colors.card,
                                    border = BorderStroke(1.dp, if (isSel) colors.primary else colors.border),
                                    modifier = Modifier.clickable { selectedInterval = days }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) colors.onPrimary else colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Icon selector
                        Text(
                            text = "アイコン:",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("🔄", "🧹", "🛏️", "🧖", "🧼", "🏃", "📚", "💊", "🚗", "🌿", "💧", "🧘").forEach { icon ->
                                val isSel = selectedRoutineIcon == icon
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) colors.primary.copy(alpha = 0.2f) else colors.card)
                                        .border(if (isSel) 2.dp else 1.dp, if (isSel) colors.primary else colors.border, CircleShape)
                                        .clickable { selectedRoutineIcon = icon },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(icon, fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                onPromoteToPeriodic?.invoke(item, selectedInterval, selectedRoutineIcon)
                                showRoutineSection = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("この設定でルーティンに追加する", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 6. Delete Button (at the bottom, clean and safe)
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("この記録・タスクを削除する", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Material 3 Date Picker Modal Bottom Sheet
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = customDateTime?.atZone(zone)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
            )
            ModalBottomSheet(
                onDismissRequest = { showDatePicker = false },
                containerColor = colors.card,
                dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("キャンセル", color = colors.textSecondary)
                        }
                        Text("日付を選択", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
                        TextButton(onClick = {
                            val epoch = datePickerState.selectedDateMillis
                            if (epoch != null) {
                                tempPickedDate = Instant.ofEpochMilli(epoch).atZone(ZoneId.of("UTC")).toLocalDate()
                                showDatePicker = false
                                showTimePicker = true
                            } else {
                                showDatePicker = false
                            }
                        }) {
                            Text("次へ (時刻)", color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DatePicker(state = datePickerState)
                }
            }
        }

        // Material 3 Time Picker Modal Bottom Sheet
        if (showTimePicker) {
            val initialH = customDateTime?.hour ?: 10
            val initialM = customDateTime?.minute ?: 0
            val timePickerState = rememberTimePickerState(
                initialHour = initialH,
                initialMinute = initialM,
                is24Hour = true
            )
            ModalBottomSheet(
                onDismissRequest = { showTimePicker = false },
                containerColor = colors.card,
                dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("キャンセル", color = colors.textSecondary)
                        }
                        Text("時刻を指定", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.textPrimary)
                        TextButton(onClick = {
                            val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                            val dt = LocalDateTime.of(tempPickedDate, time)
                            customDateTime = dt
                            val label = "📅 %d/%d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
                            if (isDone) {
                                selectedDonePreset = label
                            } else {
                                selectedTodoPreset = label
                            }
                            showTimePicker = false
                        }) {
                            Text("決定", color = colors.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TimePicker(state = timePickerState)
                }
            }
        }
    }
}
