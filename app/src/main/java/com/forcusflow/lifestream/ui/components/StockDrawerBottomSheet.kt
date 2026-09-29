package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDrawerBottomSheet(
    upcomingItems: List<TimelineItemEntity>,
    onDismiss: () -> Unit,
    onBringToToday: (TimelineItemEntity) -> Unit,
    onToggleDone: (TimelineItemEntity) -> Unit,
    onSelectItem: (TimelineItemEntity) -> Unit
) {
    val colors = LifeStreamTheme.colors
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    // Group items: Date-specific future items grouped by date, anytime/past rollover items grouped as undated stock
    val nowMillis = remember { System.currentTimeMillis() }
    val scheduledGrouped = remember(upcomingItems, nowMillis) {
        upcomingItems.filter { it.scheduledAt != null && it.scheduledAt > nowMillis }
            .groupBy { item ->
                LocalDateTime.ofInstant(Instant.ofEpochMilli(item.scheduledAt!!), zone).toLocalDate()
            }.toSortedMap()
    }
    val undatedStock = remember(upcomingItems, nowMillis) {
        upcomingItems.filter { it.scheduledAt == null || it.scheduledAt <= nowMillis }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📦", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "引き出し",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("閉じる", fontWeight = FontWeight.Bold, color = colors.primary, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "明日以降の予定や、手元から一時退避したタスクが静かに待機しています。",
                fontSize = 12.5.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (upcomingItems.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.background.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "引き出しは空です",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "未来の予定や退避タスクがここに静かにしまわれます",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                // 1. 未指定・退避タスク
                if (undatedStock.isNotEmpty()) {
                    Text(
                        text = "ストック（いつでも手元へ戻せます）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.background.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            undatedStock.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = colors.border.copy(alpha = 0.25f),
                                        thickness = 0.5.dp
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectItem(item) }
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (item.isDone) colors.statusDone else Color.Transparent,
                                        border = BorderStroke(1.5.dp, if (item.isDone) colors.statusDone else colors.border),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { onToggleDone(item) }
                                    ) {
                                        if (item.isDone) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Text(
                                        text = item.title,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.textPrimary,
                                        textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedButton(
                                        onClick = { onBringToToday(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, colors.primary.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "今日やる",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.primary
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. 日付指定の未来タスク
                scheduledGrouped.forEach { (date, items) ->
                    val daysUntil = ChronoUnit.DAYS.between(today, date)
                    val dateHeaderLabel = when (daysUntil) {
                        1L -> "明日 (${date.format(DateTimeFormatter.ofPattern("M/d E", Locale.JAPANESE))})"
                        2L -> "明後日 (${date.format(DateTimeFormatter.ofPattern("M/d E", Locale.JAPANESE))})"
                        else -> date.format(DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE))
                    }

                    Text(
                        text = dateHeaderLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.background.copy(alpha = 0.5f),
                        border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            items.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = colors.border.copy(alpha = 0.25f),
                                        thickness = 0.5.dp
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectItem(item) }
                                        .padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Checkbox circle
                                    Surface(
                                        shape = CircleShape,
                                        color = if (item.isDone) colors.statusDone else Color.Transparent,
                                        border = BorderStroke(1.5.dp, if (item.isDone) colors.statusDone else colors.border),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { onToggleDone(item) }
                                    ) {
                                        if (item.isDone) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Title and time
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary,
                                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null
                                        )
                                        item.scheduledAt?.let { sched ->
                                            val timeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(sched), zone)
                                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                                            if (timeStr != "00:00") {
                                                Text(
                                                    text = timeStr,
                                                    fontSize = 11.sp,
                                                    color = colors.textSecondary
                                                )
                                            }
                                        }
                                    }

                                    // Bring to today button
                                    OutlinedButton(
                                        onClick = { onBringToToday(item) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, colors.primary.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(
                                            text = "今日やる",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
