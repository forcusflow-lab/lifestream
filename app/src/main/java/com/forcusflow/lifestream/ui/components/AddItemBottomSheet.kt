package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * ゼロスクロール入力ボトムシート
 * キーボード表示時でもスクロール不要で親指が届く範囲ですべて完結するUI
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
        templateId: Long?
    ) -> Unit
) {
    val colors = LifeStreamTheme.colors
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isDone by remember { mutableStateOf(true) }
    var selectedTemplateId by remember { mutableStateOf<Long?>(null) }

    // Presets
    var selectedDonePreset by remember { mutableStateOf("今") }
    var selectedTodoPreset by remember { mutableStateOf("今日中") }
    var showExtraFields by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // 1. Segmented Control (Done vs ToDo) - Compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = true }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✅ 事実ログ (Done)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isDone) colors.onPrimary else colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = false }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 予定 (ToDo)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (!isDone) colors.onPrimary else colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Compact Horizontal Template Palette
            if (templates.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    templates.take(12).forEach { t ->
                        val isSelected = selectedTemplateId == t.id
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) colors.primary else colors.border,
                                    RoundedCornerShape(6.dp)
                                )
                                .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.background)
                                .clickable {
                                    selectedTemplateId = t.id
                                    title = t.title
                                    if (t.defaultAmount != null) {
                                        amountText = t.defaultAmount.toString()
                                        showExtraFields = true
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = t.iconKey ?: "📌", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = t.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.primary else colors.textPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. Title Input (Main primary action)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (isDone) "やったこと (例: カフェ、散歩、読書)" else "やること (例: 洗剤購入、ゴミ出し)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Timing Preset Chips in a Single Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDone) "日時:" else "予定:",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )

                val presets = if (isDone) {
                    listOf("今", "15分前", "1時間前", "昨晩")
                } else {
                    listOf("今日中", "1時間後", "今晩(20時)", "明日(10時)")
                }

                presets.forEach { preset ->
                    val isSelected = if (isDone) selectedDonePreset == preset else selectedTodoPreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                1.dp,
                                if (isSelected) colors.primary else colors.border,
                                RoundedCornerShape(6.dp)
                            )
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                            .clickable {
                                if (isDone) selectedDonePreset = preset else selectedTodoPreset = preset
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.textPrimary
                        )
                    }
                }

                // Toggle for Optional details (Amount & Note)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, if (showExtraFields || amountText.isNotBlank() || note.isNotBlank()) colors.primary else colors.border, RoundedCornerShape(6.dp))
                        .background(if (showExtraFields) colors.primary.copy(alpha = 0.1f) else colors.card)
                        .clickable { showExtraFields = !showExtraFields }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (showExtraFields) "▲ 詳細を閉じる" else "＋ メモ/金額",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (showExtraFields || amountText.isNotBlank() || note.isNotBlank()) colors.primary else colors.textSecondary
                    )
                }
            }

            // 5. Expandable Extra Fields (Note & Amount)
            if (showExtraFields || amountText.isNotBlank() || note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("メモ") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    if (isDone) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                            label = { Text("金額(¥)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. Action Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val zone = ZoneId.systemDefault()
                        val now = LocalDateTime.now()

                        if (isDone) {
                            val doneTime = when (selectedDonePreset) {
                                "15分前" -> now.minusMinutes(15)
                                "1時間前" -> now.minusHours(1)
                                "昨晩" -> now.minusDays(1).withHour(23).withMinute(0)
                                else -> now
                            }
                            val millis = doneTime.atZone(zone).toInstant().toEpochMilli()
                            val amt = amountText.toLongOrNull()
                            onSave(title, true, null, millis, amt, note.ifBlank { null }, selectedTemplateId)
                        } else {
                            val scheduledMillis = when (selectedTodoPreset) {
                                "今日中" -> null
                                "1時間後" -> now.plusHours(1).atZone(zone).toInstant().toEpochMilli()
                                "今晩(20時)" -> now.withHour(20).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                "明日(10時)" -> now.plusDays(1).withHour(10).withMinute(0).atZone(zone).toInstant().toEpochMilli()
                                else -> null
                            }
                            onSave(title, false, scheduledMillis, null, null, note.ifBlank { null }, selectedTemplateId)
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                enabled = title.isNotBlank()
            ) {
                Text(
                    text = if (isDone) "記録を保存する" else "ToDoを追加する",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
