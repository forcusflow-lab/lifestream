package com.forcusflow.lifestream.domain

import android.content.Context
import androidx.room.withTransaction
import com.forcusflow.lifestream.data.AppDatabase
import com.forcusflow.lifestream.data.DatabaseSeeder
import com.forcusflow.lifestream.widget.WidgetSettingsManager

enum class ResetType {
    RELOAD_SAMPLE_DATA,          // 1. サンプルデータを再表示 (既存ユーザーデータは保持)
    CLEAR_RECORDS_AND_TEMPLATES, // 2. 記録とテンプレートを削除 (メモや設定は保持)
    FULL_INITIALIZE              // 3. すべてのデータを初期化 (完全リセット)
}

data class DataCounts(
    val timelineItemCount: Int,
    val templateCount: Int,
    val memoCount: Int,
    val dailyFocusCount: Int
)

class ResetDataUseCase(
    private val db: AppDatabase,
    private val context: Context
) {
    suspend fun getDataCounts(): DataCounts {
        val items = db.timelineItemDao().getAll().size
        val templates = db.templateDao().getAll().size
        val memos = db.memoDao().getAllList().size
        val focus = db.dailyFocusDao().getAllList().size
        return DataCounts(items, templates, memos, focus)
    }

    suspend fun executeReset(type: ResetType) {
        when (type) {
            ResetType.RELOAD_SAMPLE_DATA -> {
                // サンプルデータの再投入 (既存のユーザー作成データは自動削除せず、安全に追加)
                DatabaseSeeder.seed(db.templateDao(), db.timelineItemDao(), force = true)
            }
            ResetType.CLEAR_RECORDS_AND_TEMPLATES -> {
                db.withTransaction {
                    db.timelineItemDao().deleteAll()
                    db.templateDao().deleteAll()
                }
            }
            ResetType.FULL_INITIALIZE -> {
                db.withTransaction {
                    db.timelineItemDao().deleteAll()
                    db.templateDao().deleteAll()
                    db.memoDao().deleteAll()
                    db.dailyFocusDao().deleteAll()
                }
                WidgetSettingsManager.resetAllPreferences(context)
            }
        }
    }
}
