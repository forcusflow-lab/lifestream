package com.forcusflow.lifestream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class MatrixCellStatus {
    DONE, OVERDUE, WARNING, TARGET, EMPTY
}

data class MatrixCellData(
    val status: MatrixCellStatus,
    val label: String,
    val isTapped: Boolean = false
)

data class MatrixRowData(
    val templateId: Long,
    val title: String,
    val intervalText: String,
    val week1: MatrixCellData,
    val week2: MatrixCellData,
    val week3: MatrixCellData
)

@Composable
fun CycleMatrixScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val templates by viewModel.templates.collectAsState()
    val allItems by viewModel.allItems.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    var selectedSegment by remember { mutableStateOf(0) } // 0: 周期タスク・一覧, 1: 週マトリクス表

    // Calculate water count for today
    val (todayStart, todayEnd) = remember(today) { viewModel.getDayRange(today) }
    val waterItemsToday = remember(allItems, todayStart, todayEnd) {
        allItems.filter { item ->
            val t = item.completedAt ?: item.scheduledAt
            t != null && t in todayStart..todayEnd && (item.templateId == 1L || item.title.contains("水"))
        }
    }
    val waterCount = waterItemsToday.size

    val periodicTemplates = remember(templates) {
        templates.filter { it.type == "INTERVAL" }
    }

    // Interactive overrides for Matrix Grid
    val cellOverrides = remember { mutableStateMapOf<String, MatrixCellData>() }

    val baseRows = remember {
        listOf(
            MatrixRowData(
                templateId = 4L,
                title = "風呂 排水ネット交換",
                intervalText = "推奨インターバル: 1週間ごと",
                week1 = MatrixCellData(MatrixCellStatus.DONE, "9/5 済"),
                week2 = MatrixCellData(MatrixCellStatus.OVERDUE, "~9/18 期限!"),
                week3 = MatrixCellData(MatrixCellStatus.OVERDUE, "~9/25")
            ),
            MatrixRowData(
                templateId = 2L,
                title = "フェイスパック",
                intervalText = "推奨インターバル: 3日ごと",
                week1 = MatrixCellData(MatrixCellStatus.DONE, "9/6 済"),
                week2 = MatrixCellData(MatrixCellStatus.DONE, "9/12 済"),
                week3 = MatrixCellData(MatrixCellStatus.TARGET, "9/15 目安")
            ),
            MatrixRowData(
                templateId = 5L,
                title = "シーツ洗濯",
                intervalText = "推奨インターバル: 1週間ごと",
                week1 = MatrixCellData(MatrixCellStatus.WARNING, "未実施"),
                week2 = MatrixCellData(MatrixCellStatus.DONE, "9/13 済"),
                week3 = MatrixCellData(MatrixCellStatus.TARGET, "9/20 目安")
            ),
            MatrixRowData(
                templateId = 6L,
                title = "洗濯槽クリーナー",
                intervalText = "推奨インターバル: 1か月ごと",
                week1 = MatrixCellData(MatrixCellStatus.DONE, "8/15 済"),
                week2 = MatrixCellData(MatrixCellStatus.WARNING, "未実施"),
                week3 = MatrixCellData(MatrixCellStatus.TARGET, "10/1 目安")
            ),
            MatrixRowData(
                templateId = 7L,
                title = "換気扇フィルター清掃",
                intervalText = "推奨インターバル: 1か月ごと",
                week1 = MatrixCellData(MatrixCellStatus.DONE, "9/5 済"),
                week2 = MatrixCellData(MatrixCellStatus.DONE, "9/5 済"),
                week3 = MatrixCellData(MatrixCellStatus.TARGET, "10/5 目安")
            ),
            MatrixRowData(
                templateId = 8L,
                title = "風呂 防カビくん煙剤",
                intervalText = "推奨インターバル: 2か月ごと",
                week1 = MatrixCellData(MatrixCellStatus.DONE, "9/5 済"),
                week2 = MatrixCellData(MatrixCellStatus.EMPTY, "-"),
                week3 = MatrixCellData(MatrixCellStatus.TARGET, "11/5 目安")
            )
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AppHeader(
                    title = "周期管理 ＆ 習慣",
                    subtitle = "「いつやったかわかる」家事・セルフケア周期トラッカー"
                )

                // Segment Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card)
                        .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSegment == 0) colors.primary else Color.Transparent)
                            .clickable { selectedSegment = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "周期タスク一覧 (いつやったか)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedSegment == 0) colors.onPrimary else colors.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedSegment == 1) colors.primary else Color.Transparent)
                            .clickable { selectedSegment = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "週マトリクス表",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedSegment == 1) colors.onPrimary else colors.textSecondary
                        )
                    }
                }

                if (selectedSegment == 0) {
                    // Periodic Cards View: 「いつやったかわかる」
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(periodicTemplates, key = { it.id }) { template ->
                            PeriodicTaskCard(
                                template = template,
                                onCompletedNow = {
                                    viewModel.recordCycleTask(template, LocalDate.now())
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("「」の完了を記録しました！")
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Weekly Matrix Grid View
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp)
                            .horizontalScroll(scrollState)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(540.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "タスク名 (推奨周期)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary,
                                modifier = Modifier.width(190.dp)
                            )
                            Box(modifier = Modifier.width(110.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "9/5 (土)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                            }
                            Box(modifier = Modifier.width(120.dp), contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.primary)
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "9/12 (土)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimary
                                    )
                                }
                            }
                            Box(modifier = Modifier.width(110.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "9/19 (土)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        HorizontalDivider(color = colors.border, thickness = 0.5.dp)

                        // Matrix Rows
                        LazyColumn(
                            modifier = Modifier
                                .width(540.dp)
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            items(baseRows, key = { it.templateId }) { row ->
                                val cell1 = cellOverrides["_1"] ?: row.week1
                                val cell2 = cellOverrides["_2"] ?: row.week2
                                val cell3 = cellOverrides["_3"] ?: row.week3

                                Row(
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .width(540.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Task Title & Interval
                                    Column(modifier = Modifier.width(190.dp)) {
                                        Text(
                                            text = row.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = row.intervalText,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }

                                    // Col 1 (9/5)
                                    Box(
                                        modifier = Modifier.width(110.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MatrixCellBadge(cell = cell1) {
                                            val updated = MatrixCellData(MatrixCellStatus.DONE, "9/5 済", true)
                                            cellOverrides["_1"] = updated
                                            val tmpl = templates.find { it.id == row.templateId }
                                            if (tmpl != null) {
                                                viewModel.recordCycleTask(tmpl, LocalDate.of(2026, 9, 5))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("9/5 完了を記録しました")
                                                }
                                            }
                                        }
                                    }

                                    // Col 2 (9/12)
                                    Box(
                                        modifier = Modifier.width(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MatrixCellBadge(cell = cell2) {
                                            val updated = MatrixCellData(MatrixCellStatus.DONE, "9/12 済", true)
                                            cellOverrides["_2"] = updated
                                            val tmpl = templates.find { it.id == row.templateId }
                                            if (tmpl != null) {
                                                viewModel.recordCycleTask(tmpl, LocalDate.of(2026, 9, 12))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("9/12 完了を記録しました")
                                                }
                                            }
                                        }
                                    }

                                    // Col 3 (9/19)
                                    Box(
                                        modifier = Modifier.width(110.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MatrixCellBadge(cell = cell3) {
                                            val updated = MatrixCellData(MatrixCellStatus.DONE, "9/19 済", true)
                                            cellOverrides["_3"] = updated
                                            val tmpl = templates.find { it.id == row.templateId }
                                            if (tmpl != null) {
                                                viewModel.recordCycleTask(tmpl, LocalDate.of(2026, 9, 19))
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("9/19 完了を記録しました")
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }

            // Sticky Bottom Water Tracker Widget
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💧 水を飲む: 今日${waterCount}杯達成 (目標: 6杯)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "週平均 4.8杯 / 日 | タイムラインに直結",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.recordWaterIntake()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("水を1杯記録しました！")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+1杯 記録",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodicTaskCard(
    template: TemplateEntity,
    onCompletedNow: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // Calculate elapsed days
    val lastDoneDate = template.lastCompletedAt?.let {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate()
    }
    val elapsedDays = lastDoneDate?.let { ChronoUnit.DAYS.between(it, today) }
    val interval = template.intervalDays ?: 7
    val isOverdue = elapsedDays != null && elapsedDays >= interval

    val lastDoneText = if (lastDoneDate != null && elapsedDays != null) {
        "前回: 月日 (日前)"
    } else {
        "未実施 (記録なし)"
    }

    val statusBadgeColor = when {
        isOverdue -> colors.statusOverdue
        elapsedDays != null && elapsedDays >= (interval - 1) -> colors.statusWarning
        else -> colors.statusDone
    }

    val statusBadgeText = when {
        isOverdue -> "❗ 期限超過 (日遅れ)"
        elapsedDays != null && elapsedDays >= (interval - 1) -> "⚠ 本日推奨"
        lastDoneDate != null -> "✓ 順調"
        else -> "未着手"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
            .background(colors.card)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = template.iconKey ?: "🧹", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = template.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "推奨周期: 日ごと",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusBadgeColor.copy(alpha = 0.12f))
                        .border(1.dp, statusBadgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusBadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusBadgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "いつやったか:",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = lastDoneText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }

                Button(
                    onClick = onCompletedNow,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "今やった！",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun MatrixCellBadge(
    cell: MatrixCellData,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors

    if (cell.status == MatrixCellStatus.EMPTY) {
        Text(text = "-", fontSize = 13.sp, color = colors.textSecondary)
        return
    }

    val (bgColor, iconChar, textColor) = when (cell.status) {
        MatrixCellStatus.DONE -> Triple(Color(0xFF16A34A), "✓", Color(0xFF16A34A))
        MatrixCellStatus.OVERDUE -> Triple(Color(0xFFDC2626), "!", Color(0xFFDC2626))
        MatrixCellStatus.WARNING -> Triple(Color(0xFFF59E0B), "!", Color(0xFFF59E0B))
        MatrixCellStatus.TARGET -> Triple(Color(0xFF8B5CF6), "◎", Color(0xFF8B5CF6))
        MatrixCellStatus.EMPTY -> Triple(Color.Transparent, "", colors.textSecondary)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = iconChar,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = cell.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
