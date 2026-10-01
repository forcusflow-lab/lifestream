package com.forcusflow.lifestream

import com.forcusflow.lifestream.data.TemplateEntity
import com.forcusflow.lifestream.data.TimelineItemEntity
import com.forcusflow.lifestream.domain.BackupPayload
import com.forcusflow.lifestream.domain.BackupUseCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupUseCaseTest {

    private val json = Json { prettyPrint = true }
    private val backupUseCase = BackupUseCase()

    @Test
    fun testInvalidJsonDoesNotCrashAndReturnsError() {
        val invalidJson = "{ corrupt_json: true, unterminated string"
        val result = backupUseCase.validateJson(invalidJson)

        assertFalse(result.isValid)
        assertTrue(result.errorMessage?.contains("不正なJSON形式") == true)
    }

    @Test
    fun testEmptyJsonValidation() {
        val result = backupUseCase.validateJson("   ")
        assertFalse(result.isValid)
        assertEquals("データが空です", result.errorMessage)
    }

    @Test
    fun testValidJsonValidationWithCounts() {
        val payload = BackupPayload(
            version = 1,
            templates = listOf(
                TemplateEntity(id = 1, title = "散歩", type = "SIMPLE"),
                TemplateEntity(id = 2, title = "読書", type = "INTERVAL")
            ),
            timeline_items = listOf(
                TimelineItemEntity(id = 10, title = "散歩した", isDone = true, templateId = 1)
            ),
            memos = emptyList()
        )

        val jsonStr = json.encodeToString(payload)
        val result = backupUseCase.validateJson(jsonStr)

        assertTrue(result.isValid)
        assertEquals(2, result.templateCount)
        assertEquals(1, result.timelineItemCount)
        assertEquals(0, result.memoCount)
    }
}
