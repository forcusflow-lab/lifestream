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
import com.forcusflow.lifestream.ui.theme.AppThemeMode
import com.forcusflow.lifestream.widget.TodayTimelineWidgetReceiver
import com.forcusflow.lifestream.widget.WidgetSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val itemDao = db.timelineItemDao()
    private val templateDao = db.templateDao()
    private val memoDao = db.memoDao()
    private val dailyFocusDao = db.dailyFocusDao()
    private val zone = ZoneId.systemDefault()

    // Preferences state
    val themeMode = MutableStateFlow(WidgetSettingsManager.getThemeMode(application))
    val dayCutoffHour = MutableStateFlow(WidgetSettingsManager.getCutoffHour(application))

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
    fun getCurrentTimeOfDayZone(time: LocalTime = LocalTime.now()): TimeOfDayZone {
        val hour = time.hour
        return when {
            hour in 4..10 -> TimeOfDayZone.MORNING
            hour in 11..16 -> TimeOfDayZone.AFTERNOON
            else -> TimeOfDayZone.EVENING_NIGHT
        }
    }

    fun isTemplateInActiveZone(template: TemplateEntity, currentTime: LocalTime = LocalTime.now()): Boolean {
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

    // Badge count for Today tab: pending ToDos + overdue periodic tasks
    val todayBadgeCount: StateFlow<Int> = combine(
        anytimePendingItems,
        templates
    ) { pending, tmplList ->
        val today = LocalDate.now()
        val overdueCount = tmplList.count { tmpl ->
            if (tmpl.type != "INTERVAL") return@count false
            val lastDoneMillis = tmpl.lastCompletedAt ?: return@count true
            val lastDate = java.time.Instant.ofEpochMilli(lastDoneMillis)
                .atZone(zone).toLocalDate()
            val elapsed = java.time.temporal.ChronoUnit.DAYS.between(lastDate, today)
            elapsed >= (tmpl.intervalDays ?: 7)
        }
        pending.size + overdueCount
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Search query
    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TimelineItemEntity>> = searchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) itemDao.getAllFlow() else itemDao.searchFlow(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar state (support dynamic browsing across months, default to today)
    val calendarYearMonth = MutableStateFlow(YearMonth.from(LocalDate.now()))
    val selectedCalendarDate = MutableStateFlow(LocalDate.now())

    // Last deleted / added item for Undo snackbar
    val undoItem = MutableStateFlow<TimelineItemEntity?>(null)

    // Active Timer state for actionType = "TIMER"
    val activeTimerTemplate = MutableStateFlow<TemplateEntity?>(null)
    val activeTimerItem = MutableStateFlow<TimelineItemEntity?>(null)
    val timerElapsedSeconds = MutableStateFlow(0L)
    private var timerStartTimeMillis: Long? = null
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseSeeder.seed(templateDao, itemDao)
            purgeOldStockItems()
        }
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

    fun purgeOldStockItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val all = itemDao.getAll()
            val oldStock = all.filter {
                !it.isDone &&
                (it.createdAt in 1..thirtyDaysAgo) &&
                (it.scheduledAt == null || it.scheduledAt < thirtyDaysAgo)
            }
            oldStock.forEach { itemDao.delete(it) }
        }
    }

    fun notifyWidgetUpdate() {
        TodayTimelineWidgetReceiver.updateAll(getApplication())
    }

    // Determine Day boundaries taking cutoff hour into account
    fun getLogicalDate(now: LocalDateTime = LocalDateTime.now(), cutoffHour: Int = dayCutoffHour.value): LocalDate {
        return if (now.hour < cutoffHour) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }
    }

    fun getDayRange(date: LocalDate, cutoffHour: Int = dayCutoffHour.value): Pair<Long, Long> {
        val startDateTime = LocalDateTime.of(date, LocalTime.of(cutoffHour, 0))
        val endDateTime = startDateTime.plusDays(1).minusNanos(1)
        val startMillis = startDateTime.atZone(zone).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(zone).toInstant().toEpochMilli()
        return Pair(startMillis, endMillis)
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
        val today = LocalDate.now()
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

    fun startTimer(template: TemplateEntity) {
        if (activeTimerTemplate.value?.id == template.id) {
            stopAndSaveTimer {}
            return
        }
        if (activeTimerItem.value != null) {
            stopAndSaveItemTimer {}
        }
        timerJob?.cancel()
        val start = System.currentTimeMillis()
        timerStartTimeMillis = start
        activeTimerTemplate.value = template
        timerElapsedSeconds.value = 0L
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                timerElapsedSeconds.value = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0L)
            }
        }
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
                countValue = null
            )
            val id = itemDao.insert(newItem)
            val savedItem = newItem.copy(id = id)
            templateDao.recordCompletion(template.id, now)
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
    }

    fun startItemTimer(item: TimelineItemEntity) {
        if (activeTimerItem.value?.id == item.id) {
            stopAndSaveItemTimer {}
            return
        }
        if (activeTimerTemplate.value != null) {
            stopAndSaveTimer {}
        }
        timerJob?.cancel()
        val start = System.currentTimeMillis()
        timerStartTimeMillis = start
        activeTimerItem.value = item
        timerElapsedSeconds.value = 0L
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                timerElapsedSeconds.value = ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0L)
            }
        }
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

        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(
                isDone = true,
                completedAt = now,
                durationSeconds = (item.durationSeconds ?: 0) + seconds.toInt()
            )
            itemDao.update(updated)
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
                countValue = countValue
            )
            val id = itemDao.insert(newItem)
            val savedItem = newItem.copy(id = id)
            templateDao.recordCompletion(template.id, now)
            undoItem.value = savedItem
            onRecorded(savedItem)
        }
    }

    fun undoLastItem() {
        val item = undoItem.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.delete(item)
            undoItem.value = null
        }
    }

    fun deleteItem(item: TimelineItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.delete(item)
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
            val time = LocalTime.now()
            val millis = LocalDateTime.of(targetDate, time).atZone(zone).toInstant().toEpochMilli()
            val item = TimelineItemEntity(
                title = template.title,
                isDone = true,
                completedAt = millis,
                note = null,
                templateId = template.id
            )
            itemDao.insert(item)
            val latestMillis = maxOf(millis, template.lastCompletedAt ?: 0L)
            templateDao.update(template.copy(usageCount = template.usageCount + 1, lastCompletedAt = latestMillis))
        }
    }

    fun toggleCycleTask(template: TemplateEntity, targetDate: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            val startOfDay = targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfDay = targetDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
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

    fun skipCycleTask(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // 完了ログは作らず、次回予定日を本日起点に繰り延べるため lastCompletedAt のみを更新
            val updated = template.copy(lastCompletedAt = now)
            templateDao.update(updated)
        }
    }

    /**
     * Doneアイテム（またはToDo）を周期タスク（習慣・ルーティン）へ昇格登録する
     */
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

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.delete(template)
        }
    }

    fun reloadSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.deleteAll()
            templateDao.deleteAll()
            DatabaseSeeder.seed(templateDao, itemDao)
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

    fun exportJson(): String {
        val currentItems = allItems.value
        val currentTemplates = templates.value
        val data = mapOf(
            "templates" to currentTemplates,
            "timeline_items" to currentItems
        )
        val json = Json { prettyPrint = true }
        return json.encodeToString(data)
    }

    suspend fun importJson(jsonStr: String): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonStr).jsonObject
            val templatesJson = root["templates"]
            val itemsJson = root["timeline_items"]

            var importedTemplates = 0
            var importedItems = 0

            if (templatesJson != null) {
                val tmpls = json.decodeFromJsonElement<List<TemplateEntity>>(templatesJson)
                tmpls.forEach { t ->
                    templateDao.insert(t)
                    importedTemplates++
                }
            }
            if (itemsJson != null) {
                val its = json.decodeFromJsonElement<List<TimelineItemEntity>>(itemsJson)
                its.forEach { item ->
                    itemDao.insert(item)
                    importedItems++
                }
            }
            Pair(importedTemplates, importedItems)
        }
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

    // === Memo Promotion Engine (Actionable Integration) ===
    fun promoteMemoToTodo(memo: MemoEntity, scheduledAt: Long?, deleteMemoAfter: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.insert(
                TimelineItemEntity(
                    title = memo.content.trim(),
                    isDone = false,
                    scheduledAt = scheduledAt
                )
            )
            if (deleteMemoAfter) {
                memoDao.delete(memo)
            }
        }
    }

    fun promoteMemoToDone(memo: MemoEntity, completedAt: Long = System.currentTimeMillis(), deleteMemoAfter: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.insert(
                TimelineItemEntity(
                    title = memo.content.trim(),
                    isDone = true,
                    completedAt = completedAt
                )
            )
            if (deleteMemoAfter) {
                memoDao.delete(memo)
            }
        }
    }

    fun promoteMemoToPeriodic(memo: MemoEntity, intervalDays: Int, deleteMemoAfter: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.insert(
                TemplateEntity(
                    title = memo.content.trim(),
                    type = "INTERVAL",
                    intervalDays = intervalDays,
                    iconKey = "🔄",
                    colorHex = "#34C759",
                    lastCompletedAt = System.currentTimeMillis()
                )
            )
            if (deleteMemoAfter) {
                memoDao.delete(memo)
            }
        }
    }

    fun promoteMemoToTemplate(memo: MemoEntity, iconKey: String = "⚡", colorHex: String = "#FF9500", deleteMemoAfter: Boolean = false) {
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
            itemDao.update(
                item.copy(
                    scheduledAt = null,
                    createdAt = 1000L
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
