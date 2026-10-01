package com.forcusflow.lifestream

import com.forcusflow.lifestream.data.MemoEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.domain.UndoActionManager
import com.forcusflow.lifestream.domain.UndoPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoActionManagerTest {

    @Test
    fun testRegisterAndConsumePreventsDoubleExecution() {
        val manager = UndoActionManager()

        val item = TimelineItemEntity(id = 1, title = "散歩", isDone = true)
        val action = manager.registerAction("散歩 を記録しました", UndoPayload.QuickRecord(item, null))

        assertNotNull(manager.getAction(action.actionId))
        assertEquals(action.actionId, manager.getLatestAction()?.actionId)

        // First consume succeeds
        assertTrue(manager.markConsumed(action.actionId))

        // Second consume must return false and action must no longer be retrievable
        assertFalse(manager.markConsumed(action.actionId))
        assertNull(manager.getAction(action.actionId))
        assertNull(manager.getLatestAction())
    }

    @Test
    fun testContinuousActionsDoNotConflict() {
        val manager = UndoActionManager()

        val item1 = TimelineItemEntity(id = 1, title = "タスク1", isDone = false)
        val item2 = TimelineItemEntity(id = 2, title = "タスク2", isDone = false)

        val action1 = manager.registerAction("タスク1を削除", UndoPayload.DeleteItem(item1))
        val action2 = manager.registerAction("タスク2を削除", UndoPayload.DeleteItem(item2))

        // Latest action is action2
        assertEquals(action2.actionId, manager.getLatestAction()?.actionId)

        // Consume action1 specifically via its ID (e.g. from an earlier snackbar)
        assertTrue(manager.markConsumed(action1.actionId))
        assertNull(manager.getAction(action1.actionId))

        // action2 is still available
        assertNotNull(manager.getAction(action2.actionId))
        assertEquals(action2.actionId, manager.getLatestAction()?.actionId)
    }

    @Test
    fun testMemoPromotionUndoPayload() {
        val manager = UndoActionManager()

        val memo = MemoEntity(id = 99, content = "牛乳を買う", createdAt = 1000L, updatedAt = 1000L)
        val createdItem = TimelineItemEntity(id = 200, title = "牛乳を買う", isDone = false)

        val action = manager.registerAction(
            "メモをToDoへ変換しました",
            UndoPayload.MemoPromotion(originalMemo = memo, createdItem = createdItem)
        )

        val retrieved = manager.getAction(action.actionId)
        assertNotNull(retrieved)
        val payload = retrieved!!.payload as UndoPayload.MemoPromotion
        assertEquals(99L, payload.originalMemo.id)
        assertEquals(200L, payload.createdItem?.id)
    }
}
