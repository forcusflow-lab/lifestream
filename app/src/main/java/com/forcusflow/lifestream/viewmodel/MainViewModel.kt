package com.forcusflow.lifestream.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.DailyFocusEntity
import com.forcusflow.lifestream.data.DatabaseSeeder
import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.data.TimerStateManager
import com.forcusflow.lifestream.domain.*
import com.forcusflow.lifestream.ui.theme.AppThemeMode
import com.forcusflow.lifestream.widget.TodayTimelineWidgetReceiver
import com.forcusflow.lifestream.widget.WidgetSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

enum class TimeOfDayZone(val code: String, val label: String, val description: String) {
    ALL_DAY("ALL_DAY", "いつでも", "終日表示"),
    MORNING("MORNING", "朝 ☀️", "朝の活動"),
    AFTERNOON("AFTERNOON", "昼 🍴", "昼・午後の活動"),
    EVENING_NIGHT("EVENING_NIGHT", "夜 🌙", "夕方・夜の活動")
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val itemDao = db.timelineItemDao()
    private val templateDao = db.templateDao()
    private val memoDao = db.memoDao()
    private val dailyFocusDao = db.dailyFocusDao()

    // Centralized Domain Services & UseCases
    val dateProvider = LifeDateProvider()
    val undoManager = UndoActionManager()
    val backupUseCase = BackupUseCase(db)
    val resetDataUseCase = ResetDataUseCase(db, application)
    val periodicTaskUseCase = PeriodicTaskUseCase(db, dateProvider)
    val memoPromotionUseCase = MemoPromotionUseCase(db, undoManager)

    // Preferences state
    val themeMode = MutableStateFlow(WidgetSettingsManager.getThemeMode(application))
    val dayCutoffHour = MutableStateFlow(WidgetSettingsManager.getCutoffHour(application))
    val showStreaksAndGoals = MutableStateFlow(WidgetSettingsManager.getShowStreaksAndGoals(application))

    // Navigation and Sheet state
    val currentTab = MutableStateFlow(0) // 0: 今日, 1: 履歴, 2: 周期, 3: メモ, 4: 設定
    val openAddSheetRequested = MutableStateFlow(false)

    fun requestOpenAddSheet() {
        openAddSheetRequested.value = true
    }

    fun consumeOpenAddSheetRequest() {
        openAddSheetRequested.value = false
    }

    // Time of day zone helpers
    fun getCurrentTimeOfDayZone(time: LocalTime = dateProvider.nowLocalTime()): TimeOfDayZone {
        val hour = time.hour
        return when {
            hour in 4..10 -> TimeOfDayZone.MORNING
            hour in 11..16 -> TimeOfDayZone.AFTERNOON
            else -> TimeOfDayZone.EVENING_NIGHT
        }
    }

    fun isTemplateInActiveZone(template: TemplateEntity, currentTime: LocalTime = dateProvider.nowLocalTime()): Boolean {
        val zoneStr = template.timeOfDayZone
        if (zoneStr == "ALL_DAY" || zoneStr.isBlank()) return true
        val currentZone = getCurrentTimeOfDayZone(currentTime)
        return currentZone.code == zoneStr
    }

