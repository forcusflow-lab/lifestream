package com.forcusflow.lifestream.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.forcusflow.lifestream.widget.WidgetSettingsManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.components.AddItemBottomSheet
import com.forcusflow.lifestream.ui.components.DailyFocusBottomSheet
import com.forcusflow.lifestream.ui.components.ItemDetailBottomSheet
import com.forcusflow.lifestream.ui.components.StockDrawerBottomSheet
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val templates by viewModel.templates.collectAsState()
    val pinnedTemplates by viewModel.pinnedTemplates.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val activeTimerTemplate by viewModel.activeTimerTemplate.collectAsState()
    val activeTimerItem by viewModel.activeTimerItem.collectAsState()
    val timerSeconds by viewModel.timerElapsedSeconds.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showAddSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<TimelineItemEntity?>(null) }
    var showTemplateManagerDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showFocusSheet by remember { mutableStateOf(false) }
    var showDrawerSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var isFutureTrayExpanded by remember {
        mutableStateOf(WidgetSettingsManager.isFutureTrayExpanded(context))
    }

    val openAddRequested by viewModel.openAddSheetRequested.collectAsState()
    LaunchedEffect(openAddRequested) {
        if (openAddRequested) {
            showAddSheet = true
            viewModel.consumeOpenAddSheetRequest()
        }
    }

    val cutoffHour by viewModel.dayCutoffHour.collectAsState()

    // タイムライン計算を一元化（10秒ごとの無駄な全体再計算を防止）
    val timelineData = remember(allItems, templates, cutoffHour) {
        com.forcusflow.lifestream.domain.TodayTimelineCalculator.calculate(
            allItems = allItems,
            templates = templates,
            dateProvider = viewModel.dateProvider,
            cutoffHour = cutoffHour
        )
    }
    val today = timelineData.logicalDate
    val todayDateFormatted = timelineData.dateFormatted
    val todayDateKey = remember(today) { today.toString() }
    val zone = viewModel.dateProvider.zoneId
    val dailyFocus by viewModel.getDailyFocus(todayDateKey).collectAsState(initial = null)

    val todayDoneItems = timelineData.doneItems
    val todayPendingItems = timelineData.pendingHandItems
    val todayTimelinePendingItems = timelineData.timelinePendingItems
    val todayTrayPendingItems = timelineData.trayPendingItems
    val todayItems = remember(todayDoneItems, todayPendingItems) {
        todayDoneItems + todayPendingItems
    }
    val upcomingItems = timelineData.drawerStockItems

    // NOWライン表示用時刻のみを定期更新（全体再コンポーズを防止）
    var nowTimeStr by remember { mutableStateOf(viewModel.dateProvider.formatTime(viewModel.dateProvider.nowLocalTime())) }
    var currentHour by remember { mutableIntStateOf(viewModel.dateProvider.nowLocalTime().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L) // 30秒更新で十分
            val nowTime = viewModel.dateProvider.nowLocalTime()
            nowTimeStr = viewModel.dateProvider.formatTime(nowTime)
            currentHour = nowTime.hour
        }
    }

    // 周期タスク（今日実施推奨のもの、見送り中のものは除外）
    // NOWライン下に浮上するアクティブ周期タスク
    val activePeriodicHabits = remember(timelineData) {
        timelineData.activePeriodicTemplates
            .map { Triple(it.template, (it.daysSinceLastDone ?: 0L) > (it.template.intervalDays ?: 7), it.daysSinceLastDone ?: 0L) }
    }
    // トレイ内で待機する周期タスク（指定時間帯以外のもの等）
    val trayPeriodicHabits = remember(timelineData) {
        timelineData.trayPeriodicTemplates
            .map { Triple(it.template, (it.daysSinceLastDone ?: 0L) > (it.template.intervalDays ?: 7), it.daysSinceLastDone ?: 0L) }
    }

    val listState = rememberLazyListState()
    var completingItemIds by remember { mutableStateOf(setOf<Long>()) }
    var previousDoneCount by rememberSaveable { mutableIntStateOf(-1) }

    // Open around NOW once, then preserve the user's scroll position.
    // When a new completion is added, follow NOW only if it was already visible.
    LaunchedEffect(today, todayDoneItems.size) {
        val previousCount = previousDoneCount
        val currentCount = todayDoneItems.size
        val isInitialLoad = previousCount < 0
        val completionAdded = previousCount >= 0 && currentCount > previousCount
        val wasNearNow = listState.layoutInfo.visibleItemsInfo.any { it.key == "now_line" } ||
            listState.firstVisibleItemIndex >= (previousCount - 2).coerceAtLeast(0)

        if ((isInitialLoad && (currentCount > 0 || todayPendingItems.isNotEmpty())) ||
            (completionAdded && wasNearNow)
        ) {
            // Show the latest three completed items by default; older items remain
            // reachable by swiping upward in the same LazyColumn.
            listState.animateScrollToItem((currentCount - 3).coerceAtLeast(0))
        }
        previousDoneCount = currentCount
    }

    // Ambient gradient based on time of day (Morning / Afternoon / Twilight / Night)
    // 選択中のテーマ背景色をベースに、時間帯による明度（ルミナンス）を微細に変化させる
    val isDarkTheme = colors.isDark
    val ambientGradientBrush = remember(currentHour, colors.background, isDarkTheme) {
        val base = colors.background
        fun adjustLuminance(c: Color, factor: Float): Color {
            return Color(
                red = (c.red * factor).coerceIn(0f, 1f),
                green = (c.green * factor).coerceIn(0f, 1f),
                blue = (c.blue * factor).coerceIn(0f, 1f),
                alpha = c.alpha
            )
        }
        val (topFactor, bottomFactor) = when (currentHour) {
            in 5..10 -> { // 朝: 上部が澄んだ清涼感 (+3%)
                if (isDarkTheme) Pair(1.04f, 1.0f) else Pair(1.02f, 0.99f)
            }
            in 11..16 -> { // 昼: ほぼ均一のやわらかな自然光
                if (isDarkTheme) Pair(1.02f, 0.98f) else Pair(1.01f, 0.99f)
            }
            in 17..19 -> { // 夕方: 上部にわずかな落ち着き
                if (isDarkTheme) Pair(0.98f, 0.95f) else Pair(0.99f, 0.97f)
            }
            else -> { // 夜: 深い静寂 (-3〜-5%)
                if (isDarkTheme) Pair(0.95f, 0.90f) else Pair(0.98f, 0.96f)
            }
        }
        Brush.verticalGradient(
            listOf(adjustLuminance(base, topFactor), adjustLuminance(base, bottomFactor))
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "記録・ToDo追加", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = colors.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientGradientBrush)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Calm, quiet paper-like Header: Pure Date and breath of air
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = todayDateFormatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }

                // Quiet, borderless "Today's Focus" subtitle (手帳の静かな見出し)
                val focusText = dailyFocus?.content
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showFocusSheet = true
                        }
                        .padding(vertical = 4.dp)
                ) {
                    if (focusText.isNullOrBlank()) {
                        Text(
                            text = "今日のフォーカスを一言記す...",
                            fontSize = 13.sp,
                            color = colors.textSecondary.copy(alpha = 0.6f),
                            letterSpacing = 0.2.sp
                        )
                    } else {
                        Text(
                            text = "🎯",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = focusText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            letterSpacing = 0.2.sp
                        )
                    }
                }

            // Quick Record Bar (ActionTypes: COUNT, TIMER, CHECK)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pinnedTemplates.forEach { t ->
                    val isTimer = t.actionType == "TIMER"
                    val isTimerActive = isTimer && activeTimerTemplate?.id == t.id
                    val isCount = t.actionType == "COUNT"
                    val tItemsToday = todayItems.filter { it.templateId == t.id }

                    val chipLabel = when {
                        isTimerActive -> {
                            val mins = timerSeconds / 60
                            val secs = timerSeconds % 60
                            "${t.iconKey ?: "⏱️"} %02d:%02d 計測中".format(mins, secs)
                        }
                        isTimer -> {
                            val todayTimerSec = tItemsToday.sumOf { item ->
                                item.durationSeconds
                                    ?: "\\((\\d+)分\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
                                    ?: "\\((\\d+)秒\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                                    ?: 0
                            }
                            if (todayTimerSec > 0) {
                                val timeText = when {
                                    todayTimerSec < 60 -> "${todayTimerSec}秒"
                                    todayTimerSec < 3600 -> "${(todayTimerSec + 30) / 60}分"
                                    else -> {
                                        val hrs = todayTimerSec / 3600
                                        val mins = (todayTimerSec % 3600) / 60
                                        if (mins > 0) "${hrs}時間${mins}分" else "${hrs}時間"
                                    }
                                }
                                "${t.iconKey ?: "⏱️"} ${t.title} (${timeText})"
                            } else {
                                "${t.iconKey ?: "⏱️"} ${t.title}"
                            }
                        }
                        isCount -> {
                            val count = tItemsToday.sumOf { it.countValue ?: 1 }
                            if (count > 0) {
                                val unitStr = if (t.unit.isNotBlank()) t.unit else "杯"
                                "${t.iconKey ?: "💧"} ${t.title} (${count}${unitStr})"
                            } else {
                                "${t.iconKey ?: "💧"} ${t.title}"
                            }
                        }
                        else -> {
                            if (tItemsToday.any { it.isDone }) {
                                "${t.iconKey ?: "📌"} ${t.title} (済)"
                            } else {
                                "${t.iconKey ?: "📌"} ${t.title}"
                            }
                        }
                    }

                    val chipBorderColor = when {
                        isTimerActive -> Color(0xFFEF4444)
                        isCount -> Color(0xFF0284C7)
                        t.colorHex != null -> Color(android.graphics.Color.parseColor(t.colorHex))
                        else -> colors.primary
                    }

                    val chipBgColor = when {
                        isTimerActive -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        isCount -> Color(0xFF0284C7).copy(alpha = 0.08f)
                        else -> chipBorderColor.copy(alpha = 0.08f)
                    }

                    var showChipMenu by remember { mutableStateOf(false) }

                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isTimerActive) {
                                        Modifier.border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                                    } else Modifier
                                )
                                .background(chipBgColor)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.quickRecordTemplate(t) { savedItem ->
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "${savedItem.title} を記録しました",
                                                    actionLabel = "元に戻す",
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.undoLastItem()
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showChipMenu = true
                                    }
                                )
                                .padding(horizontal = 13.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chipLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = chipBorderColor
                            )
                        }

                        DropdownMenu(
                            expanded = showChipMenu,
                            onDismissRequest = { showChipMenu = false },
                            modifier = Modifier.background(colors.card)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (t.isPinned) "ピン留めを解除" else "ピン留めする",
                                        color = colors.textPrimary,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    showChipMenu = false
                                    viewModel.toggleTemplatePin(t)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "テンプレートを編集...",
                                        color = colors.textPrimary,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    showChipMenu = false
                                    showTemplateManagerDialog = true
                                }
                            )
                        }
                    }
                }

                // 洗練された控えめな「⚙」テンプレート管理ボタン
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.card.copy(alpha = 0.6f))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showTemplateManagerDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙",
                        fontSize = 13.5.sp
                    )
                }

                if (pinnedTemplates.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.card.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showTemplateManagerDialog = true
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text("＋ 記録テンプレートを設定", fontSize = 12.5.sp, color = colors.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Taskito-style Vertical Continuous Timeline
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
            ) {
                // 1. 過去の実績ログ（DONEログ）：全件を通常スクロールで振り返れる
                if (todayDoneItems.isNotEmpty()) {
                    itemsIndexed(todayDoneItems, key = { _, item -> item.id }) { index, item ->
                        TaskitoTimelineItemRow(
                            item = item,
                            templates = templates,
                            isFirst = index == 0,
                            isLast = false,
                            onToggle = { viewModel.toggleItemDone(item) },
                            onClick = { itemToEdit = item },
                            onDelete = {
                                viewModel.deleteItem(item)
                                coroutineScope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "${item.title} を削除しました",
                                        actionLabel = "元に戻す",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreItem(item)
                                    }
                                }
                            }
                        )
                    }
                }

                // 2. NOWライン（現在時刻）
                item(key = "now_line") {
                    val hasSurfacedItems = activePeriodicHabits.isNotEmpty() || todayTimelinePendingItems.isNotEmpty()
                    TaskitoNowLineRow(
                        timeStr = nowTimeStr,
                        isFirst = todayDoneItems.isEmpty(),
                        isLast = !hasSurfacedItems && todayTrayPendingItems.isEmpty() && trayPeriodicHabits.isEmpty()
                    )

                    if (todayDoneItems.isEmpty() && todayPendingItems.isEmpty() && activePeriodicHabits.isEmpty() && trayPeriodicHabits.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.card.copy(alpha = 0.5f))
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "🌿 清々しい1日のはじまり",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "上の記録バーから、今日の最初の行動をワンタップで記録しましょう 🌿",
                                    fontSize = 12.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 2-B. タイムライン直載せ：現在アクティブな周期タスク（朝/昼/夜のゾーン合致）
                if (activePeriodicHabits.isNotEmpty()) {
                    itemsIndexed(activePeriodicHabits, key = { _, (t, _, _) -> "active_periodic_${t.id}" }) { index, (tmpl, _, _) ->
                        TaskitoPeriodicSurfacedRow(
                            template = tmpl,
                            today = today,
                            isFirst = false,
                            isLast = false,
                            onComplete = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.recordCycleTask(tmpl, today)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("「${tmpl.title}」を完了しました！ 🌿")
                                }
                            },
                            onPostpone = {
                                viewModel.postponeCycleTask(tmpl, 1)
                                coroutineScope.launch {
                                    val nextDate = today.plusDays(1)
                                    snackbarHostState.showSnackbar("「${tmpl.title}」を次回まで見送りました（次回目安: ${nextDate.monthValue}/${nextDate.dayOfMonth}）")
                                }
                            }
                        )
                    }
                }

                // 2-C. タイムライン直載せ：時間が近づいた/過ぎた予定ToDo ＆ 📌固定された終日ToDo
                if (todayTimelinePendingItems.isNotEmpty()) {
                    itemsIndexed(todayTimelinePendingItems, key = { _, item -> "timeline_pending_${item.id}" }) { index, item ->
                        val isCompleting = completingItemIds.contains(item.id)
                        val isTimerRunning = activeTimerItem?.id == item.id
                        TaskitoTimelinePendingRow(
                            item = item,
                            templates = templates,
                            isCompleting = isCompleting,
                            isTimerRunning = isTimerRunning,
                            isFirst = false,
                            isLast = false,
                            onToggle = {
                                if (!isCompleting) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    completingItemIds = completingItemIds + item.id
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(350)
                                        viewModel.toggleItemDone(item)
                                        completingItemIds = completingItemIds - item.id
                                    }
                                }
                            },
                            onStartTimer = { viewModel.startItemTimer(item) },
                            onClick = { itemToEdit = item }
                        )
                    }
                }

                // 3. 「これからの歩み」統一トレイ
                item(key = "focus_tray") {
                    Spacer(modifier = Modifier.height(10.dp))

                    // セクション見出し ＋ 右端に「引き出し」ボタンをスマート配置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val next = !isFutureTrayExpanded
                                    isFutureTrayExpanded = next
                                    WidgetSettingsManager.setFutureTrayExpanded(context, next)
                                    viewModel.notifyWidgetUpdate()
                                }
                                .padding(vertical = 3.dp, horizontal = 4.dp)
                        ) {
                            val futureCount = trayPeriodicHabits.size + todayTrayPendingItems.size
                            Text(
                                text = if (futureCount > 0) "これからの歩み ($futureCount)" else "これからの歩み",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFutureTrayExpanded) "▲" else "▼",
                                fontSize = 10.sp,
                                color = colors.textSecondary.copy(alpha = 0.6f)
                            )
                        }

                        // トレイ見出し右端の「📦 引き出し」ボタン（枠線を排した柔らかなピル）
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.card.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showDrawerSheet = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📦", fontSize = 11.5.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "引き出し (${upcomingItems.size})",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }

                    // 統一トレイ（Surface: 枠線を排し背景と調和するアンビエント面）
                    AnimatedVisibility(
                        visible = isFutureTrayExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.card.copy(alpha = 0.65f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                // A. 周期タスク（時間外または待機中のルーティン）
                                if (trayPeriodicHabits.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp)
                                    ) {
                                        Text(
                                            text = "🌿 今日のルーティン",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.primary.copy(alpha = 0.85f),
                                            letterSpacing = 0.3.sp
                                        )
                                    }

                                    trayPeriodicHabits.forEachIndexed { index, (tmpl, _, _) ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = colors.border.copy(alpha = 0.15f),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 完了チェック丸ボタン
                                            Surface(
                                                shape = CircleShape,
                                                color = Color.Transparent,
                                                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.recordCycleTask(tmpl, today)
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar("「${tmpl.title}」を完了しました！ 🌿")
                                                        }
                                                    }
                                            ) {}

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // アイコン＋タスク名
                                            Text(
                                                text = "${tmpl.iconKey ?: "🔄"} ${tmpl.title}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.textPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // 「今回は見送る」（完了とは別扱い・穏やかな表現）
                                            Text(
                                                text = "今回は見送る",
                                                fontSize = 10.5.sp,
                                                color = colors.textSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        viewModel.postponeCycleTask(tmpl, 1)
                                                        coroutineScope.launch {
                                                            val nextDate = today.plusDays(1)
                                                            snackbarHostState.showSnackbar("「${tmpl.title}」を次回まで見送りました（次回目安: ${nextDate.monthValue}/${nextDate.dayOfMonth}）")
                                                        }
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // 周期タスクとToDoの間の仕切り
                                if (trayPeriodicHabits.isNotEmpty() && todayTrayPendingItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    HorizontalDivider(
                                        color = colors.border.copy(alpha = 0.25f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }

                                // B. やること（待機中未完了ToDo）
                                if (todayTrayPendingItems.isNotEmpty()) {
                                    if (trayPeriodicHabits.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 3.dp, horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = "📋 やること",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textSecondary.copy(alpha = 0.85f),
                                                letterSpacing = 0.3.sp
                                            )
                                        }
                                    }

                                    todayTrayPendingItems.forEachIndexed { index, item ->
                                        val isTimerRunning = activeTimerItem?.id == item.id
                                        val isCompleting = completingItemIds.contains(item.id)
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = colors.border.copy(alpha = 0.15f),
                                                thickness = 0.5.dp,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { itemToEdit = item }
                                                .padding(vertical = 4.dp, horizontal = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // A. 瞬間チェック系：円形チェック枠 (○: 完了アニメーションで手書きチェック＆ディレイ)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isCompleting || item.isDone) colors.statusDone else Color.Transparent,
                                                border = BorderStroke(1.dp, if (isCompleting || item.isDone) colors.statusDone else colors.border.copy(alpha = 0.7f)),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        if (!isCompleting) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            completingItemIds = completingItemIds + item.id
                                                            coroutineScope.launch {
                                                                kotlinx.coroutines.delay(350)
                                                                viewModel.toggleItemDone(item)
                                                                completingItemIds = completingItemIds - item.id
                                                            }
                                                        }
                                                    }
                                            ) {
                                                if (isCompleting || item.isDone) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // タイトル ＋ 時刻指定表示
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // 時刻指定がある場合は左端に太字バッジとして配置（見逃し防止！）
                                                item.scheduledAt?.let { sched ->
                                                    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                                                    if (timeStr != "00:00") {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = colors.primary.copy(alpha = 0.12f),
                                                            modifier = Modifier.padding(end = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = timeStr,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = colors.primary,
                                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = item.title,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isCompleting) colors.textSecondary.copy(alpha = 0.5f) else colors.textPrimary,
                                                    textDecoration = if (isCompleting) TextDecoration.LineThrough else TextDecoration.None,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // B. 時間カウント系（作業・没頭）：タイマー再生ボタン
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isTimerRunning) colors.primary.copy(alpha = 0.15f) else Color.Transparent,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.startItemTimer(item)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (isTimerRunning) {
                                                        Text(
                                                            text = "⏹",
                                                            fontSize = 11.sp,
                                                            color = colors.primary
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "▶",
                                                            fontSize = 10.5.sp,
                                                            color = colors.textSecondary.copy(alpha = 0.45f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 手元タスクが0件のとき
                                if (todayTrayPendingItems.isEmpty() && trayPeriodicHabits.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "手元のタスクはありません。穏やかな時間をお過ごしください 🌿",
                                            fontSize = 12.sp,
                                            color = colors.textSecondary.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fixed Active Timer Mini-Player at bottom (does not scroll with timeline)
            if (activeTimerTemplate != null || activeTimerItem != null) {
                ActiveTimerBottomBar(
                    title = activeTimerTemplate?.title ?: activeTimerItem?.title ?: "",
                    iconKey = activeTimerTemplate?.iconKey ?: "⏱️",
                    elapsedSeconds = timerSeconds,
                    onStop = {
                        if (activeTimerTemplate != null) {
                            viewModel.stopAndSaveTimer { saved ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("「${saved.title}」を記録しました")
                                }
                            }
                        } else {
                            viewModel.stopAndSaveItemTimer { saved ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("「${saved.title}」の実績を記録しました")
                                }
                            }
                        }
                    },
                    onCancel = {
                        if (activeTimerTemplate != null) {
                            viewModel.cancelTimer()
                        } else {
                            viewModel.cancelItemTimer()
                        }
                    }
                )
            }
        }
    }
}

    // Add Sheet
    if (showAddSheet) {
        AddItemBottomSheet(
            templates = templates,
            cutoffHour = cutoffHour,
            onDismiss = { showAddSheet = false },
            onManageTemplates = {
                showAddSheet = false
                showTemplateManagerDialog = true
            },
            onSave = { title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue, showOnTimeline, createdAt ->
                viewModel.addTimelineItem(title, isDone, scheduledAt, completedAt, amount, note, templateId, durationSeconds, countValue, showOnTimeline, createdAt)
            }
        )
    }

    // Daily Focus Bottom Sheet
    if (showFocusSheet) {
        DailyFocusBottomSheet(
            initialFocus = dailyFocus?.content ?: "",
            dateFormatted = todayDateFormatted,
            onDismiss = { showFocusSheet = false },
            onSave = { newFocus ->
                viewModel.setDailyFocus(todayDateKey, newFocus)
                showFocusSheet = false
            },
            onClear = {
                viewModel.clearDailyFocus(todayDateKey)
                showFocusSheet = false
            }
        )
    }

    // Stock Drawer Bottom Sheet (引き出し)
    if (showDrawerSheet) {
        StockDrawerBottomSheet(
            upcomingItems = upcomingItems,
            onDismiss = { showDrawerSheet = false },
            onBringToToday = { item ->
                viewModel.rescheduleItemToToday(item)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("「${item.title}」を手元（今日）に引き出しました")
                }
            },
            onToggleDone = { item ->
                viewModel.toggleItemDone(item)
            },
            onSelectItem = { item ->
                itemToEdit = item
            }
        )
    }

    // Full-Text Search Bottom Sheet
    if (showSearchSheet) {
        SearchBottomSheet(
            allItems = allItems,
            onDismiss = { showSearchSheet = false },
            onSelectItem = { itemToEdit = it }
        )
    }
    if (showTemplateManagerDialog) {
        com.forcusflow.lifestream.ui.components.TemplateManagerDialog(
            viewModel = viewModel,
            onDismiss = { showTemplateManagerDialog = false }
        )
    }

    // Edit Item Sheet
    itemToEdit?.let { item ->
        ItemDetailBottomSheet(
            item = item,
            templates = templates,
            onDismiss = { itemToEdit = null },
            onSave = { updated ->
                viewModel.updateTimelineItem(updated)
                itemToEdit = null
            },
            onDelete = {
                viewModel.deleteItem(item)
                itemToEdit = null
                coroutineScope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "${item.title} を削除しました",
                        actionLabel = "元に戻す",
                        duration = SnackbarDuration.Short
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        viewModel.restoreItem(item)
                    }
                }
            },
            onPromoteToPeriodic = { targetItem, intervalDays, iconKey ->
                viewModel.promoteItemToPeriodicTemplate(
                    item = targetItem,
                    intervalDays = intervalDays,
                    iconKey = iconKey
                ) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("「${targetItem.title}」を周期タスクに追加しました（${intervalDays}日ごと）")
                    }
                }
                itemToEdit = null
            }
        )
    }
}

