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
    val archivedMemos = remember(memos) { memos.filter { it.isArchived } }

    var newMemoText by remember { mutableStateOf("") }
    var memoToPromote by remember { mutableStateOf<MemoEntity?>(null) }
    var memoToEdit by remember { mutableStateOf<MemoEntity?>(null) }
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
            // Header: Minimal & Peaceful
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "メモ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

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
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "追加",
                            tint = if (newMemoText.isNotBlank()) colors.onPrimary else colors.textSecondary,
                            modifier = Modifier.size(16.dp)
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
                    // Active Memos
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
                        items(activeMemos, key = { it.id }) { memo ->
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
                                        viewModel.archiveMemo(memo)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("メモを片付けました")
                                        }
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
                                            width = if (memo.isPinned) 1.5.dp else 0.5.dp,
                                            color = if (memo.isPinned) colors.primary.copy(alpha = 0.6f) else colors.border.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(colors.card)
                                        .clickable {
                                            editText = memo.content
                                            memoToEdit = memo
                                        }
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

                                        // Footer: Date & Dual Action Buttons (✓ 片付ける / ↗ 昇格)
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
                                                            viewModel.archiveMemo(memo)
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("メモを片付けました")
                                                            }
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

                                                // Minimal circular [ ↗ ] Promote button
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(colors.primary.copy(alpha = 0.12f))
                                                        .border(0.5.dp, colors.primary.copy(alpha = 0.3f), CircleShape)
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            memoToPromote = memo
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "↗",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
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
                                                Text("戻す", fontSize = 11.5.sp, color = colors.primary)
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
                                snackbarHostState.showSnackbar("メモを削除しました")
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

                // Option 1: Convert to TODO
                PromotionOptionCard(
                    icon = "📋",
                    title = "TODO（やること）にする",
                    subtitle = "今日中、または明日以降の日時を指定してタスク化",
                    onClick = {
                        memoToPromote = null
                        viewModel.promoteMemoToTodo(targetMemo, scheduledAt = null)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("「${targetMemo.content}」をTODOに追加しました")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 2: Convert to DONE
                PromotionOptionCard(
                    icon = "✅",
                    title = "できたこと（記録）にする",
                    subtitle = "今完了した実績として今日のタイムラインに即座に記録",
                    onClick = {
                        memoToPromote = null
                        viewModel.promoteMemoToDone(targetMemo)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("「${targetMemo.content}」を完了として記録しました")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 3: Convert to Periodic Task
                PromotionOptionCard(
                    icon = "🔄",
                    title = "周期ルーティンにする (7日ごと)",
                    subtitle = "周期タブに追加して定期的なリマインド習慣にする",
                    onClick = {
                        memoToPromote = null
                        viewModel.promoteMemoToPeriodic(targetMemo, intervalDays = 7)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("「${targetMemo.content}」を周期管理に追加しました")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Option 4: Convert to Quick Template
                PromotionOptionCard(
                    icon = "⚡",
                    title = "定番テンプレートにする",
                    subtitle = "今日タブ上部のワンタップ記録ボタンに追加",
                    onClick = {
                        memoToPromote = null
                        viewModel.promoteMemoToTemplate(targetMemo)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("「${targetMemo.content}」をテンプレートに追加しました")
                        }
                    }
                )
            }
        }
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