    // Data streams
    val templates: StateFlow<List<TemplateEntity>> = templateDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedTemplates: StateFlow<List<TemplateEntity>> = templates
        .map { list ->
            val nonInterval = list.filter { it.type != "INTERVAL" }
            val pinned = nonInterval.filter { it.isPinned }
            if (pinned.isNotEmpty()) pinned else nonInterval.take(4)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<TimelineItemEntity>> = itemDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val anytimePendingItems: StateFlow<List<TimelineItemEntity>> = itemDao.getAnytimePendingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memos: StateFlow<List<MemoEntity>> = memoDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Badge count for Today tab: ONLY today's scheduled ToDo count!
    // Never includes overdue periodic tasks, drawer items, streaks, or uncompleted targets.
    val todayBadgeCount: StateFlow<Int> = combine(
        allItems,
        templates,
        dayCutoffHour
    ) { items, tmplList, cutoff ->
        val timelineData = TodayTimelineCalculator.calculate(items, tmplList, dateProvider, cutoff)
        timelineData.todayBadgeCount
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Debounced Search query (reduces excessive DB queries)
    val searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<TimelineItemEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) itemDao.getAllFlow() else itemDao.searchFlow(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar state
    val calendarYearMonth = MutableStateFlow(YearMonth.from(dateProvider.nowLocalDate()))
    val selectedCalendarDate = MutableStateFlow(dateProvider.nowLocalDate())

    // Backward compatible last deleted/added item reference for simple UI listeners
    val undoItem = MutableStateFlow<TimelineItemEntity?>(null)

    // Active Timer state for actionType = "TIMER"
    val activeTimerTemplate = MutableStateFlow<TemplateEntity?>(null)
    val activeTimerItem = MutableStateFlow<TimelineItemEntity?>(null)
    val timerElapsedSeconds = MutableStateFlow(0L)
    private var timerStartTimeMillis: Long? = null
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        // Safe database seeding (never deletes or alters user-created data)
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseSeeder.seed(templateDao, itemDao, force = false)
        }

        // Restore active timer if interrupted by process death or reboot
        viewModelScope.launch(Dispatchers.IO) {
            val persisted = TimerStateManager.getTimer(getApplication())
            if (persisted != null) {
                val start = persisted.startTimeMillis
                val elapsed = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0L)
                if (persisted.templateId != null) {
                    val tmpl = templateDao.getById(persisted.templateId)
                    if (tmpl != null) {
                        withContext(Dispatchers.Main) {
                            activeTimerTemplate.value = tmpl
                            timerStartTimeMillis = start
                            timerElapsedSeconds.value = elapsed
                            startTimerTicker(start)
                        }
                    }
                } else if (persisted.itemId != null) {
                    val item = itemDao.getById(persisted.itemId)
                    if (item != null) {
                        withContext(Dispatchers.Main) {
                            activeTimerItem.value = item
                            timerStartTimeMillis = start
                            timerElapsedSeconds.value = elapsed
                            startTimerTicker(start)
                        }
                    }
                }
            }
        }

