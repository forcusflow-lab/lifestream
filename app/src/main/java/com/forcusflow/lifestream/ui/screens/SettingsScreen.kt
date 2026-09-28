package com.forcusflow.lifestream.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forcusflow.lifestream.ui.components.AppHeader
import com.forcusflow.lifestream.ui.theme.AppThemeMode
import com.forcusflow.lifestream.ui.theme.LifeStreamTheme
import com.forcusflow.lifestream.viewmodel.MainViewModel
import com.forcusflow.lifestream.widget.TodayTimelineWidgetReceiver
import com.forcusflow.lifestream.widget.WidgetFontSize
import com.forcusflow.lifestream.widget.WidgetSettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val colors = LifeStreamTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTheme by viewModel.themeMode.collectAsState()
    val cutoffHour by viewModel.dayCutoffHour.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var widgetOpacity by remember { mutableStateOf(WidgetSettingsManager.getOpacity(context)) }
    var widgetFontSize by remember { mutableStateOf(WidgetSettingsManager.getFontSize(context)) }

    var showCutoffDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

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
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 1: Frosted Glass
                ThemeSelectionCard(
                    title = "フロステッド・グラス (Glass)",
                    subtitle = "最新すりガラス調・インディゴ＆アイススレートの透明感",
                    previewColor = Color(0xFFF1F5F9),
                    previewDot = Color(0xFF6366F1),
                    isSelected = currentTheme == AppThemeMode.FROSTED_GLASS,
                    onClick = { viewModel.setTheme(AppThemeMode.FROSTED_GLASS) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 2: Nordic Clean
                ThemeSelectionCard(
                    title = "ノルディック・スノー",
                    subtitle = "北欧ミニマリズム・純白＆クリーンなスカイブルー",
                    previewColor = Color(0xFFFAFAFA),
                    previewDot = Color(0xFF0284C7),
                    isSelected = currentTheme == AppThemeMode.NORDIC_CLEAN,
                    onClick = { viewModel.setTheme(AppThemeMode.NORDIC_CLEAN) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 3: Tokyo Minimal
                ThemeSelectionCard(
                    title = "トーキョー・モダン (Flat)",
                    subtitle = "Notion/Linear風の研ぎ澄まされたソリッドモノトーン",
                    previewColor = Color(0xFFF4F4F5),
                    previewDot = Color(0xFF18181B),
                    isSelected = currentTheme == AppThemeMode.TOKYO_MINIMAL,
                    onClick = { viewModel.setTheme(AppThemeMode.TOKYO_MINIMAL) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 4: Sage & Linen
                ThemeSelectionCard(
                    title = "セージ＆リネン (Organic)",
                    subtitle = "くすみグリーンと生成りリネンが心地よい癒やし系",
                    previewColor = Color(0xFFF5F4EE),
                    previewDot = Color(0xFF2E6F52),
                    isSelected = currentTheme == AppThemeMode.SAGE_LINEN,
                    onClick = { viewModel.setTheme(AppThemeMode.SAGE_LINEN) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 5: Classic Warm
                ThemeSelectionCard(
                    title = "クラシック・ウォーム",
                    subtitle = "旧Wunderlist調の紙の温もりと木目調アクセント",
                    previewColor = Color(0xFFF4F1EA),
                    previewDot = Color(0xFF8C5A3C),
                    isSelected = currentTheme == AppThemeMode.CLASSIC_WARM,
                    onClick = { viewModel.setTheme(AppThemeMode.CLASSIC_WARM) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 6: Deep Slate
                ThemeSelectionCard(
                    title = "ディープ・スレート (Dark)",
                    subtitle = "Taskito風の洗練されたダークスレート & スカイブルー",
                    previewColor = Color(0xFF1E293B),
                    previewDot = Color(0xFF38BDF8),
                    isSelected = currentTheme == AppThemeMode.DEEP_SLATE,
                    onClick = { viewModel.setTheme(AppThemeMode.DEEP_SLATE) }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Theme 7: Pure Minimal OLED
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

            // Section: ホーム画面ウィジェット設定 (手帳ウィジェット)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "ホーム画面ウィジェット設定 (手帳ウィジェット)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "背景の透過率 (壁紙の透け感)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${(widgetOpacity * 100).toInt()}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = widgetOpacity,
                            onValueChange = {
                                widgetOpacity = it
                                WidgetSettingsManager.setOpacity(context, it)
                                TodayTimelineWidgetReceiver.updateAll(context)
                            },
                            valueRange = 0.0f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.primary,
                                activeTrackColor = colors.primary,
                                inactiveTrackColor = colors.border
                            )
                        )

                        // Preset chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                1.0f to "100%",
                                0.85f to "85% (推奨)",
                                0.5f to "50% (半透明)",
                                0.0f to "0% (透明)"
                            ).forEach { (preset, label) ->
                                val isSelected = kotlin.math.abs(widgetOpacity - preset) < 0.04f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable {
                                            widgetOpacity = preset
                                            WidgetSettingsManager.setOpacity(context, preset)
                                            TodayTimelineWidgetReceiver.updateAll(context)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.primary else colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "文字サイズ / 情報密度",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WidgetFontSize.values().forEach { size ->
                                val isSelected = widgetFontSize == size
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, if (isSelected) colors.primary else colors.border, RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable {
                                            widgetFontSize = size
                                            WidgetSettingsManager.setFontSize(context, size)
                                            TodayTimelineWidgetReceiver.updateAll(context)
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = when(size) {
                                                WidgetFontSize.COMPACT -> "コンパクト"
                                                WidgetFontSize.STANDARD -> "標準"
                                                WidgetFontSize.LARGE -> "大きめ"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.primary else colors.textPrimary
                                        )
                                        Text(
                                            text = when(size) {
                                                WidgetFontSize.COMPACT -> "手帳密度"
                                                WidgetFontSize.STANDARD -> "おすすめ"
                                                WidgetFontSize.LARGE -> "視認性重視"
                                            },
                                            fontSize = 10.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Mini Live Preview
                        Text(
                            text = "ウィジェット外観プレビュー",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = (if (colors.isDark) Color(0xFF1E293B) else Color.White).copy(alpha = widgetOpacity),
                            border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "9月29日 (火)",
                                        fontSize = (15 * widgetFontSize.scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "FocusFlow ↗",
                                        fontSize = (10 * widgetFontSize.scale).sp,
                                        color = colors.textSecondary.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "🎯 “最重要タスクに没頭する”",
                                    fontSize = (11.5f * widgetFontSize.scale).sp,
                                    color = colors.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = colors.border.copy(alpha = 0.35f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "07:30",
                                        fontSize = (10 * widgetFontSize.scale).sp,
                                        color = colors.textSecondary,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text("✓ ", fontSize = (10 * widgetFontSize.scale).sp, color = colors.statusDone, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "モーニングコーヒーで一息",
                                        fontSize = (11 * widgetFontSize.scale).sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = colors.nowLine,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "現在 07:45",
                                            fontSize = (9 * widgetFontSize.scale).sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        thickness = 1.dp,
                                        color = colors.nowLine.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("○ ", fontSize = (13 * widgetFontSize.scale).sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "手帳ウィジェットを実装・検証",
                                        fontSize = (11.5f * widgetFontSize.scale).sp,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
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
                Spacer(modifier = Modifier.height(8.dp))

                // Item 1: Cutoff Hour
                SettingActionCard(
                    title = "日付リセット境界時刻",
                    subtitle = "午前 %02d:00 (深夜の夜食は「昨晩」として集計)".format(cutoffHour),
                    actionLabel = "変更 >",
                    onClick = { showCutoffDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 2: Quick Records (Templates)
                val quickCount = templates.count { it.type != "INTERVAL" }
                SettingActionCard(
                    title = "クイック記録（テンプレート）管理",
                    subtitle = "勉強, 家事, 水など ${quickCount}件登録中（並び替え・ピン留め）",
                    actionLabel = "管理 >",
                    onClick = { showTemplatesDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 3: Data Export
                SettingActionCard(
                    title = "データ書き出し & バックアップ",
                    subtitle = "SQLite DB / JSON 形式でエクスポート (クリップボードコピー可)",
                    actionLabel = "実行 >",
                    onClick = {
                        val json = viewModel.exportJson()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("byLife Backup", json)
                        clipboard.setPrimaryClip(clip)

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, json)
                            type = "application/json"
                        }
                        try {
                            context.startActivity(Intent.createChooser(sendIntent, "byLife Backup JSON"))
                        } catch (e: Exception) {
                            // ignore if no share targets
                        }

                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("JSONデータをクリップボードにコピーしました")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 3.5: Data Import
                SettingActionCard(
                    title = "データ取り込み & 復元 (インポート)",
                    subtitle = "JSON形式のバックアップテキストから記録と設定をインポート",
                    actionLabel = "取り込み >",
                    onClick = {
                        importJsonText = ""
                        showImportDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(8.dp))

                // Item 5: Reset / Sample Data
                SettingActionCard(
                    title = "初期データの復元・再投入",
                    subtitle = "初期データ（クイック記録4種、周期2種）へリセット",
                    actionLabel = "復元 >",
                    onClick = { showResetConfirmDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Cutoff Dialog
    if (showCutoffDialog) {
        ModalBottomSheet(
            onDismissRequest = { showCutoffDialog = false },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "日付リセット境界時刻",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    TextButton(onClick = { showCutoffDialog = false }) {
                        Text("完了", fontWeight = FontWeight.Bold, color = colors.primary, fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "深夜何時までを「今日」として集計するか選択してください：",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                listOf(0, 3, 4, 5, 6).forEach { hour ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (cutoffHour == hour) colors.primary.copy(alpha = 0.08f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setDayCutoff(hour)
                                showCutoffDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                            Text(
                                "午前 %02d:00".format(hour),
                                fontSize = 15.sp,
                                fontWeight = if (cutoffHour == hour) FontWeight.Bold else FontWeight.Normal,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Templates Dialog
    if (showTemplatesDialog) {
        com.forcusflow.lifestream.ui.components.TemplateManagerDialog(
            viewModel = viewModel,
            onDismiss = { showTemplatesDialog = false }
        )
    }

    // Reset Sample Data Confirm Dialog
    if (showResetConfirmDialog) {
        ModalBottomSheet(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "サンプルデータの復元",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "データベースを初期化し、初期デフォルトデータ（クイック記録4種、周期2種）を再投入します。よろしいですか？",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetConfirmDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text("キャンセル", color = colors.textPrimary)
                    }
                    Button(
                        onClick = {
                            viewModel.reloadSampleData()
                            showResetConfirmDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("サンプルデータを復元しました")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text("復元する", color = colors.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Import JSON Dialog
    if (showImportDialog) {
        ModalBottomSheet(
            onDismissRequest = { showImportDialog = false },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("キャンセル", color = colors.textSecondary, fontSize = 15.sp)
                    }
                    Text(
                        text = "データ取り込み",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    TextButton(
                        onClick = {
                            if (importJsonText.isNotBlank()) {
                                coroutineScope.launch {
                                    val (tCount, iCount) = viewModel.importJson(importJsonText)
                                    if (tCount >= 0) {
                                        showImportDialog = false
                                        snackbarHostState.showSnackbar("復元完了: テンプレート${tCount}件、タイムライン記録${iCount}件を取り込みました")
                                    } else {
                                        snackbarHostState.showSnackbar("JSONの解析に失敗しました。フォーマットをご確認ください")
                                    }
                                }
                            }
                        },
                        enabled = importJsonText.isNotBlank()
                    ) {
                        Text(
                            "取り込む",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (importJsonText.isNotBlank()) colors.primary else colors.textSecondary.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "エクスポートしたバックアップJSONを貼り付けて復元します：",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (clipText.isNotBlank()) {
                            importJsonText = clipText
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("クリップボードが空です")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("📋 クリップボードから貼り付け", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("ここにJSONテキストを貼り付け...", fontSize = 12.sp, color = colors.textSecondary.copy(alpha = 0.5f)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )
                }
            }
        }
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
                        text = if (isSelected) "$title (現在適用中)" else title,
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
