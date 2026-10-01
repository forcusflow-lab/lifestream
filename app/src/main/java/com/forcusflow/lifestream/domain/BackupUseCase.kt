package com.forcusflow.lifestream.domain

import androidx.room.withTransaction
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ImportMode {
    APPEND,  // 既存データに追加（IDを再採番し、templateIdの参照整合性を維持）
    REPLACE  // 既存データを全置換（既存データを削除して丸ごと復元）
}

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val app: String = "byLife",
    val templates: List<TemplateEntity> = emptyList(),
    val timeline_items: List<TimelineItemEntity> = emptyList(),
    val memos: List<MemoEntity> = emptyList()
)

data class ImportValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val payload: BackupPayload? = null,
    val templateCount: Int = 0,
    val timelineItemCount: Int = 0,
    val memoCount: Int = 0
)

data class ImportExecutionResult(
    val success: Boolean,
    val importedTemplates: Int = 0,
    val importedItems: Int = 0,
    val importedMemos: Int = 0,
    val errorMessage: String? = null
)

/**
 * Hardened Backup & Import UseCase.
 * Supports transactional imports, validation, append/replace modes,
 * foreign-key / templateId remapping, and crash-proof error handling.
 */
class BackupUseCase(
    private val db: AppDatabase? = null
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String {
        val database = db ?: throw IllegalStateException("Database not provided")
        val templates = database.templateDao().getAll()
        val items = database.timelineItemDao().getAll()
        val memos = database.memoDao().getAllList()
        val payload = BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            app = "byLife",
            templates = templates,
            timeline_items = items,
            memos = memos
        )
        return json.encodeToString(payload)
    }

    fun validateJson(jsonStr: String): ImportValidationResult {
        if (jsonStr.isBlank()) {
            return ImportValidationResult(isValid = false, errorMessage = "データが空です")
        }
        return try {
            val payload = json.decodeFromString<BackupPayload>(jsonStr)
            ImportValidationResult(
                isValid = true,
                payload = payload,
                templateCount = payload.templates.size,
                timelineItemCount = payload.timeline_items.size,
                memoCount = payload.memos.size
            )
        } catch (e: Exception) {
            ImportValidationResult(
                isValid = false,
                errorMessage = "不正なJSON形式です: ${e.localizedMessage ?: "構文エラー"}"
            )
        }
    }

    suspend fun executeImport(
        payload: BackupPayload,
        mode: ImportMode
    ): ImportExecutionResult {
        return try {
            val database = db ?: throw IllegalStateException("Database not provided")
            database.withTransaction {
                when (mode) {
                    ImportMode.REPLACE -> {
                        // 1. 全置換: 既存データを削除
                        database.timelineItemDao().deleteAll()
                        database.templateDao().deleteAll()
                        database.memoDao().deleteAll()

                        // 2. 新データを投入
                        database.templateDao().insertAll(payload.templates)
                        database.timelineItemDao().insertAll(payload.timeline_items)
                        payload.memos.forEach { database.memoDao().insert(it) }

                        ImportExecutionResult(
                            success = true,
                            importedTemplates = payload.templates.size,
                            importedItems = payload.timeline_items.size,
                            importedMemos = payload.memos.size
                        )
                    }
                    ImportMode.APPEND -> {
                        // 追加モード: ID衝突を防ぐため再採番し、templateIdを安全に再マッピング
                        val templateIdMap = mutableMapOf<Long, Long>()
                        var importedTemplates = 0
                        var importedItems = 0
                        var importedMemos = 0

                        payload.templates.forEach { tmpl ->
                            val oldId = tmpl.id
                            val newTmpl = tmpl.copy(id = 0)
                            val newId = database.templateDao().insert(newTmpl)
                            templateIdMap[oldId] = newId
                            importedTemplates++
                        }

                        payload.timeline_items.forEach { item ->
                            val remappedTemplateId = if (item.templateId != null) {
                                templateIdMap[item.templateId]
                            } else null
                            val newItem = item.copy(id = 0, templateId = remappedTemplateId)
                            database.timelineItemDao().insert(newItem)
                            importedItems++
                        }

                        payload.memos.forEach { memo ->
                            val newMemo = memo.copy(id = 0)
                            database.memoDao().insert(newMemo)
                            importedMemos++
                        }

                        ImportExecutionResult(
                            success = true,
                            importedTemplates = importedTemplates,
                            importedItems = importedItems,
                            importedMemos = importedMemos
                        )
                    }
                }
            }
        } catch (e: Exception) {
            ImportExecutionResult(
                success = false,
                errorMessage = e.localizedMessage ?: "インポート処理に失敗しました"
            )
        }
    }
}
