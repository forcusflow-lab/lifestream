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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val templates by viewModel.templates.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<TemplateEntity?>(null) }
    var templateToDelete by remember { mutableStateOf<TemplateEntity?>(null) }
    var selectedCategoryTab by remember { mutableStateOf(0) } // 0: スタンプ(行動), 1: 周期タスク(メンテ)

    val actionTemplates = remember(templates) { templates.filter { it.type != "INTERVAL" } }
    val periodicTemplates = remember(templates) { templates.filter { it.type == "INTERVAL" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Column {
                Text(
                    text = "テンプレート管理",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Category Switcher Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.background)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedCategoryTab == 0) colors.primary else Color.Transparent)
                            .clickable { selectedCategoryTab = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "行動スタンプ (${actionTemplates.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCategoryTab == 0) colors.onPrimary else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedCategoryTab == 1) colors.primary else Color.Transparent)
                            .clickable { selectedCategoryTab = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "周期メンテ (${periodicTemplates.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCategoryTab == 1) colors.onPrimary else colors.textSecondary
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Add New Template Button
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedCategoryTab == 0) "＋ 新しいスタンプを追加" else "＋ 新しい周期タスクを追加",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedCategoryTab == 0) {
                    Text(
                        text = "💡 📌ピン留めしたスタンプは今日画面の上部バーに表示されます。▲▼で並び替え可能。",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                } else {
                    Text(
                        text = "💡 ▲▼で一覧での並び替え、✏️またはタップで内容を修正できます。",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val currentList = if (selectedCategoryTab == 0) actionTemplates else periodicTemplates

                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedCategoryTab == 0) "登録されたスタンプはありません" else "登録された周期タスクはありません",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        currentList.forEachIndexed { index, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { editingTemplate = t }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(text = t.iconKey ?: "📌", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = t.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.textPrimary
                                            )
                                            if (t.isPinned && t.type != "INTERVAL") {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(colors.primary.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "ピン留め中",
                                                        fontSize = 10.sp,
                                                        color = colors.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        val actionInfo = when (t.actionType) {
                                            "COUNT" -> "加算カウント (${t.unit})"
                                            "TIMER" -> "タイマー計測 (${t.unit})"
                                            else -> if (t.intervalDays != null) "${t.intervalDays}日周期" else "チェック"
                                        }
                                        val priceInfo = if (t.defaultAmount != null) " • ¥${t.defaultAmount}" else ""
                                        Text(
                                            text = "$actionInfo$priceInfo • 累計${t.usageCount}回",
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Move Up
                                    IconButton(
                                        onClick = { viewModel.moveTemplate(t, isUp = true) },
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
                                        onClick = { viewModel.moveTemplate(t, isUp = false) },
                                        enabled = index < currentList.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "下へ",
                                            tint = if (index < currentList.size - 1) colors.textPrimary else colors.textSecondary.copy(alpha = 0.25f),
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = { editingTemplate = t },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "修正",
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Pin toggle for Action Stamps
                                    if (t.type != "INTERVAL") {
                                        IconButton(
                                            onClick = { viewModel.toggleTemplatePin(t) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Text(
                                                text = if (t.isPinned) "📌" else "📍",
                                                fontSize = 15.sp
                                            )
                                        }
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = { templateToDelete = t },
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

    // Create New Template Dialog
    if (showCreateDialog) {
        TemplateEditorDialog(
            templateToEdit = null,
            initialIsPeriodic = selectedCategoryTab == 1,
            onDismiss = { showCreateDialog = false },
            onSave = { title, actionType, unit, stepValue, intervalDays, defaultAmount, iconKey, colorHex, isPinned ->
                val type = if (intervalDays != null) "INTERVAL" else if (actionType == "COUNT") "DAILY_COUNT" else "SIMPLE"
                viewModel.addTemplate(
                    title = title,
                    type = type,
                    intervalDays = intervalDays,
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

    // Edit Existing Template Dialog
    editingTemplate?.let { t ->
        TemplateEditorDialog(
            templateToEdit = t,
            initialIsPeriodic = t.type == "INTERVAL",
            onDismiss = { editingTemplate = null },
            onSave = { title, actionType, unit, stepValue, intervalDays, defaultAmount, iconKey, colorHex, isPinned ->
                val type = if (intervalDays != null) "INTERVAL" else if (actionType == "COUNT") "DAILY_COUNT" else "SIMPLE"
                viewModel.updateTemplate(
                    t.copy(
                        title = title,
                        type = type,
                        intervalDays = intervalDays,
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
                    text = "テンプレートの削除",
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
 * 新規作成・編集 兼用の包括的テンプレートエディター
 */
@Composable
fun TemplateEditorDialog(
    templateToEdit: TemplateEntity? = null,
    initialIsPeriodic: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        actionType: String,
        unit: String,
        stepValue: Int,
        intervalDays: Int?,
        defaultAmount: Long?,
        iconKey: String,
        colorHex: String,
        isPinned: Boolean
    ) -> Unit
) {
    val colors = LifeStreamTheme.colors

    var title by remember { mutableStateOf(templateToEdit?.title ?: "") }
    var selectedActionType by remember { mutableStateOf(templateToEdit?.actionType ?: "CHECK") } // "CHECK", "COUNT", "TIMER"
    var unit by remember { mutableStateOf(templateToEdit?.unit ?: "") }
    var stepValueText by remember { mutableStateOf((templateToEdit?.stepValue ?: 1).toString()) }
    var isPeriodic by remember { mutableStateOf(templateToEdit?.type == "INTERVAL" || (templateToEdit == null && initialIsPeriodic)) }
    var intervalDaysText by remember { mutableStateOf((templateToEdit?.intervalDays ?: 7).toString()) }
    var amountText by remember { mutableStateOf(templateToEdit?.defaultAmount?.toString() ?: "") }
    var isPinned by remember { mutableStateOf(templateToEdit?.isPinned ?: !initialIsPeriodic) }

    val iconList = listOf("💧", "☕", "📖", "🚶", "🧖", "🍜", "🧹", "⏱️", "💊", "🍱", "🛏️", "🧼", "🌀", "💨", "🌿", "🪴", "🚗", "🏋️", "💻", "🧘")
    var selectedIcon by remember {
        mutableStateOf(templateToEdit?.iconKey ?: if (initialIsPeriodic) "🧹" else "💧")
    }

    val colorOptions = listOf("#38BDF8", "#8C5A3C", "#3B82F6", "#10B981", "#A855F7", "#EF4444", "#F59E0B", "#6366F1", "#EC4899")
    var selectedColor by remember {
        mutableStateOf(templateToEdit?.colorHex ?: "#38BDF8")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        title = {
            Text(
                text = if (templateToEdit != null) "テンプレートの編集" else "新規テンプレート作成",
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
                        Triple("CHECK", "☑ チェック", "タップで完了記録"),
                        Triple("COUNT", "🔢 カウント", "1タップで〇杯目"),
                        Triple("TIMER", "⏱ タイム", "時間計測")
                    )
                    actionTypes.forEach { (type, label, _) ->
                        val isSel = selectedActionType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) colors.primary else Color.Transparent)
                                .clickable {
                                    selectedActionType = type
                                    if (type == "COUNT" && unit.isBlank()) unit = "杯"
                                    if (type == "TIMER" && unit.isBlank()) unit = "分"
                                }
                                .padding(vertical = 6.dp),
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

                Spacer(modifier = Modifier.height(10.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("テンプレート名 (例: 水を飲む, 英語学習)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Unit & Step value if COUNT or TIMER
                if (selectedActionType == "COUNT" || selectedActionType == "TIMER") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = { unit = it },
                            label = { Text(if (selectedActionType == "COUNT") "単位 (杯, 回)" else "単位 (分, 時間)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedActionType == "COUNT") {
                            OutlinedTextField(
                                value = stepValueText,
                                onValueChange = { if (it.all { c -> c.isDigit() }) stepValueText = it },
                                label = { Text("1回の増分") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Periodic option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPeriodic = !isPeriodic }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isPeriodic,
                        onCheckedChange = { isPeriodic = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "周期タスクとして管理する (日数指定)",
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                }

                if (isPeriodic) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = intervalDaysText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) intervalDaysText = it },
                        label = { Text("周期日数 (例: 7, 14, 30)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional default amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text("デフォルト金額 (¥) ※任意") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

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
                                .clickable { selectedIcon = ic },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = ic, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

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
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                if (!isPeriodic) {
                    Spacer(modifier = Modifier.height(10.dp))
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
                            text = "上部クイックバーにピン留めする",
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val interval = if (isPeriodic) intervalDaysText.toIntOrNull() ?: 7 else null
                        val step = stepValueText.toIntOrNull() ?: 1
                        val amt = amountText.toLongOrNull()
                        onSave(
                            title.trim(),
                            selectedActionType,
                            unit.trim(),
                            step,
                            interval,
                            amt,
                            selectedIcon,
                            selectedColor,
                            if (isPeriodic) false else isPinned
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
