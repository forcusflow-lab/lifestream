package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemBottomSheet(
    templates: List<TemplateEntity>,
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
    var selectedTemplate by remember { mutableStateOf<TemplateEntity?>(null) }

    // Time offset preset (0: now, -30: 30 min ago, -60: 1 hour ago, -720: last night)
    var selectedTimePreset by remember { mutableStateOf("今") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "新規記録 / ToDo追加",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ToDo vs Done Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done (完了ログ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDone) colors.onPrimary else colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ToDo (予定)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (!isDone) colors.onPrimary else colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Template Quick Picker
            if (templates.isNotEmpty()) {
                Text(
                    text = "テンプレート連携 (任意)",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.take(4).forEach { t ->
                        val isPicked = selectedTemplate?.id == t.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (isPicked) colors.primary else colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(if (isPicked) colors.primary.copy(alpha = 0.1f) else colors.card)
                                .clickable {
                                    if (isPicked) {
                                        selectedTemplate = null
                                    } else {
                                        selectedTemplate = t
                                        if (title.isBlank()) title = t.title
                                        if (t.defaultAmount != null && amountText.isBlank()) {
                                            amountText = t.defaultAmount.toString()
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = " ",
                                fontSize = 12.sp,
                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル (例: 定食ランチ, 洗濯)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Note input
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("一行メモ・言い訳 (例: 罪悪感ゼロ)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount input
            OutlinedTextField(
                value = amountText,
                onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                label = { Text("支出金額 (¥)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Retroactive Time Presets
            Text(
                text = if (isDone) "実績時刻のプリセット" else "予定時刻のプリセット",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            val presets = listOf("今", "30分前", "1時間前", "昨晩")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = selectedTimePreset == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (isSelected) colors.primary else colors.border,
                                RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else colors.card)
                            .clickable { selectedTimePreset = preset }
                            .padding(vertical = 8.dp),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val zone = ZoneId.systemDefault()
                        val now = LocalDateTime.now()
                        val targetDateTime = when (selectedTimePreset) {
                            "30分前" -> now.minusMinutes(30)
                            "1時間前" -> now.minusHours(1)
                            "昨晩" -> now.minusDays(1).withHour(23).withMinute(0)
                            else -> now
                        }
                        val millis = targetDateTime.atZone(zone).toInstant().toEpochMilli()
                        val amt = amountText.toLongOrNull()

                        if (isDone) {
                            onSave(title, true, null, millis, amt, note.ifBlank { null }, selectedTemplate?.id)
                        } else {
                            onSave(title, false, millis, null, amt, note.ifBlank { null }, selectedTemplate?.id)
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                enabled = title.isNotBlank()
            ) {
                Text(
                    text = if (isDone) "完了ログとして記録" else "予定ToDoを追加",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
