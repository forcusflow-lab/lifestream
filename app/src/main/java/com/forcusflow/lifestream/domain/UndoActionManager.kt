package com.forcusflow.lifestream.domain

import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import java.util.UUID

sealed class UndoPayload {
    data class DeleteItem(val item: TimelineItemEntity) : UndoPayload()
    data class QuickRecord(val createdItem: TimelineItemEntity, val templateId: Long?) : UndoPayload()
    data class MemoPromotion(
        val originalMemo: MemoEntity,
        val createdItem: TimelineItemEntity? = null,
        val createdTemplate: TemplateEntity? = null
    ) : UndoPayload()
    data class TimerComplete(val createdItem: TimelineItemEntity, val templateId: Long?) : UndoPayload()
}

data class UndoAction(
    val actionId: String = UUID.randomUUID().toString(),
    val description: String,
    val payload: UndoPayload,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thread-safe Undo Action Manager preventing race conditions, ID mismatches,
 * and double-execution across deletes, quick records, memo promotions, and timer completions.
 */
class UndoActionManager {
    private val actionHistory = mutableListOf<UndoAction>()
    private val consumedActionIds = mutableSetOf<String>()

    @Synchronized
    fun registerAction(description: String, payload: UndoPayload): UndoAction {
        val action = UndoAction(
            description = description,
            payload = payload
        )
        actionHistory.add(action)
        return action
    }

    @Synchronized
    fun getAction(actionId: String): UndoAction? {
        if (consumedActionIds.contains(actionId)) return null
        return actionHistory.find { it.actionId == actionId }
    }

    @Synchronized
    fun getLatestAction(): UndoAction? {
        return actionHistory.lastOrNull { !consumedActionIds.contains(it.actionId) }
    }

    @Synchronized
    fun markConsumed(actionId: String): Boolean {
        if (consumedActionIds.contains(actionId)) return false
        consumedActionIds.add(actionId)
        actionHistory.removeAll { it.actionId == actionId }
        return true
    }

    @Synchronized
    fun clear() {
        actionHistory.clear()
        consumedActionIds.clear()
    }
}
