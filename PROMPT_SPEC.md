# LifeStream 仕様書 (PROMPT_SPEC.md)

## 1. プロダクト概要 & コア哲学
- **プロダクト名**: LifeStream (ライフストリーム)
- **コア哲学**:
  - 説教・反省のないフラットな事実記録（水、食事、夜食出費）。
  - Taskito風の縦1本タイムライン（Done、NOWライン、ToDo、周期推奨がシームレスに直結）。
  - 長期家事・セルフケア（排水口、パック、フィルター清掃）を可視化する「週マトリクス型周期トラッカー」。

---

## 2. デザインテーマ・システム
Settingsタブより以下の3テーマを即時切替可能：
1. **クラシック・ウォーム (デフォルト)**:
   - Background: #F4F1EA
   - Card/Surface: #FFFFFF
   - Primary: #8C5A3C
   - Text: #2D241E
   - TextSecondary: #786B61
   - Border: #E2DDD5
2. **ディープ・スレート (Dark)**:
   - Background: #0F172A
   - Card/Surface: #1E293B
   - Primary: #38BDF8
   - Text: #F8FAFC
3. **ピュア・ミニマル OLED (Black)**:
   - Background: #000000
   - Card/Surface: #121212
   - Primary: #FFFFFF
   - Accent: #10B981

---

## 3. データ層設計 (Room SQLite + kotlinx-datetime + kotlinx-serialization)

### Entity 1: TimelineItemEntity
- id: Long (PK, autoGenerate = true)
- 	itle: String
- isDone: Boolean
- scheduledAt: Long? (Epoch millis。nullならAnytime)
- completedAt: Long? (Epoch millis。実績時刻)
- mount: Long? (支出金額。例: 240)
- 
ote: String? (一行メモ・言い訳。例: "罪悪感ゼロ")
- 	emplateId: Long? (紐づくテンプレートID)

### Entity 2: TemplateEntity
- id: Long (PK, autoGenerate = true)
- 	itle: String
- 	ype: String ("DAILY_COUNT", "INTERVAL", "SIMPLE")
- intervalDays: Int? (周期型の間隔日数。例: パック=3, 排水ネット=7, フィルター=30)
- defaultAmount: Long?
- iconKey: String?
- colorHex: String?
- usageCount: Int
- lastCompletedAt: Long?

---

## 4. 画面別詳細仕様（4タブ構成）

### タブ1: 今日（タイムライン）
- **Anytime ToDo カルーセル（最上部）**: 時間指定なしタスクをカード表示。チェックで即Done化。
- **クイック記録バー**: テンプレチップ（💧水, 🧖パック, 🍜夜食, 🧹排水口）。1タップで即Done ＋ 5秒間のUndo Snackbar。
- **縦軸タイムライン**: 過去の実績（緑丸）、赤色の現在時刻ライン、未来のToDo（白丸）、周期推奨タスク（紫/赤丸）。タップで編集、スワイプで削除（Undo付き）。
- **右下FAB (+)**: 自由入力シート（ToDo/Done切替、金額テンキー、遡り時刻プリセット「1時間前」「昨晩」等）。

### タブ2: 履歴（カレンダー & 検索）
- ワード検索（ログ一覧と合計金額・回数のリアルタイム集計表示）。
- カレンダーの日付タップでその日のタイムライン完全再現（9/12の夜食・パック・水・電車移動など）。

### タブ3: 周期マトリクス & 習慣
- **上部セグメント**: [ 周期マトリクス ] / [ 毎日習慣・出費 ]
- **周期マトリクス**:
  - 横軸: 週日付（9/5, 9/12, 9/19）。
  - 縦軸: 周期タスク（風呂排水ネット, フェイスパック, シーツ洗濯, 洗濯槽クリーナー, 換気扇フィルター, 防カビくん煙剤）。
  - ステータスバッジ（緑チェック ✓, 赤ビックリ !, 黄色三角 !, 紫丸 ◎）。
  - セルタップで即座に完了を記録でき、タブ1のタイムラインにも即同期。
- **下部固定バー**: 水の「今日○杯」＋ [+1杯記録] ボタン。

### タブ4: 設定
- テーマ切替（3種）。
- 日付リセット時刻設定（デフォルト 04:00 AM）。
- テンプレート一覧管理・データJSONエクスポート。
