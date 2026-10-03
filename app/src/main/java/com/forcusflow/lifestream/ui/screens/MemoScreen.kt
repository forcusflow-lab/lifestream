package com.forcusflow.lifestream.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.ui.components.AddItemBottomSheet
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.screens.AddPeriodicTaskDialog
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val memos by viewModel.memos.collectAsState()
    val activeMemos = remember(memos) { memos.filter { !it.isArchived } }
    val pinnedMemos = remember(activeMemos) { activeMemos.filter { it.isPinned } }
    val unpinnedMemos = remember(activeMemos) { activeMemos.filter { !it.isPinned } }
    val archivedMemos = remember(memos) { memos.filter { it.isArchived } }

    var newMemoText by remember { mutableStateOf("") }
    var memoToPromote by remember { mutableStateOf<MemoEntity?>(null) }
    var memoToEdit by remember { mutableStateOf<MemoEntity?>(null) }
    var memoForTodoAdd by remember { mutableStateOf<MemoEntity?>(null) }
    var memoForDoneAdd by remember { mutableStateOf<MemoEntity?>(null) }
    var memoForPeriodicAdd by remember { mutableStateOf<MemoEntity?>(null) }
    var editText by remember { mutableStateOf("") }
    var showArchivedSection by remember { mutableStateOf(false) }

    val zone = ZoneId.systemDefault()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.background)
        ) {
            // Header: Minimal & Peaceful (Unified AppHeader)
            AppHeader(
                title = "メモ",
                subtitle = "心に浮かんだアイデアや覚え書き"
            )

            // Quick Input Card (Top Instant Scratchpad)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newMemoText,
                        onValueChange = { newMemoText = it },
                        placeholder = {
                            Text(
                                text = "思いついたことをメモ...",
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (newMemoText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.insertMemo(newMemoText)
                                newMemoText = ""
                            }
                        },
                        enabled = newMemoText.isNotBlank(),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (newMemoText.isNotBlank()) colors.primary else colors.border.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "追加",
                            tint = if (newMemoText.isNotBlank()) colors.onPrimary else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Memo List
            if (activeMemos.isEmpty() && archivedMemos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📝",
                            fontSize = 36.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "まだメモはありません",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "上の入力欄からアイデアやタスクの種を残しましょう",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active Memos (Pinned & Unpinned)
                    if (activeMemos.isEmpty()) {
                        item(key = "empty_active") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "手元のメモはすべて片付きました 🌿",
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    } else {
                        // 1. Pinned Memos Section
                        if (pinnedMemos.isNotEmpty()) {
                            item(key = "header_pinned_memos") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📌 ピン留め",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary,
                                        letterSpacing = 0.3.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${pinnedMemos.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.primary.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            items(pinnedMemos, key = { "pinned_${it.id}" }) { memo ->
                                MemoCardItem(
                                    memo = memo,
                                    colors = colors,
                                    haptic = haptic,
                                    zone = zone,
                                    onEdit = {
                                        editText = memo.content
                                        memoToEdit = memo
                                    },
                                    onArchive = {
                                        viewModel.archiveMemo(memo)
                                        coroutineScope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "メモを片付けました",
                                                actionLabel = "元に戻す",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                viewModel.unarchiveMemo(memo)
                                            }
                                        }
                                    },
                                    onPromote = { memoToPromote = memo }
                                )
                            }
                        }

                        // 2. Unpinned Memos Section
                        if (unpinnedMemos.isNotEmpty()) {
                            if (pinnedMemos.isNotEmpty()) {
                                item(key = "header_unpinned_memos") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "📝 メモ",
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textSecondary,
                                            letterSpacing = 0.3.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${unpinnedMemos.size}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textSecondary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            items(unpinnedMemos, key = { it.id }) { memo ->
                                MemoCardItem(
                                    memo = memo,
                                    colors = colors,
                                    haptic = haptic,
                                    zone = zone,
                                    onEdit = {
                                        editText = memo.content
                                        memoToEdit = memo
                                    },
                                    onArchive = {
                                        viewModel.archiveMemo(memo)
                                        coroutineScope.launch {
                                            val res = snackbarHostState.showSnackbar(
                                                message = "メモを片付けました",
                                                actionLabel = "元に戻す",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                viewModel.unarchiveMemo(memo)
                                            }
                                        }
                                    },
                                    onPromote = { memoToPromote = memo }
                                )
                            }
                        }
                    }

                    // Archived Memos Section (Accordion)
                    if (archivedMemos.isNotEmpty()) {
                        item(key = "archived_header") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showArchivedSection = !showArchivedSection }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📦", fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "片付けたメモ（${archivedMemos.size}件）",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textSecondary
                                    )
                                }
                                Text(
                                    text = if (showArchivedSection) "▲" else "▼",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        if (showArchivedSection) {
                            items(archivedMemos, key = { "archived_${it.id}" }) { memo ->
                                val timeStr = remember(memo.createdAt) {
                                    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(memo.createdAt), zone)
                                    val now = LocalDateTime.now()
                                    if (dt.toLocalDate() == now.toLocalDate()) {
                                        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                                    } else if (dt.year == now.year) {
                                        dt.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
                                    } else {
                                        dt.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(0.5.dp, colors.border.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .background(colors.card.copy(alpha = 0.6f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = memo.content,
                                                fontSize = 13.sp,
                                                color = colors.textSecondary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = timeStr,
                                                fontSize = 10.5.sp,
                                                color = colors.textSecondary.copy(alpha = 0.6f)
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    viewModel.unarchiveMemo(memo)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("メモを手元に戻しました")
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("↩ 手元に戻す", fontSize = 11.5.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteMemo(memo)
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("メモを完全に削除しました")
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "削除",
                                                    tint = colors.textSecondary.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Memo Edit Modal Bottom Sheet
    if (memoToEdit != null) {
        val editing = memoToEdit!!
        ModalBottomSheet(
            onDismissRequest = { memoToEdit = null },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { memoToEdit = null }) {
                        Text("キャンセル", color = colors.textSecondary)
                    }
                    Text(
                        text = "メモを編集",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    TextButton(
                        onClick = {
                            if (editText.isNotBlank()) {
                                viewModel.updateMemo(editing.copy(content = editText.trim()))
                                memoToEdit = null
                            }
                        },
                        enabled = editText.isNotBlank()
                    ) {
                        Text(
                            text = "保存",
                            color = if (editText.isNotBlank()) colors.primary else colors.textSecondary.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.background.copy(alpha = 0.6f))
                        .border(0.5.dp, colors.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        maxLines = 8
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.toggleMemoPin(editing)
                            memoToEdit = null
                        }
                    ) {
                        Text(
                            text = if (editing.isPinned) "📌 ピン留めを解除" else "📌 ピン留めする",
                            fontSize = 13.sp,
                            color = colors.textPrimary
                        )
                    }

                    TextButton(
                        onClick = {
                            viewModel.deleteMemo(editing)
                            memoToEdit = null
                            coroutineScope.launch {
                                val res = snackbarHostState.showSnackbar(
                                    message = "メモを削除しました",
                                    actionLabel = "元に戻す",
                                    duration = SnackbarDuration.Short
                                )
                                if (res == SnackbarResult.ActionPerformed) {
                                    viewModel.insertMemo(editing)
                                }
                            }
                        }
                    ) {
                        Text("このメモを削除", color = colors.statusOverdue, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Memo Promotion Bottom Sheet (Actionable Converter)
    if (memoToPromote != null) {
        val targetMemo = memoToPromote!!
        ModalBottomSheet(
            onDismissRequest = { memoToPromote = null },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.border) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "「${targetMemo.content.take(20)}${if (targetMemo.content.length > 20) "..." else ""}」を変換",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "このメモを次のアクションとして登録します：",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Convert to TODO (編集シートを開く)
                PromotionOptionCard(
                    icon = "📋",
                    title = "TODO（やること）にする",
                    subtitle = "予定日・時間や詳細を確認してタスク化",
                    onClick = {
                        val m = targetMemo
                        memoToPromote = null
                        memoForTodoAdd = m
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Convert to DONE (記録シートを開く)
                PromotionOptionCard(
                    icon = "✅",
                    title = "できたこと（記録）にする",
                    subtitle = "実行時間や詳細を確認してタイムラインに記録",
                    onClick = {
                        val m = targetMemo
                        memoToPromote = null
                        memoForDoneAdd = m
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 3: Convert to Periodic Task (周期追加ダイアログを開く)
                PromotionOptionCard(
                    icon = "🔄",
                    title = "周期ルーティンにする",
                    subtitle = "日数間隔やゾーンを確認して周期管理に追加",
                    onClick = {
                        val m = targetMemo
                        memoToPromote = null
                        memoForPeriodicAdd = m
                    }
                )
            }
        }
    }

    // Memo -> TODO Sheet
    if (memoForTodoAdd != null) {
        val memo = memoForTodoAdd!!
        val firstLine = memo.content.lines().firstOrNull()?.trim()?.take(40) ?: memo.content.take(40)
        AddItemBottomSheet(
            onDismiss = { memoForTodoAdd = null },
            initialIsDone = false,
            initialTitle = firstLine,
            initialNote = memo.content,
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue, showOnTimeline, createdAt ->
                viewModel.addTimelineItem(
                    title = title,
                    isDone = isDone,
                    scheduledAt = scheduledAt,
                    completedAt = completedAt,
                    amount = amount,
                    note = note,
                    templateId = templateId,
                    durationSeconds = durationSeconds,
                    countValue = countValue,
                    showOnTimeline = showOnTimeline,
                    createdAt = createdAt
                )
                viewModel.archiveMemo(memo)
                memoForTodoAdd = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "「$title」をTODOに追加しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.unarchiveMemo(memo)
                    }
                }
            }
        )
    }

    // Memo -> DONE Sheet
    if (memoForDoneAdd != null) {
        val memo = memoForDoneAdd!!
        val firstLine = memo.content.lines().firstOrNull()?.trim()?.take(40) ?: memo.content.take(40)
        AddItemBottomSheet(
            onDismiss = { memoForDoneAdd = null },
            initialIsDone = true,
            initialTitle = firstLine,
            initialNote = memo.content,
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue, showOnTimeline, createdAt ->
                viewModel.addTimelineItem(
                    title = title,
                    isDone = isDone,
                    scheduledAt = scheduledAt,
                    completedAt = completedAt,
                    amount = amount,
                    note = note,
                    templateId = templateId,
                    durationSeconds = durationSeconds,
                    countValue = countValue,
                    showOnTimeline = showOnTimeline,
                    createdAt = createdAt
                )
                viewModel.archiveMemo(memo)
                memoForDoneAdd = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "「$title」を記録しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.unarchiveMemo(memo)
                    }
                }
            }
        )
    }

    // Memo -> Periodic Task Dialog
    if (memoForPeriodicAdd != null) {
        val memo = memoForPeriodicAdd!!
        val firstLine = memo.content.lines().firstOrNull()?.trim()?.take(40) ?: memo.content.take(40)
        AddPeriodicTaskDialog(
            onDismiss = { memoForPeriodicAdd = null },
            initialTitle = firstLine,
            onAdd = { title, intervalDays, iconKey, colorHex, timeOfDayZone ->
                viewModel.addTemplate(
                    title = title,
                    type = "INTERVAL",
                    intervalDays = intervalDays,
                    defaultAmount = null,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    timeOfDayZone = timeOfDayZone
                )
                viewModel.archiveMemo(memo)
                memoForPeriodicAdd = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "「$title」を周期タスクに追加しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.unarchiveMemo(memo)
                    }
                }
            }
        )
    }
}

@Composable
private fun PromotionOptionCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .background(colors.background.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoCardItem(
    memo: com.forcusflow.lifestream.data.MemoEntity,
    colors: com.forcusflow.lifestream.ui.theme.LifeStreamColors,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    zone: ZoneId,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onPromote: () -> Unit
) {
    val timeStr = remember(memo.createdAt) {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(memo.createdAt), zone)
        val now = LocalDateTime.now()
        if (dt.toLocalDate() == now.toLocalDate()) {
            dt.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else if (dt.year == now.year) {
            dt.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
        } else {
            dt.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onArchive()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.statusDone.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "片付ける",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (memo.isPinned) 1.2.dp else 0.5.dp,
                    color = if (memo.isPinned) colors.primary.copy(alpha = 0.6f) else colors.border.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .background(colors.card)
                .clickable { onEdit() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = memo.content,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (memo.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "📌",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer: Date & Dual Action Buttons (✓ 片付ける / ➔ タスクへ)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = colors.textSecondary.copy(alpha = 0.8f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minimal circular [ ✓ ] Archive button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(colors.statusDone.copy(alpha = 0.10f))
                                .border(0.5.dp, colors.statusDone.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onArchive()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "片付ける",
                                tint = colors.statusDone,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Warm pill-style "➔ タスクへ" Promote button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.primary.copy(alpha = 0.10f),
                            border = BorderStroke(0.6.dp, colors.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPromote()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "➔ タスクへ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