        // Trigger widget refresh on changes
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                allItems,
                dailyFocusDao.getAll(),
                templates
            ) { _, _, _ -> Unit }
                .drop(1)
                .collectLatest {
                    notifyWidgetUpdate()
                }
        }
    }

    fun notifyWidgetUpdate() {
        TodayTimelineWidgetReceiver.updateAll(getApplication())
    }

    // Logical date and day boundaries
    fun getLogicalDate(
        now: LocalDateTime = dateProvider.nowLocalDateTime(),
        cutoffHour: Int = dayCutoffHour.value
    ): LocalDate {
        return dateProvider.getLogicalDate(now, cutoffHour)
    }

    fun getDayRange(
        date: LocalDate,
        cutoffHour: Int = dayCutoffHour.value
    ): Pair<Long, Long> {
        return dateProvider.getDayRange(date, cutoffHour)
    }

    fun previousMonth() {
        val prev = calendarYearMonth.value.minusMonths(1)
        calendarYearMonth.value = prev
        selectedCalendarDate.value = prev.atDay(1)
    }

    fun nextMonth() {
        val next = calendarYearMonth.value.plusMonths(1)
        calendarYearMonth.value = next
        selectedCalendarDate.value = next.atDay(1)
    }

    fun goToToday() {
        val today = dateProvider.nowLocalDate()
        calendarYearMonth.value = YearMonth.from(today)
        selectedCalendarDate.value = today
    }

    fun toggleItemDone(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = item.copy(
                isDone = !item.isDone,
                completedAt = if (!item.isDone) now else null
            )
            itemDao.update(updated)
            if (updated.isDone && updated.templateId != null) {
                templateDao.recordCompletion(updated.templateId, now)
            }
        }
    }

    fun updateTimelineItem(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.update(item)
        }
    }

    private fun startTimerTicker(startMillis: Long) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                timerElapsedSeconds.value = ((System.currentTimeMillis() - startMillis) / 1000).coerceAtLeast(0L)
            }
        }
    }

    fun startTimer(template: TemplateEntity) {
        if (activeTimerTemplate.value?.id == template.id) {
            stopAndSaveTimer {}
            return
        }
        if (activeTimerItem.value != null) {
            stopAndSaveItemTimer {}
        }
        val start = System.currentTimeMillis()
        timerStartTimeMillis = start
        activeTimerTemplate.value = template
        timerElapsedSeconds.value = 0L
        TimerStateManager.saveTimer(getApplication(), template.id, null, start)
        startTimerTicker(start)
    }

    fun stopAndSaveTimer(onRecorded: (TimelineItemEntity) -> Unit = {}) {
        val template = activeTimerTemplate.value ?: return
        val start = timerStartTimeMillis
        val seconds = if (start != null) {
            ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(1L)
        } else {
            timerElapsedSeconds.value.coerceAtLeast(1L)
        }
        timerJob?.cancel()
        timerJob = null
        activeTimerTemplate.value = null
        timerStartTimeMillis = null
        timerElapsedSeconds.value = 0L
        TimerStateManager.clearTimer(getApplication())

        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = TimelineItemEntity(
                title = template.title,
                isDone = true,
                scheduledAt = null,
                completedAt = now,
                amount = template.defaultAmount,
                note = null,
                templateId = template.id,
                durationSeconds = seconds.toInt(),
                countValue = null,
                createdAt = now
            )
            val id = itemDao.insert(newItem)
            val savedItem = newItem.copy(id = id)
            templateDao.recordCompletion(template.id, now)

            val action = undoManager.registerAction(
                description = "${template.title} を記録しました（${seconds / 60}分）",
                payload = UndoPayload.TimerComplete(savedItem, template.id)
            )
            undoItem.value = savedItem
            onRecorded(savedItem)
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        activeTimerTemplate.value = null
        activeTimerItem.value = null
        timerStartTimeMillis = null
        timerElapsedSeconds.value = 0L
        TimerStateManager.clearTimer(getApplication())
    }

    fun startItemTimer(item: TimelineItemEntity) {
        if (activeTimerItem.value?.id == item.id) {
            stopAndSaveItemTimer {}
            return
        }
        if (activeTimerTemplate.value != null) {
            stopAndSaveTimer {}
        }
        val start = System.currentTimeMillis()
        timerStartTimeMillis = start
        activeTimerItem.value = item
        timerElapsedSeconds.value = 0L
        TimerStateManager.saveTimer(getApplication(), null, item.id, start)
        startTimerTicker(start)
    }

    fun stopAndSaveItemTimer(onRecorded: (TimelineItemEntity) -> Unit = {}) {
        val item = activeTimerItem.value ?: return
        val start = timerStartTimeMillis
        val seconds = if (start != null) {
            ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(1L)
        } else {
            timerElapsedSeconds.value.coerceAtLeast(1L)
        }
        timerJob?.cancel()
        timerJob = null
        activeTimerItem.value = null
        timerStartTimeMillis = null
        timerElapsedSeconds.value = 0L
        TimerStateManager.clearTimer(getApplication())

        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(
                isDone = true,
                completedAt = now,
                durationSeconds = (item.durationSeconds ?: 0) + seconds.toInt()
            )
            itemDao.update(updated)

            val action = undoManager.registerAction(
                description = "${item.title} を完了しました",
                payload = UndoPayload.QuickRecord(updated, item.templateId)
            )
            undoItem.value = updated
            onRecorded(updated)
        }
    }

    fun cancelItemTimer() {
        timerJob?.cancel()
        timerJob = null
        activeTimerItem.value = null
        timerStartTimeMillis = null
        timerElapsedSeconds.value = 0L
        TimerStateManager.clearTimer(getApplication())
    }

    fun quickRecordTemplate(template: TemplateEntity, onRecorded: (TimelineItemEntity) -> Unit) {
        if (template.actionType == "TIMER") {
            if (activeTimerTemplate.value?.id == template.id) {
                stopAndSaveTimer(onRecorded)
            } else {
                startTimer(template)
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val countValue = when (template.actionType) {
                "COUNT" -> template.stepValue.coerceAtLeast(1)
                else -> null
            }

            val newItem = TimelineItemEntity(
                title = template.title,
                isDone = true,
                scheduledAt = null,
                completedAt = now,
                amount = template.defaultAmount,
                note = null,
                templateId = template.id,
                durationSeconds = null,
                countValue = countValue,
                createdAt = now
            )
            val id = itemDao.insert(newItem)
            val savedItem = newItem.copy(id = id)
            templateDao.recordCompletion(template.id, now)

            undoManager.registerAction(
                description = "${template.title} を記録しました",
                payload = UndoPayload.QuickRecord(savedItem, template.id)
            )
            undoItem.value = savedItem
            onRecorded(savedItem)
        }
    }

    fun undoLastItem() {
        val action = undoManager.getLatestAction() ?: return
        executeUndo(action.actionId)
    }

    fun executeUndo(actionId: String) {
        val action = undoManager.getAction(actionId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            when (val payload = action.payload) {
                is UndoPayload.DeleteItem -> {
                    itemDao.insert(payload.item)
                }
                is UndoPayload.QuickRecord -> {
                    itemDao.delete(payload.createdItem)
                }
                is UndoPayload.MemoPromotion -> {
                    memoPromotionUseCase.undoPromotion(action.actionId)
                }
                is UndoPayload.TimerComplete -> {
                    itemDao.delete(payload.createdItem)
                }
            }
            undoManager.markConsumed(action.actionId)
            undoItem.value = null
        }
    }

    fun deleteItem(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.delete(item)
            val action = undoManager.registerAction(
                description = "${item.title} を削除しました",
                payload = UndoPayload.DeleteItem(item)
            )
            undoItem.value = item
        }
    }

    fun restoreItem(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.insert(item)
        }
    }

    fun addTimelineItem(
        title: String,
        isDone: Boolean,
        scheduledAt: Long?,
        completedAt: Long?,
        amount: Long?,
        note: String?,
        templateId: Long?,
        durationSeconds: Int? = null,
        countValue: Int? = null,
        createdAt: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = TimelineItemEntity(
                title = title,
                isDone = isDone,
                scheduledAt = scheduledAt,
                completedAt = completedAt,
                amount = amount,
                note = note,
                templateId = templateId,
                durationSeconds = durationSeconds,
                countValue = countValue,
                createdAt = createdAt
            )
            itemDao.insert(item)
            if (isDone && templateId != null) {
                templateDao.recordCompletion(templateId, completedAt ?: System.currentTimeMillis())
            }
        }
    }

    fun recordCycleTask(template: TemplateEntity, targetDate: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            periodicTaskUseCase.recordCompletion(template, targetDate, dayCutoffHour.value)
        }
    }

    fun toggleCycleTask(template: TemplateEntity, targetDate: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val (startOfDay, endOfDay) = getDayRange(targetDate)
            val existing = itemDao.getCompletedByTemplateAndRange(template.id, startOfDay, endOfDay).firstOrNull()
            if (existing != null) {
                itemDao.delete(existing)
                val remaining = itemDao.getCompletedByTemplate(template.id)
                val newLast = remaining.firstOrNull { it.id != existing.id }?.completedAt
                templateDao.update(template.copy(lastCompletedAt = newLast))
            } else {
                recordCycleTask(template, targetDate)
            }
        }
    }

    /**
     * 周期タスクの「今回は見送る」
     * 完了ログを作らず、lastCompletedAt も更新せず、次回目安日を延長する。
     */
    fun postponeCycleTask(template: TemplateEntity, delayDays: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            periodicTaskUseCase.postponeTask(template, dayCutoffHour.value, delayDays)
            notifyWidgetUpdate()
        }
    }

    fun skipCycleTask(template: TemplateEntity) {
        postponeCycleTask(template, 1)
    }

    fun cancelCyclePostponement(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            periodicTaskUseCase.cancelPostponement(template)
            notifyWidgetUpdate()
        }
    }

    fun promoteItemToPeriodicTemplate(
        item: TimelineItemEntity,
        intervalDays: Int = 7,
        iconKey: String = "🔄",
        colorHex: String = "#10B981",
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val cleanTitle = item.title.removeSuffix(" 実施").trim()
            val template = TemplateEntity(
                title = cleanTitle,
                type = "INTERVAL",
                intervalDays = intervalDays,
                defaultAmount = item.amount,
                iconKey = iconKey,
                colorHex = colorHex,
                usageCount = 1,
                lastCompletedAt = item.completedAt ?: System.currentTimeMillis(),
                actionType = "CHECK",
                unit = "回",
                stepValue = 1,
                isPinned = false
            )
            val templateId = templateDao.insert(template)
            val updatedItem = item.copy(templateId = templateId)
            itemDao.update(updatedItem)
            onComplete?.invoke()
        }
    }

    fun toggleTemplatePin(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = template.copy(isPinned = !template.isPinned)
            templateDao.update(updated)
        }
    }

    fun addTemplate(
        title: String,
        type: String,
        intervalDays: Int?,
        defaultAmount: Long?,
        iconKey: String?,
        colorHex: String?,
        actionType: String = "CHECK",
        unit: String = "",
        stepValue: Int = 1,
        isPinned: Boolean = false,
        timeOfDayZone: String = "ALL_DAY"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = templateDao.getAll()
            val maxOrder = existing.filter { if (type == "INTERVAL") it.type == "INTERVAL" else it.type != "INTERVAL" }
                .maxOfOrNull { it.displayOrder } ?: -1
            val template = TemplateEntity(
                title = title,
                type = type,
                intervalDays = intervalDays,
                defaultAmount = defaultAmount,
                iconKey = iconKey,
                colorHex = colorHex,
                usageCount = 0,
                lastCompletedAt = null,
                actionType = actionType,
                unit = unit,
                stepValue = stepValue,
                isPinned = isPinned,
                displayOrder = maxOrder + 1,
                timeOfDayZone = timeOfDayZone
            )
            templateDao.insert(template)
        }
    }

    fun updateTemplate(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.update(template)
        }
    }

    fun moveTemplate(template: TemplateEntity, isUp: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = templateDao.getAll().filter {
                if (template.type == "INTERVAL") it.type == "INTERVAL" else it.type != "INTERVAL"
            }.sortedWith(compareBy({ it.displayOrder }, { it.id })).toMutableList()
            val currentIndex = list.indexOfFirst { it.id == template.id }
            if (currentIndex == -1) return@launch
            val targetIndex = if (isUp) currentIndex - 1 else currentIndex + 1
            if (targetIndex !in 0 until list.size) return@launch

            val item1 = list[currentIndex]
            val item2 = list[targetIndex]
            list[currentIndex] = item2
            list[targetIndex] = item1

            val updated = list.mapIndexed { idx, item -> item.copy(displayOrder = idx) }
            templateDao.updateAll(updated)
        }
    }

    /**
     * テンプレート削除:
     * 過去ログは削除せず保持し、参照整合性を維持する。
     */
    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.delete(template)
        }
    }

    fun reloadSampleData() {
        executeReset(ResetType.RELOAD_SAMPLE_DATA)
    }

    suspend fun getDataCounts(): DataCounts {
        return resetDataUseCase.getDataCounts()
    }

    fun executeReset(type: ResetType, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            resetDataUseCase.executeReset(type)
            notifyWidgetUpdate()
            onComplete?.invoke()
        }
    }

    fun setTheme(mode: AppThemeMode) {
        themeMode.value = mode
        WidgetSettingsManager.setThemeMode(getApplication(), mode)
        notifyWidgetUpdate()
    }

    fun setDayCutoff(hour: Int) {
        dayCutoffHour.value = hour
        WidgetSettingsManager.setCutoffHour(getApplication(), hour)
        notifyWidgetUpdate()
    }

    fun setShowStreaksAndGoals(enabled: Boolean) {
        showStreaksAndGoals.value = enabled
        WidgetSettingsManager.setShowStreaksAndGoals(getApplication(), enabled)
    }

    suspend fun exportJson(): String {
        return backupUseCase.exportJson()
    }

    fun validateBackupJson(jsonStr: String): ImportValidationResult {
        return backupUseCase.validateJson(jsonStr)
    }

    suspend fun executeImport(payload: BackupPayload, mode: ImportMode): ImportExecutionResult {
        val result = backupUseCase.executeImport(payload, mode)
        if (result.success) {
            notifyWidgetUpdate()
        }
        return result
    }

    // === Memo Management ===
    fun insertMemo(content: String, isPinned: Boolean = false) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            memoDao.insert(
                MemoEntity(
                    content = content.trim(),
                    isPinned = isPinned,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun updateMemo(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.update(memo.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun toggleMemoPin(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.update(memo.copy(isPinned = !memo.isPinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteMemo(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.delete(memo)
        }
    }

    fun archiveMemo(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.update(memo.copy(isArchived = true, updatedAt = System.currentTimeMillis()))
        }
    }

    fun unarchiveMemo(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.update(memo.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
        }
    }

    // === Memo Promotion Engine with full transactional undo ===
    fun promoteMemoToTodo(
        memo: MemoEntity,
        scheduledAt: Long?,
        deleteMemoAfter: Boolean = true,
        onSuccess: ((UndoAction) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = memoPromotionUseCase.promoteMemo(
                memo = memo,
                target = PromotionTarget.ToDo(scheduledAt = scheduledAt)
            )
            if (result.success && result.undoAction != null) {
                onSuccess?.invoke(result.undoAction)
            }
        }
    }

    fun promoteMemoToDone(
        memo: MemoEntity,
        completedAt: Long = System.currentTimeMillis(),
        deleteMemoAfter: Boolean = true,
        onSuccess: ((UndoAction) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = memoPromotionUseCase.promoteMemo(
                memo = memo,
                target = PromotionTarget.Done(completedAt = completedAt)
            )
            if (result.success && result.undoAction != null) {
                onSuccess?.invoke(result.undoAction)
            }
        }
    }

    fun promoteMemoToPeriodic(
        memo: MemoEntity,
        intervalDays: Int,
        deleteMemoAfter: Boolean = false,
        onSuccess: ((UndoAction) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = memoPromotionUseCase.promoteMemo(
                memo = memo,
                target = PromotionTarget.Periodic(intervalDays = intervalDays)
            )
            if (result.success && result.undoAction != null) {
                onSuccess?.invoke(result.undoAction)
            }
        }
    }

    fun promoteMemoToTemplate(
        memo: MemoEntity,
        iconKey: String = "⚡",
        colorHex: String = "#FF9500",
        deleteMemoAfter: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.insert(
                TemplateEntity(
                    title = memo.content.trim(),
                    type = "CHECK",
                    iconKey = iconKey,
                    colorHex = colorHex
                )
            )
            if (deleteMemoAfter) {
                memoDao.delete(memo)
            }
        }
    }

    // === Reschedule Upcoming Item to Today ===
    fun rescheduleItemToToday(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.update(
                item.copy(
                    scheduledAt = null,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    // === Reschedule Item to Drawer (Stock) ===
    fun rescheduleItemToDrawer(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val (todayStart, _) = getDayRange(dateProvider.nowLocalDate())
            itemDao.update(
                item.copy(
                    scheduledAt = null,
                    createdAt = (todayStart - 1000L).coerceAtLeast(1L)
                )
            )
        }
    }

    fun insertMemo(memo: MemoEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            memoDao.insert(memo)
        }
    }

    // === Daily Focus Management ===
    fun getDailyFocus(date: String): Flow<DailyFocusEntity?> = dailyFocusDao.getFocusByDate(date)

    fun setDailyFocus(date: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = content.trim()
            if (trimmed.isBlank()) {
                dailyFocusDao.deleteByDate(date)
            } else {
                dailyFocusDao.insertOrUpdate(
                    DailyFocusEntity(
                        date = date,
                        content = trimmed,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun clearDailyFocus(date: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dailyFocusDao.deleteByDate(date)
        }
    }
}
