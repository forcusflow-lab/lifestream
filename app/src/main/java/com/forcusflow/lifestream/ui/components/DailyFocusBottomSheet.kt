package com.forcusflow.lifestream.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme

private val FOCUS_SUGGESTIONS = listOf(
    "🎯 企画書・資料の完成",
    "⚡ 溜まったタスクの一掃",
    "💻 開発・集中作業",
    "📚 読書・スキルアップ",
    "🧘 定時退社・リフレッシュ",
    "🧹 部屋の掃除・断捨離"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyFocusBottomSheet(
    initialFocus: String,
    dateFormatted: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    val colors = LifeStreamTheme.colors
    var text by remember { mutableStateOf(initialFocus) }

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
                TextButton(onClick = onDismiss) {
                    Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                }
                Text(
                    text = "今日のフォーカス",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                TextButton(
                    onClick = {
                        onSave(text.trim())
                    },
                    enabled = text.trim().isNotEmpty()
                ) {
                    Text(
                        "保存",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (text.trim().isNotEmpty()) colors.primary else colors.textSecondary.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle / Date indication
            Text(
                text = "$dateFormatted に最も集中したいこと",
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Text Input Surface
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.background.copy(alpha = 0.6f),
                border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "例: 企画書を初稿まで書き上げる、定時退社して運動する...",
                            color = colors.textSecondary.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp),
                    maxLines = 4,
                    singleLine = false,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick suggestion chips
            Text(
                text = "クイック候補",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FOCUS_SUGGESTIONS.forEach { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.background,
                        border = BorderStroke(0.5.dp, colors.border),
                        onClick = {
                            text = suggestion
                        }
                    ) {
                        Text(
                            text = suggestion,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (initialFocus.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = {
                        onClear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "フォーカスをクリアする",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
