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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

    val allItems: StateFlow<List<TimelineItemEntity>> = itemDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val anytimePendingItems: StateFlow<List<TimelineItemEntity>> = itemDao.getAnytimePendingFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query
    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TimelineItemEntity>> = searchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) itemDao.getAllFlow() else itemDao.searchFlow(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Calendar state (support dynamic browsing across months)
    val calendarYearMonth = MutableStateFlow(YearMonth.of(2026, 9))
    val selectedCalendarDate = MutableStateFlow(LocalDate.of(2026, 9, 12))

    // Segment in Tab 3 (0: 周期タスク一覧, 1: 週マトリクス表)
    val cycleMatrixSegment = MutableStateFlow(0)

    // Last deleted / added item for Undo snackbar
    val undoItem = MutableStateFlow<TimelineItemEntity?>(null)

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

    fun quickRecordTemplate(template: TemplateEntity, onRecorded: (TimelineItemEntity) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val newItem = TimelineItemEntity(
                title = if (template.type == "DAILY_COUNT") " (杯目)" else template.title,
                isDone = true,
                scheduledAt = null,
                completedAt = now,
                amount = template.defaultAmount,
                note = if (template.type == "DAILY_COUNT") "デイリー水分補給" else "クイック記録完了",
                templateId = template.id
            )
            val id = itemDao.insert(newItem)
            val savedItem = newItem.copy(id = id)
            templateDao.recordCompletion(template.id, now)
            undoItem.value = savedItem
            onRecorded(savedItem)
        }
    }

    fun recordWaterIntake() {
        val waterTemplate = templates.value.find { it.id == 1L || it.title.contains("水") }
        if (waterTemplate != null) {
            quickRecordTemplate(waterTemplate) {}
        } else {
            val now = System.currentTimeMillis()
            viewModelScope.launch(Dispatchers.IO) {
                itemDao.insert(
                    TimelineItemEntity(
                        title = "水を飲む (追加)",
                        isDone = true,
                        completedAt = now,
                        note = "デイリー水分補給"
                    )
                )
            }
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
        templateId: Long?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = TimelineItemEntity(
                title = title,
                isDone = isDone,
                scheduledAt = scheduledAt,
                completedAt = completedAt,
                amount = amount,
                note = note,
                templateId = templateId
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
                title = " 実施",
                isDone = true,
                completedAt = millis,
                note = "日周期ルーティン完了",
                templateId = template.id
            )
            itemDao.insert(item)
            templateDao.recordCompletion(template.id, millis)
        }
    }

    fun addTemplate(
        title: String,
        type: String,
        intervalDays: Int?,
        defaultAmount: Long?,
        iconKey: String?,
        colorHex: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val template = TemplateEntity(
                title = title,
                type = type,
                intervalDays = intervalDays,
                defaultAmount = defaultAmount,
                iconKey = iconKey,
                colorHex = colorHex,
                usageCount = 0,
                lastCompletedAt = null
            )
            templateDao.insert(template)
        }
    }

    fun deleteTemplate(template: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            templateDao.delete(template)
        }
    }

    fun reloadSampleData() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = itemDao.getAll()
            all.forEach { itemDao.delete(it) }
            val tList = templateDao.getAll()
            tList.forEach { templateDao.delete(it) }
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
}
