package com.forcusflow.lifestream.ui.components

import androidx.compose.animation.*
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemBottomSheet(
    initialTitle: String = "",
    initialNote: String = "",
    initialIsDone: Boolean = true,
    cutoffHour: Int = 4,
    templates: List<TemplateEntity> = emptyList(),
    onDismiss: () -> Unit,
    onManageTemplates: (() -> Unit)? = null,
    onSave: (
        title: String,
        isDone: Boolean,
        scheduledAt: Long?,
        completedAt: Long?,
        amount: Long?,
        note: String?,
        templateId: Long?,
        durationSeconds: Int?,
        countValue: Int?,
        createdAt: Long
    ) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(initialTitle) }
    var isDone by remember { mutableStateOf(initialIsDone) }

    // Progressive disclosure states
    var showNoteField by remember { mutableStateOf(initialNote.isNotBlank()) }
    var note by remember { mutableStateOf(initialNote) }

    var showAmountField by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }

    var showTimingField by remember { mutableStateOf(false) }
    var selectedDonePreset by remember { mutableStateOf("今") }

    // 1段目: "今日", "引き出し", "今週末", "日時指定"
    var selectedTodoTiming by remember { mutableStateOf("今日") }
    // 2段目: "終日", "10:00", "14:00", "18:00", or custom "HH:mm"
    var selectedTodoTime by remember { mutableStateOf("終日") }
    var customPickedTime by remember { mutableStateOf<LocalTime?>(null) }

    var selectedTemplateId by remember { mutableStateOf<Long?>(null) }
    val selectedTemplate = remember(selectedTemplateId, templates) {
        templates.find { it.id == selectedTemplateId }
    }

    var countValue by remember { mutableStateOf(1) }
    var timerMinutes by remember { mutableStateOf(15) }

    var customDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isTimePickerForDirectTime by remember { mutableStateOf(false) }
    var tempPickedDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }

    fun onExpandField(action: () -> Unit) {
        keyboardController?.hide()
        action()
        coroutineScope.launch {
            delay(120L)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
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
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "これからの予定",
                            fontWeight = if (!isDone) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (!isDone) colors.onPrimary else colors.textSecondary
                        )
                    }
                }

                // Save / Record Button (Unified filled button style)
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                onSave(title.trim(), true, null, millis, amt, note.ifBlank { null }, selectedTemplateId, durationSec, countVal, System.currentTimeMillis())
                            } else {
                                var scheduledMillis: Long? = null
                                var createdAtMillis: Long = System.currentTimeMillis()

                                when (selectedTodoTiming) {
                                    "引き出し" -> {
                                        scheduledMillis = null
                                        val todayLogical = if (now.hour < cutoffHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
                                        val todayStartMillis = todayLogical.atTime(cutoffHour, 0).atZone(zone).toInstant().toEpochMilli()
                                        createdAtMillis = (todayStartMillis - 1000L).coerceAtLeast(1L)
                                    }
                                    "今日" -> {
                                        if (selectedTodoTime != "終日") {
                                            val time = customPickedTime ?: when (selectedTodoTime) {
                                                "10:00" -> LocalTime.of(10, 0)
                                                "14:00" -> LocalTime.of(14, 0)
                                                "18:00" -> LocalTime.of(18, 0)
                                                else -> null
                                            }
                                            if (time != null) {
                                                scheduledMillis = LocalDateTime.of(now.toLocalDate(), time).atZone(zone).toInstant().toEpochMilli()
                                            }
                                        }
                                    }
                                    "今週末" -> {
                                        val daysUntilWeekend = (6 - now.dayOfWeek.value).let { if (it <= 0) it + 7 else it }
                                        val weekendDate = now.toLocalDate().plusDays(daysUntilWeekend.toLong())
                                        val time = customPickedTime ?: when (selectedTodoTime) {
                                            "10:00" -> LocalTime.of(10, 0)
                                            "14:00" -> LocalTime.of(14, 0)
                                            "18:00" -> LocalTime.of(18, 0)
                                            else -> LocalTime.of(0, 0)
                                        }
                                        scheduledMillis = LocalDateTime.of(weekendDate, time).atZone(zone).toInstant().toEpochMilli()
                                    }
                                    "日時指定" -> {
                                        if (customDateTime != null) {
                                            scheduledMillis = customDateTime!!.atZone(zone).toInstant().toEpochMilli()
                                        }
                                    }
                                }
                                onSave(title.trim(), false, scheduledMillis, null, amt, note.ifBlank { null }, selectedTemplateId, durationSec, countVal, createdAtMillis)
                            }
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    enabled = title.isNotBlank()
                ) {
                    Text(
                        text = if (isDone) "記録" else "追加",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // === 2. Main Title Input ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(0.5.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            if (isDone) "何ができましたか？ (例: 読書, 散歩, 掃除)"
                            else "何をしますか？ (例: 洗濯, レポート提出)",
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            }

            // === 3. Progressive Disclosure Chips ===
            Spacer(modifier = Modifier.height(12.dp))

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
                        onExpandField { showNoteField = !showNoteField }
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
                        onExpandField { showAmountField = !showAmountField }
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

                // [ ＋ 日時指定 / タイミング ]
                val isTimingActive = showTimingField || (isDone && selectedDonePreset != "今") || (!isDone && (selectedTodoTiming != "今日" || selectedTodoTime != "終日"))
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandField { showTimingField = !showTimingField }
                    },
                    label = {
                        val lbl = if (isDone) {
                            if (selectedDonePreset != "今") selectedDonePreset else "日時: 今"
                        } else {
                            when (selectedTodoTiming) {
                                "今日" -> if (selectedTodoTime != "終日") "今日 $selectedTodoTime" else "日時: 今日"
                                "引き出し" -> "📦 引き出し（日時未定）"
                                "今週末" -> if (selectedTodoTime != "終日") "今週末 $selectedTodoTime" else "日時: 今週末"
                                "日時指定" -> customDateTime?.let { "%d/%d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) } ?: "日時指定"
                                else -> "＋ タイミング"
                            }
                        }
                        Text(
                            text = if (showTimingField || isTimingActive) lbl else "＋ タイミング",
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

            // Timing Field Expansion (爆速・2段階設定)
            AnimatedVisibility(
                visible = showTimingField,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (isDone) {
                        Text(
                            text = "記録日時:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("今", "15分前", "1時間前", "昨晩").forEach { preset ->
                                val isSelected = selectedDonePreset == preset
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
                                            selectedDonePreset = preset
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    } else {
                        // 1段目: いつやる？ (今日 / 引き出し / 今週末 / 日時指定)
                        Text(
                            text = "いつやりますか？",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "今日" to "🌿 今日",
                                "引き出し" to "📦 引き出し（日時未定）",
                                "今週末" to "☕ 今週末",
                                "日時指定" to "📅 日時指定..."
                            ).forEach { (key, label) ->
                                val isSelected = selectedTodoTiming == key
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
                                            keyboardController?.hide()
                                            selectedTodoTiming = key
                                            if (key == "日時指定") {
                                                showDatePicker = true
                                            } else {
                                                customDateTime = null
                                            }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.primary else colors.textPrimary
                                    )
                                }
                            }
                        }

                        // 2段目: 時間も決める？ (今日 or 今週末のときのみ表示)
                        if (selectedTodoTiming == "今日" || selectedTodoTiming == "今週末") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "時刻も設定しますか？ (任意)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textSecondary.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val timeOptions = mutableListOf("終日", "10:00", "14:00", "18:00")
                                if (customPickedTime != null) {
                                    val formattedCustom = "%02d:%02d".format(customPickedTime!!.hour, customPickedTime!!.minute)
                                    if (formattedCustom !in timeOptions) {
                                        timeOptions.add(formattedCustom)
                                    }
                                }

                                timeOptions.forEach { opt ->
                                    val isSelected = selectedTodoTime == opt
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                1.dp,
                                                if (isSelected) colors.primary else colors.border.copy(alpha = 0.6f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedTodoTime = opt
                                                if (opt == "終日") customPickedTime = null
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = opt,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) colors.primary else colors.textPrimary
                                        )
                                    }
                                }

                                // ⏰ 時刻を直接選ぶ (カレンダーを挟まず直接TimePickerが開く！)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(0.5.dp, colors.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .background(colors.primary.copy(alpha = 0.05f))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            keyboardController?.hide()
                                            isTimePickerForDirectTime = true
                                            showTimePicker = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⏰ 時刻を選ぶ...",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Material 3 Date Picker Modal Bottom Sheet (特定日指定時のみ使用)
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = System.currentTimeMillis()
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
                                    isTimePickerForDirectTime = false
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

            // Material 3 Time Picker Modal Bottom Sheet (直接時刻指定 or 日付ピッカー後)
            if (showTimePicker) {
                val timePickerState = rememberTimePickerState(
                    initialHour = 14,
                    initialMinute = 0,
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
                                if (isTimePickerForDirectTime) {
                                    customPickedTime = time
                                    selectedTodoTime = "%02d:%02d".format(time.hour, time.minute)
                                } else {
                                    val dt = LocalDateTime.of(tempPickedDate, time)
                                    customDateTime = dt
                                    selectedTodoTiming = "日時指定"
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

            // === 5. Quick Template Selector (記録モード時のみ表示) ===
            if (isDone && templates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "クイック選択:",
                        fontSize = 11.5.sp,
                        color = colors.textSecondary
                    )
                    if (onManageTemplates != null) {
                        Text(
                            text = "⚙ テンプレート管理",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onManageTemplates()
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.filter { it.type != "INTERVAL" }.forEach { tmpl ->
                        val isSelected = selectedTemplateId == tmpl.id
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isSelected) {
                                    selectedTemplateId = null
                                } else {
                                    selectedTemplateId = tmpl.id
                                    if (title.isBlank()) title = tmpl.title
                                    if (tmpl.defaultAmount != null) amountText = tmpl.defaultAmount.toString()
                                }
                            },
                            label = {
                                Text(
                                    text = "${tmpl.iconKey ?: "⚡"} ${tmpl.title}",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.card,
                                labelColor = if (isSelected) colors.primary else colors.textPrimary
                            ),
                            border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.border)
                        )
                    }
                }
            }
        }
    }
}
