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
import com.forcusflow.lifestream.domain.*
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
    val showStreaksAndGoals by viewModel.showStreaksAndGoals.collectAsState()

    var widgetOpacity by remember { mutableStateOf(WidgetSettingsManager.getOpacity(context)) }
    var widgetFontSize by remember { mutableStateOf(WidgetSettingsManager.getFontSize(context)) }

    var showCutoffDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedResetType by remember { mutableStateOf(ResetType.RELOAD_SAMPLE_DATA) }
    var resetDataCounts by remember { mutableStateOf<DataCounts?>(null) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importValidation by remember { mutableStateOf<ImportValidationResult?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.APPEND) }

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
                                        text = "byLife ↗",
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

            // Section: 表示・フィロソフィー設定
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "表示・フィロソフィー設定",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "習慣の連続記録・達成率の表示",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "周期タスクの「○巡目」バッジや達成率を表示します。OFF（推奨）にすると、プレッシャーのない穏やかな表示になります。",
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = showStreaksAndGoals,
                            onCheckedChange = { viewModel.setShowStreaksAndGoals(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.border
                            )
                        )
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
                    subtitle = "${quickCount}件登録中（今日タブの「⚙」やチップの長押しからも管理できます）",
                    actionLabel = "管理 >",
                    onClick = { showTemplatesDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section: データ管理 & バックアップ
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "データ管理 & バックアップ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 1: Data Export
                SettingActionCard(
                    title = "データ書き出し & バックアップ",
                    subtitle = "JSON形式でエクスポート (共有シート・クリップボード保存)",
                    actionLabel = "書き出し >",
                    onClick = {
                        coroutineScope.launch {
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
                                context.startActivity(Intent.createChooser(sendIntent, "byLife バックアップデータ"))
                            } catch (e: Exception) {
                                // Chooser fallback
                            }
                            snackbarHostState.showSnackbar("バックアップJSONをクリップボードにコピーしました")
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 2: Data Import
                SettingActionCard(
                    title = "データ取り込み & 復元 (インポート)",
                    subtitle = "JSON形式のバックアップテキストから記録と設定を取り込み",
                    actionLabel = "取り込み >",
                    onClick = {
                        importJsonText = ""
                        importValidation = null
                        selectedImportMode = ImportMode.APPEND
                        showImportDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Item 3: Reset / Initialize Data
                SettingActionCard(
                    title = "データ初期化・リセット",
                    subtitle = "サンプル再表示、記録とテンプレート削除、全初期化を選択して実行",
                    actionLabel = "初期化 >",
                    onClick = {
                        coroutineScope.launch {
                            resetDataCounts = viewModel.getDataCounts()
                            selectedResetType = ResetType.RELOAD_SAMPLE_DATA
                            showResetDialog = true
                        }
                    }
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

    // 3-way Reset Dialog
    if (showResetDialog) {
        ModalBottomSheet(
            onDismissRequest = { showResetDialog = false },
            containerColor = colors.card,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "データの初期化・リセット",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "実行したいリセットの種類を選択してください：",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Option 1: Reload Sample Data
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedResetType == ResetType.RELOAD_SAMPLE_DATA) colors.primary.copy(alpha = 0.08f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedResetType == ResetType.RELOAD_SAMPLE_DATA) colors.primary else colors.border.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedResetType = ResetType.RELOAD_SAMPLE_DATA }
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedResetType == ResetType.RELOAD_SAMPLE_DATA,
                            onClick = { selectedResetType = ResetType.RELOAD_SAMPLE_DATA }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("1. サンプルデータを再表示", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "基本の記録テンプレートと周期タスクを再投入します。あなたが作成した既存のデータは削除されず残ります。",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Option 2: Clear Records and Templates
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedResetType == ResetType.CLEAR_RECORDS_AND_TEMPLATES) Color(0xFFEF4444).copy(alpha = 0.08f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedResetType == ResetType.CLEAR_RECORDS_AND_TEMPLATES) Color(0xFFEF4444) else colors.border.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedResetType = ResetType.CLEAR_RECORDS_AND_TEMPLATES }
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedResetType == ResetType.CLEAR_RECORDS_AND_TEMPLATES,
                            onClick = { selectedResetType = ResetType.CLEAR_RECORDS_AND_TEMPLATES },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("2. 記録とテンプレートを削除", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "タイムライン記録 (${resetDataCounts?.timelineItemCount ?: 0}件) とテンプレート (${resetDataCounts?.templateCount ?: 0}件) を削除します。メモや目標、設定は保持されます。",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Option 3: Full Initialize
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedResetType == ResetType.FULL_INITIALIZE) Color(0xFFEF4444).copy(alpha = 0.12f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selectedResetType == ResetType.FULL_INITIALIZE) Color(0xFFEF4444) else colors.border.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedResetType = ResetType.FULL_INITIALIZE }
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedResetType == ResetType.FULL_INITIALIZE,
                            onClick = { selectedResetType = ResetType.FULL_INITIALIZE },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("3. すべてのデータを初期化", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "記録・テンプレート・メモ (${resetDataCounts?.memoCount ?: 0}件)・日々の目標 (${resetDataCounts?.dailyFocusCount ?: 0}件)・ウィジェット設定を含む全データを初期状態に戻します。",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showResetDialog = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Text("キャンセル", color = colors.textPrimary)
                    }
                    val isDestructive = selectedResetType != ResetType.RELOAD_SAMPLE_DATA
                    Button(
                        onClick = {
                            viewModel.executeReset(selectedResetType) {
                                coroutineScope.launch {
                                    val msg = when (selectedResetType) {
                                        ResetType.RELOAD_SAMPLE_DATA -> "サンプルデータを再表示しました"
                                        ResetType.CLEAR_RECORDS_AND_TEMPLATES -> "記録とテンプレートを削除しました"
                                        ResetType.FULL_INITIALIZE -> "すべてのデータを初期化しました"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                            showResetDialog = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) Color(0xFFEF4444) else colors.primary
                        )
                    ) {
                        Text(
                            text = if (isDestructive) "削除を実行" else "復元する",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Hardened Import JSON Dialog (Validation + Mode Selection)
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
                        text = "データ取り込み (復元)",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    TextButton(
                        onClick = {
                            val payload = importValidation?.payload
                            if (payload != null) {
                                coroutineScope.launch {
                                    val res = viewModel.executeImport(payload, selectedImportMode)
                                    showImportDialog = false
                                    if (res.success) {
                                        snackbarHostState.showSnackbar(
                                            "取り込み完了: テンプレート${res.importedTemplates}件、記録${res.importedItems}件、メモ${res.importedMemos}件"
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar("取り込み失敗: ${res.errorMessage ?: "不明なエラー"}")
                                    }
                                }
                            }
                        },
                        enabled = importValidation?.isValid == true
                    ) {
                        Text(
                            "実行する",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (importValidation?.isValid == true) colors.primary else colors.textSecondary.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "バックアップJSONを貼り付けて復元します：",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (clipText.isNotBlank()) {
                            importJsonText = clipText
                            importValidation = viewModel.validateBackupJson(clipText)
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

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.card,
                    border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importValidation = if (it.isNotBlank()) viewModel.validateBackupJson(it) else null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
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

                // Validation Status Display
                val validation = importValidation
                if (validation != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (validation.isValid) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "✓ 有効なバックアップデータです",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF10B981)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "・テンプレート: ${validation.templateCount}件\n・タイムライン記録: ${validation.timelineItemCount}件\n・メモ: ${validation.memoCount}件",
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Import Mode Selection
                        Text(
                            text = "取り込み方式の選択：",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Append Mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedImportMode == ImportMode.APPEND) colors.primary.copy(alpha = 0.08f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedImportMode == ImportMode.APPEND) colors.primary else colors.border.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = ImportMode.APPEND }
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedImportMode == ImportMode.APPEND,
                                    onClick = { selectedImportMode = ImportMode.APPEND }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("追加 (既存データに残して追加)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                    Text("IDを新しく採番し、現在のデータと共存させます", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }
                        }

                        // Replace Mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedImportMode == ImportMode.REPLACE) Color(0xFFEF4444).copy(alpha = 0.08f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedImportMode == ImportMode.REPLACE) Color(0xFFEF4444) else colors.border.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedImportMode = ImportMode.REPLACE }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedImportMode == ImportMode.REPLACE,
                                    onClick = { selectedImportMode = ImportMode.REPLACE },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("全置換 (既存データをすべて置き換え)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444))
                                    Text("既存データを削除し、バックアップの内容と完全一致させます", fontSize = 11.sp, color = colors.textSecondary)
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ ${validation.errorMessage ?: "無効なデータ形式です"}",
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
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
