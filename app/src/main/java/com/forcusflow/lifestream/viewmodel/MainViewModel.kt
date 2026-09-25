package com.forcusflow.lifestream.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.DatabaseSeeder
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.ui.theme.AppThemeMode
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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val itemDao = db.timelineItemDao()
    private val templateDao = db.templateDao()
    private val zone = ZoneId.systemDefault()

    // Preferences state
    val themeMode = MutableStateFlow(AppThemeMode.CLASSIC_WARM)
    val dayCutoffHour = MutableStateFlow(4) // 04:00 AM

    // Navigation state
    val currentTab = MutableStateFlow(0) // 0: 今日, 1: 履歴, 2: 周期管理, 3: 設定

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
    val timerElapsedSeconds = MutableStateFlow(0L)
    private var timerStartTimeMillis: Long? = null
    private var timerJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseSeeder.seed(templateDao, itemDao)
        }
    }

    // Determine Day boundaries taking cutoff hour into account
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
                "COUNT" -> {
                    val today = LocalDate.now()
                    val (start, end) = getDayRange(today)
                    val currentCount = allItems.value.count { item ->
                        val t = item.completedAt ?: item.scheduledAt
                        t != null && t in start..end && item.templateId == template.id
                    }
                    currentCount + template.stepValue
                }
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
        countValue: Int? = null
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
                countValue = countValue
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
            val interval = template.intervalDays ?: 7
            val item = TimelineItemEntity(
                title = "${template.title} 実施",
                isDone = true,
                completedAt = millis,
                note = "${interval}日周期ルーティン完了",
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
        isPinned: Boolean = false
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
                displayOrder = maxOrder + 1
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
    }

    fun setDayCutoff(hour: Int) {
        dayCutoffHour.value = hour
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
}
