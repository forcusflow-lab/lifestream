package com.forcusflow.lifestream.domain

import androidx.room.withTransaction
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity

sealed class PromotionTarget {
    data class ToDo(val scheduledAt: Long? = null) : PromotionTarget()
    data class Done(val completedAt: Long = System.currentTimeMillis()) : PromotionTarget()
    data class Periodic(val intervalDays: Int = 7) : PromotionTarget()
}

data class PromotionResult(
    val success: Boolean,
    val undoAction: UndoAction? = null,
    val errorMessage: String? = null
)

/**
 * Transactional Memo Promotion UseCase.
 * Converts memos into ToDo, Done, or Periodic items with atomic deletion of the original memo
 * and full rollback / Undo capability (restores original memo, deletes converted target).
 */
class MemoPromotionUseCase(
    private val db: AppDatabase,
    private val undoActionManager: UndoActionManager
) {
    suspend fun promoteMemo(
        memo: MemoEntity,
        target: PromotionTarget
    ): PromotionResult {
        return try {
            var createdItem: TimelineItemEntity? = null
            var createdTemplate: TemplateEntity? = null

            db.withTransaction {
                when (target) {
                    is PromotionTarget.ToDo -> {
                        val item = TimelineItemEntity(
                            title = memo.content.trim(),
                            isDone = false,
                            scheduledAt = target.scheduledAt
                        )
                        val id = db.timelineItemDao().insert(item)
                        createdItem = item.copy(id = id)
                    }
                    is PromotionTarget.Done -> {
                        val item = TimelineItemEntity(
                            title = memo.content.trim(),
                            isDone = true,
                            completedAt = target.completedAt
                        )
                        val id = db.timelineItemDao().insert(item)
                        createdItem = item.copy(id = id)
                    }
                    is PromotionTarget.Periodic -> {
                        val template = TemplateEntity(
                            title = memo.content.trim(),
                            type = "INTERVAL",
                            intervalDays = target.intervalDays,
                            iconKey = "🔄",
                            colorHex = "#34C759",
                            lastCompletedAt = System.currentTimeMillis()
                        )
                        val id = db.templateDao().insert(template)
                        createdTemplate = template.copy(id = id)
                    }
                }
                // Atomically delete the original memo
                db.memoDao().delete(memo)
            }

            val desc = when (target) {
                is PromotionTarget.ToDo -> "メモをToDoに変換し、片付けました"
                is PromotionTarget.Done -> "メモを完了ログに変換し、片付けました"
                is PromotionTarget.Periodic -> "メモを周期タスクに変換し、片付けました"
            }

            val action = undoActionManager.registerAction(
                description = desc,
                payload = UndoPayload.MemoPromotion(
                    originalMemo = memo,
                    createdItem = createdItem,
                    createdTemplate = createdTemplate
                )
            )

            PromotionResult(success = true, undoAction = action)
        } catch (e: Exception) {
            PromotionResult(success = false, errorMessage = e.localizedMessage ?: "変換に失敗しました")
        }
    }

    suspend fun undoPromotion(actionId: String): Boolean {
        val action = undoActionManager.getAction(actionId) ?: return false
        val payload = action.payload as? UndoPayload.MemoPromotion ?: return false

        return try {
            db.withTransaction {
                payload.createdItem?.let { db.timelineItemDao().delete(it) }
                payload.createdTemplate?.let { db.templateDao().delete(it) }
                db.memoDao().insert(payload.originalMemo)
            }
            undoActionManager.markConsumed(actionId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
