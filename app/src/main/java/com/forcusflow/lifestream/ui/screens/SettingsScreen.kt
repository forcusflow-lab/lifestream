package com.forcusflow.lifestream.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.AppThemeMode
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTheme by viewModel.themeMode.collectAsState()
    val cutoffHour by viewModel.dayCutoffHour.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var showCutoffDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            AppHeader(
                title = "設定とカスタマイズ",
                subtitle = "テーマ切替 ＋ デイカットオフ ＋ テンプレート管理"
            )

            // Section: デザインテーマの選択
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "デザインテーマの選択",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Theme 1: Classic Warm
                ThemeSelectionCard(
                    title = "クラシック・ウォーム",
                    subtitle = "旧Wunderlist調の紙の温もりと木目調アクセント",
                    previewColor = Color(0xFF8C5A3C),
                    previewDot = Color(0xFF8C5A3C),
                    isSelected = currentTheme == AppThemeMode.CLASSIC_WARM,
                    onClick = { viewModel.setTheme(AppThemeMode.CLASSIC_WARM) }
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Theme 2: Deep Slate
                ThemeSelectionCard(
                    title = "ディープ・スレート (Dark)",
                    subtitle = "Taskito風の洗練されたダークスレート & スカイブルー",
                    previewColor = Color(0xFF1E293B),
                    previewDot = Color(0xFF38BDF8),
                    isSelected = currentTheme == AppThemeMode.DEEP_SLATE,
                    onClick = { viewModel.setTheme(AppThemeMode.DEEP_SLATE) }
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Theme 3: Pure Minimal OLED
                ThemeSelectionCard(
                    title = "ピュア・ミニマル (OLED Black)",
                    subtitle = "Niagara風の完全純黒・エメラルドグリーン (省電力)",
                    previewColor = Color(0xFF000000),
                    previewDot = Color(0xFF10B981),
                    isSelected = currentTheme == AppThemeMode.PURE_MINIMAL_OLED,
                    onClick = { viewModel.setTheme(AppThemeMode.PURE_MINIMAL_OLED) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: 生活リズム & デイカットオフ設定
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "生活リズム & デイカットオフ設定",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item 1: Cutoff Hour
                SettingActionCard(
                    title = "日付リセット境界時刻",
                    subtitle = "午前 %02d:00 (深夜の夜食は「昨晩」として集計)".format(cutoffHour),
                    actionLabel = "変更 >",
                    onClick = { showCutoffDialog = true }
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item 2: Templates
                SettingActionCard(
                    title = "テンプレート一覧管理",
                    subtitle = "水, パック, シーツ, 夜食など 件登録中",
                    actionLabel = "編集 >",
                    onClick = { showTemplatesDialog = true }
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item 3: Data Export
                SettingActionCard(
                    title = "データ書き出し & バックアップ",
                    subtitle = "SQLite DB / JSON 形式でエクスポート",
                    actionLabel = "実行 >",
                    onClick = {
                        val json = viewModel.exportJson()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, json)
                            type = "application/json"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "LifeStream Backup JSON"))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("JSONデータを書き出しました")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Item 4: Notifications
                SettingActionCard(
                    title = "通知・リマインダー",
                    subtitle = "周期推奨タスクの期限前通知 (ON)",
                    actionLabel = "設定 >",
                    onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("リマインダー通知は有効です")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Cutoff Dialog
    if (showCutoffDialog) {
        AlertDialog(
            onDismissRequest = { showCutoffDialog = false },
            title = { Text("日付リセット境界時刻") },
            text = {
                Column {
                    Text("深夜何時までを「今日」として集計するか選択してください：", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    listOf(0, 3, 4, 5, 6).forEach { hour ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDayCutoff(hour)
                                    showCutoffDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = cutoffHour == hour,
                                onClick = {
                                    viewModel.setDayCutoff(hour)
                                    showCutoffDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("午前 %02d:00".format(hour), fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCutoffDialog = false }) {
                    Text("閉じる")
                }
            }
        )
    }

    // Templates Dialog
    if (showTemplatesDialog) {
        AlertDialog(
            onDismissRequest = { showTemplatesDialog = false },
            title = { Text("テンプレート一覧 (件)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    templates.forEach { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = t.iconKey ?: "📌", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = t.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    val interval = if (t.intervalDays != null) "${t.intervalDays}日周期" else t.type
                                    Text(text = interval, fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }
                            Text(text = "${t.usageCount}回", fontSize = 12.sp, color = colors.primary)
                        }
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplatesDialog = false }) {
                    Text("完了")
                }
            }
        )
    }
}

@Composable
fun ThemeSelectionCard(
    title: String,
    subtitle: String,
    previewColor: Color,
    previewDot: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) colors.primary else colors.border,
                RoundedCornerShape(12.dp)
            )
            .background(colors.card)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color Preview Box
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .background(previewColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(previewDot)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isSelected) " (現在適用中)" else title,
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
            }

            Spacer(modifier = Modifier.width(8.dp))

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.primary,
                    unselectedColor = colors.border
                )
            )
        }
    }
}

@Composable
fun SettingActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    val colors = LifeStreamTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .background(colors.card)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primary
            )
        }
    }
}