@Composable
fun TaskitoNowLineRow(
    timeStr: String,
    isFirst: Boolean,
    isLast: Boolean
) {
    val colors = LifeStreamTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous stem line (36.dp width exactly matches TaskitoTimelineItemRow)
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isFirst) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isLast) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
            }
            // Center red dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.nowLine)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Red NOW Line with Pill Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.nowLine)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "現在 $timeStr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.nowLine)
            )
        }
    }
}

@Composable
fun ActiveTimerBottomBar(
    title: String,
    iconKey: String = "⏱️",
    elapsedSeconds: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val timerStr = "%02d:%02d".format(mins, secs)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        shadowElevation = 6.dp,
        border = BorderStroke(1.5.dp, colors.statusOverdue.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = iconKey, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.statusOverdue)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$timerStr 計測中...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.statusOverdue
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("破棄", fontSize = 12.sp, color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onStop,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.statusDone,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("完了・保存", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskitoTimelineItemRow(
    item: TimelineItemEntity,
    templates: List<TemplateEntity> = emptyList(),
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val zone = ZoneId.systemDefault()
    val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
    val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
        .format(DateTimeFormatter.ofPattern("HH:mm"))

    val isDone = item.isDone
    val nodeColor = if (isDone) colors.statusDone else colors.card
    val nodeBorderColor = if (isDone) colors.statusDone else colors.textSecondary

    val checkScale by animateFloatAsState(
        targetValue = if (isDone) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "timelineCheckScale"
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled || dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFEF4444).copy(alpha = 0.85f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text("削除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Taskito Continuous Stem Column
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    // Vertical Continuous Line
                    Column(modifier = Modifier.fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(2.dp)
                                .background(if (isFirst) Color.Transparent else colors.border)
                                .align(Alignment.CenterHorizontally)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(2.dp)
                                .background(if (isLast) Color.Transparent else colors.border)
                                .align(Alignment.CenterHorizontally)
                        )
                    }

                    // Node Circle (Interactive - tap to toggle done, 48.dp touch target & spring bounce)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .scale(checkScale)
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(2.dp, nodeBorderColor, CircleShape)
                                .background(nodeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "完了",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Time & Title & Notes (Clicking card opens edit modal)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.card)
                        .border(0.5.dp, colors.border, RoundedCornerShape(10.dp))
                        .clickable { onClick() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = timeStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        // Clean title without synthetic brackets
                        val cleanTitle = remember(item.title) {
                            item.title
                                .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                .trim()
                        }

                        // Extracted duration in seconds (from column or fallback parsing of legacy title)
                        val durationSec = remember(item.durationSeconds, item.title) {
                            item.durationSeconds
                                ?: "\\((\\d+)分\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()?.times(60)
                                ?: "\\((\\d+)秒\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                        }

                        // Extracted count value (from column or fallback parsing of legacy title)
                        val countVal = remember(item.countValue, item.title) {
                            item.countValue
                                ?: "\\((\\d+)[杯回個本皿枚]目?\\)".toRegex().find(item.title)?.groupValues?.get(1)?.toIntOrNull()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cleanTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            if (durationSec != null && durationSec > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                val durationText = if (durationSec >= 60) "${(durationSec + 30) / 60}分" else "${durationSec}秒"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "⏱ $durationText",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }

                            if (countVal != null && countVal > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                val matched = templates.find { it.id == item.templateId }
                                val unit = matched?.unit?.ifBlank { "回" } ?: "回"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0284C7).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$countVal$unit",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }
                        }

                        val displayNote = remember(item.note) {
                            when {
                                item.note.isNullOrBlank() -> null
                                item.note in listOf("クイック記録完了", "時間計測完了", "デイリー習慣カウント") -> null
                                item.note.startsWith("計測時間: ") -> null // 二重表示防止
                                item.note.startsWith("時間計測完了 (") && item.note.endsWith(")") -> null
                                else -> item.note
                            }
                        }
                        if (displayNote != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayNote,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textSecondary
                            )
                        }
                    }

                    // Only show useful Amount badge (if present)
                    if (item.amount != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, colors.primary, RoundedCornerShape(8.dp))
                                .background(colors.card)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "¥${item.amount}",
                                fontSize = 12.sp,
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

@Composable
fun TaskitoPeriodicSurfacedRow(
    template: TemplateEntity,
    today: LocalDate,
    isFirst: Boolean,
    isLast: Boolean,
    onComplete: () -> Unit,
    onPostpone: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous stem line
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isFirst) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isLast) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // Node Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(2.dp, colors.primary.copy(alpha = 0.8f), CircleShape)
                        .background(Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Surface Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "習慣",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "${template.iconKey ?: "🔄"} ${template.title}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "今回は見送る",
                    fontSize = 11.sp,
                    color = colors.textSecondary.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onPostpone() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun TaskitoTimelinePendingRow(
    item: TimelineItemEntity,
    templates: List<TemplateEntity>,
    isCompleting: Boolean,
    isTimerRunning: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onStartTimer: () -> Unit,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    val zone = remember { ZoneId.systemDefault() }

    val schedTimeStr = remember(item.scheduledAt) {
        item.scheduledAt?.let { sched ->
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            if (t != "00:00") t else null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Continuous stem line
        Box(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isFirst) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(2.dp)
                        .background(if (isLast) Color.Transparent else colors.border)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // Node Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (isCompleting || item.isDone) colors.statusDone else colors.border.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .background(if (isCompleting || item.isDone) colors.statusDone else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleting || item.isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Surface Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (item.showOnTimeline && item.scheduledAt == null) colors.primary.copy(alpha = 0.05f) else colors.card,
            border = BorderStroke(
                1.dp,
                if (item.showOnTimeline && item.scheduledAt == null) colors.primary.copy(alpha = 0.35f) else colors.border.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (schedTimeStr != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.primary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = schedTimeStr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    } else if (item.showOnTimeline) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.primary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "📌 今日",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCompleting) colors.textSecondary.copy(alpha = 0.5f) else colors.textPrimary,
                        textDecoration = if (isCompleting) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Start Timer Button
                Surface(
                    shape = CircleShape,
                    color = if (isTimerRunning) colors.primary.copy(alpha = 0.15f) else Color.Transparent,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStartTimer()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isTimerRunning) {
                            Text(text = "⏹", fontSize = 11.sp, color = colors.primary)
                        } else {
                            Text(text = "▶", fontSize = 10.5.sp, color = colors.textSecondary.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    allItems: List<TimelineItemEntity>,
    onDismiss: () -> Unit,
    onSelectItem: (TimelineItemEntity) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val haptic = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableStateOf(0) } // 0: すべて, 1: できたこと, 2: 予定, 3: 支出のみ
    val filterLabels = listOf("すべて", "📝 できたこと", "📋 予定", "💴 支出のみ")

    val zone = remember { ZoneId.systemDefault() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }

    val filteredItems = remember(allItems, searchQuery, selectedFilterIndex) {
        allItems.filter { item ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                item.title.contains(searchQuery, ignoreCase = true) ||
                    (item.note?.contains(searchQuery, ignoreCase = true) == true)
            }

            val matchesFilter = when (selectedFilterIndex) {
                1 -> item.isDone
                2 -> !item.isDone
                3 -> (item.amount != null && item.amount > 0)
                else -> true
            }

            matchesQuery && matchesFilter
        }.sortedByDescending { it.completedAt ?: it.scheduledAt ?: 0L }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "記録・予定の全文検索",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "閉じる",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "タイトル、メモ、内容で検索...",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "クリア",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterLabels.forEachIndexed { index, label ->
                    val isSelected = selectedFilterIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) colors.primary else colors.card)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.primary else colors.border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilterIndex = index
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.onPrimary else colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${filteredItems.size}件のアイテム",
                fontSize = 12.sp,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔍",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "一致するアイテムは見つかりませんでした" else "記録がありません",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredItems.size) { idx ->
                        val item = filteredItems[idx]
                        val timestamp = item.completedAt ?: item.scheduledAt ?: System.currentTimeMillis()
                        val dateTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zone)
                            .format(dateFormatter)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card)
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectItem(item)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Pill
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isDone) colors.statusDone.copy(alpha = 0.15f) else colors.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isDone) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = colors.statusDone,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(colors.primary)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val cleanTitle = item.title
                                            .replace("\\s*\\(\\d+(分|秒)\\)".toRegex(), "")
                                            .replace("\\s*\\(\\d+[杯回個本皿枚]目?\\)".toRegex(), "")
                                            .trim()
                                        Text(
                                            text = cleanTitle,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = dateTimeStr,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    if (!item.note.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.note,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (item.amount != null && item.amount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.statusTarget.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "¥%,d".format(item.amount),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.statusTarget
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
