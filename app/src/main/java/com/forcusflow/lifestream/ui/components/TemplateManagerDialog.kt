package com.forcusflow.lifestream.ui.components

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.forcusflow.lifestream.viewmodel.MainViewModel

@Composable
fun TemplateManagerDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val templates by viewModel.templates.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    var templateToDelete by remember { mutableStateOf<TemplateEntity?>(null) }

    // 日常のクイック記録のみを抽出（周期タスクは周期・ルーティンタブで一元管理）
    val quickActionTemplates = remember(templates) {
        templates.filter { it.type != "INTERVAL" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Column {
                Text(
                    text = "クイック記録の管理",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "今日タブの上部バーや＋登録で使う日常アクション",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Add New Quick Record Button
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCreateDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "＋ クイック記録を追加",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "💡 📌ピン留めした項目は今日画面の上部バーに常駐します。▲▼で並び替え可能。",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (quickActionTemplates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "登録されたクイック記録はありません",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        quickActionTemplates.forEachIndexed { index, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        editingTemplate = t
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 左側: アイコン + タイトル + サブ情報（無駄な改行なし）
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = t.iconKey ?: "📌", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = t.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary,
                                            maxLines = 1
                                        )
                                        val actionInfo = when (t.actionType) {
                                            "COUNT" -> "カウント (${t.unit})"
                                            "TIMER" -> "時間計測 (${t.unit})"
                                            else -> "チェック"
                                        }
                                        val priceInfo = if (t.defaultAmount != null) " • ¥${t.defaultAmount}" else ""
                                        Text(
                                            text = "$actionInfo$priceInfo • 累計${t.usageCount}回",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // 右側操作ボタン群 (▲, ▼, ✏️, 📌/📍, 🗑)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.moveTemplate(t, isUp = true)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "上へ",
                                            tint = if (index > 0) colors.textPrimary else colors.textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.moveTemplate(t, isUp = false)
                                        },
                                        enabled = index < quickActionTemplates.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下へ",
                                            tint = if (index < quickActionTemplates.size - 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            editingTemplate = t
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "修正",
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Pin toggle (鮮やかな📌 vs 薄いグレー📍で一目瞭然に表現)
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.toggleTemplatePin(t)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text(
                                            text = if (t.isPinned) "📌" else "📍",
                                            fontSize = 15.sp,
                                            modifier = if (t.isPinned) Modifier else Modifier.alpha(0.3f)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            templateToDelete = t
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "削除",
                                            tint = colors.statusOverdue.copy(alpha = 0.8f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    )

    // Create New Quick Record Dialog
    if (showCreateDialog) {
        TemplateEditorDialog(
            templateToEdit = null,
            onDismiss = { showCreateDialog = false },
            onSave = { title, actionType, unit, stepValue, defaultAmount, iconKey, colorHex, isPinned ->
                val type = if (actionType == "COUNT") "DAILY_COUNT" else "SIMPLE"
                viewModel.addTemplate(
                    title = title,
                    type = type,
                    intervalDays = null,
                    defaultAmount = defaultAmount,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    actionType = actionType,
                    unit = unit,
                    stepValue = stepValue,
                    isPinned = isPinned
                )
                showCreateDialog = false
            }
        )
    }

    // Edit Existing Quick Record Dialog
    editingTemplate?.let { t ->
        TemplateEditorDialog(
            templateToEdit = t,
            onDismiss = { editingTemplate = null },
            onSave = { title, actionType, unit, stepValue, defaultAmount, iconKey, colorHex, isPinned ->
                val type = if (actionType == "COUNT") "DAILY_COUNT" else "SIMPLE"
                viewModel.updateTemplate(
                    t.copy(
                        title = title,
                        type = type,
                        intervalDays = null,
                        defaultAmount = defaultAmount,
                        iconKey = iconKey,
                        colorHex = colorHex,
                        actionType = actionType,
                        unit = unit,
                        stepValue = stepValue,
                        isPinned = isPinned
                    )
                )
                editingTemplate = null
            }
        )
    }

    // Delete Confirmation Dialog
    templateToDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = {
                Text(
                    text = "クイック記録の削除",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "「${t.title}」を削除してもよろしいですか？\n※これまでに記録したログデータは削除されません。",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteTemplate(t)
                        templateToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusOverdue)
                ) {
                    Text("削除する")
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

/**
 * 新規作成・編集 兼用のクイック記録エディター
 */
@Composable
fun TemplateEditorDialog(
    templateToEdit: TemplateEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        actionType: String,
        unit: String,
        stepValue: Int,
        defaultAmount: Long?,
        iconKey: String,
        colorHex: String,
        isPinned: Boolean
    ) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    var title by remember { mutableStateOf(templateToEdit?.title ?: "") }
    var selectedActionType by remember { mutableStateOf(templateToEdit?.actionType ?: "CHECK") } // "CHECK", "COUNT", "TIMER"
    var unit by remember {
        mutableStateOf(
            templateToEdit?.unit ?: when (templateToEdit?.actionType) {
                "COUNT" -> "杯"
                "TIMER" -> "分"
                else -> ""
            }
        )
    }
    var stepValueText by remember { mutableStateOf((templateToEdit?.stepValue ?: 1).toString()) }
    var amountText by remember { mutableStateOf(templateToEdit?.defaultAmount?.toString() ?: "") }
    var isPinned by remember { mutableStateOf(templateToEdit?.isPinned ?: true) }

    val iconList = listOf("📚", "🧹", "💧", "🍪", "☕", "📖", "🚶", "🧖", "🍜", "⏱️", "💊", "🍱", "🛏️", "🧼", "🌀", "💨", "🌿", "🪴", "🚗", "🏋️", "💻", "🧘")
    var selectedIcon by remember {
        mutableStateOf(templateToEdit?.iconKey ?: "💧")
    }

    val colorOptions = listOf("#3B82F6", "#10B981", "#38BDF8", "#F59E0B", "#8C5A3C", "#A855F7", "#EF4444", "#6366F1", "#EC4899")
    var selectedColor by remember {
        mutableStateOf(templateToEdit?.colorHex ?: "#3B82F6")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = if (templateToEdit != null) "クイック記録の編集" else "新規クイック記録の作成",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // ActionType Selector (CHECK, COUNT, TIMER)
                Text(
                    text = "アクションタイプ:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    val actionTypes = listOf(
                        Triple("CHECK", "☑ チェック", "タップで完了"),
                        Triple("COUNT", "🔢 カウント", "回数・杯数"),
                        Triple("TIMER", "⏱ 時間", "作業時間の計測")
                    )
                    actionTypes.forEach { (type, label, _) ->
                        val isSel = selectedActionType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) colors.primary else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedActionType = type
                                    if (type == "COUNT" && (unit.isBlank() || unit == "分" || unit == "時間")) {
                                        unit = "杯"
                                    }
                                    if (type == "TIMER" && (unit.isBlank() || unit != "分" && unit != "時間")) {
                                        unit = "分"
                                    }
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) colors.onPrimary else colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名前 (例: 勉強, 水を飲む, お菓子)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Unit & Step value if COUNT or TIMER
                if (selectedActionType == "COUNT") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "単位と増分:", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text("単位") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = stepValueText,
                            onValueChange = { if (it.all { c -> c.isDigit() }) stepValueText = it },
                            label = { Text("1回の増分") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // カウント専用の単位プリセット（「分」は完全排除）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("杯", "個", "回", "本", "錠", "枚").forEach { u ->
                            val isSel = unit == u
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) colors.primary.copy(alpha = 0.15f) else colors.background)
                                    .border(0.5.dp, if (isSel) colors.primary else colors.border, RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        unit = u
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) colors.primary else colors.textPrimary
                                )
                            }
                        }
                    }
                } else if (selectedActionType == "TIMER") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "時間単位:", fontSize = 12.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("分", "時間").forEach { u ->
                            val isSel = unit == u
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) colors.primary.copy(alpha = 0.15f) else colors.background)
                                    .border(1.dp, if (isSel) colors.primary else colors.border, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        unit = u
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) colors.primary else colors.textPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Optional default amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("デフォルト金額 (¥) ※任意") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Icon Picker
                Text(text = "アイコン:", fontSize = 12.sp, color = colors.textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    iconList.forEach { ic ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (selectedIcon == ic) colors.primary else colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(if (selectedIcon == ic) colors.primary.copy(alpha = 0.15f) else colors.card)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedIcon = ic
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = ic, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Color Picker
                Text(text = "カラー:", fontSize = 12.sp, color = colors.textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorOptions.forEach { hex ->
                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parsedColor)
                                .border(
                                    2.dp,
                                    if (selectedColor == hex) Color.White else Color.Transparent,
                                    CircleShape
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedColor = hex
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pin to top bar checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPinned = !isPinned },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "今日画面の上部バーにピン留めする",
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val step = stepValueText.toIntOrNull() ?: 1
                        val amt = amountText.toLongOrNull()
                        onSave(
                            title.trim(),
                            selectedActionType,
                            unit.trim(),
                            step,
                            amt,
                            selectedIcon,
                            selectedColor,
                            isPinned
                        )
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(if (templateToEdit != null) "変更を保存" else "作成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
