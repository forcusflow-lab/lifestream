package com.forcusflow.lifestream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * プログレッシブ・ディスクロージャー（段階的開示）採用 登録ボトムシート
 * - 最初はタイトルと保存ボタンのみで圧倒的にシンプル・広々
 * - メモ・金額・日時指定はタップした時だけ滑らかに展開
 * - 画面がごちゃごちゃせず、極上の使い心地を実現
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemBottomSheet(
    templates: List<TemplateEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        isDone: Boolean,
        scheduledAt: Long?,
        completedAt: Long?,
        amount: Long?,
        note: String?,
        templateId: Long?,
        durationSeconds: Int?,
        countValue: Int?
    ) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    var title by remember { mutableStateOf("") }
    var isDone by remember { mutableStateOf(true) }

    // Progressive disclosure states
    var showNoteField by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    var showAmountField by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }

    var showTimingField by remember { mutableStateOf(false) }
    var selectedDonePreset by remember { mutableStateOf("今") }
    var selectedTodoPreset by remember { mutableStateOf("今日中") }

    var selectedTemplateId by remember { mutableStateOf<Long?>(null) }
    val selectedTemplate = remember(selectedTemplateId, templates) {
        templates.find { it.id == selectedTemplateId }
    }

    var countValue by remember { mutableStateOf(1) }
    var timerMinutes by remember { mutableStateOf(15) }

    var customDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var customHour by remember { mutableStateOf(10) }
    var customMinute by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // === 1. Top Bar: Cancel, Segment Toggle, Save Button ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "閉じる",
                        tint = colors.textSecondary
                    )
                }

                // Centered Segment Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isDone) colors.primary else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isDone = true
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "できたこと",
                            fontWeight = if (isDone) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isDone) colors.onPrimary else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (!isDone) colors.primary else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isDone = false
                                selectedTemplateId = null
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "やること",
                            fontWeight = if (!isDone) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (!isDone) colors.onPrimary else colors.textSecondary
                        )
                    }
                }

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            val zone = ZoneId.systemDefault()
                            val now = LocalDateTime.now()
                            val amt = amountText.toLongOrNull()

                            val durationSec = if (selectedTemplate?.actionType == "TIMER") timerMinutes * 60 else null
                            val countVal = if (selectedTemplate?.actionType == "COUNT") countValue else null

                            if (isDone) {
                                val doneTime = when (selectedDonePreset) {
                                    "15分前" -> now.minusMinutes(15)
                                    "1時間前" -> now.minusHours(1)
                                    "昨晩" -> now.minusDays(1).withHour(23).withMinute(0)
                                    else -> now
                                }
                                val millis = doneTime.atZone(zone).toInstant().toEpochMilli()
                                onSave(title.trim(), true, null, millis, amt, note.ifBlank { null }, selectedTemplateId, durationSec, countVal)
                            } else {
                                val scheduledMillis = when (selectedTodoPreset) {
                                    "今日中" -> null
                                    "1時間後" -> now.plusHours(1).atZone(zone).toInstant().toEpochMilli()
                                    "今晩(20:00)" -> now.withHour(20).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                    "明日(10:00)" -> now.plusDays(1).withHour(10).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                    "📅 日時指定..." -> LocalDateTime.of(customDate, LocalTime.of(customHour, customMinute)).atZone(zone).toInstant().toEpochMilli()
                                    else -> null
                                }
                                onSave(title.trim(), false, scheduledMillis, null, amt, note.ifBlank { null }, selectedTemplateId, durationSec, countVal)
                            }
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    enabled = title.isNotBlank()
                ) {
                    Text(
                        text = if (isDone) "記録" else "保存",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === 2. Main Title Input (広々・明瞭) ===
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        if (isDone) "何ができましたか？ (例: 読書, 散歩, 掃除)"
                        else "何をしますか？ (例: 洗濯, レポート提出)"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedContainerColor = colors.background.copy(alpha = 0.5f),
                    unfocusedContainerColor = colors.background.copy(alpha = 0.5f)
                )
            )

            // Dynamic Stepper for COUNT / TIMER Templates
            if (selectedTemplate?.actionType == "COUNT") {
                val unitStr = if (selectedTemplate.unit.isNotBlank()) selectedTemplate.unit else "杯"
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "数量 (${unitStr}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (countValue > 1) countValue-- },
                            enabled = countValue > 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$countValue $unitStr",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        FilledTonalIconButton(
                            onClick = { countValue++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (selectedTemplate?.actionType == "TIMER") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "計測時間 (分):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (timerMinutes > 5) timerMinutes -= 5 },
                            enabled = timerMinutes > 5,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("-5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$timerMinutes 分",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        FilledTonalIconButton(
                            onClick = { timerMinutes += 5 },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("+5", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === 3. Progressive Disclosure Chips (タップで必要項目を展開) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [ ＋ メモ ]
                val isNoteActive = showNoteField || note.isNotBlank()
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showNoteField = !showNoteField
                    },
                    label = {
                        Text(
                            text = if (note.isNotBlank()) "メモあり" else if (showNoteField) "メモ入力中" else "＋ メモ",
                            fontSize = 12.sp,
                            fontWeight = if (isNoteActive) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isNoteActive) colors.primary.copy(alpha = 0.12f) else colors.card,
                        labelColor = if (isNoteActive) colors.primary else colors.textSecondary
                    ),
                    border = BorderStroke(1.dp, if (isNoteActive) colors.primary else colors.border)
                )

                // [ ＋ 金額 ]
                val isAmountActive = showAmountField || amountText.isNotBlank()
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showAmountField = !showAmountField
                    },
                    label = {
                        Text(
                            text = if (amountText.isNotBlank()) "¥${amountText}" else if (showAmountField) "金額入力中" else "＋ 金額",
                            fontSize = 12.sp,
                            fontWeight = if (isAmountActive) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isAmountActive) colors.statusTarget.copy(alpha = 0.12f) else colors.card,
                        labelColor = if (isAmountActive) colors.statusTarget else colors.textSecondary
                    ),
                    border = BorderStroke(1.dp, if (isAmountActive) colors.statusTarget else colors.border)
                )

                // [ ＋ 日時指定 ]
                val isTimingActive = showTimingField || (isDone && selectedDonePreset != "今") || (!isDone && selectedTodoPreset != "今日中")
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showTimingField = !showTimingField
                    },
                    label = {
                        val lbl = if (isDone) {
                            if (selectedDonePreset != "今") selectedDonePreset else "日時: 今"
                        } else {
                            if (selectedTodoPreset != "今日中") selectedTodoPreset else "日時: 今日中"
                        }
                        Text(
                            text = if (showTimingField || isTimingActive) lbl else "＋ 日時指定",
                            fontSize = 12.sp,
                            fontWeight = if (isTimingActive) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isTimingActive) colors.primary.copy(alpha = 0.12f) else colors.card,
                        labelColor = if (isTimingActive) colors.primary else colors.textSecondary
                    ),
                    border = BorderStroke(1.dp, if (isTimingActive) colors.primary else colors.border)
                )
            }

            // === 4. Expandable Fields ===
            // Note Field Expansion
            AnimatedVisibility(
                visible = showNoteField || note.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("メモ (任意)") },
                        placeholder = { Text("気づき、詳細、振り返りなど") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3
                    )
                }
            }

            // Amount Field Expansion
            AnimatedVisibility(
                visible = showAmountField || amountText.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                        label = { Text("支出金額") },
                        placeholder = { Text("0") },
                        prefix = { Text("¥ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            }

            // Timing Field Expansion
            AnimatedVisibility(
                visible = showTimingField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = if (isDone) "記録日時:" else "予定日時:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val presets = if (isDone) {
                        listOf("今", "15分前", "1時間前", "昨晩")
                    } else {
                        listOf("今日中", "1時間後", "今晩(20:00)", "明日(10:00)", "📅 日時指定...")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            val isSelected = if (isDone) selectedDonePreset == preset else selectedTodoPreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSelected) colors.primary else colors.border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isDone) selectedDonePreset = preset else selectedTodoPreset = preset
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

                    // If "📅 日時指定..." picked for Todo
                    if (!isDone && selectedTodoPreset == "📅 日時指定...") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.background,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                // Date selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("指定日:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { customDate = customDate.minusDays(1) }, modifier = Modifier.size(28.dp)) {
                                            Text("◀", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                        val dayName = listOf("月", "火", "水", "木", "金", "土", "日")[customDate.dayOfWeek.value - 1]
                                        Text(
                                            text = "${customDate.monthValue}月${customDate.dayOfMonth}日 ($dayName)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        IconButton(onClick = { customDate = customDate.plusDays(1) }, modifier = Modifier.size(28.dp)) {
                                            Text("▶", fontSize = 12.sp, color = colors.textSecondary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val todayDate = LocalDate.now()
                                    listOf(
                                        "今日" to todayDate,
                                        "明日" to todayDate.plusDays(1),
                                        "明後日" to todayDate.plusDays(2),
                                        "来週月曜" to todayDate.plusDays((8 - todayDate.dayOfWeek.value).toLong())
                                    ).forEach { (lbl, d) ->
                                        val isSel = customDate == d
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primary.copy(alpha = 0.15f) else colors.card)
                                                .border(1.dp, if (isSel) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                                .clickable { customDate = d }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(lbl, fontSize = 11.sp, color = if (isSel) colors.primary else colors.textPrimary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Time selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("指定時刻:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { customHour = (customHour - 1 + 24) % 24 }, modifier = Modifier.size(28.dp)) {
                                            Text("-1h", fontSize = 11.sp, color = colors.textSecondary)
                                        }
                                        Text(
                                            text = "%02d:%02d".format(customHour, customMinute),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        IconButton(onClick = { customHour = (customHour + 1) % 24 }, modifier = Modifier.size(28.dp)) {
                                            Text("+1h", fontSize = 11.sp, color = colors.textSecondary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "朝 (9:00)" to (9 to 0),
                                        "昼 (12:00)" to (12 to 0),
                                        "夕 (18:00)" to (18 to 0),
                                        "夜 (21:00)" to (21 to 0)
                                    ).forEach { (lbl, timePair) ->
                                        val isSel = customHour == timePair.first && customMinute == timePair.second
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSel) colors.primary.copy(alpha = 0.15f) else colors.card)
                                                .border(1.dp, if (isSel) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                                .clickable {
                                                    customHour = timePair.first
                                                    customMinute = timePair.second
                                                }
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(lbl, fontSize = 11.sp, color = if (isSel) colors.primary else colors.textPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // === 5. Quick Template Selector (記録モード時のみ表示) ===
            if (isDone && templates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "クイック選択:",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    templates.take(12).forEach { t ->
                        val isSelected = selectedTemplateId == t.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) colors.primary else colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTemplateId = t.id
                                    title = t.title
                                    if (t.defaultAmount != null) {
                                        amountText = t.defaultAmount.toString()
                                        showAmountField = true
                                    }
                                    if (t.actionType == "COUNT") {
                                        countValue = 1
                                    } else if (t.actionType == "TIMER") {
                                        timerMinutes = 15
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = t.iconKey ?: "📌", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = t.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
