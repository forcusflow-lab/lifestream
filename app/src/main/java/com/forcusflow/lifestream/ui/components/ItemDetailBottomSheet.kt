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
    var title by remember { mutableStateOf(item.title) }
    var note by remember { mutableStateOf(item.note ?: "") }
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
                matchedTemplate?.actionType == "COUNT" || "\\d+(杯|回|個|本|皿|枚)".toRegex().containsMatchIn(item.title) -> "COUNT"
                matchedTemplate?.actionType == "TIMER" || "\\d+分".toRegex().containsMatchIn(item.title) || (item.note?.contains("\\d+分".toRegex()) == true) -> "TIMER"
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
                        val updated = item.copy(
                            title = title.trim(),
                            note = note.trim().ifBlank { null },
                            amount = parsedAmount,
                            isDone = isDone,
                            completedAt = if (isDone && item.completedAt == null) System.currentTimeMillis() else if (!isDone) null else item.completedAt
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
                            .clickable {
                                selectedActionType = typeKey
                                if (typeKey == "COUNT" && !"\\d+".toRegex().containsMatchIn(title)) {
                                    title = "$title (1${selectedUnit}目)"
                                } else if (typeKey == "TIMER" && !"\\d+分".toRegex().containsMatchIn(title)) {
                                    title = "$title (15分)"
                                    if (note.isBlank()) note = "計測時間: 15分"
                                }
                            }
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
                        text = "数量 (${selectedUnit}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val regex = "(\\d+)$selectedUnit".toRegex()
                                val match = regex.find(title)
                                val cur = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                val next = (cur - 1).coerceAtLeast(1)
                                title = if (match != null) {
                                    title.replace(regex, "$next$selectedUnit")
                                } else {
                                    "$title ($next$selectedUnit)"
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-1", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val regex = "(\\d+)$selectedUnit".toRegex()
                                val match = regex.find(title)
                                val cur = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                                val next = cur + 1
                                title = if (match != null) {
                                    title.replace(regex, "$next$selectedUnit")
                                } else {
                                    "$title ($next$selectedUnit)"
                                }
                            },
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
                                .clickable {
                                    val oldU = selectedUnit
                                    selectedUnit = u
                                    title = title.replace(oldU, u)
                                }
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
                        text = "作業時間 (分):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                val regex = "(\\d+)分".toRegex()
                                val match = regex.find(title) ?: regex.find(note)
                                val cur = match?.groupValues?.get(1)?.toIntOrNull() ?: 15
                                val next = (cur - 5).coerceAtLeast(1)
                                title = if (regex.containsMatchIn(title)) title.replace(regex, "${next}分") else "$title (${next}分)"
                                note = "計測時間: ${next}分"
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("-5分", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val regex = "(\\d+)分".toRegex()
                                val match = regex.find(title) ?: regex.find(note)
                                val cur = match?.groupValues?.get(1)?.toIntOrNull() ?: 15
                                val next = cur + 5
                                title = if (regex.containsMatchIn(title)) title.replace(regex, "${next}分") else "$title (${next}分)"
                                note = "計測時間: ${next}分"
                            },
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
    }
}
