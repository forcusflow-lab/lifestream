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
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemBottomSheet(
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

    // Presets for Done vs ToDo
    var selectedDonePreset by remember { mutableStateOf("今") }
    var selectedTodoPreset by remember { mutableStateOf("今日中") }

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
                text = if (isDone) "やったことを記録（事実ログ）" else "これからやるToDoを追加",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ToDo vs Done Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✅ やったこと記録 (Done)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isDone) colors.onPrimary else colors.textSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isDone) colors.primary else Color.Transparent)
                        .clickable { isDone = false }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📋 これからToDo (予定)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (!isDone) colors.onPrimary else colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (isDone) "やったこと（例: 定食ランチ、シーツ洗濯）" else "やること（例: 洗濯用洗剤をポチる）") },
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
                label = { Text("一行メモ・言い訳（例: 罪悪感ゼロ、チキン南蛮定食）") },
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
                label = { Text("支出金額 (¥) ※任意") },
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

            // Time Presets
            if (isDone) {
                Text(
                    text = "いつやりましたか？",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val donePresets = listOf("今", "15分前", "1時間前", "昨晩")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    donePresets.forEach { preset ->
                        val isSelected = selectedDonePreset == preset
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
                                .clickable { selectedDonePreset = preset }
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
            } else {
                Text(
                    text = "予定の時期・タイミング",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val todoPresets = listOf("今日中", "1時間後", "今晩", "明日")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    todoPresets.forEach { preset ->
                        val isSelected = selectedTodoPreset == preset
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
                                .clickable { selectedTodoPreset = preset }
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val zone = ZoneId.systemDefault()
                        val now = LocalDateTime.now()
                        val amt = amountText.toLongOrNull()

                        if (isDone) {
                            val doneTime = when (selectedDonePreset) {
                                "15分前" -> now.minusMinutes(15)
                                "1時間前" -> now.minusHours(1)
                                "昨晩" -> now.minusDays(1).withHour(23).withMinute(0)
                                else -> now
                            }
                            val millis = doneTime.atZone(zone).toInstant().toEpochMilli()
                            onSave(title, true, null, millis, amt, note.ifBlank { null }, null)
                        } else {
                            val (scheduledMillis, finalTitle) = when (selectedTodoPreset) {
                                "今日中" -> Pair(null, if (!title.startsWith("今日中:")) "今日中: " else title)
                                "1時間後" -> Pair(now.plusHours(1).atZone(zone).toInstant().toEpochMilli(), title)
                                "今晩" -> Pair(now.withHour(20).withMinute(0).atZone(zone).toInstant().toEpochMilli(), title)
                                "明日" -> Pair(now.plusDays(1).withHour(10).withMinute(0).atZone(zone).toInstant().toEpochMilli(), title)
                                else -> Pair(null, title)
                            }
                            onSave(finalTitle, false, scheduledMillis, null, amt, note.ifBlank { null }, null)
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
                    text = if (isDone) "完了ログとして記録する" else "ToDoとして追加する",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
